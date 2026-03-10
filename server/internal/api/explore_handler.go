package api

import (
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"gorm.io/gorm"
)


// --- Phase 7: Developer & Publisher Spotlight ---

// DeveloperSummary is the API response for a single developer in the developer list.
type DeveloperSummary struct {
	Name      string   `json:"name"`
	GameCount int      `json:"gameCount"`
	AvgRating float64  `json:"avgRating"`
	Consoles  []string `json:"consoles"`
}

// DeveloperListResponse is the API response for the developers list endpoint.
type DeveloperListResponse struct {
	Developers []DeveloperSummary `json:"developers"`
}

// DeveloperDetailResponse is the API response for a developer detail page.
type DeveloperDetailResponse struct {
	Name      string         `json:"name"`
	GameCount int            `json:"gameCount"`
	AvgRating float64        `json:"avgRating"`
	Consoles  []string       `json:"consoles"`
	Games     []GameResponse `json:"games"`
}

// PublisherDetailResponse is the API response for a publisher detail page.
type PublisherDetailResponse struct {
	Name      string         `json:"name"`
	GameCount int            `json:"gameCount"`
	AvgRating float64        `json:"avgRating"`
	Consoles  []string       `json:"consoles"`
	Games     []GameResponse `json:"games"`
}

// DeveloperSpotlightResponse is the API response for the featured developer spotlight.
type DeveloperSpotlightResponse struct {
	Name      string         `json:"name"`
	GameCount int            `json:"gameCount"`
	AvgRating float64        `json:"avgRating"`
	Consoles  []string       `json:"consoles"`
	TopGames  []GameResponse `json:"topGames"`
	HeroURL   string         `json:"heroUrl"`
}
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
		c.Header("Cache-Control", "private, max-age=300")
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

	c.Header("Cache-Control", "private, max-age=300")
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

	c.Header("Cache-Control", "private, max-age=300")
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
	c.Header("Cache-Control", "private, max-age=300")
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

	c.Header("Cache-Control", "private, max-age=120")
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

// GetSurpriseGame returns a single random game with optional filters.
// GET /api/explore/surprise?console=&genre=&minRating=
func (h *ExploreHandler) GetSurpriseGame(c *gin.Context) {
	userID := getUserID(c)

	query := h.DB.Preload("Console").Where("cover_url != ''")

	// Optional console filter
	if consoleAbbr := c.Query("console"); consoleAbbr != "" {
		var console db.Console
		if err := h.DB.Where("LOWER(abbreviation) = LOWER(?)", consoleAbbr).First(&console).Error; err == nil {
			query = query.Where("console_id = ?", console.ID)
		}
	}

	// Optional genre filter
	if genre := c.Query("genre"); genre != "" {
		query = query.Where("genre = ?", genre)
	}

	// Optional minimum rating (default 70 for quality threshold)
	minRating := 70.0
	if mr := c.Query("minRating"); mr != "" {
		if r, err := strconv.ParseFloat(mr, 64); err == nil && r >= 0 && r <= 100 {
			minRating = r
		}
	}
	if minRating > 0 {
		query = query.Where("rating >= ?", minRating)
	}

	var game db.Game
	if err := query.Order("RANDOM()").Limit(1).First(&game).Error; err != nil {
		if err.Error() == "record not found" {
			c.JSON(http.StatusNotFound, gin.H{"error": "no eligible games found"})
			return
		}
		slog.Error("failed to fetch surprise game", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch surprise game"})
		return
	}

	c.Header("Cache-Control", "no-store")
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

	c.Header("Cache-Control", "private, max-age=120")
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

	c.Header("Cache-Control", "private, max-age=120")
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
		c.Header("Cache-Control", "private, max-age=120")
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
		c.Header("Cache-Control", "private, max-age=120")
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
		c.Header("Cache-Control", "private, max-age=120")
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

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, PlayersLikeYouResponse{
		Games:             ToGameResponses(sorted, h.DB, userID),
		SimilarUsersCount: len(similarUsers),
	})
}

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

// --- Phase 9: Visual Browsing — Gallery & Art Modes ---

// ScreenshotItem represents a single screenshot in the gallery response.
type ScreenshotItem struct {
	URL              string `json:"url"`
	GameID           string `json:"gameId"`
	GameTitle        string `json:"gameTitle"`
	ConsoleName      string `json:"consoleName"`
	ConsoleAbbr      string `json:"consoleAbbreviation"`
	ConsoleColor     string `json:"consoleColor"`
}

// ScreenshotGalleryResponse is the paginated response for the screenshot gallery.
type ScreenshotGalleryResponse struct {
	Screenshots []ScreenshotItem `json:"screenshots"`
	Page        int              `json:"page"`
	TotalPages  int              `json:"totalPages"`
	TotalCount  int              `json:"totalCount"`
}

// ArtworkItem represents a single IGDB artwork image in the gallery response.
type ArtworkItem struct {
	URL          string `json:"url"`
	Width        int    `json:"width"`
	Height       int    `json:"height"`
	GameID       string `json:"gameId"`
	GameTitle    string `json:"gameTitle"`
	ConsoleName  string `json:"consoleName"`
	ConsoleAbbr  string `json:"consoleAbbreviation"`
	ConsoleColor string `json:"consoleColor"`
}

// ArtworkGalleryResponse is the paginated response for the IGDB artwork gallery.
type ArtworkGalleryResponse struct {
	Artworks   []ArtworkItem `json:"artworks"`
	Page       int           `json:"page"`
	TotalPages int           `json:"totalPages"`
	TotalCount int           `json:"totalCount"`
}

// CoverItem represents a single cover in the cover gallery response.
type CoverItem struct {
	CoverURL         string  `json:"coverUrl"`
	GameID           string  `json:"gameId"`
	GameTitle        string  `json:"gameTitle"`
	ConsoleName      string  `json:"consoleName"`
	ConsoleAbbr      string  `json:"consoleAbbreviation"`
	ConsoleColor     string  `json:"consoleColor"`
	Rating           float64 `json:"rating"`
	CoverAspectRatio float64 `json:"coverAspectRatio"`
}

// CoverGalleryResponse is the paginated response for the cover gallery.
type CoverGalleryResponse struct {
	Covers     []CoverItem `json:"covers"`
	Page       int         `json:"page"`
	TotalPages int         `json:"totalPages"`
	TotalCount int         `json:"totalCount"`
}

// parsePagination parses page/limit query params with defaults and max bounds.
// Returns page (1-based), limit, and offset for SQL queries.
func parsePagination(c *gin.Context, defaultLimit, maxLimit int) (page, limit, offset int) {
	page = 1
	if p, err := strconv.Atoi(c.Query("page")); err == nil && p > 0 {
		page = p
	}
	limit = defaultLimit
	if l, err := strconv.Atoi(c.Query("limit")); err == nil && l > 0 {
		limit = l
	}
	if limit > maxLimit {
		limit = maxLimit
	}
	offset = (page - 1) * limit
	return
}

// totalPages computes the number of pages given a total count and page size.
func totalPages(totalCount, limit int) int {
	if limit <= 0 {
		return 0
	}
	return int(math.Ceil(float64(totalCount) / float64(limit)))
}

// GetScreenshotGallery returns a paginated stream of screenshots with minimal game metadata.
func (h *ExploreHandler) GetScreenshotGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 40, 100)

	consoleFilter := strings.TrimSpace(c.Query("console"))
	genreFilter := strings.TrimSpace(c.Query("genre"))

	// Build the base query joining screenshots with games and consoles.
	baseQuery := h.DB.Table("game_screenshots").
		Joins("JOIN games ON games.id = game_screenshots.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("game_screenshots.deleted_at IS NULL")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}
	if genreFilter != "" {
		baseQuery = baseQuery.Where("LOWER(games.genre) = LOWER(?)", genreFilter)
	}

	// Count total matching screenshots.
	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count screenshots for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load screenshot gallery"})
		return
	}

	type screenshotRow struct {
		URL          string
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
	}

	var rows []screenshotRow
	// Reuse baseQuery with select/order/pagination for the data fetch.
	if err := baseQuery.
		Select("game_screenshots.url, games.id as game_id, games.title as game_title, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Order("(game_screenshots.id * 2654435761) % 2147483647").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load screenshot gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load screenshot gallery"})
		return
	}

	items := make([]ScreenshotItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, ScreenshotItem{
			URL:          resolveImageURL(r.URL),
			GameID:       strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:    r.GameTitle,
			ConsoleName:  r.ConsoleName,
			ConsoleAbbr:  r.ConsoleAbbr,
			ConsoleColor: r.ConsoleColor,
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ScreenshotGalleryResponse{
		Screenshots: items,
		Page:        page,
		TotalPages:  totalPages(int(count), limit),
		TotalCount:  int(count),
	})
}

// GetArtworkGallery returns a paginated stream of IGDB promotional artwork.
func (h *ExploreHandler) GetArtworkGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 40, 100)

	// Count total artwork images.
	var count int64
	if err := h.DB.Table("game_artwork_images").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Count(&count).Error; err != nil {
		slog.Error("failed to count artwork for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load artwork gallery"})
		return
	}

	type artworkRow struct {
		IGDBImageID  string
		Width        int
		Height       int
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
		Rating       float64
	}

	var rows []artworkRow
	if err := h.DB.Table("game_artwork_images").
		Select("game_artwork_images.igdb_image_id, game_artwork_images.width, game_artwork_images.height, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color").
		Joins("JOIN games ON games.id = game_artwork_images.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load artwork gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load artwork gallery"})
		return
	}

	items := make([]ArtworkItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, ArtworkItem{
			URL:          igdb.ImageURL(r.IGDBImageID, "screenshot_big"),
			Width:        r.Width,
			Height:       r.Height,
			GameID:       strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:    r.GameTitle,
			ConsoleName:  r.ConsoleName,
			ConsoleAbbr:  r.ConsoleAbbr,
			ConsoleColor: r.ConsoleColor,
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, ArtworkGalleryResponse{
		Artworks:   items,
		Page:       page,
		TotalPages: totalPages(int(count), limit),
		TotalCount: int(count),
	})
}

// GetCoverGallery returns a paginated dense cover art feed with minimal metadata.
func (h *ExploreHandler) GetCoverGallery(c *gin.Context) {
	page, limit, offset := parsePagination(c, 60, 200)

	consoleFilter := strings.TrimSpace(c.Query("console"))

	baseQuery := h.DB.Table("games").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("games.deleted_at IS NULL").
		Where("games.cover_url != ''")

	if consoleFilter != "" {
		baseQuery = baseQuery.Where("LOWER(consoles.abbreviation) = LOWER(?)", consoleFilter)
	}

	// Count total covers.
	var count int64
	if err := baseQuery.Count(&count).Error; err != nil {
		slog.Error("failed to count covers for gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load cover gallery"})
		return
	}

	type coverRow struct {
		CoverURL     string
		GameID       uint
		GameTitle    string
		ConsoleName  string
		ConsoleAbbr  string
		ConsoleColor string
		Rating       float64
		CoverAspect  string
	}

	var rows []coverRow
	if err := baseQuery.
		Select("games.cover_url, games.id as game_id, games.title as game_title, games.rating, consoles.name as console_name, LOWER(consoles.abbreviation) as console_abbr, consoles.color_theme as console_color, consoles.cover_aspect").
		Order("games.rating DESC").
		Offset(offset).
		Limit(limit).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to load cover gallery", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load cover gallery"})
		return
	}

	items := make([]CoverItem, 0, len(rows))
	for _, r := range rows {
		items = append(items, CoverItem{
			CoverURL:         resolveImageURL(r.CoverURL),
			GameID:           strconv.FormatUint(uint64(r.GameID), 10),
			GameTitle:        r.GameTitle,
			ConsoleName:      r.ConsoleName,
			ConsoleAbbr:      r.ConsoleAbbr,
			ConsoleColor:     r.ConsoleColor,
			Rating:           r.Rating,
			CoverAspectRatio: parseAspectRatio(r.CoverAspect),
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, CoverGalleryResponse{
		Covers:     items,
		Page:       page,
		TotalPages: totalPages(int(count), limit),
		TotalCount: int(count),
	})
}

// --- Phase 12: Achievement & Challenge-Driven Discovery ---

// AchievementGameResponse is one game with aggregated achievement stats across all players.
type AchievementGameResponse struct {
	Game              GameResponse `json:"game"`
	TotalAchievements int          `json:"totalAchievements"`
	AvgCompletion     float64      `json:"avgCompletion"`
	PlayersAttempted  int          `json:"playersAttempted"`
	PlayersCompleted  int          `json:"playersCompleted"`
}

// EasyToCompleteResponse is the API response for the easy-to-complete endpoint.
type EasyToCompleteResponse struct {
	Games []AchievementGameResponse `json:"games"`
}

// HardestGamesResponse is the API response for the hardest-games endpoint.
type HardestGamesResponse struct {
	Games []AchievementGameResponse `json:"games"`
}

// AlmostDoneGame is one game the current user has nearly completed (80-99%).
type AlmostDoneGame struct {
	Game              GameResponse `json:"game"`
	UnlockedCount     int          `json:"unlockedCount"`
	TotalCount        int          `json:"totalCount"`
	CompletionPercent float64      `json:"completionPercent"`
}

// AlmostDoneResponse is the API response for the almost-done endpoint.
type AlmostDoneResponse struct {
	Games []AlmostDoneGame `json:"games"`
}

// FreshChallengeGame is a game with cached achievements that the user hasn't started.
type FreshChallengeGame struct {
	Game              GameResponse `json:"game"`
	TotalAchievements int          `json:"totalAchievements"`
	TotalPoints       int          `json:"totalPoints"`
}

// FreshChallengesResponse is the API response for the fresh-challenges endpoint.
type FreshChallengesResponse struct {
	Games []FreshChallengeGame `json:"games"`
}

// ExploreChallengeResponse is a lightweight challenge representation for the explore page.
type ExploreChallengeResponse struct {
	ID              string     `json:"id"`
	CreatorUsername  string     `json:"creatorUsername"`
	GameID          string     `json:"gameId"`
	GameTitle       string     `json:"gameTitle"`
	GameCoverURL    string     `json:"gameCoverUrl,omitempty"`
	ConsoleName     string     `json:"consoleName,omitempty"`
	Name            string     `json:"name"`
	Description     string     `json:"description,omitempty"`
	Type            string     `json:"type"`
	Difficulty      string     `json:"difficulty"`
	AttemptCount    int        `json:"attemptCount"`
	CompletionCount int        `json:"completionCount"`
	ExpiresAt       *time.Time `json:"expiresAt,omitempty"`
	CreatedAt       time.Time  `json:"createdAt"`
}

// ActiveChallengesResponse is the API response for the active-challenges endpoint.
type ActiveChallengesResponse struct {
	Challenges []ExploreChallengeResponse `json:"challenges"`
}

// --- Phase 10: Social & Community Discovery ---

// TrendingGameResponse is one game in the trending shelf, with player count this week.
type TrendingGameResponse struct {
	Game         GameResponse `json:"game"`
	PlayersThisWeek int      `json:"playersThisWeek"`
}

// TrendingResponse is the API response for the trending endpoint.
type TrendingResponse struct {
	Games []TrendingGameResponse `json:"games"`
}

// CommunityTopGame is one game in the community-top shelf.
type CommunityTopGame struct {
	Game       GameResponse `json:"game"`
	AvgRating  float64      `json:"avgRating"`
	RatingCount int         `json:"ratingCount"`
}

// CommunityTopResponse is the API response for the community-top endpoint.
type CommunityTopResponse struct {
	Games []CommunityTopGame `json:"games"`
}

// CultClassicGame is one game in the cult classics shelf.
type CultClassicGame struct {
	Game            GameResponse `json:"game"`
	CommunityRating float64     `json:"communityRating"`
	IgdbRating      float64     `json:"igdbRating"`
	RatingCount     int         `json:"ratingCount"`
}

// CultClassicsResponse is the API response for the cult-classics endpoint.
type CultClassicsResponse struct {
	Games []CultClassicGame `json:"games"`
}

// RecentReviewItem is one review in the recently-reviewed shelf.
type RecentReviewItem struct {
	Game       GameResponse `json:"game"`
	Rating     int          `json:"rating"`
	Review     string       `json:"review"`
	ReviewerName string    `json:"reviewerName"`
	ReviewedAt time.Time    `json:"reviewedAt"`
}

// RecentlyReviewedResponse is the API response for the recently-reviewed endpoint.
type RecentlyReviewedResponse struct {
	Reviews []RecentReviewItem `json:"reviews"`
}

// ActiveNowItem is one active game in the active-now shelf.
type ActiveNowItem struct {
	Game             GameResponse `json:"game"`
	ActiveSessions   int          `json:"activeSessions"`
	ActiveChallenges int          `json:"activeChallenges"`
}

// ActiveNowResponse is the API response for the active-now endpoint.
type ActiveNowResponse struct {
	Games []ActiveNowItem `json:"games"`
}

// GetTrending returns games with the most distinct players in the last 7 days.
// GET /api/explore/trending
func (h *ExploreHandler) GetTrending(c *gin.Context) {
	userID := getUserID(c)
	cutoff := time.Now().AddDate(0, 0, -7)

	type trendingRow struct {
		GameID          uint
		PlayersThisWeek int
	}
	var rows []trendingRow
	if err := h.DB.
		Table("play_histories").
		Select("game_id, COUNT(DISTINCT user_id) as players_this_week").
		Where("last_played >= ? AND play_time > 0 AND deleted_at IS NULL", cutoff).
		Group("game_id").
		Having("players_this_week >= 1").
		Order("players_this_week DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch trending games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch trending games"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=30")
		c.JSON(http.StatusOK, TrendingResponse{Games: []TrendingGameResponse{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load trending games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load trending games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	// Build player-count map for quick lookup
	playerCountMap := make(map[uint]int, len(rows))
	for _, r := range rows {
		playerCountMap[r.GameID] = r.PlayersThisWeek
	}

	// Batch load user game data
	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]TrendingGameResponse, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		result = append(result, TrendingGameResponse{
			Game:            toGameResponseWithData(g, &userData),
			PlayersThisWeek: r.PlayersThisWeek,
		})
	}

	c.Header("Cache-Control", "private, max-age=30")
	c.JSON(http.StatusOK, TrendingResponse{Games: result})
}

// GetCommunityTop returns games with the highest average user ratings on this server.
// Requires at least 2 ratings per game. Uses community ratings, not IGDB ratings.
// GET /api/explore/community-top
func (h *ExploreHandler) GetCommunityTop(c *gin.Context) {
	userID := getUserID(c)

	type communityRow struct {
		GameID      uint
		AvgRating   float64
		RatingCount int
	}
	var rows []communityRow
	if err := h.DB.
		Table("game_ratings").
		Select("game_id, AVG(rating) as avg_rating, COUNT(*) as rating_count").
		Where("deleted_at IS NULL").
		Group("game_id").
		Having("rating_count >= 2").
		Order("avg_rating DESC, rating_count DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch community top", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch community top games"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, CommunityTopResponse{Games: []CommunityTopGame{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load community top games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load community top games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	// Build lookup maps
	ratingMap := make(map[uint]communityRow, len(rows))
	for _, r := range rows {
		ratingMap[r.GameID] = r
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]CommunityTopGame, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		result = append(result, CommunityTopGame{
			Game:        toGameResponseWithData(g, &userData),
			AvgRating:   math.Round(r.AvgRating*100) / 100,
			RatingCount: r.RatingCount,
		})
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, CommunityTopResponse{Games: result})
}

// GetCultClassics returns games with high community ratings but moderate IGDB ratings.
// "Your community rates these higher than the critics."
// Criteria: avg community rating >= 4.0 (out of 5), IGDB rating < 75, at least 2 ratings.
// GET /api/explore/cult-classics
func (h *ExploreHandler) GetCultClassics(c *gin.Context) {
	userID := getUserID(c)

	type cultRow struct {
		GameID          uint
		CommunityRating float64
		RatingCount     int
	}
	var rows []cultRow
	if err := h.DB.
		Table("game_ratings").
		Select("game_ratings.game_id, AVG(game_ratings.rating) as community_rating, COUNT(*) as rating_count").
		Joins("JOIN games ON games.id = game_ratings.game_id AND games.deleted_at IS NULL").
		Where("game_ratings.deleted_at IS NULL AND games.rating < 75").
		Group("game_ratings.game_id").
		Having("rating_count >= 2 AND community_rating >= 4.0").
		Order("community_rating DESC, rating_count DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch cult classics", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch cult classics"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, CultClassicsResponse{Games: []CultClassicGame{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load cult classic games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load cult classic games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]CultClassicGame, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		result = append(result, CultClassicGame{
			Game:            toGameResponseWithData(g, &userData),
			CommunityRating: math.Round(r.CommunityRating*100) / 100,
			IgdbRating:      g.Rating,
			RatingCount:     r.RatingCount,
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, CultClassicsResponse{Games: result})
}

// GetRecentlyReviewed returns games with recent user reviews (non-empty review text).
// GET /api/explore/recently-reviewed
func (h *ExploreHandler) GetRecentlyReviewed(c *gin.Context) {
	userID := getUserID(c)

	type reviewRow struct {
		GameID       uint
		Rating       int
		Review       string
		ReviewerName string
		CreatedAt    time.Time
	}
	var rows []reviewRow
	if err := h.DB.
		Table("game_ratings").
		Select("game_ratings.game_id, game_ratings.rating, game_ratings.review, users.username as reviewer_name, game_ratings.created_at").
		Joins("JOIN users ON users.id = game_ratings.user_id AND users.deleted_at IS NULL").
		Where("game_ratings.deleted_at IS NULL AND game_ratings.review != ''").
		Order("game_ratings.created_at DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch recently reviewed", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch recently reviewed"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=30")
		c.JSON(http.StatusOK, RecentlyReviewedResponse{Reviews: []RecentReviewItem{}})
		return
	}

	// Collect unique game IDs
	gameIDSet := make(map[uint]bool, len(rows))
	for _, r := range rows {
		gameIDSet[r.GameID] = true
	}
	gameIDs := make([]uint, 0, len(gameIDSet))
	for id := range gameIDSet {
		gameIDs = append(gameIDs, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load reviewed games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load reviewed games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]RecentReviewItem, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		result = append(result, RecentReviewItem{
			Game:         toGameResponseWithData(g, &userData),
			Rating:       r.Rating,
			Review:       r.Review,
			ReviewerName: r.ReviewerName,
			ReviewedAt:   r.CreatedAt,
		})
	}

	c.Header("Cache-Control", "private, max-age=30")
	c.JSON(http.StatusOK, RecentlyReviewedResponse{Reviews: result})
}

// GetActiveNow returns games with currently active shared sessions or challenges.
// GET /api/explore/active-now
func (h *ExploreHandler) GetActiveNow(c *gin.Context) {
	userID := getUserID(c)

	// Count active shared sessions per game
	type sessionRow struct {
		GameID       uint
		SessionCount int
	}
	var sessionRows []sessionRow
	if err := h.DB.
		Table("shared_sessions").
		Select("game_id, COUNT(*) as session_count").
		Where("status = 'active' AND deleted_at IS NULL").
		Group("game_id").
		Scan(&sessionRows).Error; err != nil {
		slog.Error("failed to fetch active sessions", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch active games"})
		return
	}

	// Count active challenges per game
	type challengeRow struct {
		GameID         uint
		ChallengeCount int
	}
	var challengeRows []challengeRow
	if err := h.DB.
		Table("challenges").
		Select("game_id, COUNT(*) as challenge_count").
		Where("status = 'active' AND deleted_at IS NULL").
		Group("game_id").
		Scan(&challengeRows).Error; err != nil {
		slog.Error("failed to fetch active challenges", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch active games"})
		return
	}

	// Merge into a combined map
	type activeInfo struct {
		sessions   int
		challenges int
	}
	activeMap := make(map[uint]*activeInfo)
	for _, r := range sessionRows {
		activeMap[r.GameID] = &activeInfo{sessions: r.SessionCount}
	}
	for _, r := range challengeRows {
		if info, ok := activeMap[r.GameID]; ok {
			info.challenges = r.ChallengeCount
		} else {
			activeMap[r.GameID] = &activeInfo{challenges: r.ChallengeCount}
		}
	}

	if len(activeMap) == 0 {
		c.Header("Cache-Control", "private, max-age=30")
		c.JSON(http.StatusOK, ActiveNowResponse{Games: []ActiveNowItem{}})
		return
	}

	gameIDs := make([]uint, 0, len(activeMap))
	for id := range activeMap {
		gameIDs = append(gameIDs, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load active games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load active games"})
		return
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	// Sort by total activity descending
	type sortItem struct {
		game  db.Game
		info  *activeInfo
		total int
	}
	sorted := make([]sortItem, 0, len(games))
	for _, g := range games {
		info := activeMap[g.ID]
		sorted = append(sorted, sortItem{
			game:  g,
			info:  info,
			total: info.sessions + info.challenges,
		})
	}
	sort.Slice(sorted, func(i, j int) bool {
		return sorted[i].total > sorted[j].total
	})

	// Limit to 20
	if len(sorted) > 20 {
		sorted = sorted[:20]
	}

	result := make([]ActiveNowItem, 0, len(sorted))
	for _, s := range sorted {
		result = append(result, ActiveNowItem{
			Game:             toGameResponseWithData(s.game, &userData),
			ActiveSessions:   s.info.sessions,
			ActiveChallenges: s.info.challenges,
		})
	}

	c.Header("Cache-Control", "private, max-age=30")
	c.JSON(http.StatusOK, ActiveNowResponse{Games: result})
}

// --- Phase 11: Temporal Discovery ---

// OnThisDayResponse is the API response for the on-this-day endpoint.
type OnThisDayResponse struct {
	Date  string         `json:"date"`
	Games []GameResponse `json:"games"`
}

// BestOfYearResponse is the API response for the best-of-year endpoint.
type BestOfYearResponse struct {
	Year  int            `json:"year"`
	Games []GameResponse `json:"games"`
}

// AnniversaryItem represents a game the user played roughly N years ago.
type AnniversaryItem struct {
	Game     GameResponse `json:"game"`
	YearsAgo int         `json:"yearsAgo"`
	PlayedAt time.Time    `json:"playedAt"`
}

// AnniversariesResponse is the API response for the your-anniversaries endpoint.
type AnniversariesResponse struct {
	Anniversaries []AnniversaryItem `json:"anniversaries"`
}

// DecadesResponse is the API response for the decades endpoint.
type DecadesResponse struct {
	Decade string         `json:"decade"`
	Label  string         `json:"label"`
	Games  []GameResponse `json:"games"`
}

// parseReleaseDateMonthDay attempts to extract month and day from a release_date string.
// Supports formats like "2000-03-10", "March 10, 2000", "Mar 10, 2000".
// Returns (month, day, true) on success, or (0, 0, false) if unparseable or year-only.
func parseReleaseDateMonthDay(releaseDate string) (time.Month, int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, 0, false
	}

	// Try ISO format: "2000-03-10" or "2000-3-10"
	if len(releaseDate) >= 10 && releaseDate[4] == '-' {
		t, err := time.Parse("2006-01-02", releaseDate[:10])
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	// Try "January 2, 2006" / "Jan 2, 2006" formats
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
		"2 January 2006",
		"02 January 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Month(), t.Day(), true
		}
	}

	return 0, 0, false
}

// parseReleaseDateYear attempts to extract the year from a release_date string.
// Supports "2000-03-10", "March 10, 2000", "1996", etc.
func parseReleaseDateYear(releaseDate string) (int, bool) {
	releaseDate = strings.TrimSpace(releaseDate)
	if releaseDate == "" {
		return 0, false
	}

	// Try ISO format first
	if len(releaseDate) >= 4 {
		year, err := strconv.Atoi(releaseDate[:4])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	// Try text formats like "March 10, 2000"
	layouts := []string{
		"January 2, 2006",
		"Jan 2, 2006",
		"January 02, 2006",
		"Jan 02, 2006",
	}
	for _, layout := range layouts {
		t, err := time.Parse(layout, releaseDate)
		if err == nil {
			return t.Year(), true
		}
	}

	// Try plain 4-digit year at end (e.g. "Q4 1996" — just grab trailing year)
	parts := strings.Fields(releaseDate)
	for i := len(parts) - 1; i >= 0; i-- {
		year, err := strconv.Atoi(parts[i])
		if err == nil && year >= 1970 && year <= 2100 {
			return year, true
		}
	}

	return 0, false
}

// GetOnThisDay returns games released on today's month/day across all years.
// GET /api/explore/on-this-day
func (h *ExploreHandler) GetOnThisDay(c *gin.Context) {
	userID := getUserID(c)
	now := time.Now()
	targetMonth := now.Month()
	targetDay := now.Day()

	dateLabel := now.Format("January 2")

	// Load all games that have a release_date set
	var allGames []db.Game
	if err := h.DB.Preload("Console").
		Where("release_date != '' AND release_date IS NOT NULL AND deleted_at IS NULL").
		Find(&allGames).Error; err != nil {
		slog.Error("failed to fetch games for on-this-day", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	// Filter to games matching today's month+day
	type matchedGame struct {
		game db.Game
		year int
	}
	var matched []matchedGame
	for _, g := range allGames {
		month, day, ok := parseReleaseDateMonthDay(g.ReleaseDate)
		if !ok {
			continue
		}
		if month == targetMonth && day == targetDay {
			year, _ := parseReleaseDateYear(g.ReleaseDate)
			matched = append(matched, matchedGame{game: g, year: year})
		}
	}

	// Sort by year ascending (oldest first)
	sort.Slice(matched, func(i, j int) bool {
		return matched[i].year < matched[j].year
	})

	// Limit to 20
	if len(matched) > 20 {
		matched = matched[:20]
	}

	if len(matched) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, OnThisDayResponse{Date: dateLabel, Games: []GameResponse{}})
		return
	}

	games := make([]db.Game, len(matched))
	gameIDs := make([]uint, len(matched))
	for i, m := range matched {
		games[i] = m.game
		gameIDs[i] = m.game.ID
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)
	result := make([]GameResponse, len(games))
	for i, g := range games {
		result[i] = toGameResponseWithData(g, &userData)
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, OnThisDayResponse{Date: dateLabel, Games: result})
}

// GetBestOfYear returns the top-rated games from a specific year.
// GET /api/explore/best-of-year/:year
func (h *ExploreHandler) GetBestOfYear(c *gin.Context) {
	userID := getUserID(c)

	yearStr := c.Param("year")
	year, err := strconv.Atoi(yearStr)
	if err != nil || year < 1970 || year > 2100 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid year"})
		return
	}

	yearPrefix := fmt.Sprintf("%d", year)

	// Find games whose release_date starts with the year
	var games []db.Game
	if err := h.DB.Preload("Console").
		Where("release_date LIKE ? AND deleted_at IS NULL", yearPrefix+"%").
		Where("rating > 0").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch best-of-year games", "error", err, "year", year)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	if len(games) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, BestOfYearResponse{Year: year, Games: []GameResponse{}})
		return
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, BestOfYearResponse{
		Year:  year,
		Games: ToGameResponses(games, h.DB, userID),
	})
}

// GetYourAnniversaries returns personal milestones — games the user played
// roughly 1, 2, 3... years ago (within a 3-day window around today's date).
// GET /api/explore/your-anniversaries
func (h *ExploreHandler) GetYourAnniversaries(c *gin.Context) {
	userID := getUserID(c)

	now := time.Now()

	// Look back up to 10 years
	var allAnniversaries []AnniversaryItem
	for yearsAgo := 1; yearsAgo <= 10; yearsAgo++ {
		anniversary := now.AddDate(-yearsAgo, 0, 0)
		windowStart := anniversary.AddDate(0, 0, -3)
		windowEnd := anniversary.AddDate(0, 0, 3)

		var histories []db.PlayHistory
		if err := h.DB.
			Where("user_id = ? AND last_played BETWEEN ? AND ? AND deleted_at IS NULL",
				userID, windowStart, windowEnd).
			Find(&histories).Error; err != nil {
			slog.Error("failed to fetch anniversary play histories", "error", err, "yearsAgo", yearsAgo)
			continue
		}

		// Deduplicate by game ID (keep the one closest to the anniversary date)
		gameMap := make(map[uint]db.PlayHistory)
		for _, ph := range histories {
			existing, exists := gameMap[ph.GameID]
			if !exists {
				gameMap[ph.GameID] = ph
			} else {
				// Keep the one closest to the exact anniversary date
				existingDiff := existing.LastPlayed.Sub(anniversary)
				if existingDiff < 0 {
					existingDiff = -existingDiff
				}
				newDiff := ph.LastPlayed.Sub(anniversary)
				if newDiff < 0 {
					newDiff = -newDiff
				}
				if newDiff < existingDiff {
					gameMap[ph.GameID] = ph
				}
			}
		}

		for _, ph := range gameMap {
			allAnniversaries = append(allAnniversaries, AnniversaryItem{
				YearsAgo: yearsAgo,
				PlayedAt: ph.LastPlayed,
				Game:     GameResponse{ID: strconv.FormatUint(uint64(ph.GameID), 10)}, // placeholder, will be filled
			})
		}
	}

	if len(allAnniversaries) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, AnniversariesResponse{Anniversaries: []AnniversaryItem{}})
		return
	}

	// Collect unique game IDs
	gameIDSet := make(map[uint]bool)
	for _, a := range allAnniversaries {
		gid, _ := strconv.ParseUint(a.Game.ID, 10, 64)
		gameIDSet[uint(gid)] = true
	}
	gameIDs := make([]uint, 0, len(gameIDSet))
	for id := range gameIDSet {
		gameIDs = append(gameIDs, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load anniversary games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]AnniversaryItem, 0, len(allAnniversaries))
	for _, a := range allAnniversaries {
		gid, _ := strconv.ParseUint(a.Game.ID, 10, 64)
		g, ok := gameMap[uint(gid)]
		if !ok {
			continue
		}
		result = append(result, AnniversaryItem{
			Game:     toGameResponseWithData(g, &userData),
			YearsAgo: a.YearsAgo,
			PlayedAt: a.PlayedAt,
		})
	}

	// Sort by yearsAgo ascending (most recent anniversaries first)
	sort.Slice(result, func(i, j int) bool {
		if result[i].YearsAgo != result[j].YearsAgo {
			return result[i].YearsAgo < result[j].YearsAgo
		}
		return result[i].Game.Title < result[j].Game.Title
	})

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, AnniversariesResponse{Anniversaries: result})
}

// decadeRange maps a decade string to its year range.
var decadeRange = map[string][2]int{
	"80s": {1980, 1989},
	"90s": {1990, 1999},
	"00s": {2000, 2009},
}

// decadeLabel maps a decade string to a display label.
var decadeLabel = map[string]string{
	"80s": "The 80s",
	"90s": "The 90s",
	"00s": "The 00s",
}

// GetDecades returns the best games of a given decade.
// GET /api/explore/decades/:decade
func (h *ExploreHandler) GetDecades(c *gin.Context) {
	userID := getUserID(c)

	decade := c.Param("decade")
	yearRange, ok := decadeRange[decade]
	if !ok {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid decade; valid values: 80s, 90s, 00s"})
		return
	}
	label := decadeLabel[decade]

	// Build LIKE conditions for each year in the range
	conditions := make([]string, 0, yearRange[1]-yearRange[0]+1)
	args := make([]interface{}, 0, yearRange[1]-yearRange[0]+1)
	for y := yearRange[0]; y <= yearRange[1]; y++ {
		conditions = append(conditions, "release_date LIKE ?")
		args = append(args, fmt.Sprintf("%d%%", y))
	}
	whereClause := "(" + strings.Join(conditions, " OR ") + ")"

	var games []db.Game
	if err := h.DB.Preload("Console").
		Where(whereClause, args...).
		Where("rating > 0 AND deleted_at IS NULL").
		Order("rating DESC").
		Limit(30).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch decade games", "error", err, "decade", decade)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch games"})
		return
	}

	if len(games) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, DecadesResponse{Decade: decade, Label: label, Games: []GameResponse{}})
		return
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, DecadesResponse{
		Decade: decade,
		Label:  label,
		Games:  ToGameResponses(games, h.DB, userID),
	})
}

// --- Phase 12: Achievement & Challenge-Driven Discovery handlers ---

// GetEasyToComplete returns games with the highest average achievement completion rate.
// GET /api/explore/easy-to-complete
func (h *ExploreHandler) GetEasyToComplete(c *gin.Context) {
	userID := getUserID(c)

	// For each game with cached achievements, compute:
	// - total achievements (from cache)
	// - per-user unlock counts
	// - average completion % across all users who unlocked at least 1
	type achievementRow struct {
		GameID           uint
		RAGameID         uint
		TotalCount       int
		PlayersAttempted int
		AvgUnlocked      float64
	}
	var rows []achievementRow
	if err := h.DB.
		Table("game_achievement_caches").
		Select(`game_achievement_caches.game_id,
			game_achievement_caches.ra_game_id,
			game_achievement_caches.total_count,
			COUNT(DISTINCT sub.user_id) as players_attempted,
			CAST(AVG(sub.unlocked_count) AS REAL) as avg_unlocked`).
		Joins(`JOIN (
			SELECT ra_game_id, user_id, COUNT(*) as unlocked_count
			FROM user_achievement_progresses
			WHERE deleted_at IS NULL
			GROUP BY ra_game_id, user_id
		) sub ON sub.ra_game_id = game_achievement_caches.ra_game_id`).
		Where("game_achievement_caches.game_id > 0 AND game_achievement_caches.total_count > 0 AND game_achievement_caches.deleted_at IS NULL").
		Group("game_achievement_caches.game_id, game_achievement_caches.ra_game_id, game_achievement_caches.total_count").
		Having("players_attempted >= 1").
		Order("(avg_unlocked / game_achievement_caches.total_count) DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch easy-to-complete games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch easy-to-complete games"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, EasyToCompleteResponse{Games: []AchievementGameResponse{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load easy-to-complete games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	// Count players who completed 100% per game
	raGameIDs := make([]uint, len(rows))
	for i, r := range rows {
		raGameIDs[i] = r.RAGameID
	}
	// Build a map from ra_game_id to total_count for the completion check
	raTotalMap := make(map[uint]int, len(rows))
	for _, r := range rows {
		raTotalMap[r.RAGameID] = r.TotalCount
	}

	// Count completions: users who unlocked all achievements for a game
	completedMap := make(map[uint]int, len(rows))
	type userUnlockRow struct {
		RAGameID uint
		UserID   uint
		Unlocked int
	}
	var userUnlocks []userUnlockRow
	if err := h.DB.
		Table("user_achievement_progresses").
		Select("ra_game_id, user_id, COUNT(*) as unlocked").
		Where("ra_game_id IN ? AND deleted_at IS NULL", raGameIDs).
		Group("ra_game_id, user_id").
		Scan(&userUnlocks).Error; err == nil {
		for _, u := range userUnlocks {
			if total, ok := raTotalMap[u.RAGameID]; ok && u.Unlocked >= total {
				completedMap[u.RAGameID]++
			}
		}
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	// Build result in query order
	raToGameID := make(map[uint]uint, len(rows))
	for _, r := range rows {
		raToGameID[r.RAGameID] = r.GameID
	}

	result := make([]AchievementGameResponse, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		avgCompletion := 0.0
		if r.TotalCount > 0 {
			avgCompletion = math.Round((r.AvgUnlocked/float64(r.TotalCount))*10000) / 100
		}
		result = append(result, AchievementGameResponse{
			Game:              toGameResponseWithData(g, &userData),
			TotalAchievements: r.TotalCount,
			AvgCompletion:     avgCompletion,
			PlayersAttempted:  r.PlayersAttempted,
			PlayersCompleted:  completedMap[r.RAGameID],
		})
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, EasyToCompleteResponse{Games: result})
}

// GetHardestGames returns games with the lowest average achievement completion rate.
// Only includes games where at least 2 users have attempted.
// GET /api/explore/hardest-games
func (h *ExploreHandler) GetHardestGames(c *gin.Context) {
	userID := getUserID(c)

	type achievementRow struct {
		GameID           uint
		RAGameID         uint
		TotalCount       int
		PlayersAttempted int
		AvgUnlocked      float64
	}
	var rows []achievementRow
	if err := h.DB.
		Table("game_achievement_caches").
		Select(`game_achievement_caches.game_id,
			game_achievement_caches.ra_game_id,
			game_achievement_caches.total_count,
			COUNT(DISTINCT sub.user_id) as players_attempted,
			CAST(AVG(sub.unlocked_count) AS REAL) as avg_unlocked`).
		Joins(`JOIN (
			SELECT ra_game_id, user_id, COUNT(*) as unlocked_count
			FROM user_achievement_progresses
			WHERE deleted_at IS NULL
			GROUP BY ra_game_id, user_id
		) sub ON sub.ra_game_id = game_achievement_caches.ra_game_id`).
		Where("game_achievement_caches.game_id > 0 AND game_achievement_caches.total_count > 0 AND game_achievement_caches.deleted_at IS NULL").
		Group("game_achievement_caches.game_id, game_achievement_caches.ra_game_id, game_achievement_caches.total_count").
		Having("players_attempted >= 2").
		Order("(avg_unlocked / game_achievement_caches.total_count) ASC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch hardest games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch hardest games"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=300")
		c.JSON(http.StatusOK, HardestGamesResponse{Games: []AchievementGameResponse{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	raGameIDs := make([]uint, len(rows))
	raTotalMap := make(map[uint]int, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
		raGameIDs[i] = r.RAGameID
		raTotalMap[r.RAGameID] = r.TotalCount
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load hardest games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	// Count completions
	completedMap := make(map[uint]int, len(rows))
	type userUnlockRow struct {
		RAGameID uint
		UserID   uint
		Unlocked int
	}
	var userUnlocks []userUnlockRow
	if err := h.DB.
		Table("user_achievement_progresses").
		Select("ra_game_id, user_id, COUNT(*) as unlocked").
		Where("ra_game_id IN ? AND deleted_at IS NULL", raGameIDs).
		Group("ra_game_id, user_id").
		Scan(&userUnlocks).Error; err == nil {
		for _, u := range userUnlocks {
			if total, ok := raTotalMap[u.RAGameID]; ok && u.Unlocked >= total {
				completedMap[u.RAGameID]++
			}
		}
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]AchievementGameResponse, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		avgCompletion := 0.0
		if r.TotalCount > 0 {
			avgCompletion = math.Round((r.AvgUnlocked/float64(r.TotalCount))*10000) / 100
		}
		result = append(result, AchievementGameResponse{
			Game:              toGameResponseWithData(g, &userData),
			TotalAchievements: r.TotalCount,
			AvgCompletion:     avgCompletion,
			PlayersAttempted:  r.PlayersAttempted,
			PlayersCompleted:  completedMap[r.RAGameID],
		})
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, HardestGamesResponse{Games: result})
}

// GetAlmostDone returns games where the current user has completed 80-99% of achievements.
// GET /api/explore/almost-done
func (h *ExploreHandler) GetAlmostDone(c *gin.Context) {
	userID := getUserID(c)

	// Get the user's unlock counts per RA game, joined with the cache for total counts
	type progressRow struct {
		GameID        uint
		RAGameID      uint
		TotalCount    int
		UnlockedCount int
	}
	var rows []progressRow
	if err := h.DB.
		Table("game_achievement_caches").
		Select(`game_achievement_caches.game_id,
			game_achievement_caches.ra_game_id,
			game_achievement_caches.total_count,
			COUNT(user_achievement_progresses.id) as unlocked_count`).
		Joins(`JOIN user_achievement_progresses ON user_achievement_progresses.ra_game_id = game_achievement_caches.ra_game_id
			AND user_achievement_progresses.user_id = ? AND user_achievement_progresses.deleted_at IS NULL`, userID).
		Where("game_achievement_caches.game_id > 0 AND game_achievement_caches.total_count > 0 AND game_achievement_caches.deleted_at IS NULL").
		Group("game_achievement_caches.game_id, game_achievement_caches.ra_game_id, game_achievement_caches.total_count").
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch almost-done games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch almost-done games"})
		return
	}

	// Filter to 80-99% completion
	var filtered []progressRow
	for _, r := range rows {
		if r.TotalCount == 0 {
			continue
		}
		pct := float64(r.UnlockedCount) / float64(r.TotalCount) * 100
		if pct >= 80.0 && pct < 100.0 {
			filtered = append(filtered, r)
		}
	}

	if len(filtered) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, AlmostDoneResponse{Games: []AlmostDoneGame{}})
		return
	}

	// Sort by completion % descending
	sort.Slice(filtered, func(i, j int) bool {
		pctI := float64(filtered[i].UnlockedCount) / float64(filtered[i].TotalCount)
		pctJ := float64(filtered[j].UnlockedCount) / float64(filtered[j].TotalCount)
		return pctI > pctJ
	})

	gameIDs := make([]uint, len(filtered))
	for i, r := range filtered {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load almost-done games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]AlmostDoneGame, 0, len(filtered))
	for _, r := range filtered {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		pct := math.Round(float64(r.UnlockedCount)/float64(r.TotalCount)*10000) / 100
		result = append(result, AlmostDoneGame{
			Game:              toGameResponseWithData(g, &userData),
			UnlockedCount:     r.UnlockedCount,
			TotalCount:        r.TotalCount,
			CompletionPercent: pct,
		})
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, AlmostDoneResponse{Games: result})
}

// GetFreshChallenges returns games with cached achievements where the user has 0 unlocks.
// GET /api/explore/fresh-challenges
func (h *ExploreHandler) GetFreshChallenges(c *gin.Context) {
	userID := getUserID(c)

	// Find all RA game IDs where this user has at least 1 unlock
	var startedRAGameIDs []uint
	if err := h.DB.
		Table("user_achievement_progresses").
		Select("DISTINCT ra_game_id").
		Where("user_id = ? AND deleted_at IS NULL", userID).
		Scan(&startedRAGameIDs).Error; err != nil {
		slog.Error("failed to fetch started RA games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch fresh challenges"})
		return
	}

	query := h.DB.
		Table("game_achievement_caches").
		Where("game_id > 0 AND total_count > 0 AND deleted_at IS NULL")

	if len(startedRAGameIDs) > 0 {
		query = query.Where("ra_game_id NOT IN ?", startedRAGameIDs)
	}

	type cacheRow struct {
		GameID      uint
		TotalCount  int
		TotalPoints int
	}
	var rows []cacheRow
	if err := query.
		Select("game_id, total_count, total_points").
		Order("total_count DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch fresh challenges", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch fresh challenges"})
		return
	}

	if len(rows) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, FreshChallengesResponse{Games: []FreshChallengeGame{}})
		return
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load fresh challenge games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to load games"})
		return
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

	result := make([]FreshChallengeGame, 0, len(rows))
	for _, r := range rows {
		g, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		result = append(result, FreshChallengeGame{
			Game:              toGameResponseWithData(g, &userData),
			TotalAchievements: r.TotalCount,
			TotalPoints:       r.TotalPoints,
		})
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, FreshChallengesResponse{Games: result})
}

// GetActiveChallenges returns active (non-expired) Challenge entities, most recent first.
// GET /api/explore/active-challenges
func (h *ExploreHandler) GetActiveChallenges(c *gin.Context) {
	now := time.Now()

	var challenges []db.Challenge
	if err := h.DB.
		Preload("Creator").
		Preload("Game").
		Preload("Game.Console").
		Where("status = ? AND deleted_at IS NULL", "active").
		Where("expires_at IS NULL OR expires_at > ?", now).
		Order("created_at DESC").
		Limit(10).
		Find(&challenges).Error; err != nil {
		slog.Error("failed to fetch active challenges", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch active challenges"})
		return
	}

	if len(challenges) == 0 {
		c.Header("Cache-Control", "private, max-age=120")
		c.JSON(http.StatusOK, ActiveChallengesResponse{Challenges: []ExploreChallengeResponse{}})
		return
	}

	result := make([]ExploreChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		coverURL := ""
		if ch.Game.CoverURL != "" {
			coverURL = resolveImageURL(ch.Game.CoverURL)
		}
		consoleName := ""
		if ch.Game.Console.Name != "" {
			consoleName = ch.Game.Console.Name
		}
		result = append(result, ExploreChallengeResponse{
			ID:              strconv.FormatUint(uint64(ch.ID), 10),
			CreatorUsername:  ch.Creator.Username,
			GameID:          strconv.FormatUint(uint64(ch.GameID), 10),
			GameTitle:       ch.Game.Title,
			GameCoverURL:    coverURL,
			ConsoleName:     consoleName,
			Name:            ch.Name,
			Description:     ch.Description,
			Type:            ch.Type,
			Difficulty:      ch.Difficulty,
			AttemptCount:    ch.AttemptCount,
			CompletionCount: ch.CompletionCount,
			ExpiresAt:       ch.ExpiresAt,
			CreatedAt:       ch.CreatedAt,
		})
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, ActiveChallengesResponse{Challenges: result})
}

// --- Phase 14: Wild Features — Wizard, Badges, Completionist Map ---

// WizardStep represents a single step in the decision wizard.
type WizardStep struct {
	Step    int              `json:"step"`
	Title   string           `json:"title"`
	Type    string           `json:"type"` // "mood", "era", "vibe"
	Options []WizardOption   `json:"options"`
}

// WizardOption is a selectable option in a wizard step.
type WizardOption struct {
	ID          string `json:"id"`
	Label       string `json:"label"`
	Description string `json:"description,omitempty"`
	ImageURL    string `json:"imageUrl,omitempty"`
}

// WizardResponse is the API response for the wizard endpoint.
type WizardResponse struct {
	Steps []WizardStep `json:"steps"`
}

// WizardResultsResponse is the response for wizard recommendations.
type WizardResultsResponse struct {
	Games []GameResponse `json:"games"`
	Title string         `json:"title"`
}

// GetWizardSteps returns the decision wizard configuration (the steps and options).
// GET /api/explore/wizard
func (h *ExploreHandler) GetWizardSteps(c *gin.Context) {
	steps := []WizardStep{
		{
			Step:  1,
			Title: "What are you in the mood for?",
			Type:  "mood",
			Options: []WizardOption{
				{ID: "action", Label: "Action & Excitement", Description: "Fast-paced thrills"},
				{ID: "chill", Label: "Chill & Relaxing", Description: "Laid-back vibes"},
				{ID: "story", Label: "Deep Story", Description: "Rich narrative experiences"},
				{ID: "challenge", Label: "A Real Challenge", Description: "Test your skills"},
				{ID: "fun", Label: "Pure Fun", Description: "Simple pick-up-and-play"},
			},
		},
		{
			Step:  2,
			Title: "Pick an era",
			Type:  "era",
			Options: []WizardOption{
				{ID: "80s", Label: "The 80s", Description: "Birth of console gaming"},
				{ID: "early90s", Label: "Early 90s", Description: "16-bit golden age"},
				{ID: "late90s", Label: "Late 90s", Description: "3D revolution begins"},
				{ID: "2000s", Label: "The 2000s", Description: "Handheld renaissance"},
				{ID: "any", Label: "Any Era", Description: "Surprise me"},
			},
		},
		{
			Step:  3,
			Title: "Refine your vibe",
			Type:  "vibe",
			Options: []WizardOption{
				{ID: "solo", Label: "Solo Adventure", Description: "Just me and the game"},
				{ID: "multiplayer", Label: "Multiplayer", Description: "Games with friends"},
				{ID: "short", Label: "Quick Session", Description: "Short and sweet"},
				{ID: "long", Label: "Deep Dive", Description: "Hours of content"},
				{ID: "any", Label: "Anything Goes", Description: "No preference"},
			},
		},
	}

	c.Header("Cache-Control", "private, max-age=300")
	c.JSON(http.StatusOK, WizardResponse{Steps: steps})
}

// GetWizardResults returns 5 game recommendations based on wizard choices.
// GET /api/explore/wizard/results?mood=action&era=90s&vibe=solo
func (h *ExploreHandler) GetWizardResults(c *gin.Context) {
	userID := getUserID(c)
	mood := c.Query("mood")
	era := c.Query("era")
	vibe := c.Query("vibe")

	query := h.DB.Preload("Console").Where("cover_url != ''")

	// Mood -> genre/theme mapping
	switch mood {
	case "action":
		query = query.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up"})
	case "chill":
		query = query.Where("genre IN ?", []string{"Puzzle", "Simulation", "Sports"})
	case "story":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure"})
	case "challenge":
		query = query.Where("genre IN ?", []string{"Action", "Platformer", "Shooter"}).
			Where("rating >= 75")
	case "fun":
		query = query.Where("genre IN ?", []string{"Platformer", "Arcade", "Racing", "Puzzle"})
	}

	// Era -> year range mapping
	switch era {
	case "80s":
		query = query.Where("release_date >= '1980' AND release_date < '1990'")
	case "early90s":
		query = query.Where("release_date >= '1990' AND release_date < '1995'")
	case "late90s":
		query = query.Where("release_date >= '1995' AND release_date < '2000'")
	case "2000s":
		query = query.Where("release_date >= '2000' AND release_date < '2010'")
	// "any" = no filter
	}

	// Vibe -> refinement
	switch vibe {
	case "solo":
		query = query.Where("(players IS NULL OR players = 0 OR players = 1)")
	case "multiplayer":
		query = query.Where("players > 1")
	case "short":
		// Prefer games with shorter average sessions
		query = query.Where("genre NOT IN ?", []string{"RPG"})
	case "long":
		query = query.Where("genre IN ?", []string{"RPG", "Adventure", "Strategy"})
	// "any" = no filter
	}

	var games []db.Game
	if err := query.Where("rating > 0").
		Order("rating DESC, RANDOM()").
		Limit(5).
		Find(&games).Error; err != nil {
		slog.Error("failed to fetch wizard results", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch wizard results"})
		return
	}

	// If not enough results, relax and try without rating filter
	if len(games) < 3 {
		relaxed := h.DB.Preload("Console").Where("cover_url != ''")
		switch mood {
		case "action":
			relaxed = relaxed.Where("genre IN ?", []string{"Action", "Shooter", "Fighting", "Beat 'em up", "Platformer"})
		case "story":
			relaxed = relaxed.Where("genre IN ?", []string{"RPG", "Adventure"})
		default:
			// No genre filter for relaxed search
		}
		relaxed.Order("RANDOM()").Limit(5).Find(&games)
	}

	title := "Your Perfect Picks"
	if mood != "" {
		titles := map[string]string{
			"action":    "Action-Packed Picks",
			"chill":     "Chill & Relaxing",
			"story":     "Story-Driven Adventures",
			"challenge": "Challenge Accepted",
			"fun":       "Pure Fun Picks",
		}
		if t, ok := titles[mood]; ok {
			title = t
		}
	}

	c.Header("Cache-Control", "no-store")
	c.JSON(http.StatusOK, WizardResultsResponse{
		Games: ToGameResponses(games, h.DB, userID),
		Title: title,
	})
}

// ExplorerBadge represents a discovery/exploration badge.
type ExplorerBadge struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Icon        string `json:"icon"`
	Earned      bool   `json:"earned"`
	Progress    int    `json:"progress"`
	Target      int    `json:"target"`
}

// ExplorerBadgesResponse is the API response for explorer badges.
type ExplorerBadgesResponse struct {
	Badges []ExplorerBadge `json:"badges"`
}

// GetExplorerBadges returns the user's exploration breadth badges.
// GET /api/user/explorer-badges
func (h *ExploreHandler) GetExplorerBadges(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Count distinct consoles played
	var consolesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.console_id)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
	`, userID).Scan(&consolesPlayed).Error; err != nil {
		slog.Error("failed to query consoles played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total consoles with games
	var totalConsoles int64
	if err := h.DB.Raw(`SELECT COUNT(DISTINCT console_id) FROM games WHERE deleted_at IS NULL`).Scan(&totalConsoles).Error; err != nil {
		slog.Error("failed to query total consoles", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count distinct genres played
	var genresPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT g.genre)
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL AND g.genre != ''
	`, userID).Scan(&genresPlayed).Error; err != nil {
		slog.Error("failed to query genres played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count distinct decades played
	var decadesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT CAST(SUBSTR(g.release_date, 1, 3) AS TEXT))
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL AND g.deleted_at IS NULL
		AND g.release_date != '' AND LENGTH(g.release_date) >= 4
	`, userID).Scan(&decadesPlayed).Error; err != nil {
		slog.Error("failed to query decades played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total games played
	var gamesPlayed int64
	if err := h.DB.Raw(`
		SELECT COUNT(DISTINCT ph.game_id)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&gamesPlayed).Error; err != nil {
		slog.Error("failed to query games played", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}

	// Count total play time (seconds)
	var totalPlayTime int64
	if err := h.DB.Raw(`
		SELECT COALESCE(SUM(ph.play_time), 0)
		FROM play_histories ph
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
	`, userID).Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to query total play time", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute badges"})
		return
	}
	totalHours := totalPlayTime / 3600

	badges := []ExplorerBadge{
		{
			ID:          "console-explorer",
			Name:        "Console Explorer",
			Description: "Play games on every console",
			Icon:        "gamepad",
			Earned:      consolesPlayed >= totalConsoles && totalConsoles > 0,
			Progress:    int(consolesPlayed),
			Target:      int(totalConsoles),
		},
		{
			ID:          "genre-master",
			Name:        "Genre Master",
			Description: "Play games from 10 different genres",
			Icon:        "layers",
			Earned:      genresPlayed >= 10,
			Progress:    int(genresPlayed),
			Target:      10,
		},
		{
			ID:          "time-traveler",
			Name:        "Time Traveler",
			Description: "Play games from 5 different decades",
			Icon:        "clock",
			Earned:      decadesPlayed >= 5,
			Progress:    int(decadesPlayed),
			Target:      5,
		},
		{
			ID:          "centurion",
			Name:        "Centurion",
			Description: "Play 100 different games",
			Icon:        "trophy",
			Earned:      gamesPlayed >= 100,
			Progress:    int(gamesPlayed),
			Target:      100,
		},
		{
			ID:          "dedicated-gamer",
			Name:        "Dedicated Gamer",
			Description: "Accumulate 50 hours of play time",
			Icon:        "timer",
			Earned:      totalHours >= 50,
			Progress:    int(totalHours),
			Target:      50,
		},
		{
			ID:          "marathon-runner",
			Name:        "Marathon Runner",
			Description: "Accumulate 200 hours of play time",
			Icon:        "flame",
			Earned:      totalHours >= 200,
			Progress:    int(totalHours),
			Target:      200,
		},
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, ExplorerBadgesResponse{Badges: badges})
}

// CompletionistConsole represents per-console completion stats.
type CompletionistConsole struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	TotalGames  int    `json:"totalGames"`
	PlayedGames int    `json:"playedGames"`
	Percentage  int    `json:"percentage"`
}

// CompletionistMapResponse is the API response for the completionist map.
type CompletionistMapResponse struct {
	Consoles     []CompletionistConsole `json:"consoles"`
	TotalGames   int                    `json:"totalGames"`
	TotalPlayed  int                    `json:"totalPlayed"`
	OverallPct   int                    `json:"overallPct"`
}

// GetCompletionistMap returns per-console completion percentages.
// GET /api/user/completionist-map
func (h *ExploreHandler) GetCompletionistMap(c *gin.Context) {
	userID := getUserID(c)
	if userID == 0 {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "authentication required"})
		return
	}

	// Get per-console game counts
	type consoleRow struct {
		ConsoleID    uint
		ConsoleName  string
		Abbreviation string
		TotalGames   int
	}
	var consoleRows []consoleRow
	if err := h.DB.Raw(`
		SELECT c.id AS console_id, c.name AS console_name, c.abbreviation, COUNT(g.id) AS total_games
		FROM consoles c
		JOIN games g ON g.console_id = c.id AND g.deleted_at IS NULL
		WHERE c.deleted_at IS NULL
		GROUP BY c.id
		HAVING COUNT(g.id) > 0
		ORDER BY c.name
	`).Scan(&consoleRows).Error; err != nil {
		slog.Error("failed to query console counts", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute completionist map"})
		return
	}

	// Get per-console played counts for this user
	type playedRow struct {
		ConsoleID   uint
		PlayedGames int
	}
	var playedRows []playedRow
	if err := h.DB.Raw(`
		SELECT g.console_id, COUNT(DISTINCT g.id) AS played_games
		FROM play_histories ph
		JOIN games g ON g.id = ph.game_id AND g.deleted_at IS NULL
		WHERE ph.user_id = ? AND ph.deleted_at IS NULL
		GROUP BY g.console_id
	`, userID).Scan(&playedRows).Error; err != nil {
		slog.Error("failed to query played counts", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to compute completionist map"})
		return
	}

	playedMap := make(map[uint]int)
	for _, pr := range playedRows {
		playedMap[pr.ConsoleID] = pr.PlayedGames
	}

	var consoles []CompletionistConsole
	totalGames := 0
	totalPlayed := 0

	for _, cr := range consoleRows {
		played := playedMap[cr.ConsoleID]
		pct := 0
		if cr.TotalGames > 0 {
			pct = played * 100 / cr.TotalGames
		}
		consoles = append(consoles, CompletionistConsole{
			ID:          cr.Abbreviation,
			Name:        cr.ConsoleName,
			TotalGames:  cr.TotalGames,
			PlayedGames: played,
			Percentage:  pct,
		})
		totalGames += cr.TotalGames
		totalPlayed += played
	}

	overallPct := 0
	if totalGames > 0 {
		overallPct = totalPlayed * 100 / totalGames
	}

	c.Header("Cache-Control", "private, max-age=120")
	c.JSON(http.StatusOK, CompletionistMapResponse{
		Consoles:    consoles,
		TotalGames:  totalGames,
		TotalPlayed: totalPlayed,
		OverallPct:  overallPct,
	})
}
