package api

import (
	"context"
	"net/http"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humagin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
)

// --- Input / output types ---

// SetupDiagnosticsInput is the empty input for GET /api/setup/diagnostics.
type SetupDiagnosticsInput struct{}

// SetupDiagnosticsResponse is the wire format for GET /api/setup/diagnostics.
type SetupDiagnosticsResponse struct {
	Checks []DiagnosticCheck `json:"checks" doc:"Ordered list of server health checks."`
}

// SetupDiagnosticsOutput wraps the diagnostics response.
type SetupDiagnosticsOutput struct {
	Body SetupDiagnosticsResponse
}

// RegisterSetupDiagnosticsRoutes wires the setup diagnostics endpoint into the
// huma API. The endpoint is intentionally anonymous when no users exist yet
// (fresh install / first-time setup); once any user is created, the handler
// self-enforces admin-only access by validating the bearer token inline.
func RegisterSetupDiagnosticsRoutes(api huma.API, h *SetupHandler) {
	huma.Register(api, huma.Operation{
		OperationID: "getSetupDiagnostics",
		Method:      http.MethodGet,
		Path:        "/api/setup/diagnostics",
		Summary:     "Run server configuration health checks",
		Description: "Returns ordered server health checks (database, JWT secret, encryption key, game dirs, storage, IGDB, SteamGridDB). Public during first-time setup when no users exist; requires admin/owner auth afterwards.",
		Tags:        []string{"setup"},
		Middlewares: huma.Middlewares{setupDiagnosticsRequestContext},
	}, h.HumaDiagnostics)
}

// HumaDiagnostics is the huma handler for GET /api/setup/diagnostics.
//
// Mirrors the raw gin handler's dual-mode authentication: anonymous access is
// allowed when no users exist (fresh install), otherwise the caller must supply
// a valid bearer token for an active admin or owner account.
func (h *SetupHandler) HumaDiagnostics(ctx context.Context, _ *SetupDiagnosticsInput) (*SetupDiagnosticsOutput, error) {
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)

	if userCount > 0 {
		// Require admin/owner auth — pull the bearer token from the underlying
		// gin request. This mirrors the anonymous→authenticated transition in
		// the raw gin handler rather than registering the middleware at the
		// route level (which would reject pre-setup callers).
		gctx, ok := ctx.Value(humaReqCtxKey{}).(humaRequestContext)
		if !ok {
			return nil, huma.Error401Unauthorized("authentication required")
		}
		token := gctx.BearerToken
		if token == "" {
			return nil, huma.Error401Unauthorized("authentication required")
		}

		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err != nil {
			return nil, huma.Error401Unauthorized("invalid token")
		}

		if IsTokenBlacklisted(h.DB, token) {
			return nil, huma.Error401Unauthorized("token revoked")
		}

		var user db.User
		if err := h.DB.Select("id", "role", "disabled", "token_version").First(&user, claims.UserID).Error; err != nil {
			return nil, huma.Error401Unauthorized("user not found")
		}
		if user.Disabled {
			return nil, huma.Error403Forbidden("account disabled")
		}
		if claims.TokenVersion != user.TokenVersion {
			return nil, huma.Error401Unauthorized("token invalidated")
		}
		if user.Role != "admin" && user.Role != "owner" {
			return nil, huma.Error403Forbidden("admin access required")
		}
	}

	checks := h.runDiagnostics()
	return &SetupDiagnosticsOutput{Body: SetupDiagnosticsResponse{Checks: checks}}, nil
}

// humaReqCtxKey is the context key used by setupDiagnosticsRequestContext to
// stash the bearer token extracted from the incoming gin request. Kept local
// so it can't collide with other huma context keys in this package.
type humaReqCtxKey struct{}

// humaRequestContext carries per-request bits that the diagnostics handler
// needs to make its auth decision.
type humaRequestContext struct {
	BearerToken string
}

// setupDiagnosticsRequestContext is a huma middleware that extracts the Bearer
// token from the incoming gin request and stashes it in the huma context. This
// lets the diagnostics handler run its own auth check for the
// users-exist-but-token-is-invalid path without forcing the operation itself
// to require a token (which would block first-time setup).
func setupDiagnosticsRequestContext(ctx huma.Context, next func(huma.Context)) {
	gctx := humagin.Unwrap(ctx)
	var token string
	if header := gctx.GetHeader("Authorization"); header != "" {
		parts := strings.SplitN(header, " ", 2)
		if len(parts) == 2 && parts[0] == "Bearer" {
			token = parts[1]
		}
	}
	next(huma.WithValue(ctx, humaReqCtxKey{}, humaRequestContext{BearerToken: token}))
}
