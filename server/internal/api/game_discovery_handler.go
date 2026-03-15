package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	"gorm.io/gorm"
)

// GameDiscoveryHandler handles game discovery endpoints (similar games, developer games).
type GameDiscoveryHandler struct {
	DB      *gorm.DB
	Scraper *scraper.Scraper
}

// SimilarGameResponse is the API response for a similar game.
type SimilarGameResponse struct {
	Name        string  `json:"name"`
	CoverUrl    string  `json:"coverUrl"`
	Rating      float64 `json:"rating"`
	LocalGameId *string `json:"localGameId"`
}

// similarGamesStaleness is how long cached similar games data is considered fresh.
const similarGamesStaleness = 7 * 24 * time.Hour

// GetSimilarGames returns IGDB similar games for a local game.
// Results are cached in the local DB and refreshed when stale (>7 days).
func (h *GameDiscoveryHandler) GetSimilarGames(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	igdbGameID := parseIGDBGameID(game.ScraperID)
	if igdbGameID == 0 {
		c.JSON(http.StatusOK, []SimilarGameResponse{})
		return
	}

	// Check local DB for cached similar games
	var cached []db.SimilarGame
	h.DB.Where("game_id = ?", game.ID).Find(&cached)

	fresh := len(cached) > 0 && time.Since(cached[0].UpdatedAt) < similarGamesStaleness

	// Lazily configure IGDB client if credentials are available but client isn't set up yet
	if h.Scraper != nil && h.Scraper.IGDBClient == nil {
		clientID, clientSecret := igdbCredentials(h.DB)
		if clientID != "" && clientSecret != "" {
			h.Scraper.IGDBClient = igdb.NewClient(clientID, clientSecret)
		}
	}

	if !fresh && h.Scraper != nil && h.Scraper.IGDBClient != nil && h.Scraper.IGDBClient.IsConfigured() {
		similarGames, err := h.Scraper.IGDBClient.GetSimilarGames(igdbGameID)
		if err != nil {
			slog.Warn("failed to fetch similar games from IGDB", "game", game.Title, "error", err)
			// Fall through to serve stale data if available
		} else {
			h.upsertSimilarGames(game.ID, similarGames)
			// Re-read from DB to get consistent data
			h.DB.Where("game_id = ?", game.ID).Find(&cached)
		}
	}

	// Build response with local library cross-reference
	result := make([]SimilarGameResponse, 0, len(cached))
	for _, sg := range cached {
		resp := SimilarGameResponse{
			Name:   sg.Name,
			Rating: sg.Rating,
		}

		// Check for local game match by case-insensitive title
		var localGame db.Game
		if err := h.DB.Where("LOWER(title) = LOWER(?)", sg.Name).First(&localGame).Error; err == nil {
			localID := fmt.Sprintf("%d", localGame.ID)
			resp.LocalGameId = &localID
			if localGame.CoverURL != "" {
				resp.CoverUrl = resolveImageURL(localGame.CoverURL)
			}
		}
		if resp.CoverUrl == "" && sg.CoverLocalPath != "" {
			resp.CoverUrl = resolveImageURL(sg.CoverLocalPath)
		}

		result = append(result, resp)
	}

	c.JSON(http.StatusOK, result)
}

// upsertSimilarGames inserts or updates cached similar games for a game.
func (h *GameDiscoveryHandler) upsertSimilarGames(gameID uint, games []igdb.SimilarGame) {
	for _, g := range games {
		coverImageID := ""
		if g.Cover != nil {
			coverImageID = g.Cover.ImageID
		}

		// Download cover image locally
		coverLocalPath := ""
		if coverImageID != "" && h.Scraper != nil {
			coverURL := igdb.ImageURL(coverImageID, "cover_big")
			subpath := fmt.Sprintf("similar/%d/%d/cover.jpg", gameID, g.ID)
			coverLocalPath = h.Scraper.DownloadExternalImage(coverURL, subpath)
		}

		sg := db.SimilarGame{
			GameID:         gameID,
			IGDBGameID:     g.ID,
			Name:           g.Name,
			CoverImageID:   coverImageID,
			CoverLocalPath: coverLocalPath,
			Rating:         g.TotalRating,
		}

		// Upsert: update if exists, create if not
		var existing db.SimilarGame
		err := h.DB.Where("game_id = ? AND igdb_game_id = ?", gameID, g.ID).First(&existing).Error
		if err == nil {
			updates := map[string]interface{}{
				"name":           sg.Name,
				"cover_image_id": sg.CoverImageID,
				"rating":         sg.Rating,
			}
			if coverLocalPath != "" {
				updates["cover_local_path"] = coverLocalPath
			}
			h.DB.Model(&existing).Updates(updates)
		} else {
			h.DB.Create(&sg)
		}
	}

	// Remove old entries that are no longer in the similar games list
	igdbIDs := make([]int, len(games))
	for i, g := range games {
		igdbIDs[i] = g.ID
	}
	if len(igdbIDs) > 0 {
		h.DB.Where("game_id = ? AND igdb_game_id NOT IN ?", gameID, igdbIDs).Delete(&db.SimilarGame{})
	} else {
		h.DB.Where("game_id = ?", gameID).Delete(&db.SimilarGame{})
	}
}

// DeveloperGameResponse is the API response for a developer game.
type DeveloperGameResponse struct {
	Name        string `json:"name"`
	CoverUrl    string `json:"coverUrl"`
	LocalGameId string `json:"localGameId"`
}

// GetDeveloperGames returns other games by the same developer that are in the local library.
func (h *GameDiscoveryHandler) GetDeveloperGames(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	if game.Developer == "" {
		c.JSON(http.StatusOK, []DeveloperGameResponse{})
		return
	}

	// Find other games by the same developer in the local library
	var otherGames []db.Game
	if err := h.DB.Where("developer = ? AND id != ?", game.Developer, game.ID).
		Order("title asc").
		Limit(20).
		Find(&otherGames).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer games"})
		return
	}

	result := make([]DeveloperGameResponse, 0, len(otherGames))
	for _, g := range otherGames {
		coverUrl := resolveImageURL(g.CoverURL)
		result = append(result, DeveloperGameResponse{
			Name:        g.Title,
			CoverUrl:    coverUrl,
			LocalGameId: fmt.Sprintf("%d", g.ID),
		})
	}

	c.JSON(http.StatusOK, result)
}

// parseIGDBGameID extracts the IGDB game ID from a ScraperID like "igdb:1234".
func parseIGDBGameID(scraperID string) int {
	if !strings.HasPrefix(scraperID, "igdb:") {
		return 0
	}
	id, err := strconv.Atoi(scraperID[5:])
	if err != nil {
		return 0
	}
	return id
}
