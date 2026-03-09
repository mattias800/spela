package api

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ArtworkHandler handles game artwork endpoints.
type ArtworkHandler struct {
	DB *gorm.DB
}

// GameArtworkResponse is the API response for a game's artwork URLs.
type GameArtworkResponse struct {
	HeroURL string `json:"heroUrl,omitempty"`
	GridURL string `json:"gridUrl,omitempty"`
	LogoURL string `json:"logoUrl,omitempty"`
	IconURL string `json:"iconUrl,omitempty"`
}

// GetGameArtwork returns the SteamGridDB artwork for a game.
// If artwork exists in the DB, return it. If not, return empty (no scrape triggered).
func (h *ArtworkHandler) GetGameArtwork(c *gin.Context) {
	id := c.Param("id")

	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", id).First(&artwork).Error; err != nil {
		// No artwork found — return empty response (not an error)
		c.JSON(http.StatusOK, GameArtworkResponse{})
		return
	}

	c.JSON(http.StatusOK, GameArtworkResponse{
		HeroURL: artwork.HeroURL,
		GridURL: artwork.GridURL,
		LogoURL: artwork.LogoURL,
		IconURL: artwork.IconURL,
	})
}
