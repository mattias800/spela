package api

import (
	"log/slog"
	"math"
	"net/http"
	"sort"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch easy-to-complete games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch hardest games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch almost-done games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch fresh challenges"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch fresh challenges"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to load games"})
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch active challenges"})
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
