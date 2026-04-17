package api

import (
	"context"
	"net/http"
	"sort"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// GetPlayHeatmapInput is the input for GET /api/user/play-heatmap.
type GetPlayHeatmapInput struct{}

// GetPlayHeatmapOutput wraps the heatmap list.
type GetPlayHeatmapOutput struct {
	Body []HeatmapEntry
}

// GetPublicPlayHeatmapInput is the input for GET /api/users/{id}/play-heatmap.
type GetPublicPlayHeatmapInput struct {
	ID string `path:"id" doc:"User ID."`
}

// GetPublicPlayHeatmapOutput wraps the public heatmap list.
type GetPublicPlayHeatmapOutput struct {
	Body []HeatmapEntry
}

// GetUserStatsInput is the input for GET /api/user/stats.
type GetUserStatsInput struct{}

// GetUserStatsOutput wraps the user stats response.
type GetUserStatsOutput struct {
	Body UserStatsResponse
}

// GetPlayStatsInput is the input for GET /api/user/play-stats.
type GetPlayStatsInput struct{}

// GetPlayStatsOutput wraps the flat play-stats list.
type GetPlayStatsOutput struct {
	Body []PlayStatsEntry
}

// GetRecentGamesInput is the input for GET /api/user/recent.
type GetRecentGamesInput struct{}

// GetRecentGamesOutput wraps the recent games list.
type GetRecentGamesOutput struct {
	Body []GameResponse
}

// ReportEmulatorErrorInput is the input for POST /api/emulator/error.
type ReportEmulatorErrorInput struct {
	Body ReportEmulatorErrorRequest
}

// ReportedResponse is the wire format for the report-success body.
type ReportedResponse struct {
	Reported bool `json:"reported"`
}

// ReportEmulatorErrorOutput wraps the reported response.
type ReportEmulatorErrorOutput struct {
	Body ReportedResponse
}

// GetGameKeyMappingInput is the input for GET /api/user/games/{gameId}/keymapping.
type GetGameKeyMappingInput struct {
	GameID string `path:"gameId" doc:"Game ID."`
}

// GetGameKeyMappingOutput wraps the key-mapping response.
type GetGameKeyMappingOutput struct {
	Body GameKeyMappingResponse
}

// UpdateGameKeyMappingInput is the input for PUT /api/user/games/{gameId}/keymapping.
type UpdateGameKeyMappingInput struct {
	GameID string `path:"gameId" doc:"Game ID."`
	Body   UpdateGameKeyMappingRequest
}

// UpdateGameKeyMappingOutput wraps the updated key-mapping response.
type UpdateGameKeyMappingOutput struct {
	Body GameKeyMappingResponse
}

// DeleteGameKeyMappingInput is the input for DELETE /api/user/games/{gameId}/keymapping.
type DeleteGameKeyMappingInput struct {
	GameID string `path:"gameId" doc:"Game ID."`
}

// DeleteGameKeyMappingOutput wraps the delete success message.
type DeleteGameKeyMappingOutput struct {
	Body MessageResponse
}

// RegisterUserExtraRoutes wires the remaining user-facing read/mutation
// endpoints (stats, heatmap, recent, error reporting, key mappings) into
// the huma API.
func RegisterUserExtraRoutes(api huma.API, userHandler *UserHandler, statsHandler *StatsHandler, keyMappingHandler *GameKeyMappingHandler, systemEventHandler *SystemEventHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getUserStats",
		Method:      http.MethodGet,
		Path:        "/api/user/stats",
		Summary:     "Get personal gameplay statistics",
		Description: "Returns the caller's aggregate play time, games played, streaks, and most-played game.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, userHandler.HumaGetUserStats)

	huma.Register(api, huma.Operation{
		OperationID: "getPlayStats",
		Method:      http.MethodGet,
		Path:        "/api/user/play-stats",
		Summary:     "Get per-game play statistics",
		Description: "Returns a flat list of {gameId, playTime, lastPlayedAt} entries for every game the caller has played.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, userHandler.HumaGetPlayStats)

	huma.Register(api, huma.Operation{
		OperationID: "getRecentGames",
		Method:      http.MethodGet,
		Path:        "/api/user/recent",
		Summary:     "Get recent games",
		Description: "Returns the caller's 20 most recently played games with embedded play history.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, userHandler.HumaGetRecentGames)

	huma.Register(api, huma.Operation{
		OperationID: "getPlayHeatmap",
		Method:      http.MethodGet,
		Path:        "/api/user/play-heatmap",
		Summary:     "Get personal play heatmap",
		Description: "Returns the caller's daily play activity for the last 365 days.",
		Tags:        []string{"user", "stats"},
		Middlewares: mw,
		Security:    sec,
	}, statsHandler.HumaGetPlayHeatmap)

	huma.Register(api, huma.Operation{
		OperationID: "getPublicPlayHeatmap",
		Method:      http.MethodGet,
		Path:        "/api/users/{id}/play-heatmap",
		Summary:     "Get a user's play heatmap",
		Description: "Returns another user's daily play activity for the last 365 days.",
		Tags:        []string{"user", "stats"},
		Middlewares: mw,
		Security:    sec,
	}, statsHandler.HumaGetPublicPlayHeatmap)

	huma.Register(api, huma.Operation{
		OperationID: "reportEmulatorError",
		Method:      http.MethodPost,
		Path:        "/api/emulator/error",
		Summary:     "Report a client-side emulator error",
		Description: "Records a client-facing emulator failure (core download, WASM compile, missing ROM) as an operational system event for admin visibility.",
		Tags:        []string{"system"},
		Middlewares: mw,
		Security:    sec,
	}, systemEventHandler.HumaReportEmulatorError)

	huma.Register(api, huma.Operation{
		OperationID: "getGameKeyMapping",
		Method:      http.MethodGet,
		Path:        "/api/user/games/{gameId}/keymapping",
		Summary:     "Get per-game key mapping",
		Description: "Returns the caller's custom key mapping override for a specific game.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, keyMappingHandler.HumaGetGameKeyMapping)

	huma.Register(api, huma.Operation{
		OperationID: "updateGameKeyMapping",
		Method:      http.MethodPut,
		Path:        "/api/user/games/{gameId}/keymapping",
		Summary:     "Set per-game key mapping",
		Description: "Upserts the caller's custom key mapping for a specific game.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, keyMappingHandler.HumaUpdateGameKeyMapping)

	huma.Register(api, huma.Operation{
		OperationID: "deleteGameKeyMapping",
		Method:      http.MethodDelete,
		Path:        "/api/user/games/{gameId}/keymapping",
		Summary:     "Delete per-game key mapping",
		Description: "Removes the caller's custom key mapping for a specific game, reverting to the console default.",
		Tags:        []string{"user"},
		Middlewares: mw,
		Security:    sec,
	}, keyMappingHandler.HumaDeleteGameKeyMapping)
}

// --- Handlers: StatsHandler heatmap ---

// HumaGetPlayHeatmap is the huma handler for GET /api/user/play-heatmap.
func (h *StatsHandler) HumaGetPlayHeatmap(ctx context.Context, _ *GetPlayHeatmapInput) (*GetPlayHeatmapOutput, error) {
	uid := UserIDFromContext(ctx)
	entries := h.buildHeatmapEntries(uid)
	return &GetPlayHeatmapOutput{Body: entries}, nil
}

// HumaGetPublicPlayHeatmap is the huma handler for GET /api/users/{id}/play-heatmap.
func (h *StatsHandler) HumaGetPublicPlayHeatmap(_ context.Context, in *GetPublicPlayHeatmapInput) (*GetPublicPlayHeatmapOutput, error) {
	var user db.User
	if err := h.DB.First(&user, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}
	entries := h.buildHeatmapEntries(user.ID)
	return &GetPublicPlayHeatmapOutput{Body: entries}, nil
}

// buildHeatmapEntries materialises the heatmap for the last 365 days.
func (h *StatsHandler) buildHeatmapEntries(userID uint) []HeatmapEntry {
	since := time.Now().UTC().Truncate(24*time.Hour).AddDate(0, 0, -365)

	var activities []db.DailyPlayActivity
	h.DB.Where("user_id = ? AND date >= ?", userID, since).
		Order("date ASC").
		Find(&activities)

	entries := make([]HeatmapEntry, 0, len(activities))
	for _, a := range activities {
		entries = append(entries, HeatmapEntry{
			Date:     a.Date.Format("2006-01-02"),
			PlayTime: a.PlayTime,
		})
	}
	return entries
}

// --- Handlers: UserHandler ---

// HumaGetUserStats is the huma handler for GET /api/user/stats.
func (h *UserHandler) HumaGetUserStats(ctx context.Context, _ *GetUserStatsInput) (*GetUserStatsOutput, error) {
	uid := UserIDFromContext(ctx)

	var agg struct {
		TotalPlayTime int64
		GamesPlayed   int64
		LastPlayed    string
	}
	if err := h.DB.Model(&db.PlayHistory{}).
		Where("user_id = ?", uid).
		Select("COALESCE(SUM(play_time), 0) as total_play_time, COUNT(*) as games_played, MAX(last_played) as last_played").
		Scan(&agg).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch user stats")
	}

	if agg.GamesPlayed == 0 {
		return &GetUserStatsOutput{Body: UserStatsResponse{
			TotalPlayTime:      0,
			GamesPlayed:        0,
			CurrentStreak:      0,
			LongestStreak:      0,
			MostPlayedGame:     nil,
			MostPlayedGameTime: 0,
			LastPlayedAt:       nil,
		}}, nil
	}

	var topPH db.PlayHistory
	h.DB.Where("user_id = ?", uid).Order("play_time DESC").First(&topPH)

	var mostPlayedGame *GameResponse
	var mostPlayedGameTime int64
	if topPH.ID != 0 {
		var game db.Game
		if err := h.DB.Preload("Console").First(&game, topPH.GameID).Error; err == nil {
			resp := ToGameResponse(game, h.DB, uid)
			mostPlayedGame = &resp
			mostPlayedGameTime = topPH.PlayTime
		}
	}

	var playDateStrings []string
	h.DB.Model(&db.PlayHistory{}).
		Where("user_id = ?", uid).
		Select("DISTINCT DATE(last_played) as play_date").
		Order("play_date DESC").
		Pluck("play_date", &playDateStrings)

	playDates := make([]time.Time, 0, len(playDateStrings))
	for _, s := range playDateStrings {
		t := parseTimeString(s)
		if !t.IsZero() {
			playDates = append(playDates, t)
		}
	}
	sort.Slice(playDates, func(i, j int) bool { return playDates[i].After(playDates[j]) })

	currentStreak, longestStreak := calculateStreaks(playDates, time.Now())

	var lastPlayedAt *time.Time
	if agg.LastPlayed != "" {
		parsed := parseTimeString(agg.LastPlayed)
		if !parsed.IsZero() {
			lastPlayedAt = &parsed
		}
	}

	return &GetUserStatsOutput{Body: UserStatsResponse{
		TotalPlayTime:      agg.TotalPlayTime,
		GamesPlayed:        agg.GamesPlayed,
		CurrentStreak:      currentStreak,
		LongestStreak:      longestStreak,
		MostPlayedGame:     mostPlayedGame,
		MostPlayedGameTime: mostPlayedGameTime,
		LastPlayedAt:       lastPlayedAt,
	}}, nil
}

// HumaGetPlayStats is the huma handler for GET /api/user/play-stats.
func (h *UserHandler) HumaGetPlayStats(ctx context.Context, _ *GetPlayStatsInput) (*GetPlayStatsOutput, error) {
	uid := UserIDFromContext(ctx)

	var histories []db.PlayHistory
	if err := h.DB.Where("user_id = ?", uid).Find(&histories).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch play stats")
	}

	result := make([]PlayStatsEntry, 0, len(histories))
	for _, ph := range histories {
		if ph.PlayTime <= 0 && ph.LastPlayed.IsZero() {
			continue
		}
		result = append(result, PlayStatsEntry{
			GameID:       ph.GameID,
			PlayTime:     ph.PlayTime,
			LastPlayedAt: ph.LastPlayed.Format(time.RFC3339),
		})
	}
	return &GetPlayStatsOutput{Body: result}, nil
}

// HumaGetRecentGames is the huma handler for GET /api/user/recent.
func (h *UserHandler) HumaGetRecentGames(ctx context.Context, _ *GetRecentGamesInput) (*GetRecentGamesOutput, error) {
	uid := UserIDFromContext(ctx)

	var history []db.PlayHistory
	if err := h.DB.Where("user_id = ?", uid).
		Preload("Game").Preload("Game.Console").
		Order("last_played desc").
		Limit(20).
		Find(&history).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch recent games")
	}

	gameIDs := make([]uint, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID != 0 {
			gameIDs = append(gameIDs, ph.Game.ID)
		}
	}
	data := loadUserGameData(h.DB, uid, gameIDs)

	games := make([]GameResponse, 0, len(history))
	for _, ph := range history {
		if ph.Game.ID == 0 {
			continue
		}
		resp := toGameResponseWithData(ph.Game, &data)
		resp.LastPlayedAt = &ph.LastPlayed
		resp.TotalPlayTime = ph.PlayTime
		games = append(games, resp)
	}
	return &GetRecentGamesOutput{Body: games}, nil
}

// --- Handlers: SystemEventHandler emulator error ---

// HumaReportEmulatorError is the huma handler for POST /api/emulator/error.
func (h *SystemEventHandler) HumaReportEmulatorError(ctx context.Context, in *ReportEmulatorErrorInput) (*ReportEmulatorErrorOutput, error) {
	uid := UserIDFromContext(ctx)
	var uidPtr *uint
	if uid != 0 {
		u := uid
		uidPtr = &u
	}
	uname := UsernameFromContext(ctx)

	metadata := db.EmulatorJSLoadFailedMetadata{
		Error:  in.Body.Error,
		GameID: in.Body.GameID,
		Core:   in.Body.Core,
	}

	db.RecordOperationalEvent(h.DB, db.SystemEventInput{
		EventType: db.SystemEventEmulatorJSLoadFailed,
		Username:  uname,
		UserID:    uidPtr,
		Path:      "/api/emulator/error",
		Metadata:  metadata,
	})

	return &ReportEmulatorErrorOutput{Body: ReportedResponse{Reported: true}}, nil
}
