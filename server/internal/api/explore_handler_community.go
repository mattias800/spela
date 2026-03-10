package api

import (
	"log/slog"
	"math"
	"net/http"
	"sort"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
)

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

