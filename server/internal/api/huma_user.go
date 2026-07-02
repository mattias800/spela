package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// GetProfileInput is the input for GET /api/user/profile.
type GetProfileInput struct{}

// GetProfileOutput wraps the current user's profile for the huma response envelope.
type GetProfileOutput struct {
	Body UserResponse
}

// GetPreferencesInput is the input for GET /api/user/preferences.
type GetPreferencesInput struct{}

// GetPreferencesOutput wraps the current user's emulation preferences for the
// huma response envelope.
type GetPreferencesOutput struct {
	Body UserPreferencesResponse
}

// RegisterUserRoutes wires the authenticated user-profile read endpoints into
// the huma API. Mutating endpoints (update profile / password / preferences)
// stay on raw gin in this batch.
func RegisterUserRoutes(api huma.API, h *UserHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getUserProfile",
		Method:      http.MethodGet,
		Path:        "/api/user/profile",
		Summary:     "Get current user profile",
		Description: "Returns the authenticated user's profile (username, email, role, avatar, timestamps).",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetProfile)

	huma.Register(api, huma.Operation{
		OperationID: "getUserPreferences",
		Method:      http.MethodGet,
		Path:        "/api/user/preferences",
		Summary:     "Get current user preferences",
		Description: "Returns the authenticated user's emulation preferences including shaders, themes, key mappings, preferred regions and RetroAchievements status.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPreferences)
}

// HumaGetProfile is the huma handler for GET /api/user/profile.
func (h *UserHandler) HumaGetProfile(ctx context.Context, _ *GetProfileInput) (*GetProfileOutput, error) {
	userID := UserIDFromContext(ctx)
	var user db.User
	if err := h.DB.First(&user, userID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}
	return &GetProfileOutput{Body: ToUserResponse(user)}, nil
}

// HumaGetPreferences is the huma handler for GET /api/user/preferences.
func (h *UserHandler) HumaGetPreferences(ctx context.Context, _ *GetPreferencesInput) (*GetPreferencesOutput, error) {
	uid := UserIDFromContext(ctx)
	var user db.User
	if err := h.DB.First(&user, uid).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	consoleShaders := h.buildConsoleShaderMap(uid)
	consoleRenderScales := h.buildConsoleRenderScaleMap(uid)
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

	return &GetPreferencesOutput{
		Body: UserPreferencesResponse{
			ShowPerformanceOverlay:   user.ShowPerfOverlay,
			AutoSaveEnabled:          user.AutoSaveEnabled,
			AutoLoadSaveEnabled:      user.AutoLoadSaveEnabled,
			AutoUpdateCoresEnabled:   user.AutoUpdateCoresEnabled,
			SelectedShader:           user.SelectedShader,
			SelectedTheme:            selectedTheme,
			DefaultSecondScreenPage:  defaultSecondScreenPage,
			ConsoleShaders:           consoleShaders,
			ConsoleRenderScales:      consoleRenderScales,
			ConsoleSaveStatePolicies: consoleSaveStatePolicies,
			GameSaveStatePolicies:    gameSaveStatePolicies,
			SelectedKeyMapping:       selectedKeyMapping,
			CustomKeyMapping:         customKeyMapping,
			ConsoleKeyMappings:       consoleKeyMappings,
			PreferredRegions:         preferredRegions,
			RALinked:                 raLinked,
			RAUsername:               raCred.RAUsername,
			RAHardcoreEnabled:        raCred.HardcoreEnabled,
		},
	}, nil
}
