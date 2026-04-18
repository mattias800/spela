package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humagin"
	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Request / response shapes -----------------------------------------------

// AuthLoginRequest is the body for POST /api/auth/login.
type AuthLoginRequest struct {
	Username string `json:"username" minLength:"1" doc:"Username to sign in as."`
	Password string `json:"password" minLength:"1" maxLength:"72" doc:"Plain-text password (max 72 bytes — bcrypt limit)."`
}

// AuthRegisterRequest is the body for POST /api/auth/register and /api/auth/setup.
// Matches the original gin binding (alphanum, min=3, max=64 for username; valid
// email; min=8, max=72 for password).
type AuthRegisterRequest struct {
	Username string `json:"username" minLength:"3" maxLength:"64" pattern:"^[a-zA-Z0-9]+$" doc:"New account username (3-64 alphanumeric characters)."`
	Email    string `json:"email" format:"email" doc:"New account email."`
	Password string `json:"password" minLength:"8" maxLength:"72" doc:"New account password (8-72 characters)."`
}

// AuthRefreshRequest is the body for POST /api/auth/refresh.
type AuthRefreshRequest struct {
	RefreshToken string `json:"refreshToken" minLength:"1" doc:"Raw refresh token previously issued by login/register/refresh."`
}

// AuthRegisterResponse is the union-style response for POST /api/auth/register.
// Successful registration returns the AuthLoginResponse fields (accessToken,
// refreshToken, user) and HTTP 201. A successful registration that requires
// admin approval returns { "pending": true, "message": "..." } with HTTP 202.
// Using a single struct with omitempty fields preserves the historical wire
// format exactly while keeping the OpenAPI spec a single response type.
type AuthRegisterResponse struct {
	// Pending-approval fields.
	Pending bool   `json:"pending,omitempty" doc:"True when the new account is awaiting admin approval. When true, no tokens are returned."`
	Message string `json:"message,omitempty" doc:"Human-readable status message (only set when pending)."`

	// Normal success fields (omitted when pending).
	AccessToken  string        `json:"accessToken,omitempty" doc:"Bearer access token."`
	RefreshToken string        `json:"refreshToken,omitempty" doc:"Refresh token (rotate via /api/auth/refresh)."`
	User         *UserResponse `json:"user,omitempty" doc:"Registered user profile."`
}

// SetupStatusResponse is the JSON shape for GET /api/auth/setup-status.
type SetupStatusResponse struct {
	NeedsSetup          bool  `json:"needsSetup" doc:"True when no user accounts exist and the server requires first-time setup."`
	RegistrationEnabled bool  `json:"registrationEnabled" doc:"True when new-account registration is currently allowed."`
	GameCount           int64 `json:"gameCount" doc:"Total number of games indexed on the server."`
}

// --- Huma inputs / outputs ---------------------------------------------------

// AuthLoginInput wraps the login body.
type AuthLoginInput struct {
	Body AuthLoginRequest
}

// AuthLoginOutput wraps the login response body.
type AuthLoginOutput struct {
	Body AuthLoginResponse
}

// AuthRegisterInput wraps the register body.
type AuthRegisterInput struct {
	Body AuthRegisterRequest
}

// AuthRegisterOutput uses huma's dynamic-status pattern: 201 Created for a
// standard registration, 202 Accepted when the account requires admin approval.
type AuthRegisterOutput struct {
	Status int
	Body   AuthRegisterResponse
}

// AuthRefreshInput wraps the refresh body.
type AuthRefreshInput struct {
	Body AuthRefreshRequest
}

// AuthRefreshOutput wraps the refresh response body.
type AuthRefreshOutput struct {
	Body AuthLoginResponse
}

// AuthSetupInput wraps the setup body (same shape as register).
type AuthSetupInput struct {
	Body AuthRegisterRequest
}

// AuthSetupOutput wraps the setup response body.
type AuthSetupOutput struct {
	Body AuthLoginResponse
}

// SetupStatusInput is the empty input for GET /api/auth/setup-status.
type SetupStatusInput struct{}

// SetupStatusOutput wraps the setup-status response body.
type SetupStatusOutput struct {
	Body SetupStatusResponse
}

// AuthLogoutInput captures the Authorization header so the handler can
// blacklist the bearer access token. The header is optional — clients can
// also pass the token via ?token= as a fallback (matches the gin handler).
type AuthLogoutInput struct {
	Authorization string `header:"Authorization" doc:"Bearer access token to blacklist."`
	TokenQuery    string `query:"token" doc:"Fallback access token to blacklist when no Authorization header is sent."`
}

// AuthLogoutResponse mirrors the historical gin response body.
type AuthLogoutResponse struct {
	Message string `json:"message" doc:"Confirmation message."`
}

// AuthLogoutOutput wraps the logout response body.
type AuthLogoutOutput struct {
	Body AuthLogoutResponse
}

// --- Request-context bridging ------------------------------------------------

// authReqCtxKey is the private context key under which we stash the underlying
// *gin.Context for auth handlers. This lets us reuse recordSecurityEventCtx —
// which needs the client IP and request path from gin — inside huma handlers
// that otherwise only receive a context.Context.
type authReqCtxKey struct{}

// authRequestContextMiddleware is a huma middleware that attaches the
// underlying *gin.Context to the huma context so handlers can read the client
// IP / request path when recording security events. Must run before any handler
// that calls securityEventCtxFromHuma.
func authRequestContextMiddleware(ctx huma.Context, next func(huma.Context)) {
	gctx := humagin.Unwrap(ctx)
	next(huma.WithValue(ctx, authReqCtxKey{}, gctx))
}

// ginContextFromCtx returns the *gin.Context stashed in ctx by
// authRequestContextMiddleware, or nil if the middleware hasn't run (e.g. in
// a unit test that invokes the handler directly).
func ginContextFromCtx(ctx context.Context) *gin.Context {
	if v := ctx.Value(authReqCtxKey{}); v != nil {
		if g, ok := v.(*gin.Context); ok {
			return g
		}
	}
	return nil
}

// recordSecurityEventFromHumaCtx records a security event using the *gin.Context
// stashed by authRequestContextMiddleware. Falls back to calling
// db.RecordSecurityEvent directly (without IP/Path) if the middleware wasn't
// applied — keeps the function usable in tests that bypass routing.
func recordSecurityEventFromHumaCtx(database *gorm.DB, ctx context.Context, in db.SystemEventInput) {
	if g := ginContextFromCtx(ctx); g != nil {
		if in.IP == "" {
			in.IP = g.ClientIP()
		}
		if in.Path == "" && g.Request != nil && g.Request.URL != nil {
			in.Path = g.Request.URL.Path
		}
	}
	db.RecordSecurityEvent(database, in)
}

// --- IP rate-limit adapter for huma ------------------------------------------

// IPRateLimitMiddleware wraps a RateLimiter's IP-scoped gin middleware so huma
// operations can enforce the same per-IP request budget as the raw gin auth
// endpoints. This is the huma equivalent of UserRateLimitMiddleware for anon
// routes — login/register/setup/refresh all run under IP-scoped limiters
// because the caller isn't authenticated yet.
func IPRateLimitMiddleware(rl *RateLimiter) func(huma.Context, func(huma.Context)) {
	ginLimiter := rl.RateLimit()
	return func(ctx huma.Context, next func(huma.Context)) {
		gctx := humagin.Unwrap(ctx)
		ginLimiter(gctx)
		if gctx.IsAborted() {
			return
		}
		next(ctx)
	}
}

// --- Route registration ------------------------------------------------------

// RegisterAuthRoutes wires the public authentication endpoints (login, register,
// refresh, setup, setup-status) into the huma API. Rate limiters mirror the raw
// gin routes: authLimiter protects login/register/setup, refreshLimiter guards
// /refresh, and setup-status is unrestricted. Logout is registered separately
// in RegisterAuthProtectedRoutes since it requires an authenticated user.
func RegisterAuthRoutes(api huma.API, h *AuthHandler, authLimiter *RateLimiter, refreshLimiter *RateLimiter) {
	authRL := IPRateLimitMiddleware(authLimiter)
	refreshRL := IPRateLimitMiddleware(refreshLimiter)
	reqCtx := huma.Middlewares{authRequestContextMiddleware}

	huma.Register(api, huma.Operation{
		OperationID: "authLogin",
		Method:      http.MethodPost,
		Path:        "/api/auth/login",
		Summary:     "Sign in",
		Description: "Authenticates a user and returns access + refresh tokens. Enforces lockout after repeated failures and performs a dummy bcrypt comparison on unknown usernames to prevent timing-based username enumeration.",
		Tags:        []string{"auth"},
		Middlewares: append(reqCtx, authRL),
	}, h.HumaLogin)

	huma.Register(api, huma.Operation{
		OperationID:   "authRegister",
		Method:        http.MethodPost,
		Path:          "/api/auth/register",
		Summary:       "Register a new account",
		Description:   "Creates a new user account. The very first user becomes the server owner and is signed in immediately. Subsequent registrations are pending admin approval (HTTP 202) unless registration has been disabled.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"auth"},
		Middlewares:   append(reqCtx, authRL),
	}, h.HumaRegister)

	huma.Register(api, huma.Operation{
		OperationID: "authRefresh",
		Method:      http.MethodPost,
		Path:        "/api/auth/refresh",
		Summary:     "Rotate refresh token",
		Description: "Exchanges a refresh token for new access + refresh tokens. Uses token families for replay detection: presenting a consumed token revokes the entire family.",
		Tags:        []string{"auth"},
		Middlewares: append(reqCtx, refreshRL),
	}, h.HumaRefresh)

	huma.Register(api, huma.Operation{
		OperationID:   "authSetup",
		Method:        http.MethodPost,
		Path:          "/api/auth/setup",
		Summary:       "Perform first-time server setup",
		Description:   "Creates the initial owner account. Fails with 403 after a user already exists.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"auth"},
		Middlewares:   append(reqCtx, authRL),
	}, h.HumaSetup)

	huma.Register(api, huma.Operation{
		OperationID: "authSetupStatus",
		Method:      http.MethodGet,
		Path:        "/api/auth/setup-status",
		Summary:     "Get server setup status",
		Description: "Returns whether the server needs first-time setup, whether new-account registration is enabled, and how many games are indexed.",
		Tags:        []string{"auth"},
	}, h.HumaSetupStatus)
}

// --- Handlers ----------------------------------------------------------------

// HumaLogin is the huma implementation of POST /api/auth/login. Security
// behaviour (lockout, dummy bcrypt, system events, status codes, user-facing
// error messages) matches the raw gin Login handler 1:1 so clients cannot
// distinguish which stack served the request.
func (h *AuthHandler) HumaLogin(ctx context.Context, in *AuthLoginInput) (*AuthLoginOutput, error) {
	req := in.Body

	// Check account lockout before proceeding.
	if h.isLockedOut(req.Username) {
		recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
			EventType: db.SystemEventLoginLocked,
			Username:  req.Username,
		})
		return nil, huma.NewError(http.StatusTooManyRequests, "Your account has been temporarily locked due to too many failed login attempts. Please try again later.")
	}

	var user db.User
	userFound := h.DB.Where("username = ?", req.Username).First(&user).Error == nil

	if !userFound {
		// Run bcrypt against a dummy hash to prevent timing-based username
		// enumeration (see auth_handler.go comment).
		auth.CheckPassword(req.Password, dummyBcryptHash)
		h.recordFailedLogin(req.Username)
		recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
			EventType: db.SystemEventLoginFailed,
			Reason:    "unknown_user",
			Username:  req.Username,
		})
		return nil, huma.NewError(http.StatusUnauthorized, "Invalid username or password.")
	}

	if !auth.CheckPassword(req.Password, user.PasswordHash) {
		h.recordFailedLogin(req.Username)
		// Re-read the attempt to log the new failure count and detect lockout
		// escalation.
		var attempt db.LoginAttempt
		h.DB.Where("username = ?", hashUsername(req.Username)).First(&attempt)
		recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
			EventType: db.SystemEventLoginFailed,
			Reason:    "bad_password",
			Username:  req.Username,
			UserID:    &user.ID,
			Metadata:  db.LoginFailedMetadata{FailedCount: attempt.FailedCount},
		})
		if attempt.FailedCount >= maxLoginAttempts {
			recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
				EventType: db.SystemEventAccountLocked,
				Username:  req.Username,
				UserID:    &user.ID,
				Metadata: db.AccountLockedMetadata{
					FailedCount: attempt.FailedCount,
					LockedUntil: attempt.LockedUntil,
				},
			})
		}
		return nil, huma.NewError(http.StatusUnauthorized, "Invalid username or password.")
	}

	if user.PendingApproval {
		recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
			EventType: db.SystemEventLoginBlocked,
			Reason:    "pending_approval",
			Username:  req.Username,
			UserID:    &user.ID,
		})
		return nil, huma.NewError(http.StatusForbidden, "Your account is awaiting admin approval. Please try again once an admin has approved it.")
	}

	if user.Disabled {
		recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
			EventType: db.SystemEventLoginBlocked,
			Reason:    "disabled",
			Username:  req.Username,
			UserID:    &user.ID,
		})
		return nil, huma.NewError(http.StatusForbidden, "Your account has been disabled. Please contact an administrator.")
	}

	// Successful login — clear any failed attempts.
	h.clearFailedLogins(req.Username)
	recordSecurityEventFromHumaCtx(h.DB, ctx, db.SystemEventInput{
		EventType: db.SystemEventLoginSuccess,
		Username:  req.Username,
		UserID:    &user.ID,
	})

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), h.JWTSecret, user.TokenVersion)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Sign in failed. Please try again.")
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Sign in failed. Please try again.")
	}

	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	return &AuthLoginOutput{Body: AuthLoginResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	}}, nil
}

// HumaRegister is the huma implementation of POST /api/auth/register. Returns
// 201 Created with tokens on success, or 202 Accepted with {pending, message}
// when the new account is awaiting admin approval — matching the raw gin shape.
func (h *AuthHandler) HumaRegister(ctx context.Context, in *AuthRegisterInput) (*AuthRegisterOutput, error) {
	req := in.Body

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Registration failed. Please try again.")
	}

	// Use a transaction to atomically check registration eligibility, determine
	// the role (first user becomes owner), and create the user. This prevents a
	// TOCTOU race where two simultaneous registrations could both see
	// userCount=0 and both get the owner role.
	//
	// The transaction callback returns a *humaError (via huma.NewError) when it
	// needs to surface a specific status/message to the client; a generic error
	// (e.g. db failure) is mapped to a 500 afterwards.
	var user db.User
	var txErr error
	_ = h.DB.Transaction(func(tx *gorm.DB) error {
		var count int64
		tx.Model(&db.User{}).Where("username = ? OR email = ?", req.Username, req.Email).Count(&count)
		if count > 0 {
			txErr = huma.NewError(http.StatusConflict, "That username or email is already taken.")
			return fmt.Errorf("duplicate")
		}

		var totalUsers int64
		tx.Model(&db.User{}).Count(&totalUsers)
		if totalUsers > 0 {
			var setting db.ServerSetting
			if err := tx.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
				if setting.Value == "false" {
					txErr = huma.NewError(http.StatusForbidden, "Registration is currently disabled. Please contact an administrator.")
					return fmt.Errorf("disabled")
				}
			}
		}

		role := db.RoleUser
		if totalUsers == 0 {
			role = db.RoleOwner
		}
		pendingApproval := role != db.RoleOwner

		user = db.User{
			Username:        req.Username,
			Email:           req.Email,
			PasswordHash:    hash,
			Role:            role,
			PendingApproval: pendingApproval,
		}
		if err := tx.Create(&user).Error; err != nil {
			txErr = huma.NewError(http.StatusInternalServerError, "Registration failed. Please try again.")
			return err
		}
		return nil
	})
	if txErr != nil {
		return nil, txErr
	}

	// Non-owner registrations require admin approval; return 202 Accepted with
	// {pending: true, message: "..."} and no tokens. Matches the raw gin shape.
	if user.PendingApproval {
		return &AuthRegisterOutput{
			Status: http.StatusAccepted,
			Body: AuthRegisterResponse{
				Pending: true,
				Message: "Your account is pending admin approval.",
			},
		}, nil
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), h.JWTSecret)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Account created but sign-in failed. Please sign in manually.")
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Account created but sign-in failed. Please sign in manually.")
	}

	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	userResp := ToUserResponse(user)
	return &AuthRegisterOutput{
		Status: http.StatusCreated,
		Body: AuthRegisterResponse{
			AccessToken:  accessToken,
			RefreshToken: refreshToken,
			User:         &userResp,
		},
	}, nil
}

// HumaRefresh is the huma implementation of POST /api/auth/refresh. Preserves
// replay-detection semantics: presenting a consumed token (or a token that
// matches a consumed hash) revokes the entire token family.
func (h *AuthHandler) HumaRefresh(_ context.Context, in *AuthRefreshInput) (*AuthRefreshOutput, error) {
	req := in.Body

	tokenHash := auth.HashRefreshToken(req.RefreshToken)

	var rt db.RefreshToken
	if err := h.DB.Where("token = ?", tokenHash).First(&rt).Error; err != nil {
		// Token not found — check if it was a consumed token (replay detection).
		var consumed db.RefreshToken
		if h.DB.Unscoped().Where("token = ? AND consumed = ?", tokenHash, true).First(&consumed).Error == nil {
			slog.Warn("refresh token replay detected, revoking token family",
				"user_id", consumed.UserID, "family", consumed.TokenFamily)
			h.DB.Where("token_family = ? AND user_id = ?", consumed.TokenFamily, consumed.UserID).
				Delete(&db.RefreshToken{})
		}
		return nil, huma.NewError(http.StatusUnauthorized, "Your session has expired. Please sign in again.")
	}

	if rt.Consumed {
		slog.Warn("consumed refresh token presented", "user_id", rt.UserID, "family", rt.TokenFamily)
		h.DB.Where("token_family = ? AND user_id = ?", rt.TokenFamily, rt.UserID).
			Delete(&db.RefreshToken{})
		return nil, huma.NewError(http.StatusUnauthorized, "Your session has expired. Please sign in again.")
	}

	if time.Now().After(rt.ExpiresAt) {
		h.DB.Delete(&rt)
		return nil, huma.NewError(http.StatusUnauthorized, "Your session has expired. Please sign in again.")
	}

	var user db.User
	if err := h.DB.First(&user, rt.UserID).Error; err != nil {
		return nil, huma.NewError(http.StatusUnauthorized, "Your account could not be found. Please sign in again.")
	}

	if user.Disabled {
		h.DB.Where("token_family = ? AND user_id = ?", rt.TokenFamily, rt.UserID).
			Delete(&db.RefreshToken{})
		return nil, huma.NewError(http.StatusForbidden, "Your account has been disabled. Please contact an administrator.")
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), h.JWTSecret, user.TokenVersion)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Session refresh failed. Please sign in again.")
	}

	newRefreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Session refresh failed. Please sign in again.")
	}

	family := rt.TokenFamily
	if family == "" {
		family = generateTokenFamily()
	}

	newRT := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(newRefreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: family,
	}
	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&rt).Update("consumed", true).Error; err != nil {
			return err
		}
		return tx.Create(&newRT).Error
	}); err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Session refresh failed. Please sign in again.")
	}

	return &AuthRefreshOutput{Body: AuthLoginResponse{
		AccessToken:  accessToken,
		RefreshToken: newRefreshToken,
		User:         ToUserResponse(user),
	}}, nil
}

// HumaSetup is the huma implementation of POST /api/auth/setup. Creates the
// initial owner account; fails with 403 if a user already exists.
func (h *AuthHandler) HumaSetup(_ context.Context, in *AuthSetupInput) (*AuthSetupOutput, error) {
	req := in.Body

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Setup failed. Please try again.")
	}

	var user db.User
	var txErr error
	_ = h.DB.Transaction(func(tx *gorm.DB) error {
		var userCount int64
		tx.Model(&db.User{}).Count(&userCount)
		if userCount > 0 {
			txErr = huma.NewError(http.StatusForbidden, "Setup has already been completed.")
			return fmt.Errorf("setup already completed")
		}

		user = db.User{
			Username:     req.Username,
			Email:        req.Email,
			PasswordHash: hash,
			Role:         db.RoleOwner,
		}
		if err := tx.Create(&user).Error; err != nil {
			txErr = huma.NewError(http.StatusInternalServerError, "Setup failed. Please try again.")
			return err
		}
		return nil
	})
	if txErr != nil {
		return nil, txErr
	}

	accessToken, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), h.JWTSecret)
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Setup failed. Please try again.")
	}

	refreshToken, err := auth.GenerateRefreshToken()
	if err != nil {
		return nil, huma.NewError(http.StatusInternalServerError, "Setup failed. Please try again.")
	}

	rt := db.RefreshToken{
		UserID:      user.ID,
		Token:       auth.HashRefreshToken(refreshToken),
		ExpiresAt:   time.Now().Add(auth.RefreshTokenDuration),
		TokenFamily: generateTokenFamily(),
	}
	h.DB.Create(&rt)

	return &AuthSetupOutput{Body: AuthLoginResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		User:         ToUserResponse(user),
	}}, nil
}

// HumaSetupStatus is the huma implementation of GET /api/auth/setup-status.
func (h *AuthHandler) HumaSetupStatus(_ context.Context, _ *SetupStatusInput) (*SetupStatusOutput, error) {
	var userCount int64
	h.DB.Model(&db.User{}).Count(&userCount)

	var gameCount int64
	h.DB.Model(&db.Game{}).Count(&gameCount)

	registrationEnabled := true
	var setting db.ServerSetting
	if err := h.DB.Where("key = ?", "registration_enabled").First(&setting).Error; err == nil {
		registrationEnabled = setting.Value != "false"
	}

	return &SetupStatusOutput{Body: SetupStatusResponse{
		NeedsSetup:          userCount == 0,
		RegistrationEnabled: registrationEnabled,
		GameCount:           gameCount,
	}}, nil
}

// RegisterAuthProtectedRoutes wires authentication endpoints that require an
// already-authenticated user — currently just /api/auth/logout. Kept in a
// separate registration func so the public RegisterAuthRoutes doesn't depend
// on the JWT secret + DB it needs for RequireAuth middleware composition.
func RegisterAuthProtectedRoutes(api huma.API, h *AuthHandler, jwtSecret string, database *gorm.DB) {
	huma.Register(api, huma.Operation{
		OperationID: "authLogout",
		Method:      http.MethodPost,
		Path:        "/api/auth/logout",
		Summary:     "Sign out",
		Description: "Blacklists the bearer access token (preventing reuse for its remaining lifetime) and revokes every refresh token belonging to the authenticated user, signing them out across all devices.",
		Tags:        []string{"auth"},
		Security:    []map[string][]string{{"bearer": {}}},
		Middlewares: huma.Middlewares{RequireAuth(jwtSecret, database)},
	}, h.HumaLogout)
}

// HumaLogout is the huma implementation of POST /api/auth/logout. Mirrors the
// gin Logout handler 1:1: blacklists the bearer access token (so it can't be
// reused for its remaining lifetime) and deletes every refresh token belonging
// to the user (signs them out everywhere). The response body matches the
// historical gin shape so existing clients aren't affected.
func (h *AuthHandler) HumaLogout(ctx context.Context, in *AuthLogoutInput) (*AuthLogoutOutput, error) {
	token := ""
	if in.Authorization != "" {
		parts := splitAuthHeader(in.Authorization)
		if len(parts) == 2 && parts[0] == "Bearer" {
			token = parts[1]
		}
	}
	if token == "" {
		token = in.TokenQuery
	}

	if token != "" {
		if claims, err := auth.ValidateAccessToken(token, h.JWTSecret); err == nil && claims.ExpiresAt != nil {
			hash := sha256.Sum256([]byte(token))
			h.DB.Create(&db.TokenBlacklist{
				TokenHash: hex.EncodeToString(hash[:]),
				ExpiresAt: claims.ExpiresAt.Time,
			})
		}
	}

	uid := UserIDFromContext(ctx)
	h.DB.Where("user_id = ?", uid).Delete(&db.RefreshToken{})

	return &AuthLogoutOutput{Body: AuthLogoutResponse{Message: "logged out"}}, nil
}

// splitAuthHeader splits an Authorization header into at most 2 parts on
// whitespace. Pulled out as a helper so both the huma handler above and the
// existing gin handler can share parsing semantics if needed in the future.
func splitAuthHeader(h string) []string {
	return strings.SplitN(h, " ", 2)
}
