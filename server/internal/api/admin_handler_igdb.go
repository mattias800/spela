package api

import (
	"log/slog"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
)

// IGDBSearchResult represents a single IGDB game candidate for admin match correction.
type IGDBSearchResult struct {
	IGDBID      int    `json:"igdbId"`
	Name        string `json:"name"`
	CoverURL    string `json:"coverUrl,omitempty"`
	ReleaseYear int    `json:"releaseYear,omitempty"`
	Developer   string `json:"developer,omitempty"`
	Summary     string `json:"summary,omitempty"`
}

// SearchIGDB searches IGDB for game candidates scoped to the game's platform.
// GET /api/admin/games/:id/igdb-search?q=query
func (h *AdminHandler) SearchIGDB(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	query := c.Query("q")
	if query == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "query parameter 'q' is required"})
		return
	}

	h.tryConfigureIGDB()
	if h.Scraper.IGDBClient == nil || !h.Scraper.IGDBClient.IsConfigured() {
		c.JSON(http.StatusServiceUnavailable, gin.H{"error": "IGDB is not configured"})
		return
	}

	platformIDs, ok := igdb.AbbreviationToIGDBPlatform[game.Console.Abbreviation]
	if !ok {
		c.JSON(http.StatusBadRequest, gin.H{"error": "no IGDB platform mapping for this console"})
		return
	}

	games, err := h.Scraper.IGDBClient.SearchGame(query, platformIDs)
	if err != nil {
		slog.Warn("IGDB search failed", "query", query, "error", err)
		c.JSON(http.StatusBadGateway, gin.H{"error": "IGDB search failed"})
		return
	}

	results := make([]IGDBSearchResult, 0, len(games))
	for _, g := range games {
		result := IGDBSearchResult{
			IGDBID:  g.ID,
			Name:    g.Name,
			Summary: g.Summary,
		}
		if g.Cover != nil && g.Cover.ImageID != "" {
			result.CoverURL = igdb.ImageURL(g.Cover.ImageID, "cover_big")
		}
		if g.FirstReleaseDate > 0 {
			result.ReleaseYear = time.Unix(g.FirstReleaseDate, 0).Year()
		}
		for _, ic := range g.InvolvedCompanies {
			if ic.Developer {
				result.Developer = ic.Company.Name
				break
			}
		}
		results = append(results, result)
	}

	c.JSON(http.StatusOK, results)
}

// ApplyIGDBMatch re-scrapes a game using a specific admin-chosen IGDB game ID.
// POST /api/admin/games/:id/igdb-match with body { "igdbId": 1234 }
func (h *AdminHandler) ApplyIGDBMatch(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var req struct {
		IGDBID int `json:"igdbId" binding:"required,min=1"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		slog.Debug("request binding failed", "error", err)
		c.JSON(http.StatusBadRequest, gin.H{"error": "igdbId is required and must be a positive integer"})
		return
	}

	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	if err := h.Scraper.ScrapeGameWithIGDBMatch(&game, req.IGDBID); err != nil {
		slog.Warn("IGDB match failed", "game", game.Title, "igdbId", req.IGDBID, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to apply IGDB match"})
		return
	}

	// Reload with all associations for the response
	h.DB.Preload("Console").Preload("Discs").Preload("Screenshots").First(&game, game.ID)
	userID, _ := c.Get("userId")
	uid, _ := userID.(uint)
	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, uid))
}
