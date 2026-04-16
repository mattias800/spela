package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"path/filepath"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scraper"
)

// CoverOption represents a single available cover art source.
type CoverOption struct {
	Source      string `json:"source"`
	URL         string `json:"url"`
	Label       string `json:"label,omitempty"`
	LibRetroName string `json:"libretroName,omitempty"`
}

// GetGameCovers returns the available cover art options for a game.
func (h *AdminHandler) GetGameCovers(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	covers := make([]CoverOption, 0)

	// Determine region of the current libretro cover for labeling
	gameName := strings.TrimSuffix(game.FileName, filepath.Ext(game.FileName))

	if game.LibRetroCoverURL != "" {
		region := scraper.ExtractRegion(gameName)
		label := "LibRetro"
		if region != "" {
			label = fmt.Sprintf("LibRetro (%s)", region)
		}
		covers = append(covers, CoverOption{Source: "libretro", URL: resolveImageURL(game.LibRetroCoverURL), Label: label})
	}

	if game.IGDBCoverURL != "" {
		covers = append(covers, CoverOption{Source: "igdb", URL: resolveImageURL(game.IGDBCoverURL), Label: "IGDB"})
	}

	// Include the current cover as "custom" if it differs from both known sources
	// (e.g. pre-migration games with the old boxart.png naming)
	if game.CoverURL != "" && game.CoverURL != game.LibRetroCoverURL && game.CoverURL != game.IGDBCoverURL {
		covers = append(covers, CoverOption{Source: "custom", URL: resolveImageURL(game.CoverURL), Label: "Custom"})
	}

	// Find regional variants from LibRetro
	if h.Scraper != nil {
		var console db.Console
		if err := h.DB.First(&console, game.ConsoleID).Error; err == nil {
			variants := h.Scraper.FindRegionalVariants(console.Abbreviation, gameName)
			for _, v := range variants {
				thumbURL := scraper.LibRetroThumbnailURL(console.Abbreviation, v.LibRetroName)
				if thumbURL == "" {
					continue
				}
				covers = append(covers, CoverOption{
					Source:      "libretro-regional",
					URL:         thumbURL,
					Label:       fmt.Sprintf("LibRetro (%s)", v.Region),
					LibRetroName: v.LibRetroName,
				})
			}
		}
	}

	// Determine which source is active
	active := ""
	if game.CoverURL != "" {
		switch game.CoverURL {
		case game.LibRetroCoverURL:
			active = "libretro"
		case game.IGDBCoverURL:
			active = "igdb"
		default:
			active = "custom"
		}
	}

	c.JSON(http.StatusOK, gin.H{
		"active": active,
		"covers": covers,
	})
}

// SetGameCover sets the active cover art for a game from one of the available sources.
func (h *AdminHandler) SetGameCover(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var req struct {
		Source      string `json:"source" binding:"required"`
		LibRetroName string `json:"libretroName,omitempty"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request body"})
		return
	}

	var newCoverURL string
	switch req.Source {
	case "libretro":
		if game.LibRetroCoverURL == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "no LibRetro cover available"})
			return
		}
		newCoverURL = game.LibRetroCoverURL
	case "igdb":
		if game.IGDBCoverURL == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "no IGDB cover available"})
			return
		}
		newCoverURL = game.IGDBCoverURL
	case "libretro-regional":
		if req.LibRetroName == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "libretroName is required for libretro-regional source"})
			return
		}
		if h.Scraper == nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "scraper not available"})
			return
		}
		var console db.Console
		if err := h.DB.First(&console, game.ConsoleID).Error; err != nil {
			c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load console"})
			return
		}
		// Validate libretroName against known regional variants to prevent SSRF
		gameName := strings.TrimSuffix(game.FileName, filepath.Ext(game.FileName))
		variants := h.Scraper.FindRegionalVariants(console.Abbreviation, gameName)
		validName := false
		for _, v := range variants {
			if v.LibRetroName == req.LibRetroName {
				validName = true
				break
			}
		}
		if !validName {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "libretroName is not a known regional variant for this game"})
			return
		}
		gameIDStr := fmt.Sprintf("%d", game.ID)
		subpath := fmt.Sprintf("%s/%s/boxart-libretro.png", console.Abbreviation, gameIDStr)
		path := h.Scraper.DownloadRegionalCover(console.Abbreviation, req.LibRetroName, subpath)
		if path == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "failed to download regional cover"})
			return
		}
		// Update LibRetroCoverURL to the newly downloaded regional variant
		game.LibRetroCoverURL = path
		newCoverURL = path
	default:
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "source must be 'libretro', 'igdb', or 'libretro-regional'"})
		return
	}

	updates := map[string]interface{}{
		"cover_url":          newCoverURL,
		"cover_manually_set": true,
	}
	// When selecting a regional variant, also persist the new libretro cover path
	if req.Source == "libretro-regional" {
		updates["lib_retro_cover_url"] = newCoverURL
	}

	if err := h.DB.Model(&game).Updates(updates).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update cover"})
		return
	}

	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	if err := h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, id).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to reload game"})
		return
	}
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
}
