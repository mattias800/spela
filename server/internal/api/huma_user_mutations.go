package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humagin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)


// UpdateProfileInput is the input for PUT /api/user/profile.
type UpdateProfileInput struct {
	Body UpdateProfileRequest
}

// UpdateProfileOutput wraps the updated profile for the huma response envelope.
type UpdateProfileOutput struct {
	Body UserResponse
}

// UpdatePreferencesInput is the input for PUT /api/user/preferences.
type UpdatePreferencesInput struct {
	Body UpdatePreferencesRequest
}

// UpdatePreferencesOutput wraps the updated preferences for the huma response envelope.
type UpdatePreferencesOutput struct {
	Body UserPreferencesResponse
}

// ChangePasswordRequest is the body for PUT /api/user/password.
type ChangePasswordRequest struct {
	CurrentPassword string `json:"currentPassword" doc:"Current password, required to authenticate the change."`
	NewPassword     string `json:"newPassword" minLength:"8" maxLength:"72" doc:"New password (8-72 characters)."`
}

// ChangePasswordInput is the input for PUT /api/user/password.
type ChangePasswordInput struct {
	Authorization string `header:"Authorization" doc:"Bearer access token; used to blacklist the current access token after a successful change."`
	Body          ChangePasswordRequest
}

// MessageResponse is a simple {"message": "..."} wire-format response used by
// endpoints that report success with a single human-readable message string.
type MessageResponse struct {
	Message string `json:"message" doc:"Human-readable success message."`
}

// ChangePasswordOutput wraps the success-message response body.
type ChangePasswordOutput struct {
	Body MessageResponse
}

// RegisterUserMutationRoutes wires the authenticated user-profile mutation
// endpoints (update profile / preferences / password) into the huma API. The
// password endpoint additionally goes through the auth rate limiter to match
// the raw gin route.
func RegisterUserMutationRoutes(api huma.API, h *UserHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter, authLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "updateUserProfile",
		Method:      http.MethodPut,
		Path:        "/api/user/profile",
		Summary:     "Update current user profile",
		Description: "Updates the authenticated user's email and/or avatar URL. Changing the email requires the current password to be supplied.",
		Tags:        []string{"user"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaUpdateProfile)

	huma.Register(api, huma.Operation{
		OperationID: "updateUserPreferences",
		Method:      http.MethodPut,
		Path:        "/api/user/preferences",
		Summary:     "Update current user preferences",
		Description: "Partially updates the authenticated user's emulation preferences. Nil fields are left untouched. Also upserts per-console shader and key-mapping overrides.",
		Tags:        []string{"user"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    sec,
	}, h.HumaUpdatePreferences)

	// Password change runs under both the per-user limiter and the auth
	// limiter (brute-force protection) to match the raw gin route.
	authRL := authLimiterMiddleware(authLimiter)
	huma.Register(api, huma.Operation{
		OperationID: "changeUserPassword",
		Method:      http.MethodPut,
		Path:        "/api/user/password",
		Summary:     "Change current user password",
		Description: "Verifies the current password, sets a new one, revokes existing refresh tokens and blacklists the current access token.",
		Tags:        []string{"user"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit, authRL},
		Security:    sec,
	}, h.HumaChangePassword)
}

// authLimiterMiddleware wraps a RateLimiter's RateLimit gin middleware (which
// is IP-scoped, not user-scoped) so huma operations can layer brute-force
// protection in addition to the per-user budget.
func authLimiterMiddleware(rl *RateLimiter) func(huma.Context, func(huma.Context)) {
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

// HumaUpdateProfile is the huma handler for PUT /api/user/profile.
func (h *UserHandler) HumaUpdateProfile(ctx context.Context, in *UpdateProfileInput) (*UpdateProfileOutput, error) {
	userID := UserIDFromContext(ctx)
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	req := in.Body

	if req.Email != "" {
		if req.CurrentPassword == "" {
			return nil, huma.Error400BadRequest("current password is required to change email")
		}
		if !auth.CheckPassword(req.CurrentPassword, user.PasswordHash) {
			return nil, huma.Error401Unauthorized("incorrect password")
		}
		var existing db.User
		if err := h.DB.Where("email = ? AND id != ?", req.Email, user.ID).First(&existing).Error; err == nil {
			return nil, huma.Error409Conflict("email already in use")
		}
		user.Email = req.Email
	}
	if req.AvatarURL != "" {
		parsed, err := url.Parse(req.AvatarURL)
		if err != nil || (parsed.Scheme != "https" && parsed.Scheme != "http") {
			return nil, huma.Error400BadRequest("avatar URL must be a valid HTTP(S) URL")
		}
		if len(req.AvatarURL) > 512 {
			return nil, huma.Error400BadRequest("avatar URL too long")
		}
		if isPrivateURL(req.AvatarURL) {
			return nil, huma.Error400BadRequest("avatar URL must not point to internal networks")
		}
		user.AvatarURL = req.AvatarURL
	}

	if err := h.DB.Save(&user).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update profile")
	}

	return &UpdateProfileOutput{Body: ToUserResponse(user)}, nil
}

// HumaUpdatePreferences is the huma handler for PUT /api/user/preferences.
func (h *UserHandler) HumaUpdatePreferences(ctx context.Context, in *UpdatePreferencesInput) (*UpdatePreferencesOutput, error) {
	uid := UserIDFromContext(ctx)
	var user db.User
	if err := h.DB.First(&user, uid).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	req := in.Body

	if req.ShowPerformanceOverlay != nil {
		user.ShowPerfOverlay = *req.ShowPerformanceOverlay
	}
	if req.AutoSaveEnabled != nil {
		user.AutoSaveEnabled = *req.AutoSaveEnabled
	}
	if req.AutoLoadSaveEnabled != nil {
		user.AutoLoadSaveEnabled = *req.AutoLoadSaveEnabled
	}
	if req.AutoUpdateCoresEnabled != nil {
		user.AutoUpdateCoresEnabled = *req.AutoUpdateCoresEnabled
	}
	if req.SelectedShader != nil {
		user.SelectedShader = *req.SelectedShader
	}
	if req.SelectedTheme != nil {
		user.SelectedTheme = *req.SelectedTheme
	}
	if req.DefaultSecondScreenPage != nil {
		valid := map[string]bool{"art": true, "controls": true, "dashboard": true, "save_slots": true}
		if !valid[*req.DefaultSecondScreenPage] {
			return nil, huma.Error400BadRequest("invalid defaultSecondScreenPage value; must be one of: art, controls, dashboard, save_slots")
		}
		user.DefaultSecondScreenPage = *req.DefaultSecondScreenPage
	}
	if req.SelectedKeyMapping != nil {
		user.SelectedKeyMapping = *req.SelectedKeyMapping
	}
	if req.CustomKeyMapping != nil {
		b, _ := json.Marshal(req.CustomKeyMapping)
		user.CustomKeyMapping = string(b)
	}
	if req.PreferredRegions != nil {
		user.PreferredRegions = strings.Join(*req.PreferredRegions, ",")
	}

	if err := h.DB.Save(&user).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update preferences")
	}

	if req.ConsoleShaders != nil {
		for consoleAbbr, shader := range req.ConsoleShaders {
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
				continue
			}
			if shader == "" || shader == "none" {
				h.DB.Where("user_id = ? AND console_id = ?", uid, console.ID).
					Delete(&db.ConsoleShaderPreference{})
			} else {
				var existing db.ConsoleShaderPreference
				result := h.DB.Where("user_id = ? AND console_id = ?", uid, console.ID).First(&existing)
				if result.Error == nil {
					h.DB.Model(&existing).Update("shader", shader)
				} else {
					h.DB.Create(&db.ConsoleShaderPreference{
						UserID:    uid,
						ConsoleID: console.ID,
						Shader:    shader,
					})
				}
			}
		}
	}

	if req.ConsoleSaveStatePolicies != nil {
		for consoleAbbr, raw := range req.ConsoleSaveStatePolicies {
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
				continue
			}
			choice, clear := normalizeSaveStateChoice(raw)
			if clear {
				h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).
					Delete(&db.ConsoleSaveStatePolicy{})
				continue
			}
			if choice == "" {
				// Garbage value — drop the entry rather than destroy
				// the user's existing override. Only an explicit empty
				// string is allowed to clear a row.
				continue
			}
			// Unscoped lookup so a previously soft-deleted row gets
			// revived in place rather than re-created with a new ID.
			// This intentionally diverges from the ConsoleShaders
			// pattern (which uses scoped writes) — we want stable
			// row IDs for the future audit trail in #804 phase 4b.
			var existing db.ConsoleSaveStatePolicy
			result := h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).First(&existing)
			if result.Error == nil {
				existing.Choice = choice
				existing.DeletedAt = gorm.DeletedAt{}
				h.DB.Unscoped().Save(&existing)
			} else {
				h.DB.Create(&db.ConsoleSaveStatePolicy{
					UserID:    uid,
					ConsoleID: console.ID,
					Choice:    choice,
				})
			}
		}
	}

	if req.GameSaveStatePolicies != nil {
		for gameIDStr, raw := range req.GameSaveStatePolicies {
			gameID, err := strconv.ParseUint(gameIDStr, 10, 64)
			if err != nil {
				continue
			}
			var game db.Game
			if err := h.DB.First(&game, gameID).Error; err != nil {
				continue
			}
			choice, clear := normalizeSaveStateChoice(raw)
			if clear {
				h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, game.ID).
					Delete(&db.GameSaveStatePolicy{})
				continue
			}
			if choice == "" {
				// Garbage value — drop the entry rather than destroy
				// an existing override. Same semantics as
				// ConsoleSaveStatePolicies.
				continue
			}
			var existing db.GameSaveStatePolicy
			result := h.DB.Unscoped().Where("user_id = ? AND game_id = ?", uid, game.ID).First(&existing)
			if result.Error == nil {
				existing.Choice = choice
				existing.DeletedAt = gorm.DeletedAt{}
				h.DB.Unscoped().Save(&existing)
			} else {
				h.DB.Create(&db.GameSaveStatePolicy{
					UserID: uid,
					GameID: game.ID,
					Choice: choice,
				})
			}
		}
	}

	if req.ConsoleKeyMappings != nil {
		for consoleAbbr, km := range req.ConsoleKeyMappings {
			var console db.Console
			if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err != nil {
				continue
			}
			if km.SelectedMapping == "" {
				h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).
					Delete(&db.ConsoleKeyMappingPreference{})
			} else {
				customJSON := ""
				if km.CustomMapping != nil {
					b, _ := json.Marshal(km.CustomMapping)
					customJSON = string(b)
				}
				var existing db.ConsoleKeyMappingPreference
				result := h.DB.Unscoped().Where("user_id = ? AND console_id = ?", uid, console.ID).First(&existing)
				if result.Error == nil {
					existing.SelectedMapping = km.SelectedMapping
					existing.CustomMapping = customJSON
					existing.DeletedAt = gorm.DeletedAt{}
					h.DB.Unscoped().Save(&existing)
				} else {
					h.DB.Create(&db.ConsoleKeyMappingPreference{
						UserID:          uid,
						ConsoleID:       console.ID,
						SelectedMapping: km.SelectedMapping,
						CustomMapping:   customJSON,
					})
				}
			}
		}
	}

	consoleShaders := h.buildConsoleShaderMap(uid)
	consoleSaveStatePolicies := h.buildConsoleSaveStatePolicyMap(uid)
	gameSaveStatePolicies := h.buildGameSaveStatePolicyMap(uid)
	consoleKeyMappings := h.buildConsoleKeyMappingMap(uid)
	customKeyMapping := parseJSONMap(user.CustomKeyMapping)

	selectedKeyMapping := user.SelectedKeyMapping
	if selectedKeyMapping == "" {
		selectedKeyMapping = "arrows-left"
	}

	selectedTheme := user.SelectedTheme
	if selectedTheme == "" {
		selectedTheme = "default-dark"
	}

	defaultSecondScreenPage := user.DefaultSecondScreenPage
	if defaultSecondScreenPage == "" {
		defaultSecondScreenPage = "art"
	}

	preferredRegions := parsePreferredRegions(user.PreferredRegions)

	var raCred db.RetroAchievementCredential
	raLinked := h.DB.Where("user_id = ?", uid).First(&raCred).Error == nil

	return &UpdatePreferencesOutput{
		Body: UserPreferencesResponse{
			ShowPerformanceOverlay:  user.ShowPerfOverlay,
			AutoSaveEnabled:         user.AutoSaveEnabled,
			AutoLoadSaveEnabled:     user.AutoLoadSaveEnabled,
			AutoUpdateCoresEnabled:  user.AutoUpdateCoresEnabled,
			SelectedShader:          user.SelectedShader,
			SelectedTheme:           selectedTheme,
			DefaultSecondScreenPage: defaultSecondScreenPage,
			ConsoleShaders:           consoleShaders,
			ConsoleSaveStatePolicies: consoleSaveStatePolicies,
			GameSaveStatePolicies:    gameSaveStatePolicies,
			SelectedKeyMapping:       selectedKeyMapping,
			CustomKeyMapping:        customKeyMapping,
			ConsoleKeyMappings:      consoleKeyMappings,
			PreferredRegions:        preferredRegions,
			RALinked:                raLinked,
			RAUsername:              raCred.RAUsername,
			RAHardcoreEnabled:       raCred.HardcoreEnabled,
		},
	}, nil
}

// HumaChangePassword is the huma handler for PUT /api/user/password.
func (h *UserHandler) HumaChangePassword(ctx context.Context, in *ChangePasswordInput) (*ChangePasswordOutput, error) {
	userID := UserIDFromContext(ctx)
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	req := in.Body

	if !auth.CheckPassword(req.CurrentPassword, user.PasswordHash) {
		return nil, huma.Error401Unauthorized("incorrect current password")
	}

	hash, err := auth.HashPassword(req.NewPassword)
	if err != nil {
		return nil, huma.Error500InternalServerError("failed to hash password")
	}

	user.PasswordHash = hash
	user.TokenVersion++

	if err := h.DB.Save(&user).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update password")
	}

	// Revoke all refresh tokens for this user.
	h.DB.Where("user_id = ?", user.ID).Delete(&db.RefreshToken{})

	// Blacklist the current access token. The Authorization header is bound
	// into the input via huma's `header:"..."` tag.
	var token string
	if in.Authorization != "" {
		parts := strings.SplitN(in.Authorization, " ", 2)
		if len(parts) == 2 && parts[0] == "Bearer" {
			token = parts[1]
		}
	}
	if token != "" {
		claims, err := auth.ValidateAccessToken(token, h.JWTSecret)
		if err == nil && claims.ExpiresAt != nil {
			tokenHash := sha256.Sum256([]byte(token))
			bl := db.TokenBlacklist{
				TokenHash: hex.EncodeToString(tokenHash[:]),
				ExpiresAt: claims.ExpiresAt.Time,
			}
			h.DB.Create(&bl)
		}
	}

	return &ChangePasswordOutput{Body: MessageResponse{Message: "password changed"}}, nil
}
