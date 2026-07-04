package api

import (
	"context"
	"net/http"
	"strconv"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// SetTitlePlatformPreferenceInput is the input for choosing the preferred
// platform release of a logical title group.
type SetTitlePlatformPreferenceInput struct {
	GameID string `path:"gameId" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID to prefer for this logical title."`
}

// TitlePlatformPreferenceResponse is returned after a successful preference
// upsert.
type TitlePlatformPreferenceResponse struct {
	TitleKey        string `json:"titleKey"`
	PreferredGameID string `json:"preferredGameId"`
}

// SetTitlePlatformPreferenceOutput wraps the saved preference response.
type SetTitlePlatformPreferenceOutput struct {
	Body TitlePlatformPreferenceResponse
}

// RegisterTitlePlatformPreferenceRoutes wires user title-platform preference
// endpoints into the huma API.
func RegisterTitlePlatformPreferenceRoutes(api huma.API, h *UserHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "setTitlePlatformPreference",
		Method:      http.MethodPut,
		Path:        "/api/user/title-platform-preferences/{gameId}",
		Summary:     "Set preferred platform for a title",
		Description: "Stores the authenticated user's preferred platform release for the logical title group containing the supplied game.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSetTitlePlatformPreference)
}

// HumaSetTitlePlatformPreference is the huma handler for
// PUT /api/user/title-platform-preferences/{gameId}.
func (h *UserHandler) HumaSetTitlePlatformPreference(ctx context.Context, in *SetTitlePlatformPreferenceInput) (*SetTitlePlatformPreferenceOutput, error) {
	userID := UserIDFromContext(ctx)

	gameID, err := strconv.ParseUint(in.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, uint(gameID)).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	titleKey := titleDedupeKey(game)
	now := time.Now().UTC()
	preference := db.UserTitlePlatformPreference{
		UserID:          userID,
		TitleKey:        titleKey,
		PreferredGameID: game.ID,
	}
	if err := h.DB.Clauses(clause.OnConflict{
		Columns: []clause.Column{
			{Name: "user_id"},
			{Name: "title_key"},
		},
		DoUpdates: clause.Assignments(map[string]any{
			"preferred_game_id": game.ID,
			"updated_at":        now,
		}),
	}).Create(&preference).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to save preferred platform")
	}

	return &SetTitlePlatformPreferenceOutput{Body: TitlePlatformPreferenceResponse{
		TitleKey:        titleKey,
		PreferredGameID: strconv.FormatUint(uint64(game.ID), 10),
	}}, nil
}
