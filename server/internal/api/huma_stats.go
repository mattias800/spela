package api

import (
	"context"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// MostPlayedInput is the input for GET /api/stats/most-played.
type MostPlayedInput struct{}

// MostPlayedOutput wraps the most-played body for the huma response envelope.
type MostPlayedOutput struct {
	Body MostPlayedResponse
}

// MostActivePlayersInput is the input for GET /api/stats/most-active-players.
type MostActivePlayersInput struct{}

// MostActivePlayersOutput wraps the most-active-players body for the huma
// response envelope.
type MostActivePlayersOutput struct {
	Body MostActivePlayersResponse
}

// RegisterStatsRoutes wires the simple community-stats endpoints into the
// huma API. Heatmap endpoints stay on raw gin for now (this migration covers
// only the top-level read paths).
func RegisterStatsRoutes(api huma.API, h *StatsHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)

	huma.Register(api, huma.Operation{
		OperationID: "getMostPlayed",
		Method:      http.MethodGet,
		Path:        "/api/stats/most-played",
		Summary:     "Most played games",
		Description: "Returns the top 25 most-played games across all users, sorted by total play time descending.",
		Tags:        []string{"stats"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaMostPlayedGames)

	huma.Register(api, huma.Operation{
		OperationID: "getMostActivePlayers",
		Method:      http.MethodGet,
		Path:        "/api/stats/most-active-players",
		Summary:     "Most active players",
		Description: "Returns the top 25 players across the server ranked by total play time.",
		Tags:        []string{"stats"},
		Middlewares: huma.Middlewares{requireAuth, rateLimit},
		Security:    []map[string][]string{{"bearer": {}}},
	}, h.HumaMostActivePlayers)
}

// HumaMostPlayedGames is the huma handler for GET /api/stats/most-played.
func (h *StatsHandler) HumaMostPlayedGames(ctx context.Context, _ *MostPlayedInput) (*MostPlayedOutput, error) {
	type row struct {
		GameID        uint
		TotalPlayers  int64
		TotalPlayTime int64
	}
	var rows []row
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("game_id, COUNT(DISTINCT user_id) as total_players, SUM(play_time) as total_play_time").
		Group("game_id").
		Order("total_play_time DESC").
		Limit(25).
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch most played games")
	}

	if len(rows) == 0 {
		return &MostPlayedOutput{Body: MostPlayedResponse{Games: []MostPlayedEntry{}}}, nil
	}

	// Load full games with console preloaded.
	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}
	var games []db.Game
	h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games)

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userID := UserIDFromContext(ctx)
	data := loadGameResponseData(h.DB, userID, games)

	entries := make([]MostPlayedEntry, 0, len(rows))
	for _, r := range rows {
		game, ok := gameMap[r.GameID]
		if !ok {
			continue
		}
		entries = append(entries, MostPlayedEntry{
			Game:          toGameResponseWithData(game, &data),
			TotalPlayers:  r.TotalPlayers,
			TotalPlayTime: r.TotalPlayTime,
		})
	}

	return &MostPlayedOutput{Body: MostPlayedResponse{Games: entries}}, nil
}

// HumaMostActivePlayers is the huma handler for GET /api/stats/most-active-players.
func (h *StatsHandler) HumaMostActivePlayers(_ context.Context, _ *MostActivePlayersInput) (*MostActivePlayersOutput, error) {
	type row struct {
		UserID        uint
		Username      string
		AvatarURL     string
		TotalPlayTime int64
		GamesPlayed   int64
		LastPlayed    string
	}
	var rows []row
	if err := h.DB.Model(&db.PlayHistory{}).
		Select("play_histories.user_id, users.username, users.avatar_url, SUM(play_histories.play_time) as total_play_time, COUNT(DISTINCT play_histories.game_id) as games_played, MAX(play_histories.last_played) as last_played").
		Joins("JOIN users ON users.id = play_histories.user_id").
		Group("play_histories.user_id").
		Order("total_play_time DESC").
		Limit(25).
		Scan(&rows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch most active players")
	}

	players := make([]ActivePlayerEntry, 0, len(rows))
	for _, r := range rows {
		lastPlayed := parseTimeString(r.LastPlayed)
		players = append(players, ActivePlayerEntry{
			UserID:        strconv.FormatUint(uint64(r.UserID), 10),
			Username:      r.Username,
			AvatarURL:     r.AvatarURL,
			TotalPlayTime: r.TotalPlayTime,
			GamesPlayed:   r.GamesPlayed,
			LastPlayed:    lastPlayed,
		})
	}

	return &MostActivePlayersOutput{Body: MostActivePlayersResponse{Players: players}}, nil
}
