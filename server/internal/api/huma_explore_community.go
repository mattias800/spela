package api

import (
	"context"
	"log/slog"
	"math"
	"net/http"
	"sort"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetTrendingInput is the input for GET /api/explore/trending.
type GetTrendingInput struct{}

// GetTrendingOutput wraps the trending response.
type GetTrendingOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         TrendingResponse
}

// GetCommunityTopInput is the input for GET /api/explore/community-top.
type GetCommunityTopInput struct{}

// GetCommunityTopOutput wraps the community-top response.
type GetCommunityTopOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         CommunityTopResponse
}

// GetCultClassicsInput is the input for GET /api/explore/cult-classics.
type GetCultClassicsInput struct{}

// GetCultClassicsOutput wraps the cult classics response.
type GetCultClassicsOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         CultClassicsResponse
}

// GetRecentlyReviewedInput is the input for GET /api/explore/recently-reviewed.
type GetRecentlyReviewedInput struct{}

// GetRecentlyReviewedOutput wraps the recently-reviewed response.
type GetRecentlyReviewedOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         RecentlyReviewedResponse
}

// GetActiveNowInput is the input for GET /api/explore/active-now.
type GetActiveNowInput struct{}

// GetActiveNowOutput wraps the active-now response.
type GetActiveNowOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ActiveNowResponse
}

// RegisterExploreCommunityRoutes wires the social / community discovery endpoints.
func RegisterExploreCommunityRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getTrending",
		Method:      http.MethodGet,
		Path:        "/api/explore/trending",
		Summary:     "Get trending games",
		Description: "Returns up to 20 games with the most distinct players in the last 7 days.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetTrending)

	huma.Register(api, huma.Operation{
		OperationID: "getCommunityTop",
		Method:      http.MethodGet,
		Path:        "/api/explore/community-top",
		Summary:     "Get top-rated games by the community",
		Description: "Returns games with the highest average user ratings on this server (at least 2 ratings).",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCommunityTop)

	huma.Register(api, huma.Operation{
		OperationID: "getCultClassics",
		Method:      http.MethodGet,
		Path:        "/api/explore/cult-classics",
		Summary:     "Get community cult classics",
		Description: "Returns games where the community rates higher than critics — avg community rating >= 4.0 with IGDB rating < 75.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetCultClassics)

	huma.Register(api, huma.Operation{
		OperationID: "getRecentlyReviewed",
		Method:      http.MethodGet,
		Path:        "/api/explore/recently-reviewed",
		Summary:     "Get games with recent user reviews",
		Description: "Returns the 20 most recent user reviews (non-empty review text) with game + reviewer metadata.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRecentlyReviewed)

	huma.Register(api, huma.Operation{
		OperationID: "getActiveNow",
		Method:      http.MethodGet,
		Path:        "/api/explore/active-now",
		Summary:     "Get games with active shared sessions or challenges",
		Description: "Returns up to 20 games currently with active shared sessions and/or active challenges, sorted by combined activity.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetActiveNow)
}

// --- Handlers ---

// HumaGetTrending is the huma handler for GET /api/explore/trending.
func (h *ExploreHandler) HumaGetTrending(ctx context.Context, _ *GetTrendingInput) (*GetTrendingOutput, error) {
	userID := UserIDFromContext(ctx)
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
		return nil, huma.Error500InternalServerError("failed to fetch trending games")
	}

	if len(rows) == 0 {
		return &GetTrendingOutput{
			CacheControl: "private, max-age=30",
			Body:         TrendingResponse{Games: []TrendingGameResponse{}},
		}, nil
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load trending games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load trending games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
	}

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

	return &GetTrendingOutput{
		CacheControl: "private, max-age=30",
		Body:         TrendingResponse{Games: result},
	}, nil
}

// HumaGetCommunityTop is the huma handler for GET /api/explore/community-top.
func (h *ExploreHandler) HumaGetCommunityTop(ctx context.Context, _ *GetCommunityTopInput) (*GetCommunityTopOutput, error) {
	userID := UserIDFromContext(ctx)

	type communityRow struct {
		GameID      uint
		AvgRating   float64
		RatingCount int
	}
	var rows []communityRow
	if err := h.DB.
		Table("game_ratings").
		Select("game_ratings.game_id, AVG(game_ratings.rating) as avg_rating, COUNT(*) as rating_count").
		Joins("JOIN games ON games.id = game_ratings.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Where("game_ratings.deleted_at IS NULL").
		Group("game_ratings.game_id").
		Having("rating_count >= 2").
		Order("avg_rating DESC, rating_count DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch community top", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch community top games")
	}

	if len(rows) == 0 {
		return &GetCommunityTopOutput{
			CacheControl: "private, max-age=120",
			Body:         CommunityTopResponse{Games: []CommunityTopGame{}},
		}, nil
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load community top games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load community top games")
	}

	gameMap := make(map[uint]db.Game, len(games))
	for _, g := range games {
		gameMap[g.ID] = g
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

	return &GetCommunityTopOutput{
		CacheControl: "private, max-age=120",
		Body:         CommunityTopResponse{Games: result},
	}, nil
}

// HumaGetCultClassics is the huma handler for GET /api/explore/cult-classics.
func (h *ExploreHandler) HumaGetCultClassics(ctx context.Context, _ *GetCultClassicsInput) (*GetCultClassicsOutput, error) {
	userID := UserIDFromContext(ctx)

	type cultRow struct {
		GameID          uint
		CommunityRating float64
		RatingCount     int
	}
	var rows []cultRow
	if err := h.DB.
		Table("game_ratings").
		Select("game_ratings.game_id, AVG(game_ratings.rating) as community_rating, COUNT(*) as rating_count").
		Joins("JOIN games ON games.id = game_ratings.game_id AND games.deleted_at IS NULL AND games.is_primary = true").
		Where("game_ratings.deleted_at IS NULL AND games.rating < 75").
		Group("game_ratings.game_id").
		Having("rating_count >= 2 AND community_rating >= 4.0").
		Order("community_rating DESC, rating_count DESC").
		Limit(20).
		Scan(&rows).Error; err != nil {
		slog.Error("failed to fetch cult classics", "error", err)
		return nil, huma.Error500InternalServerError("failed to fetch cult classics")
	}

	if len(rows) == 0 {
		return &GetCultClassicsOutput{
			CacheControl: "private, max-age=300",
			Body:         CultClassicsResponse{Games: []CultClassicGame{}},
		}, nil
	}

	gameIDs := make([]uint, len(rows))
	for i, r := range rows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load cult classic games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load cult classic games")
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
			Game:              toGameResponseWithData(g, &userData),
			CommunityRating:   math.Round(r.CommunityRating*100) / 100,
			IGDBCriticsRating: g.IGDBCriticsRating,
			RatingCount:       r.RatingCount,
		})
	}

	return &GetCultClassicsOutput{
		CacheControl: "private, max-age=300",
		Body:         CultClassicsResponse{Games: result},
	}, nil
}

// HumaGetRecentlyReviewed is the huma handler for GET /api/explore/recently-reviewed.
func (h *ExploreHandler) HumaGetRecentlyReviewed(ctx context.Context, _ *GetRecentlyReviewedInput) (*GetRecentlyReviewedOutput, error) {
	userID := UserIDFromContext(ctx)

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
		return nil, huma.Error500InternalServerError("failed to fetch recently reviewed")
	}

	if len(rows) == 0 {
		return &GetRecentlyReviewedOutput{
			CacheControl: "private, max-age=30",
			Body:         RecentlyReviewedResponse{Reviews: []RecentReviewItem{}},
		}, nil
	}

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
		return nil, huma.Error500InternalServerError("failed to load reviewed games")
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

	return &GetRecentlyReviewedOutput{
		CacheControl: "private, max-age=30",
		Body:         RecentlyReviewedResponse{Reviews: result},
	}, nil
}

// HumaGetActiveNow is the huma handler for GET /api/explore/active-now.
func (h *ExploreHandler) HumaGetActiveNow(ctx context.Context, _ *GetActiveNowInput) (*GetActiveNowOutput, error) {
	userID := UserIDFromContext(ctx)

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
		return nil, huma.Error500InternalServerError("failed to fetch active games")
	}

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
		return nil, huma.Error500InternalServerError("failed to fetch active games")
	}

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
		return &GetActiveNowOutput{
			CacheControl: "private, max-age=30",
			Body:         ActiveNowResponse{Games: []ActiveNowItem{}},
		}, nil
	}

	gameIDs := make([]uint, 0, len(activeMap))
	for id := range activeMap {
		gameIDs = append(gameIDs, id)
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ?", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load active games", "error", err)
		return nil, huma.Error500InternalServerError("failed to load active games")
	}

	userData := loadUserGameData(h.DB, userID, gameIDs)

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

	return &GetActiveNowOutput{
		CacheControl: "private, max-age=30",
		Body:         ActiveNowResponse{Games: result},
	}, nil
}
