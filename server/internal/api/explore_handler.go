package api

import (
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// FeaturedSeriesResponse is the API response for a featured series on the Explore page.
type FeaturedSeriesResponse struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	LibraryGames int    `json:"libraryGames"`
	TotalGames   int    `json:"totalGames"`
	ConsoleCount int    `json:"consoleCount"`
	HeroURL      string `json:"heroUrl,omitempty"`
}

// ExploreHandler handles explore page endpoints.
type ExploreHandler struct {
	DB *gorm.DB
}

// FeaturedGameResponse is the API response for a featured game in the hero carousel.
type FeaturedGameResponse struct {
	GameID              string  `json:"gameId"`
	Title               string  `json:"title"`
	HeroURL             string  `json:"heroUrl"`
	LogoURL             string  `json:"logoUrl"`
	ConsoleID           string  `json:"consoleId"`
	ConsoleName         string  `json:"consoleName"`
	ConsoleAbbreviation string  `json:"consoleAbbreviation"`
	ConsoleColor        string  `json:"consoleColor"`
	Rating              float64 `json:"rating"`
	Genre               string  `json:"genre"`
	IsFavorite          bool    `json:"isFavorite"`
	IsPlayLater         bool    `json:"isPlayLater"`
}

// ExploreRowResponse is the API response for a single curated row on the explore page.
type ExploreRowResponse struct {
	ID    string         `json:"id"`
	Title string         `json:"title"`
	Games []GameResponse `json:"games"`
}

// ExploreRowsResponse is the API response for all explore rows.
type ExploreRowsResponse struct {
	Rows []ExploreRowResponse `json:"rows"`
}

// GetExploreFeatured returns featured games for the hero carousel.
// Games must have both hero art and logo art from SteamGridDB, sorted by IGDB rating descending.
func (h *ExploreHandler) GetExploreFeatured(c *gin.Context) {
	userID := getUserID(c)

	// Find games that have hero art AND logo art, sorted by rating desc, limit 8
	type featuredRow struct {
		GameID    uint
		Title     string
		HeroURL   string
		LogoURL   string
		Rating    float64
		Genre     string
		ConsoleID uint
	}

	var rows []featuredRow
	err := h.DB.
		Table("games").
		Select("games.id AS game_id, games.title, game_artworks.hero_url, game_artworks.logo_url, games.rating, games.genre, games.console_id").
		Joins("JOIN game_artworks ON game_artworks.game_id = games.id").
		Where("games.deleted_at IS NULL").
		Where("game_artworks.hero_url != '' AND game_artworks.logo_url != ''").
		Order("games.rating DESC").
		Limit(8).
		Scan(&rows).Error
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch featured games"})
		return
	}

	if len(rows) == 0 {
		c.JSON(http.StatusOK, []FeaturedGameResponse{})
		return
	}

	// Batch-load console data for these games
	consoleIDs := make([]uint, 0, len(rows))
	for _, r := range rows {
		consoleIDs = append(consoleIDs, r.ConsoleID)
	}
	var consoles []db.Console
	if err := h.DB.Where("id IN ?", consoleIDs).Find(&consoles).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch console data"})
		return
	}
	consoleMap := make(map[uint]db.Console, len(consoles))
	for _, con := range consoles {
		consoleMap[con.ID] = con
	}

	// Batch-load user data (favorites, play later)
	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}
	favorites := make(map[uint]bool)
	playLater := make(map[uint]bool)
	if userID > 0 {
		var favs []db.Favorite
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&favs).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch favorites"})
			return
		}
		for _, f := range favs {
			favorites[f.GameID] = true
		}
		var plItems []db.PlayLaterItem
		if err := h.DB.Where("user_id = ? AND game_id IN ?", userID, gameIDs).Find(&plItems).Error; err != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch play later items"})
			return
		}
		for _, item := range plItems {
			playLater[item.GameID] = true
		}
	}

	result := make([]FeaturedGameResponse, len(rows))
	for i, r := range rows {
		con := consoleMap[r.ConsoleID]
		abbr := strings.ToLower(con.Abbreviation)
		result[i] = FeaturedGameResponse{
			GameID:              strconv.FormatUint(uint64(r.GameID), 10),
			Title:               r.Title,
			HeroURL:             r.HeroURL,
			LogoURL:             r.LogoURL,
			ConsoleID:           abbr,
			ConsoleName:         con.Name,
			ConsoleAbbreviation: abbr,
			ConsoleColor:        con.ColorTheme,
			Rating:              r.Rating,
			Genre:               r.Genre,
			IsFavorite:          favorites[r.GameID],
			IsPlayLater:         playLater[r.GameID],
		}
	}

	c.JSON(http.StatusOK, result)
}

// GetExploreRows returns all curated shelf rows for the explore page.
// Empty rows are omitted from the response.
func (h *ExploreHandler) GetExploreRows(c *gin.Context) {
	userID := getUserID(c)

	rows := []ExploreRowResponse{}

	// Top Rated: top 20 games by IGDB rating, rating > 0
	if row, err := h.buildTopRatedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build top-rated row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Recently Added: top 20 games by created_at DESC
	if row, err := h.buildRecentlyAddedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recently-added row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Hidden Gems: high rating + low play time across all users
	if row, err := h.buildHiddenGemsRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build hidden-gems row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	// Most Played on Your Server: top 20 by total play time across all users
	if row, err := h.buildMostPlayedRow(userID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build most-played row"})
		return
	} else if row != nil {
		rows = append(rows, *row)
	}

	c.JSON(http.StatusOK, ExploreRowsResponse{Rows: rows})
}

// buildTopRatedRow returns the top 20 games by IGDB rating, cross-console.
func (h *ExploreHandler) buildTopRatedRow(userID uint) (*ExploreRowResponse, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("rating > 0").
		Order("rating DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "top-rated",
		Title: "Top Rated",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildRecentlyAddedRow returns the 20 most recently added games.
func (h *ExploreHandler) buildRecentlyAddedRow(userID uint) (*ExploreRowResponse, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Order("created_at DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "recently-added",
		Title: "Recently Added",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildHiddenGemsRow returns games with high IGDB rating but low total play time
// across all users. Uses SQL to compute the play-time threshold and filter in a
// single bounded query instead of loading all games/play history into memory.
func (h *ExploreHandler) buildHiddenGemsRow(userID uint) (*ExploreRowResponse, error) {
	// First, check if there are at least 5 games total (per acceptance criteria,
	// hidden gems section is hidden when library is tiny)
	var totalGames int64
	if err := h.DB.Model(&db.Game{}).Count(&totalGames).Error; err != nil {
		return nil, err
	}
	if totalGames < 5 {
		return nil, nil
	}

	// Calculate the play-time threshold (25th percentile) in SQL.
	// We only need the count of games with play time > 0 and the threshold value.
	type thresholdRow struct {
		TotalPlayTime int64
	}
	var playTimes []thresholdRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_time), 0) as total_play_time").
		Group("game_id").
		Having("total_play_time > 0").
		Order("total_play_time ASC").
		Scan(&playTimes).Error; err != nil {
		return nil, err
	}

	var threshold int64
	if len(playTimes) > 0 {
		idx := len(playTimes) / 4
		threshold = playTimes[idx].TotalPlayTime
		if threshold == 0 {
			threshold = 1
		}
	}
	// If threshold is 0 (no play history at all), all rated games qualify

	// Query games with rating >= 75 and low/zero play time in a single bounded query.
	// LEFT JOIN aggregated play history, filter where total play time <= threshold or NULL.
	var games []db.Game
	query := h.DB.Preload("Console").
		Joins("LEFT JOIN (SELECT game_id, COALESCE(SUM(play_time), 0) as total_play_time FROM play_histories GROUP BY game_id) ph ON ph.game_id = games.id").
		Where("games.rating >= 75").
		Where("games.deleted_at IS NULL")

	if threshold > 0 {
		query = query.Where("ph.total_play_time IS NULL OR ph.total_play_time <= ?", threshold)
	}

	if err := query.
		Order("games.rating DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ExploreRowResponse{
		ID:    "hidden-gems",
		Title: "Hidden Gems",
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildMostPlayedRow returns the top 20 games by total play time across all users.
func (h *ExploreHandler) buildMostPlayedRow(userID uint) (*ExploreRowResponse, error) {
	// Aggregate play time per game across all users, bounded to top 20
	type playTimeRow struct {
		GameID        uint
		TotalPlayTime int64
	}
	var playTimeRows []playTimeRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, COALESCE(SUM(play_time), 0) as total_play_time").
		Group("game_id").
		Having("total_play_time > 0").
		Order("total_play_time DESC").
		Limit(20).
		Scan(&playTimeRows).Error; err != nil {
		return nil, err
	}

	if len(playTimeRows) == 0 {
		return nil, nil
	}

	// Load the game objects (scoped to only the game IDs we need)
	gameIDs := make([]uint, len(playTimeRows))
	for i, r := range playTimeRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	// Re-sort games by total play time (the IN query may not preserve order)
	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sortedGames := make([]db.Game, 0, len(playTimeRows))
	for _, r := range playTimeRows {
		if g, ok := gameMap[r.GameID]; ok {
			sortedGames = append(sortedGames, g)
		}
	}

	return &ExploreRowResponse{
		ID:    "most-played",
		Title: "Most Played on Your Server",
		Games: ToGameResponses(sortedGames, h.DB, userID),
	}, nil
}

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

	c.JSON(http.StatusOK, result)
}

// MoodResponse is the API response for a single mood option.
type MoodResponse struct {
	ID          string   `json:"id"`
	Name        string   `json:"name"`
	Description string   `json:"description"`
	Icon        string   `json:"icon"`
	Gradient    []string `json:"gradient"`
}

// moodDefinitions holds the static list of available moods.
var moodDefinitions = []MoodResponse{
	{ID: "chill", Name: "Chill", Description: "Relax with something easy-going", Icon: "\U0001F3B5", Gradient: []string{"#1B5E20", "#4CAF50"}},
	{ID: "challenge", Name: "Challenge Me", Description: "Test your skills with something tough", Icon: "\U0001F525", Gradient: []string{"#B71C1C", "#F44336"}},
	{ID: "nostalgia", Name: "Nostalgia Trip", Description: "Revisit your most-played classics", Icon: "\u2728", Gradient: []string{"#4A148C", "#9C27B0"}},
	{ID: "something-new", Name: "Something New", Description: "Try a game you haven't played yet", Icon: "\U0001F195", Gradient: []string{"#0D47A1", "#2196F3"}},
	{ID: "quick", Name: "Quick Session", Description: "Pick up and play in under 15 minutes", Icon: "\u26A1", Gradient: []string{"#E65100", "#FF9800"}},
	{ID: "together", Name: "Play Together", Description: "Games built for multiplayer fun", Icon: "\U0001F3AE", Gradient: []string{"#006064", "#00BCD4"}},
}

// GetExploreMoods returns the list of available moods for the mood picker.
// GET /api/explore/moods
func (h *ExploreHandler) GetExploreMoods(c *gin.Context) {
	c.JSON(http.StatusOK, moodDefinitions)
}

// GetMoodGames returns games matching the specified mood criteria.
// GET /api/explore/mood/:mood
func (h *ExploreHandler) GetMoodGames(c *gin.Context) {
	userID := getUserID(c)
	mood := c.Param("mood")

	var games []db.Game
	var err error

	switch mood {
	case "chill":
		games, err = h.getMoodChillGames()
	case "challenge":
		games, err = h.getMoodChallengeGames()
	case "nostalgia":
		games, err = h.getMoodNostalgiaGames(userID)
	case "something-new":
		games, err = h.getMoodSomethingNewGames(userID)
	case "quick":
		games, err = h.getMoodQuickGames()
	case "together":
		games, err = h.getMoodTogetherGames()
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid mood"})
		return
	}

	if err != nil {
		slog.Error("failed to fetch mood games", "mood", mood, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch mood games"})
		return
	}

	c.JSON(http.StatusOK, ToGameResponses(games, h.DB, userID))
}

// getMoodChillGames returns games suitable for chill/relaxed play.
// Matches themes (Fantasy, Comedy), genres (Puzzle, Simulation), or keywords (relaxing, casual).
func (h *ExploreHandler) getMoodChillGames() ([]db.Game, error) {
	// Collect matching game IDs from themes, keywords, and genre
	gameIDSet := make(map[uint]bool)

	// Themes: Fantasy, Comedy
	var themeGameIDs []uint
	if err := h.DB.Model(&db.GameTheme{}).
		Select("DISTINCT game_id").
		Where("name IN ?", []string{"Fantasy", "Comedy"}).
		Pluck("game_id", &themeGameIDs).Error; err != nil {
		return nil, err
	}
	for _, id := range themeGameIDs {
		gameIDSet[id] = true
	}

	// Keywords: relaxing, casual
	var keywordGameIDs []uint
	if err := h.DB.Model(&db.GameKeyword{}).
		Select("DISTINCT game_id").
		Where("LOWER(name) LIKE ? OR LOWER(name) LIKE ?", "%relaxing%", "%casual%").
		Pluck("game_id", &keywordGameIDs).Error; err != nil {
		return nil, err
	}
	for _, id := range keywordGameIDs {
		gameIDSet[id] = true
	}

	// Genre: Puzzle, Simulation
	var genreGameIDs []uint
	if err := h.DB.Model(&db.Game{}).
		Select("id").
		Where("LOWER(genre) LIKE ? OR LOWER(genre) LIKE ?", "%puzzle%", "%simulation%").
		Pluck("id", &genreGameIDs).Error; err != nil {
		return nil, err
	}
	for _, id := range genreGameIDs {
		gameIDSet[id] = true
	}

	return h.loadGamesByIDs(gameIDSet, "games.rating DESC", 20)
}

// getMoodChallengeGames returns games that are difficult or intense.
// Matches keywords (difficult, hardcore) or themes (Survival, Horror).
func (h *ExploreHandler) getMoodChallengeGames() ([]db.Game, error) {
	gameIDSet := make(map[uint]bool)

	// Keywords: difficult, hardcore
	var keywordGameIDs []uint
	if err := h.DB.Model(&db.GameKeyword{}).
		Select("DISTINCT game_id").
		Where("LOWER(name) LIKE ? OR LOWER(name) LIKE ?", "%difficult%", "%hardcore%").
		Pluck("game_id", &keywordGameIDs).Error; err != nil {
		return nil, err
	}
	for _, id := range keywordGameIDs {
		gameIDSet[id] = true
	}

	// Themes: Survival, Horror
	var themeGameIDs []uint
	if err := h.DB.Model(&db.GameTheme{}).
		Select("DISTINCT game_id").
		Where("name IN ?", []string{"Survival", "Horror"}).
		Pluck("game_id", &themeGameIDs).Error; err != nil {
		return nil, err
	}
	for _, id := range themeGameIDs {
		gameIDSet[id] = true
	}

	return h.loadGamesByIDs(gameIDSet, "games.rating DESC", 20)
}

// getMoodNostalgiaGames returns the user's most-played games.
func (h *ExploreHandler) getMoodNostalgiaGames(userID uint) ([]db.Game, error) {
	// Get the user's most-played games ordered by play_time DESC
	type playRow struct {
		GameID   uint
		PlayTime int64
	}
	var playRows []playRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, play_time").
		Where("user_id = ? AND play_time > 0", userID).
		Order("play_time DESC").
		Limit(20).
		Scan(&playRows).Error; err != nil {
		return nil, err
	}

	if len(playRows) == 0 {
		return []db.Game{}, nil
	}

	gameIDs := make([]uint, len(playRows))
	for i, r := range playRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		return nil, err
	}

	// Re-sort by play time (IN query doesn't preserve order)
	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sorted := make([]db.Game, 0, len(playRows))
	for _, r := range playRows {
		if g, ok := gameMap[r.GameID]; ok {
			sorted = append(sorted, g)
		}
	}

	return sorted, nil
}

// getMoodSomethingNewGames returns games the user hasn't played yet.
func (h *ExploreHandler) getMoodSomethingNewGames(userID uint) ([]db.Game, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Joins("LEFT JOIN play_histories ON play_histories.game_id = games.id AND play_histories.user_id = ? AND play_histories.deleted_at IS NULL", userID).
		Where("games.deleted_at IS NULL").
		Where("play_histories.id IS NULL OR play_histories.play_time = 0").
		Order("games.rating DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	return games, nil
}

// getMoodQuickGames returns games with short average session times (< 15 min / 900 seconds).
func (h *ExploreHandler) getMoodQuickGames() ([]db.Game, error) {
	// Aggregate play_histories per game: total_play_time / count(*) = avg session time
	type quickRow struct {
		GameID         uint
		AvgSessionTime float64
	}
	var quickRows []quickRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, CAST(SUM(play_time) AS REAL) / COUNT(*) as avg_session_time").
		Where("play_time > 0").
		Group("game_id").
		Having("avg_session_time < 900 AND avg_session_time > 0").
		Scan(&quickRows).Error; err != nil {
		return nil, err
	}

	if len(quickRows) == 0 {
		return []db.Game{}, nil
	}

	gameIDs := make([]uint, len(quickRows))
	for i, r := range quickRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("id IN ?", gameIDs).
		Order("rating DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	return games, nil
}

// getMoodTogetherGames returns multiplayer-capable games (players > 1).
func (h *ExploreHandler) getMoodTogetherGames() ([]db.Game, error) {
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("players > 1").
		Order("rating DESC").
		Limit(20).
		Find(&games).Error; err != nil {
		return nil, err
	}

	return games, nil
}

// loadGamesByIDs loads games by a set of IDs with the given order and limit.
// Returns an empty slice (not nil) if no IDs match.
func (h *ExploreHandler) loadGamesByIDs(idSet map[uint]bool, order string, limit int) ([]db.Game, error) {
	if len(idSet) == 0 {
		return []db.Game{}, nil
	}

	ids := make([]uint, 0, len(idSet))
	for id := range idSet {
		ids = append(ids, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("id IN ?", ids).
		Order(order).
		Limit(limit).
		Find(&games).Error; err != nil {
		return nil, err
	}

	return games, nil
}

// GetSurpriseGame returns a single random game with high rating and cover art.
// GET /api/explore/surprise
func (h *ExploreHandler) GetSurpriseGame(c *gin.Context) {
	userID := getUserID(c)

	// Pick a random eligible game using SQL ORDER BY RANDOM() to avoid loading all games into memory
	var game db.Game
	if err := h.DB.Preload("Console").
		Where("rating > 70 AND cover_url != ''").
		Order("RANDOM()").
		Limit(1).
		First(&game).Error; err != nil {
		if err.Error() == "record not found" {
			c.JSON(http.StatusNotFound, gin.H{"error": "no eligible games found"})
			return
		}
		slog.Error("failed to fetch surprise game", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch surprise game"})
		return
	}

	c.JSON(http.StatusOK, ToGameResponse(game, h.DB, userID))
}

// --- Phase 6: Personalized Recommendations ---

// ForYouRowResponse represents a single recommendation row in the "For You" response.
type ForYouRowResponse struct {
	Type       string        `json:"type"`
	Title      string        `json:"title"`
	SourceGame *GameResponse `json:"sourceGame,omitempty"`
	Genre      string        `json:"genre,omitempty"`
	Games      []GameResponse `json:"games"`
}

// ForYouResponse is the API response for the personalized for-you endpoint.
type ForYouResponse struct {
	Rows []ForYouRowResponse `json:"rows"`
}

// TasteProfileGenre represents a genre breakdown in the taste profile.
type TasteProfileGenre struct {
	Name       string  `json:"name"`
	Percentage float64 `json:"percentage"`
	PlayTime   int64   `json:"playTime"`
	GameCount  int     `json:"gameCount"`
}

// TasteProfileTheme represents a theme breakdown in the taste profile.
type TasteProfileTheme struct {
	Name       string  `json:"name"`
	Percentage float64 `json:"percentage"`
	PlayTime   int64   `json:"playTime"`
	GameCount  int     `json:"gameCount"`
}

// TasteProfileConsole represents a console breakdown in the taste profile.
type TasteProfileConsole struct {
	Name         string `json:"name"`
	Abbreviation string `json:"abbreviation"`
	PlayTime     int64  `json:"playTime"`
	GameCount    int    `json:"gameCount"`
}

// TasteProfileResponse is the API response for the user taste profile.
type TasteProfileResponse struct {
	TotalPlayTime int64                 `json:"totalPlayTime"`
	Genres        []TasteProfileGenre   `json:"genres"`
	Themes        []TasteProfileTheme   `json:"themes"`
	TopConsoles   []TasteProfileConsole `json:"topConsoles"`
}

// PlayersLikeYouResponse is the API response for collaborative filtering recommendations.
type PlayersLikeYouResponse struct {
	Games             []GameResponse `json:"games"`
	SimilarUsersCount int            `json:"similarUsersCount"`
}

// GetForYou returns personalized recommendation rows based on the user's play history.
// GET /api/explore/for-you
func (h *ExploreHandler) GetForYou(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	rows := []ForYouRowResponse{}

	// a) "Because you played [Game]" — top 3 most-played games, find same-genre unplayed games
	becauseRows, err := h.buildBecauseYouPlayedRows(userID)
	if err != nil {
		slog.Error("failed to build because-you-played rows", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recommendations"})
		return
	}
	rows = append(rows, becauseRows...)

	// b) "More [Genre] for you" — most-played genre, top-rated unplayed games
	moreGenreRow, err := h.buildMoreGenreRow(userID)
	if err != nil {
		slog.Error("failed to build more-genre row", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recommendations"})
		return
	}
	if moreGenreRow != nil {
		rows = append(rows, *moreGenreRow)
	}

	// c) "Your unfinished business" — played < 30 min, last played > 7 days ago
	unfinishedRow, err := h.buildUnfinishedRow(userID)
	if err != nil {
		slog.Error("failed to build unfinished row", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recommendations"})
		return
	}
	if unfinishedRow != nil {
		rows = append(rows, *unfinishedRow)
	}

	// d) "Expand your horizons" — genres the user has never played
	expandRow, err := h.buildExpandHorizonsRow(userID)
	if err != nil {
		slog.Error("failed to build expand-horizons row", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build recommendations"})
		return
	}
	if expandRow != nil {
		rows = append(rows, *expandRow)
	}

	c.JSON(http.StatusOK, ForYouResponse{Rows: rows})
}

// buildBecauseYouPlayedRows generates "Because you played [Game]" recommendation rows.
func (h *ExploreHandler) buildBecauseYouPlayedRows(userID uint) ([]ForYouRowResponse, error) {
	// Get top 3 most-played games
	type playRow struct {
		GameID   uint
		PlayTime int64
	}
	var topPlayed []playRow
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, play_time").
		Where("user_id = ? AND play_time > 0", userID).
		Order("play_time DESC").
		Limit(3).
		Scan(&topPlayed).Error; err != nil {
		return nil, err
	}

	if len(topPlayed) == 0 {
		return nil, nil
	}

	// Get all played game IDs for this user (to exclude from recommendations)
	var playedGameIDs []uint
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id").
		Where("user_id = ? AND play_time > 0", userID).
		Pluck("game_id", &playedGameIDs).Error; err != nil {
		return nil, err
	}
	playedSet := make(map[uint]bool, len(playedGameIDs))
	for _, id := range playedGameIDs {
		playedSet[id] = true
	}

	// Load the source games
	sourceGameIDs := make([]uint, len(topPlayed))
	for i, tp := range topPlayed {
		sourceGameIDs[i] = tp.GameID
	}
	var sourceGames []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", sourceGameIDs).Find(&sourceGames).Error; err != nil {
		return nil, err
	}
	sourceGameMap := make(map[uint]db.Game, len(sourceGames))
	for _, g := range sourceGames {
		sourceGameMap[g.ID] = g
	}

	var rows []ForYouRowResponse
	// Track already-recommended game IDs to avoid duplication across rows
	alreadyRecommended := make(map[uint]bool)

	for _, tp := range topPlayed {
		srcGame, ok := sourceGameMap[tp.GameID]
		if !ok || srcGame.Genre == "" {
			continue
		}

		// Find games in the same genre that the user hasn't played
		var recGames []db.Game
		query := h.DB.Preload("Console").
			Where("games.genre = ? AND games.deleted_at IS NULL", srcGame.Genre)

		if len(playedGameIDs) > 0 {
			query = query.Where("games.id NOT IN ?", playedGameIDs)
		}

		if err := query.
			Order("games.rating DESC").
			Limit(20). // fetch extra to filter duplicates
			Find(&recGames).Error; err != nil {
			return nil, err
		}

		// Filter out already-recommended games
		var filtered []db.Game
		for _, g := range recGames {
			if !alreadyRecommended[g.ID] {
				filtered = append(filtered, g)
				alreadyRecommended[g.ID] = true
				if len(filtered) >= 10 {
					break
				}
			}
		}

		if len(filtered) == 0 {
			continue
		}

		srcResp := ToGameResponse(srcGame, h.DB, userID)
		rows = append(rows, ForYouRowResponse{
			Type:       "because_you_played",
			Title:      fmt.Sprintf("Because you played %s", srcGame.Title),
			SourceGame: &srcResp,
			Games:      ToGameResponses(filtered, h.DB, userID),
		})
	}

	return rows, nil
}

// buildMoreGenreRow generates the "More [Genre] for you" recommendation row.
func (h *ExploreHandler) buildMoreGenreRow(userID uint) (*ForYouRowResponse, error) {
	// Find the user's most-played genre by summing play time per genre
	type genreRow struct {
		Genre         string
		TotalPlayTime int64
	}
	var genreRows []genreRow
	if err := h.DB.
		Table("play_histories").
		Select("games.genre, SUM(play_histories.play_time) as total_play_time").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND games.genre != '' AND play_histories.deleted_at IS NULL", userID).
		Group("games.genre").
		Order("total_play_time DESC").
		Limit(1).
		Scan(&genreRows).Error; err != nil {
		return nil, err
	}

	if len(genreRows) == 0 || genreRows[0].Genre == "" {
		return nil, nil
	}

	topGenre := genreRows[0].Genre

	// Get played game IDs for this user
	var playedGameIDs []uint
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id").
		Where("user_id = ? AND play_time > 0", userID).
		Pluck("game_id", &playedGameIDs).Error; err != nil {
		return nil, err
	}

	// Find top-rated unplayed games in this genre
	var games []db.Game
	query := h.DB.Preload("Console").
		Where("games.genre = ? AND games.deleted_at IS NULL", topGenre)
	if len(playedGameIDs) > 0 {
		query = query.Where("games.id NOT IN ?", playedGameIDs)
	}
	if err := query.
		Order("games.rating DESC").
		Limit(10).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ForYouRowResponse{
		Type:  "more_genre",
		Title: fmt.Sprintf("More %s for you", topGenre),
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// buildUnfinishedRow generates the "Your unfinished business" recommendation row.
func (h *ExploreHandler) buildUnfinishedRow(userID uint) (*ForYouRowResponse, error) {
	sevenDaysAgo := time.Now().Add(-7 * 24 * time.Hour)

	// Find games played < 30 min (1800 seconds) and last played > 7 days ago
	var histories []db.PlayHistory
	if err := h.DB.
		Where("user_id = ? AND play_time > 0 AND play_time < 1800 AND last_played < ?", userID, sevenDaysAgo).
		Order("last_played DESC").
		Limit(10).
		Find(&histories).Error; err != nil {
		return nil, err
	}

	if len(histories) == 0 {
		return nil, nil
	}

	gameIDs := make([]uint, len(histories))
	for i, h := range histories {
		gameIDs[i] = h.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	// Re-sort by last_played DESC (the IN query may not preserve order)
	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sorted := make([]db.Game, 0, len(histories))
	for _, h := range histories {
		if g, ok := gameMap[h.GameID]; ok {
			sorted = append(sorted, g)
		}
	}

	return &ForYouRowResponse{
		Type:  "unfinished",
		Title: "Your unfinished business",
		Games: ToGameResponses(sorted, h.DB, userID),
	}, nil
}

// buildExpandHorizonsRow generates the "Expand your horizons" recommendation row.
func (h *ExploreHandler) buildExpandHorizonsRow(userID uint) (*ForYouRowResponse, error) {
	// Get all genres the user has played
	var playedGenres []string
	if err := h.DB.
		Table("play_histories").
		Select("DISTINCT games.genre").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND games.genre != '' AND play_histories.deleted_at IS NULL", userID).
		Pluck("games.genre", &playedGenres).Error; err != nil {
		return nil, err
	}

	// If user has no play history, we can't determine what's "new" for them
	if len(playedGenres) == 0 {
		return nil, nil
	}

	// Find genres the user has never played, ranked by number of high-rated games (rating > 70)
	type unplayedGenreRow struct {
		Genre     string
		GameCount int64
	}
	var unplayedGenres []unplayedGenreRow
	if err := h.DB.Model(&db.Game{}).
		Select("genre, COUNT(*) as game_count").
		Where("genre NOT IN ? AND genre != '' AND rating > 70 AND deleted_at IS NULL", playedGenres).
		Group("genre").
		Order("game_count DESC").
		Limit(1).
		Scan(&unplayedGenres).Error; err != nil {
		return nil, err
	}

	if len(unplayedGenres) == 0 {
		return nil, nil
	}

	targetGenre := unplayedGenres[0].Genre

	// Get top-rated games in this genre
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("genre = ? AND rating > 70 AND deleted_at IS NULL", targetGenre).
		Order("rating DESC").
		Limit(10).
		Find(&games).Error; err != nil {
		return nil, err
	}

	if len(games) == 0 {
		return nil, nil
	}

	return &ForYouRowResponse{
		Type:  "expand_horizons",
		Title: fmt.Sprintf("Expand your horizons — try %s", targetGenre),
		Genre: targetGenre,
		Games: ToGameResponses(games, h.DB, userID),
	}, nil
}

// GetTasteProfile returns the user's genre/theme breakdown based on play history.
// GET /api/user/taste-profile
func (h *ExploreHandler) GetTasteProfile(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Calculate total play time
	var totalPlayTime int64
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_time), 0)").
		Where("user_id = ? AND play_time > 0", userID).
		Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to calculate total play time", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build taste profile"})
		return
	}

	// Genre breakdown
	type genreRow struct {
		Genre     string
		PlayTime  int64
		GameCount int
	}
	var genreRows []genreRow
	if err := h.DB.
		Table("play_histories").
		Select("games.genre, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND games.genre != '' AND play_histories.deleted_at IS NULL", userID).
		Group("games.genre").
		Order("play_time DESC").
		Scan(&genreRows).Error; err != nil {
		slog.Error("failed to get genre breakdown", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build taste profile"})
		return
	}

	genres := make([]TasteProfileGenre, len(genreRows))
	for i, r := range genreRows {
		pct := float64(0)
		if totalPlayTime > 0 {
			pct = math.Round(float64(r.PlayTime) / float64(totalPlayTime) * 100)
		}
		genres[i] = TasteProfileGenre{
			Name:       r.Genre,
			Percentage: pct,
			PlayTime:   r.PlayTime,
			GameCount:  r.GameCount,
		}
	}

	// Theme breakdown
	type themeRow struct {
		Name      string
		PlayTime  int64
		GameCount int
	}
	var themeRows []themeRow
	if err := h.DB.
		Table("play_histories").
		Select("game_themes.name, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN game_themes ON game_themes.game_id = play_histories.game_id").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND play_histories.deleted_at IS NULL", userID).
		Group("game_themes.name").
		Order("play_time DESC").
		Scan(&themeRows).Error; err != nil {
		slog.Error("failed to get theme breakdown", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build taste profile"})
		return
	}

	themes := make([]TasteProfileTheme, len(themeRows))
	for i, r := range themeRows {
		pct := float64(0)
		if totalPlayTime > 0 {
			pct = math.Round(float64(r.PlayTime) / float64(totalPlayTime) * 100)
		}
		themes[i] = TasteProfileTheme{
			Name:       r.Name,
			Percentage: pct,
			PlayTime:   r.PlayTime,
			GameCount:  r.GameCount,
		}
	}

	// Console breakdown
	type consoleRow struct {
		Name         string
		Abbreviation string
		PlayTime     int64
		GameCount    int
	}
	var consoleRows []consoleRow
	if err := h.DB.
		Table("play_histories").
		Select("consoles.name, consoles.abbreviation, SUM(play_histories.play_time) as play_time, COUNT(DISTINCT play_histories.game_id) as game_count").
		Joins("JOIN games ON games.id = play_histories.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id").
		Where("play_histories.user_id = ? AND play_histories.play_time > 0 AND play_histories.deleted_at IS NULL", userID).
		Group("consoles.id").
		Order("play_time DESC").
		Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to get console breakdown", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to build taste profile"})
		return
	}

	topConsoles := make([]TasteProfileConsole, len(consoleRows))
	for i, r := range consoleRows {
		topConsoles[i] = TasteProfileConsole{
			Name:         r.Name,
			Abbreviation: strings.ToLower(r.Abbreviation),
			PlayTime:     r.PlayTime,
			GameCount:    r.GameCount,
		}
	}

	c.JSON(http.StatusOK, TasteProfileResponse{
		TotalPlayTime: totalPlayTime,
		Genres:        genres,
		Themes:        themes,
		TopConsoles:   topConsoles,
	})
}

// GetPlayersLikeYou returns collaborative filtering recommendations based on favorite overlap.
// GET /api/explore/players-like-you
func (h *ExploreHandler) GetPlayersLikeYou(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Get the current user's favorites
	var myFavIDs []uint
	if err := h.DB.Model(&db.Favorite{}).
		Select("game_id").
		Where("user_id = ?", userID).
		Pluck("game_id", &myFavIDs).Error; err != nil {
		slog.Error("failed to get user favorites", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to get recommendations"})
		return
	}

	if len(myFavIDs) == 0 {
		c.JSON(http.StatusOK, PlayersLikeYouResponse{
			Games:             []GameResponse{},
			SimilarUsersCount: 0,
		})
		return
	}

	myFavSet := make(map[uint]bool, len(myFavIDs))
	for _, id := range myFavIDs {
		myFavSet[id] = true
	}

	// Find all other users who have at least one overlapping favorite
	type userOverlap struct {
		UserID       uint
		OverlapCount int
	}
	var overlaps []userOverlap
	if err := h.DB.Model(&db.Favorite{}).
		Select("user_id, COUNT(*) as overlap_count").
		Where("user_id != ? AND game_id IN ?", userID, myFavIDs).
		Group("user_id").
		Order("overlap_count DESC").
		Scan(&overlaps).Error; err != nil {
		slog.Error("failed to find similar users", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to get recommendations"})
		return
	}

	if len(overlaps) == 0 {
		c.JSON(http.StatusOK, PlayersLikeYouResponse{
			Games:             []GameResponse{},
			SimilarUsersCount: 0,
		})
		return
	}

	// Compute Jaccard similarity for each candidate user
	// Jaccard = |intersection| / |union|
	// We need the total favorite count for each candidate
	type similarUser struct {
		userID     uint
		similarity float64
	}

	candidateUserIDs := make([]uint, len(overlaps))
	overlapMap := make(map[uint]int, len(overlaps))
	for i, o := range overlaps {
		candidateUserIDs[i] = o.UserID
		overlapMap[o.UserID] = o.OverlapCount
	}

	// Get total favorite count for each candidate
	type favCountRow struct {
		UserID   uint
		FavCount int
	}
	var favCounts []favCountRow
	if err := h.DB.Model(&db.Favorite{}).
		Select("user_id, COUNT(*) as fav_count").
		Where("user_id IN ?", candidateUserIDs).
		Group("user_id").
		Scan(&favCounts).Error; err != nil {
		slog.Error("failed to get candidate favorite counts", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to get recommendations"})
		return
	}

	var similarUsers []similarUser
	myFavCount := len(myFavIDs)
	for _, fc := range favCounts {
		intersection := overlapMap[fc.UserID]
		union := myFavCount + fc.FavCount - intersection
		if union == 0 {
			continue
		}
		similarity := float64(intersection) / float64(union)
		similarUsers = append(similarUsers, similarUser{
			userID:     fc.UserID,
			similarity: similarity,
		})
	}

	// Sort by similarity DESC (already mostly sorted by overlap, but Jaccard may reorder)
	for i := 0; i < len(similarUsers); i++ {
		for j := i + 1; j < len(similarUsers); j++ {
			if similarUsers[j].similarity > similarUsers[i].similarity {
				similarUsers[i], similarUsers[j] = similarUsers[j], similarUsers[i]
			}
		}
	}

	// Take top 5
	if len(similarUsers) > 5 {
		similarUsers = similarUsers[:5]
	}

	// Get favorites from similar users that the current user hasn't favorited
	topUserIDs := make([]uint, len(similarUsers))
	for i, su := range similarUsers {
		topUserIDs[i] = su.userID
	}

	type recGameRow struct {
		GameID    uint
		UserCount int
	}
	var recGameRows []recGameRow
	if err := h.DB.Model(&db.Favorite{}).
		Select("game_id, COUNT(DISTINCT user_id) as user_count").
		Where("user_id IN ? AND game_id NOT IN ?", topUserIDs, myFavIDs).
		Group("game_id").
		Order("user_count DESC").
		Limit(20).
		Scan(&recGameRows).Error; err != nil {
		slog.Error("failed to get recommended games from similar users", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to get recommendations"})
		return
	}

	if len(recGameRows) == 0 {
		c.JSON(http.StatusOK, PlayersLikeYouResponse{
			Games:             []GameResponse{},
			SimilarUsersCount: len(similarUsers),
		})
		return
	}

	// Load and sort games
	gameIDs := make([]uint, len(recGameRows))
	for i, r := range recGameRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load recommended games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to get recommendations"})
		return
	}

	// Re-sort by user_count (preserve the order from the recommendation query)
	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}
	sorted := make([]db.Game, 0, len(recGameRows))
	for _, r := range recGameRows {
		if g, ok := gameMap[r.GameID]; ok {
			sorted = append(sorted, g)
		}
	}

	c.JSON(http.StatusOK, PlayersLikeYouResponse{
		Games:             ToGameResponses(sorted, h.DB, userID),
		SimilarUsersCount: len(similarUsers),
	})
}

