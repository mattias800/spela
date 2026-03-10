package api

import (
	"log/slog"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// GetExploreFeaturedSeries returns the top series for the Explore page shelf.
// Only series with at least 2 library games are included, sorted by library game count DESC, limit 20.
// GET /api/explore/series/featured
func (h *ExploreHandler) GetExploreFeaturedSeries(c *gin.Context) {
	// Query series with library game counts, filtering for >= 2 library games
	type seriesRow struct {
		ID           uint
		Name         string
		TotalGames   int
		LibraryGames int
	}

	var rows []seriesRow
	if err := h.DB.
		Table("game_series").
		Select(`game_series.id, game_series.name,
			COUNT(game_series_entries.id) as total_games,
			COUNT(CASE WHEN games.id IS NOT NULL AND games.deleted_at IS NULL THEN 1 END) as library_games`).
		Joins("JOIN game_series_entries ON game_series_entries.series_id = game_series.id").
		Joins("LEFT JOIN games ON games.id = game_series_entries.game_id").
		Group("game_series.id").
		Having("library_games >= 2").
		Order("library_games DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch featured series"})
		return
	}

	if len(rows) == 0 {

		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, []FeaturedSeriesResponse{})
		return
	}

	// Collect series IDs for batch lookups
	seriesIDs := make([]uint, len(rows))
	for i, r := range rows {
		seriesIDs[i] = r.ID
	}

	// For each series, find console count and hero art.
	// Batch-load all entries with local games to count unique consoles per series.
	type entryRow struct {
		SeriesID  uint
		GameID    uint
		ConsoleID uint
		Rating    float64
	}
	var entryRows []entryRow
	if err := h.DB.Table("game_series_entries").
		Select("game_series_entries.series_id, games.id as game_id, games.console_id, games.rating").
		Joins("JOIN games ON games.id = game_series_entries.game_id AND games.deleted_at IS NULL").
		Where("game_series_entries.series_id IN ?", seriesIDs).
		Scan(&entryRows).Error; err != nil {
		slog.Error("failed to batch-load series entries", "error", err)
	}

	// Group entries by series
	type seriesGameInfo struct {
		gameID    uint
		consoleID uint
		rating    float64
	}
	seriesGames := make(map[uint][]seriesGameInfo)
	for _, e := range entryRows {
		seriesGames[e.SeriesID] = append(seriesGames[e.SeriesID], seriesGameInfo{
			gameID:    e.GameID,
			consoleID: e.ConsoleID,
			rating:    e.Rating,
		})
	}

	// Collect all game IDs across all series for batch artwork lookup
	allGameIDs := make([]uint, 0)
	for _, games := range seriesGames {
		for _, g := range games {
			allGameIDs = append(allGameIDs, g.gameID)
		}
	}

	// Batch-load hero artworks
	artworkMap := make(map[uint]string) // gameID -> heroURL
	if len(allGameIDs) > 0 {
		var artworks []db.GameArtwork
		h.DB.Where("game_id IN ? AND hero_url != ''", allGameIDs).Find(&artworks)
		for _, a := range artworks {
			artworkMap[a.GameID] = a.HeroURL
		}
	}

	// Build responses
	result := make([]FeaturedSeriesResponse, len(rows))
	for i, r := range rows {
		// Count unique consoles
		consolesSet := make(map[uint]bool)
		// Find best hero URL (highest rated library game with hero art)
		var bestHeroURL string
		var bestRating float64 = -1
		for _, g := range seriesGames[r.ID] {
			consolesSet[g.consoleID] = true
			if heroURL, ok := artworkMap[g.gameID]; ok {
				if g.rating > bestRating {
					bestRating = g.rating
					bestHeroURL = heroURL
				}
			}
		}

		result[i] = FeaturedSeriesResponse{
			ID:           strconv.FormatUint(uint64(r.ID), 10),
			Name:         r.Name,
			LibraryGames: r.LibraryGames,
			TotalGames:   r.TotalGames,
			ConsoleCount: len(consolesSet),
			HeroURL:      bestHeroURL,
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, result)
}
