package api

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// GetGameArtworkInput is the input for GET /api/games/{id}/artwork.
type GetGameArtworkInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GetGameArtworkOutput wraps the SteamGridDB artwork URLs for the huma
// response envelope.
type GetGameArtworkOutput struct {
	Body GameArtworkResponse
}

// RegisterArtworkRoutes wires the game-artwork read endpoint into the huma API.
func RegisterArtworkRoutes(api huma.API, h *ArtworkHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)

	huma.Register(api, huma.Operation{
		OperationID: "getGameArtwork",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/artwork",
		Summary:     "Get game artwork URLs",
		Description: "Returns the SteamGridDB hero, grid, logo, and icon artwork URLs for a game. Returns an empty response (not 404) when no artwork has been scraped yet.",
		Tags:        []string{"games"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaGetGameArtwork)
}

// HumaGetGameArtwork is the huma handler for GET /api/games/{id}/artwork.
func (h *ArtworkHandler) HumaGetGameArtwork(_ context.Context, in *GetGameArtworkInput) (*GetGameArtworkOutput, error) {
	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", in.ID).First(&artwork).Error; err != nil {
		// No artwork found — match the raw gin handler: return empty response (not an error).
		return &GetGameArtworkOutput{Body: GameArtworkResponse{}}, nil
	}

	return &GetGameArtworkOutput{Body: GameArtworkResponse{
		HeroURL: resolveImageURL(artwork.HeroURL),
		GridURL: resolveImageURL(artwork.GridURL),
		LogoURL: resolveImageURL(artwork.LogoURL),
		IconURL: resolveImageURL(artwork.IconURL),
	}}, nil
}
