package api

import (
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

// --- Phase 8: Console Showcase Pages ---

// GenreCount holds a genre name and the number of games in that genre.
type GenreCount struct {
	Name      string `json:"name"`
	GameCount int    `json:"gameCount"`
}

// ConsoleShowcaseResponse is the API response for a console showcase page.
type ConsoleShowcaseResponse struct {
	Console        ConsoleResponse    `json:"console"`
	Essentials     []GameResponse     `json:"essentials"`
	HiddenGems     []GameResponse     `json:"hiddenGems"`
	GenreBreakdown []GenreCount       `json:"genreBreakdown"`
	TopDevelopers  []DeveloperSummary `json:"topDevelopers"`
	RecentlyPlayed []GameResponse     `json:"recentlyPlayed"`
}

// ConsoleHighlight is a compact summary of a console for the explore page quick-jump row.
type ConsoleHighlight struct {
	ID         string        `json:"id"`
	Name       string        `json:"name"`
	ColorTheme string        `json:"colorTheme"`
	IconURL    string        `json:"iconUrl"`
	LogoURL    string        `json:"logoUrl"`
	GameCount  int           `json:"gameCount"`
	TopGame    *GameResponse `json:"topGame"`
}

// ConsoleHighlightsResponse is the API response for the console highlights endpoint.
type ConsoleHighlightsResponse struct {
	Consoles []ConsoleHighlight `json:"consoles"`
}

// GetConsoleShowcase returns aggregated showcase data for a specific console.
// GET /api/explore/consoles/:id/showcase
func (h *ExploreHandler) GetConsoleShowcase(c *gin.Context) {
	userID := getUserID(c)
	abbr := strings.ToLower(c.Param("id"))

	// Look up the console by abbreviation
	var console db.Console
	if err := h.DB.Where("LOWER(abbreviation) = ?", abbr).First(&console).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "console not found"})
		return
	}

	// Count games for the console response
	var gameCount int64
	h.DB.Model(&db.Game{}).Where("console_id = ? AND deleted_at IS NULL", console.ID).Count(&gameCount)
	console.GameCount = int(gameCount)

	// --- Essentials: top 10 by IGDB rating ---
	var essentials []db.Game
	if err := h.DB.Preload("Console").
		Where("console_id = ? AND rating > 0 AND deleted_at IS NULL", console.ID).
		Order("rating DESC").
		Limit(10).
		Find(&essentials).Error; err != nil {
		slog.Error("failed to fetch console essentials", "console", abbr, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch console data"})
		return
	}

	// --- Hidden Gems: high rating + low play time for this console ---
	// Exclude essentials to avoid showing the same games in both shelves
	essentialIDs := make([]uint, len(essentials))
	for i, g := range essentials {
		essentialIDs[i] = g.ID
	}
	hiddenGems := h.buildConsoleHiddenGems(console.ID, essentialIDs)

	// --- Genre Breakdown: count games per genre for this console ---
	genreBreakdown := h.buildGenreBreakdown(console.ID)

	// --- Top Developers: top 5 developers by game count for this console ---
	topDevelopers := h.buildConsoleTopDevelopers(console.ID, console.Name)

	// --- Recently Played: user's recently played on this console ---
	var recentlyPlayed []db.Game
	if userID > 0 {
		var playHistories []db.PlayHistory
		if err := h.DB.
			Joins("JOIN games ON games.id = play_histories.game_id").
			Where("play_histories.user_id = ? AND games.console_id = ? AND games.deleted_at IS NULL", userID, console.ID).
			Order("play_histories.last_played DESC").
			Limit(10).
			Find(&playHistories).Error; err != nil {
			slog.Error("failed to fetch recently played", "console", abbr, "error", err)
		} else {
			gameIDs := make([]uint, 0, len(playHistories))
			for _, ph := range playHistories {
				gameIDs = append(gameIDs, ph.GameID)
			}
			if len(gameIDs) > 0 {
				if err := h.DB.Preload("Console").
					Where("id IN ? AND deleted_at IS NULL", gameIDs).
					Find(&recentlyPlayed).Error; err != nil {
					slog.Error("failed to fetch recently played games", "console", abbr, "error", err)
				}
				// Re-order to match play history order
				gameMap := make(map[uint]db.Game, len(recentlyPlayed))
				for _, g := range recentlyPlayed {
					gameMap[g.ID] = g
				}
				ordered := make([]db.Game, 0, len(gameIDs))
				for _, id := range gameIDs {
					if g, ok := gameMap[id]; ok {
						ordered = append(ordered, g)
					}
				}
				recentlyPlayed = ordered
			}
		}
	}

	// Batch-load user game data for all games at once (essentials + hidden gems + recently played)
	allGames := make([]db.Game, 0, len(essentials)+len(hiddenGems)+len(recentlyPlayed))
	allGames = append(allGames, essentials...)
	allGames = append(allGames, hiddenGems...)
	allGames = append(allGames, recentlyPlayed...)
	allGameIDs := make([]uint, len(allGames))
	for i, g := range allGames {
		allGameIDs[i] = g.ID
	}
	userData := loadUserGameData(h.DB, userID, allGameIDs)

	toResponses := func(games []db.Game) []GameResponse {
		result := make([]GameResponse, len(games))
		for i, g := range games {
			result[i] = toGameResponseWithData(g, &userData)
		}
		return result
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ConsoleShowcaseResponse{
		Console:        ToConsoleResponse(console),
		Essentials:     toResponses(essentials),
		HiddenGems:     toResponses(hiddenGems),
		GenreBreakdown: genreBreakdown,
		TopDevelopers:  topDevelopers,
		RecentlyPlayed: toResponses(recentlyPlayed),
	})
}

// buildConsoleHiddenGems returns games with high rating but low play time for a console.
func (h *ExploreHandler) buildConsoleHiddenGems(consoleID uint, excludeIDs []uint) []db.Game {
	// Calculate play-time threshold (25th percentile) for this console
	type thresholdRow struct {
		TotalPlayTime int64
	}
	var playTimes []thresholdRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_histories.play_time), 0) as total_play_time").
		Joins("JOIN games ON games.id = play_histories.game_id").
		Where("games.console_id = ? AND games.deleted_at IS NULL", consoleID).
		Group("play_histories.game_id").
		Having("total_play_time > 0").
		Order("total_play_time ASC").
		Scan(&playTimes).Error; err != nil {
		slog.Error("failed to calculate play time threshold for console hidden gems", "error", err)
		return nil
	}

	var threshold int64
	if len(playTimes) > 0 {
		idx := len(playTimes) / 4
		threshold = playTimes[idx].TotalPlayTime
		if threshold == 0 {
			threshold = 1
		}
	}

	var games []db.Game
	query := h.DB.Preload("Console").
		Joins("LEFT JOIN (SELECT game_id, COALESCE(SUM(play_time), 0) as total_play_time FROM play_histories GROUP BY game_id) ph ON ph.game_id = games.id").
		Where("games.console_id = ?", consoleID).
		Where("games.rating >= 70").
		Where("games.deleted_at IS NULL")

	if len(excludeIDs) > 0 {
		query = query.Where("games.id NOT IN ?", excludeIDs)
	}

	if threshold > 0 {
		query = query.Where("ph.total_play_time IS NULL OR ph.total_play_time <= ?", threshold)
	}

	if err := query.
		Order("games.rating DESC").
		Limit(10).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch console hidden gems", "error", err)
		return nil
	}

	return games
}

// buildGenreBreakdown returns genre counts for games on a specific console.
func (h *ExploreHandler) buildGenreBreakdown(consoleID uint) []GenreCount {
	type genreRow struct {
		Genre     string
		GameCount int
	}
	var rows []genreRow
	if err := h.DB.
		Table("games").
		Select("genre, COUNT(*) as game_count").
		Where("console_id = ? AND deleted_at IS NULL AND genre != ''", consoleID).
		Group("genre").
		Order("game_count DESC").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch genre breakdown", "error", err)
		return []GenreCount{}
	}

	result := make([]GenreCount, len(rows))
	for i, r := range rows {
		result[i] = GenreCount{
			Name:      r.Genre,
			GameCount: r.GameCount,
		}
	}
	return result
}

// buildConsoleTopDevelopers returns the top 5 developers by game count for a console.
func (h *ExploreHandler) buildConsoleTopDevelopers(consoleID uint, consoleName string) []DeveloperSummary {
	type devRow struct {
		Developer string
		GameCount int
		AvgRating float64
	}

	var rows []devRow
	if err := h.DB.
		Table("games").
		Select("developer, COUNT(*) as game_count, AVG(CASE WHEN rating > 0 THEN rating ELSE NULL END) as avg_rating").
		Where("console_id = ? AND deleted_at IS NULL AND developer != ''", consoleID).
		Group("developer").
		Order("game_count DESC").
		Limit(5).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch top developers for console", "error", err)
		return []DeveloperSummary{}
	}

	developers := make([]DeveloperSummary, len(rows))
	for i, r := range rows {
		avgRating := 0.0
		if r.AvgRating > 0 {
			avgRating = math.Round(r.AvgRating*10) / 10
		}
		developers[i] = DeveloperSummary{
			Name:      r.Developer,
			GameCount: r.GameCount,
			AvgRating: avgRating,
			Consoles:  []string{consoleName},
		}
	}
	return developers
}

// GetConsoleHighlights returns a compact list of consoles with their top game for the explore page.
// GET /api/explore/console-highlights
func (h *ExploreHandler) GetConsoleHighlights(c *gin.Context) {
	userID := getUserID(c)

	// Get all consoles with game counts
	var consoles []db.Console
	if err := h.DB.Order("name ASC").Find(&consoles).Error; err != nil {
		slog.Error("failed to fetch consoles for highlights", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch consoles"})
		return
	}

	// Count games per console
	type consoleCount struct {
		ConsoleID uint
		GameCount int
	}
	var counts []consoleCount
	if err := h.DB.
		Table("games").
		Select("console_id, COUNT(*) as game_count").
		Where("deleted_at IS NULL").
		Group("console_id").
		Scan(&counts).Error; err != nil {
		slog.Error("failed to count games per console", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch console data"})
		return
	}

	countMap := make(map[uint]int, len(counts))
	for _, cc := range counts {
		countMap[cc.ConsoleID] = cc.GameCount
	}

	// Find the top game per console (highest rated game with hero art)
	type topGameRow struct {
		ConsoleID uint
		GameID    uint
	}
	// For each console, find the highest-rated game that has hero art.
	// Use GROUP BY to get the max-rated game per console efficiently.
	var topGameRows []topGameRow
	if err := h.DB.Raw(`
		SELECT console_id, game_id FROM (
			SELECT games.console_id, games.id as game_id, games.rating,
				ROW_NUMBER() OVER (PARTITION BY games.console_id ORDER BY games.rating DESC) as rn
			FROM games
			JOIN game_artworks ON game_artworks.game_id = games.id AND game_artworks.hero_url != ''
			WHERE games.deleted_at IS NULL AND games.rating > 0
		) ranked WHERE rn = 1
	`).Scan(&topGameRows).Error; err != nil {
		slog.Error("failed to fetch top games for console highlights", "error", err)
		// Continue without top games
	}

	// Map top game per console (query already returns exactly one per console)
	topGameByConsole := make(map[uint]uint, len(topGameRows))
	for _, row := range topGameRows {
		topGameByConsole[row.ConsoleID] = row.GameID
	}

	// Batch-load all top games
	topGameIDs := make([]uint, 0, len(topGameByConsole))
	for _, gid := range topGameByConsole {
		topGameIDs = append(topGameIDs, gid)
	}

	var topGames []db.Game
	if len(topGameIDs) > 0 {
		if err := h.DB.Preload("Console").Where("id IN ?", topGameIDs).Find(&topGames).Error; err != nil {
			slog.Error("failed to load top games for console highlights", "error", err)
		}
	}

	topGameResponses := ToGameResponses(topGames, h.DB, userID)
	topGameResponseMap := make(map[string]*GameResponse, len(topGameResponses))
	for i := range topGameResponses {
		topGameResponseMap[topGameResponses[i].ID] = &topGameResponses[i]
	}

	highlights := make([]ConsoleHighlight, 0, len(consoles))
	for _, con := range consoles {
		gc := countMap[con.ID]
		if gc == 0 {
			continue // Skip consoles with no games
		}

		abbr := strings.ToLower(con.Abbreviation)
		highlight := ConsoleHighlight{
			ID:         abbr,
			Name:       con.Name,
			ColorTheme: con.ColorTheme,
			IconURL:    "/api/consoles/" + abbr + "/icon",
			LogoURL:    "/api/consoles/" + abbr + "/logo",
			GameCount:  gc,
		}

		// Attach top game if found
		if topGameID, ok := topGameByConsole[con.ID]; ok {
			idStr := strconv.FormatUint(uint64(topGameID), 10)
			if gr, ok := topGameResponseMap[idStr]; ok {
				highlight.TopGame = gr
			}
		}

		highlights = append(highlights, highlight)
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ConsoleHighlightsResponse{Consoles: highlights})
}

