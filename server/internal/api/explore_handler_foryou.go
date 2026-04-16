package api

import (
	"fmt"
	"log/slog"
	"math"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

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
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "authentication required"})
		return
	}

	rows := []ForYouRowResponse{}

	// a) "Because you played [Game]" — top 3 most-played games, find same-genre unplayed games
	becauseRows, err := h.buildBecauseYouPlayedRows(userID)
	if err != nil {
		slog.Error("failed to build because-you-played rows", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build recommendations"})
		return
	}
	rows = append(rows, becauseRows...)

	// b) "More [Genre] for you" — most-played genre, top-rated unplayed games
	moreGenreRow, err := h.buildMoreGenreRow(userID)
	if err != nil {
		slog.Error("failed to build more-genre row", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build recommendations"})
		return
	}
	if moreGenreRow != nil {
		rows = append(rows, *moreGenreRow)
	}

	// c) "Your unfinished business" — played < 30 min, last played > 7 days ago
	unfinishedRow, err := h.buildUnfinishedRow(userID)
	if err != nil {
		slog.Error("failed to build unfinished row", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build recommendations"})
		return
	}
	if unfinishedRow != nil {
		rows = append(rows, *unfinishedRow)
	}

	// d) "Expand your horizons" — genres the user has never played
	expandRow, err := h.buildExpandHorizonsRow(userID)
	if err != nil {
		slog.Error("failed to build expand-horizons row", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build recommendations"})
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
			Where("games.genre = ? AND games.is_primary = true AND games.deleted_at IS NULL", srcGame.Genre)

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
		Where("games.genre = ? AND games.is_primary = true AND games.deleted_at IS NULL", topGenre)
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
		Where("genre NOT IN ? AND genre != '' AND rating > 70 AND is_primary = true AND deleted_at IS NULL", playedGenres).
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
		Where("genre = ? AND rating > 70 AND is_primary = true AND deleted_at IS NULL", targetGenre).
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
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "authentication required"})
		return
	}

	// Calculate total play time
	var totalPlayTime int64
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("COALESCE(SUM(play_time), 0)").
		Where("user_id = ? AND play_time > 0", userID).
		Scan(&totalPlayTime).Error; err != nil {
		slog.Error("failed to calculate total play time", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build taste profile"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build taste profile"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build taste profile"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to build taste profile"})
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
		c.JSON(http.StatusUnauthorized, ErrorResponse{Error: "authentication required"})
		return
	}

	// Get the current user's favorites
	var myFavIDs []uint
	if err := h.DB.Model(&db.Favorite{}).
		Select("game_id").
		Where("user_id = ?", userID).
		Pluck("game_id", &myFavIDs).Error; err != nil {
		slog.Error("failed to get user favorites", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to get recommendations"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to get recommendations"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to get recommendations"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to get recommendations"})
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
	if err := h.DB.Preload("Console").Where("id IN ? AND is_primary = true", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load recommended games", "error", err)
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to get recommendations"})
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
