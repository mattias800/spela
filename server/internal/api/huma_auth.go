package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humagin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// huma context value keys. Defined as a private type so external packages
// can't accidentally collide; access them through the typed helpers below.
type humaCtxKey int

const (
	ctxKeyUserID humaCtxKey = iota
	ctxKeyUsername
	ctxKeyUserRole
)

// RequireAuth returns a huma middleware that runs the existing gin
// AuthMiddleware logic (JWT validation, blacklist check, disabled-user check,
// token-version check) and bridges the resulting userId/username/role into
// the huma context so handlers can read them via the helpers below.
//
// This is the bridge between the existing gin auth stack and huma operations
// during the incremental migration. Once every handler is migrated to huma
// we can either keep this bridge or rewrite RequireAuth as a pure huma
// middleware that doesn't depend on gin's c.Set/c.Get pattern.
func RequireAuth(jwtSecret string, database *gorm.DB) func(huma.Context, func(huma.Context)) {
	ginAuth := AuthMiddleware(jwtSecret, database)
	return func(ctx huma.Context, next func(huma.Context)) {
		gctx := humagin.Unwrap(ctx)
		ginAuth(gctx)
		if gctx.IsAborted() {
			return
		}
		// Bridge gin context values into huma context so handlers can read
		// them with the typed helpers below.
		if uid, ok := gctx.Get("userId"); ok {
			ctx = huma.WithValue(ctx, ctxKeyUserID, uid)
		}
		if uname, ok := gctx.Get("username"); ok {
			ctx = huma.WithValue(ctx, ctxKeyUsername, uname)
		}
		if role, ok := gctx.Get("role"); ok {
			ctx = huma.WithValue(ctx, ctxKeyUserRole, role)
		}
		next(ctx)
	}
}

// RequireAdmin returns a huma middleware that requires the authenticated
// user to be an admin or owner. Must be composed AFTER RequireAuth so the
// role value is present in the context. Takes the API for content-negotiated
// error responses.
func RequireAdmin(api huma.API) func(huma.Context, func(huma.Context)) {
	return func(ctx huma.Context, next func(huma.Context)) {
		role := UserRoleFromContext(ctx.Context())
		if !db.IsAdminOrOwner(role) {
			_ = huma.WriteErr(api, ctx, http.StatusForbidden,
				"You don't have permission to access this resource.")
			return
		}
		next(ctx)
	}
}

// UserRateLimitMiddleware wraps a *RateLimiter's UserRateLimit gin middleware
// so huma operations can enforce the same per-user request budget as the raw
// gin routes. Must be composed AFTER RequireAuth so the userId is set on the
// underlying gin context. Mirrors the pattern used in RequireAuth — we unwrap
// the gin context, delegate to the existing gin handler, and bail out if the
// handler aborted the request.
func UserRateLimitMiddleware(rl *RateLimiter) func(huma.Context, func(huma.Context)) {
	ginLimiter := rl.UserRateLimit()
	return func(ctx huma.Context, next func(huma.Context)) {
		gctx := humagin.Unwrap(ctx)
		ginLimiter(gctx)
		if gctx.IsAborted() {
			return
		}
		next(ctx)
	}
}

// UserIDFromContext returns the authenticated user ID, or 0 if no user is
// present (handler called without RequireAuth, or auth failed).
func UserIDFromContext(ctx context.Context) uint {
	if v := ctx.Value(ctxKeyUserID); v != nil {
		if uid, ok := v.(uint); ok {
			return uid
		}
	}
	return 0
}

// UsernameFromContext returns the authenticated username, or "" if not present.
func UsernameFromContext(ctx context.Context) string {
	if v := ctx.Value(ctxKeyUsername); v != nil {
		if uname, ok := v.(string); ok {
			return uname
		}
	}
	return ""
}

// UserRoleFromContext returns the authenticated user role, or "" if not present.
func UserRoleFromContext(ctx context.Context) db.UserRole {
	if v := ctx.Value(ctxKeyUserRole); v != nil {
		if role, ok := v.(db.UserRole); ok {
			return role
		}
	}
	return ""
}
