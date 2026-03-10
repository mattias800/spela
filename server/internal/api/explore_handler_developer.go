package api

import (
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// GetDevelopers returns a list of developers with game counts, average ratings, and console lists.
// GET /api/explore/developers
func (h *ExploreHandler) GetDevelopers(c *gin.Context) {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN rating > 0 THEN rating ELSE NULL END) as avg_rating").
		Where("games.deleted_at IS NULL AND developer != ''").
		Group("developer").
		Order("game_count DESC").
		Limit(50).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developers", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developers"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, DeveloperListResponse{Developers: []DeveloperSummary{}})
		return
	}

	// Collect developer names for batch console lookup
	devNames := make([]string, len(rows))
	for i, r := range rows {
		devNames[i] = r.Developer
	}

	// Batch-load distinct consoles per developer
	type devConsoleRow struct {
		Developer    string
		Abbreviation string
	}
	var consoleRows []devConsoleRow
	if err := h.DB.
		Table("games").
		Select("DISTINCT games.developer, consoles.abbreviation").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("games.deleted_at IS NULL AND games.developer IN ?", devNames).
		Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to fetch developer consoles", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developers"})
		return
	}

	devConsoles := make(map[string][]string)
	for _, cr := range consoleRows {
		abbr := strings.ToLower(cr.Abbreviation)
		devConsoles[cr.Developer] = append(devConsoles[cr.Developer], abbr)
	}

	developers := make([]DeveloperSummary, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		consoles := devConsoles[r.Developer]
		if consoles == nil {
			consoles = []string{}
		}
		developers[i] = DeveloperSummary{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
			Consoles:  consoles,
		}
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperListResponse{Developers: developers})
}

// GetDeveloperDetail returns all games by a specific developer with user context.
// GET /api/explore/developers/:name
func (h *ExploreHandler) GetDeveloperDetail(c *gin.Context) {
	userID := getUserID(c)
	name := c.Param("name")

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch developer games", "developer", name, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer games"})
		return
	}

	// Calculate stats
	avgRating, consoles := calcRatingAndConsoles(games)

	// Use canonical casing from the first game's developer field
	canonicalName := name
	if len(games) > 0 && games[0].Developer != "" {
		canonicalName = games[0].Developer
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperDetailResponse{
		Name:      canonicalName,
		GameCount: len(games),
		AvgRating: avgRating,
		Consoles:  consoles,
		Games:     ToGameResponses(games, h.DB, userID),
	})
}

// GetPublisherDetail returns all games by a specific publisher with user context.
// GET /api/explore/publishers/:name
func (h *ExploreHandler) GetPublisherDetail(c *gin.Context) {
	userID := getUserID(c)
	name := c.Param("name")

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.publisher) = LOWER(?) AND games.deleted_at IS NULL", name).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch publisher games", "publisher", name, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch publisher games"})
		return
	}

	// Calculate stats
	avgRating, consoles := calcRatingAndConsoles(games)

	// Use canonical casing from the first game's publisher field
	canonicalName := name
	if len(games) > 0 && games[0].Publisher != "" {
		canonicalName = games[0].Publisher
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, PublisherDetailResponse{
		Name:      canonicalName,
		GameCount: len(games),
		AvgRating: avgRating,
		Consoles:  consoles,
		Games:     ToGameResponses(games, h.DB, userID),
	})
}

// GetDeveloperSpotlight returns a featured developer with top games and hero art.
// The spotlight rotates weekly using a deterministic selection from the top 5 developers.
// GET /api/explore/developers/spotlight
func (h *ExploreHandler) GetDeveloperSpotlight(c *gin.Context) {
	userID := getUserID(c)

	// Find the top 5 developers by game count (only those with games that have hero art)
	type devRow struct {
		Developer string
		GameCount int
	}
	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("games.developer, COUNT(DISTINCT games.id) as game_count").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id AND game_artworks.hero_url != ''").
		Where("games.deleted_at IS NULL AND games.developer != ''").
		Group("games.developer").
		Order("game_count DESC").
		Limit(5).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch developer spotlight candidates", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer spotlight"})
		return
	}

	if len(rows) == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "no developers with hero art found"})
		return
	}

	// Pick based on week number for deterministic weekly rotation
	_, weekNumber := time.Now().ISOWeek()
	selectedDev := rows[weekNumber%len(rows)]

	// Load all games for this developer
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("LOWER(games.developer) = LOWER(?) AND games.deleted_at IS NULL", selectedDev.Developer).
		Order("games.rating DESC, games.title ASC").
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch spotlight developer games", "developer", selectedDev.Developer, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch developer spotlight"})
		return
	}

	avgRating, consoles := calcRatingAndConsoles(games)

	// Take top 8 for the response
	topGames := games
	if len(topGames) > 8 {
		topGames = topGames[:8]
	}

	// Find hero URL from the highest-rated game with hero art
	heroURL := ""
	if len(games) > 0 {
		gameIDs := make([]uint, len(games))
		for i, g := range games {
			gameIDs[i] = g.ID
		}
		var artwork db.GameArtwork
		if err := h.DB.
			Joins("JOIN games ON games.id = game_artworks.game_id").
			Where("game_artworks.game_id IN ? AND game_artworks.hero_url != ''", gameIDs).
			Order("games.rating DESC").
			First(&artwork).Error; err == nil {
			heroURL = artwork.HeroURL
		}
	}

	// Count total games by this developer (including those without hero art)
	var totalGameCount int64
	h.DB.Model(&db.Game{}).
		Where("LOWER(developer) = LOWER(?) AND deleted_at IS NULL", selectedDev.Developer).
		Count(&totalGameCount)

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DeveloperSpotlightResponse{
		Name:      selectedDev.Developer,
		GameCount: int(totalGameCount),
		AvgRating: avgRating,
		Consoles:  consoles,
		TopGames:  ToGameResponses(topGames, h.DB, userID),
		HeroURL:   heroURL,
	})
}

// calcRatingAndConsoles computes the average rating and distinct console display names
// for a slice of games. Returns (avgRating, consoles) sorted alphabetically.
func calcRatingAndConsoles(games []db.Game) (float64, []string) {
	var ratingSum float64
	var ratingCount int
	consoleSet := make(map[string]bool)

	for _, g := range games {
		if g.Rating > 0 {
			ratingSum += g.Rating
			ratingCount++
		}
		if g.Console.Name != "" {
			consoleSet[g.Console.Name] = true
		}
	}

	avgRating := 0.0
	if ratingCount > 0 {
		avgRating = math.Round(ratingSum/float64(ratingCount)*10) / 10
	}

	consoles := make([]string, 0, len(consoleSet))
	for name := range consoleSet {
		consoles = append(consoles, name)
	}
	sort.Strings(consoles)
	if len(consoles) == 0 {
		consoles = []string{}
	}

	return avgRating, consoles
}
