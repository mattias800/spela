package api

import (
	"context"
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetEasyToCompleteInput is the input for GET /api/explore/easy-to-complete.
type GetEasyToCompleteInput struct{}

// GetEasyToCompleteOutput wraps the easy-to-complete response.
type GetEasyToCompleteOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         EasyToCompleteResponse
}

// GetHardestGamesInput is the input for GET /api/explore/hardest-games.
type GetHardestGamesInput struct{}

// GetHardestGamesOutput wraps the hardest-games response.
type GetHardestGamesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         HardestGamesResponse
}

// GetAlmostDoneInput is the input for GET /api/explore/almost-done.
type GetAlmostDoneInput struct{}

// GetAlmostDoneOutput wraps the almost-done response.
type GetAlmostDoneOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         AlmostDoneResponse
}

// GetFreshChallengesInput is the input for GET /api/explore/fresh-challenges.
type GetFreshChallengesInput struct{}

// GetFreshChallengesOutput wraps the fresh-challenges response.
type GetFreshChallengesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         FreshChallengesResponse
}

// GetActiveChallengesInput is the input for GET /api/explore/active-challenges.
type GetActiveChallengesInput struct{}

// GetActiveChallengesOutput wraps the active-challenges response.
type GetActiveChallengesOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ActiveChallengesResponse
}

// RegisterExploreChallengeRoutes wires the achievement / challenge discovery endpoints.
func RegisterExploreChallengeRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getEasyToComplete",
		Method:      http.MethodGet,
		Path:        "/api/explore/easy-to-complete",
		Summary:     "Get games with highest avg achievement completion",
		Description: "Returns up to 20 games with the highest average achievement completion rate across players.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetEasyToComplete)

	huma.Register(api, huma.Operation{
		OperationID: "getHardestGames",
		Method:      http.MethodGet,
		Path:        "/api/explore/hardest-games",
		Summary:     "Get games with lowest avg achievement completion",
		Description: "Returns up to 20 games with the lowest average achievement completion rate. Requires at least 2 players attempting.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetHardestGames)

	huma.Register(api, huma.Operation{
		OperationID: "getAlmostDone",
		Method:      http.MethodGet,
		Path:        "/api/explore/almost-done",
		Summary:     "Get games the caller has nearly completed",
		Description: "Returns games where the caller has completed between 80 and 99 percent of achievements.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetAlmostDone)

	huma.Register(api, huma.Operation{
		OperationID: "getFreshChallenges",
		Method:      http.MethodGet,
		Path:        "/api/explore/fresh-challenges",
		Summary:     "Get games with cached achievements the caller hasn't started",
		Description: "Returns up to 20 games with cached achievements where the caller has zero unlocks yet.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetFreshChallenges)

	huma.Register(api, huma.Operation{
		OperationID: "getActiveChallenges",
		Method:      http.MethodGet,
		Path:        "/api/explore/active-challenges",
		Summary:     "Get active community challenges",
		Description: "Returns the 10 most recent active Challenge entities that have not yet expired.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetActiveChallenges)
}

// --- Handlers ---

// HumaGetEasyToComplete is the huma handler for GET /api/explore/easy-to-complete.
func (h *ExploreHandler) HumaGetEasyToComplete(ctx context.Context, _ *GetEasyToCompleteInput) (*GetEasyToCompleteOutput, error) {
	userID := UserIDFromContext(ctx)

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
		return nil, huma.Error500InternalServerError("failed to fetch easy-to-complete games")
	}

	if len(rows) == 0 {
		return &GetEasyToCompleteOutput{
			CacheControl: "private, max-age=120",
			Body:         EasyToCompleteResponse{Games: []AchievementGameResponse{}},
		}, nil
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load easy-to-complete games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	raGameIDs := make([]uint, len(rows))
	for i, r := range rows {
		raGameIDs[i] = r.RAGameID
	}
	raTotalMap := make(map[uint]int, len(rows))
	for _, r := range rows {
		raTotalMap[r.RAGameID] = r.TotalCount
	}

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

	userData := loadGameResponseData(h.DB, userID, games)

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

	return &GetEasyToCompleteOutput{
		CacheControl: "private, max-age=120",
		Body:         EasyToCompleteResponse{Games: result},
	}, nil
}

// HumaGetHardestGames is the huma handler for GET /api/explore/hardest-games.
func (h *ExploreHandler) HumaGetHardestGames(ctx context.Context, _ *GetHardestGamesInput) (*GetHardestGamesOutput, error) {
	userID := UserIDFromContext(ctx)

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
		return nil, huma.Error500InternalServerError("failed to fetch hardest games")
	}

	if len(rows) == 0 {
		return &GetHardestGamesOutput{
			CacheControl: "private, max-age=300",
			Body:         HardestGamesResponse{Games: []AchievementGameResponse{}},
		}, nil
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
		return nil, huma.Error500InternalServerError("failed to load games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

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

	userData := loadGameResponseData(h.DB, userID, games)

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

	return &GetHardestGamesOutput{
		CacheControl: "private, max-age=300",
		Body:         HardestGamesResponse{Games: result},
	}, nil
}

// HumaGetAlmostDone is the huma handler for GET /api/explore/almost-done.
func (h *ExploreHandler) HumaGetAlmostDone(ctx context.Context, _ *GetAlmostDoneInput) (*GetAlmostDoneOutput, error) {
	userID := UserIDFromContext(ctx)

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
		return nil, huma.Error500InternalServerError("failed to fetch almost-done games")
	}

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
		return &GetAlmostDoneOutput{
			CacheControl: "private, max-age=120",
			Body:         AlmostDoneResponse{Games: []AlmostDoneGame{}},
		}, nil
	}

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
		return nil, huma.Error500InternalServerError("failed to load games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadGameResponseData(h.DB, userID, games)

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

	return &GetAlmostDoneOutput{
		CacheControl: "private, max-age=120",
		Body:         AlmostDoneResponse{Games: result},
	}, nil
}

// HumaGetFreshChallenges is the huma handler for GET /api/explore/fresh-challenges.
func (h *ExploreHandler) HumaGetFreshChallenges(ctx context.Context, _ *GetFreshChallengesInput) (*GetFreshChallengesOutput, error) {
	userID := UserIDFromContext(ctx)

	var startedRAGameIDs []uint
	if err := h.DB.
		Table("user_achievement_progresses").
		Select("DISTINCT ra_game_id").
		Where("user_id = ? AND deleted_at IS NULL", userID).
		Scan(&startedRAGameIDs).Error; err != nil {
		slog.Error("failed to fetch started RA games", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch fresh challenges")
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
		return nil, huma.Error500InternalServerError("failed to fetch fresh challenges")
	}

	if len(rows) == 0 {
		return &GetFreshChallengesOutput{
			CacheControl: "private, max-age=120",
			Body:         FreshChallengesResponse{Games: []FreshChallengeGame{}},
		}, nil
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load fresh challenge games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

	userData := loadGameResponseData(h.DB, userID, games)

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

	return &GetFreshChallengesOutput{
		CacheControl: "private, max-age=120",
		Body:         FreshChallengesResponse{Games: result},
	}, nil
}

// HumaGetActiveChallenges is the huma handler for GET /api/explore/active-challenges.
func (h *ExploreHandler) HumaGetActiveChallenges(_ context.Context, _ *GetActiveChallengesInput) (*GetActiveChallengesOutput, error) {
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
		return nil, huma.Error500InternalServerError("failed to fetch active challenges")
	}

	if len(challenges) == 0 {
		return &GetActiveChallengesOutput{
			CacheControl: "private, max-age=120",
			Body:         ActiveChallengesResponse{Challenges: []ExploreChallengeResponse{}},
		}, nil
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
			CreatorUsername: ch.Creator.Username,
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

	return &GetActiveChallengesOutput{
		CacheControl: "private, max-age=120",
		Body:         ActiveChallengesResponse{Challenges: result},
	}, nil
}
