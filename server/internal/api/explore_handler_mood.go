package api

import (
	"log/slog"
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

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
