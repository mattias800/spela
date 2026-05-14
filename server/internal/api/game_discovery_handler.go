package api

import (
	"fmt"
	"strconv"
	"strings"
	"time"

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
	IGDBGameID        int     `json:"igdbGameId"`
	Name              string  `json:"name"`
	CoverUrl          string  `json:"coverUrl"`
	IGDBCriticsRating float64 `json:"igdbCriticsRating"`
	LocalGameId       *string `json:"localGameId"`
}

// similarGamesStaleness is how long cached similar games data is considered fresh.
const similarGamesStaleness = 7 * 24 * time.Hour

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

		platforms := formatIGDBPlatformsList(g.Platforms)

		sg := db.SimilarGame{
			GameID:            gameID,
			IGDBGameID:        g.ID,
			Name:              g.Name,
			CoverImageID:      coverImageID,
			CoverLocalPath:    coverLocalPath,
			IGDBCriticsRating: g.TotalRating,
			Platforms:         platforms,
		}

		// Upsert: update if exists, create if not
		var existing db.SimilarGame
		err := h.DB.Where("game_id = ? AND igdb_game_id = ?", gameID, g.ID).First(&existing).Error
		if err == nil {
			updates := map[string]interface{}{
				"name":           sg.Name,
				"cover_image_id": sg.CoverImageID,
				"rating":         sg.IGDBCriticsRating,
				"platforms":      platforms,
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

// formatIGDBPlatformsList serialises a slice of IGDB platform IDs into the
// comma-separated string format stored on [db.SimilarGame.Platforms].
// Returns "" for empty input so the column matches the legacy/missing case.
func formatIGDBPlatformsList(platforms []int) string {
	if len(platforms) == 0 {
		return ""
	}
	parts := make([]string, len(platforms))
	for i, p := range platforms {
		parts[i] = strconv.Itoa(p)
	}
	return strings.Join(parts, ",")
}

// parseIGDBPlatformsList parses the comma-separated platform ID string stored
// on [db.SimilarGame.Platforms] back to a slice of IGDB platform IDs.
// Tolerant of legacy empty values, whitespace, and unparseable entries.
func parseIGDBPlatformsList(s string) []int {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	result := make([]int, 0, len(parts))
	for _, p := range parts {
		n, err := strconv.Atoi(strings.TrimSpace(p))
		if err == nil {
			result = append(result, n)
		}
	}
	return result
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
