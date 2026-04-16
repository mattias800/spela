package api

import (
	"fmt"
	"log/slog"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// HeroOption represents a single available hero art image from SteamGridDB.
type HeroOption struct {
	URL   string `json:"url"`
	Thumb string `json:"thumb"`
	ID    int    `json:"id"`
}

// GetGameHeroes returns the available hero art options for a game from SteamGridDB.
func (h *AdminHandler) GetGameHeroes(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", game.ID).First(&artwork).Error; err != nil {
		c.JSON(http.StatusOK, gin.H{"activeUrl": "", "heroes": []any{}})
		return
	}

	if artwork.SteamGridDBID == 0 {
		c.JSON(http.StatusOK, gin.H{"activeUrl": "", "heroes": []any{}})
		return
	}

	if h.Scraper == nil || h.Scraper.SteamGridDBClient == nil {
		c.JSON(http.StatusOK, gin.H{
			"activeUrl": resolveImageURL(artwork.HeroURL),
			"heroes":    []any{},
		})
		return
	}

	heroes, err := h.Scraper.SteamGridDBClient.GetHeroes(artwork.SteamGridDBID)
	if err != nil {
		slog.Warn("failed to fetch hero options from SteamGridDB", "game", game.Title, "error", err)
		c.JSON(http.StatusOK, gin.H{
			"activeUrl": resolveImageURL(artwork.HeroURL),
			"heroes":    []any{},
		})
		return
	}

	options := make([]HeroOption, len(heroes))
	for i, hero := range heroes {
		options[i] = HeroOption{
			URL:   hero.URL,
			Thumb: hero.Thumb,
			ID:    hero.ID,
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"activeUrl": resolveImageURL(artwork.HeroURL),
		"heroes":    options,
	})
}

// SetGameHero sets the hero art for a game from a SteamGridDB image URL.
func (h *AdminHandler) SetGameHero(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var req SetGameHeroRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	if h.Scraper == nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "scraper not available"})
		return
	}

	// Download the image locally
	gameIDStr := fmt.Sprintf("%d", game.ID)
	subpath := fmt.Sprintf("%s/%s/artwork-hero.jpg", game.Console.Abbreviation, gameIDStr)
	localPath := h.Scraper.DownloadExternalImage(req.URL, subpath)
	if localPath == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "failed to download hero image"})
		return
	}

	// Upsert artwork record
	var artwork db.GameArtwork
	if err := h.DB.Where("game_id = ?", game.ID).First(&artwork).Error; err != nil {
		artwork = db.GameArtwork{GameID: game.ID}
	}
	artwork.HeroURL = localPath
	artwork.HeroManuallySet = true

	if artwork.ID == 0 {
		if err := h.DB.Create(&artwork).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save hero art"})
			return
		}
	} else {
		if err := h.DB.Save(&artwork).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save hero art"})
			return
		}
	}

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin changed hero art", "admin_id", adminID, "game", game.Title, "hero_url", req.URL)

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, id).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to reload game"})
		return
	}
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
}
