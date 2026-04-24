package api

import (
	"fmt"
	"time"

	"github.com/spela/server/internal/db"
)

// --- Phase 6: Personalized Recommendations ---
//
// The gin handlers GetForYou, GetTasteProfile, and GetPlayersLikeYou have been
// migrated to huma — see huma_explore_foryou.go. The helpers used by both the
// gin and huma implementations live here so the huma handlers keep working
// unchanged.

// ForYouRowResponse represents a single recommendation row in the "For You" response.
type ForYouRowResponse struct {
	Type       string         `json:"type"`
	Title      string         `json:"title"`
	SourceGame *GameResponse  `json:"sourceGame,omitempty"`
	Genre      string         `json:"genre"`
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
