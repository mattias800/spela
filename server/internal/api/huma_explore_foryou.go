package api

import (
	"context"
	"log/slog"
	"net/http"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Inputs / outputs ---

// GetForYouInput is the input for GET /api/explore/for-you.
type GetForYouInput struct{}

// GetForYouOutput wraps the personalized recommendation rows.
type GetForYouOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         ForYouResponse
}

// GetPlayersLikeYouInput is the input for GET /api/explore/players-like-you.
type GetPlayersLikeYouInput struct{}

// GetPlayersLikeYouOutput wraps the collaborative-filter recommendations.
type GetPlayersLikeYouOutput struct {
	CacheControl string `header:"Cache-Control"`
	Body         PlayersLikeYouResponse
}

// RegisterExploreForYouRoutes wires the personalized recommendation endpoints.
func RegisterExploreForYouRoutes(api huma.API, h *ExploreHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getForYou",
		Method:      http.MethodGet,
		Path:        "/api/explore/for-you",
		Summary:     "Get personalized recommendation rows",
		Description: "Returns personalized recommendation shelves derived from the caller's play history.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetForYou)

	huma.Register(api, huma.Operation{
		OperationID: "getPlayersLikeYou",
		Method:      http.MethodGet,
		Path:        "/api/explore/players-like-you",
		Summary:     "Get collaborative-filter game recommendations",
		Description: "Returns games favourited by users whose favourites overlap with the caller's. Uses Jaccard similarity.",
		Tags:        []string{"explore"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPlayersLikeYou)
}

// --- Handlers ---

// HumaGetForYou is the huma handler for GET /api/explore/for-you.
func (h *ExploreHandler) HumaGetForYou(ctx context.Context, _ *GetForYouInput) (*GetForYouOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	rows := []ForYouRowResponse{}

	becauseRows, err := h.buildBecauseYouPlayedRows(userID)
	if err != nil {
		slog.Error("failed to build because-you-played rows", "error", err)
		return nil, huma.Error500InternalServerError("failed to build recommendations")
	}
	rows = append(rows, becauseRows...)

	moreGenreRow, err := h.buildMoreGenreRow(userID)
	if err != nil {
		slog.Error("failed to build more-genre row", "error", err)
		return nil, huma.Error500InternalServerError("failed to build recommendations")
	}
	if moreGenreRow != nil {
		rows = append(rows, *moreGenreRow)
	}

	unfinishedRow, err := h.buildUnfinishedRow(userID)
	if err != nil {
		slog.Error("failed to build unfinished row", "error", err)
		return nil, huma.Error500InternalServerError("failed to build recommendations")
	}
	if unfinishedRow != nil {
		rows = append(rows, *unfinishedRow)
	}

	expandRow, err := h.buildExpandHorizonsRow(userID)
	if err != nil {
		slog.Error("failed to build expand-horizons row", "error", err)
		return nil, huma.Error500InternalServerError("failed to build recommendations")
	}
	if expandRow != nil {
		rows = append(rows, *expandRow)
	}

	return &GetForYouOutput{
		CacheControl: "private, max-age=120",
		Body:         ForYouResponse{Rows: rows},
	}, nil
}

// HumaGetPlayersLikeYou is the huma handler for GET /api/explore/players-like-you.
func (h *ExploreHandler) HumaGetPlayersLikeYou(ctx context.Context, _ *GetPlayersLikeYouInput) (*GetPlayersLikeYouOutput, error) {
	userID := UserIDFromContext(ctx)
	if userID == 0 {
		return nil, huma.Error401Unauthorized("authentication required")
	}

	var myFavIDs []uint
	if err := h.DB.Model(&db.Favorite{}).
		Select("game_id").
		Where("user_id = ?", userID).
		Pluck("game_id", &myFavIDs).Error; err != nil {
		slog.Error("failed to get user favorites", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	if len(myFavIDs) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: 0,
			},
		}, nil
	}

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
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	if len(overlaps) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: 0,
			},
		}, nil
	}

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
		return nil, huma.Error500InternalServerError("failed to get recommendations")
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

	for i := 0; i < len(similarUsers); i++ {
		for j := i + 1; j < len(similarUsers); j++ {
			if similarUsers[j].similarity > similarUsers[i].similarity {
				similarUsers[i], similarUsers[j] = similarUsers[j], similarUsers[i]
			}
		}
	}

	if len(similarUsers) > 5 {
		similarUsers = similarUsers[:5]
	}

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
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

	if len(recGameRows) == 0 {
		return &GetPlayersLikeYouOutput{
			CacheControl: "private, max-age=120",
			Body: PlayersLikeYouResponse{
				Games:             []GameResponse{},
				SimilarUsersCount: len(similarUsers),
			},
		}, nil
	}

	gameIDs := make([]uint, len(recGameRows))
	for i, r := range recGameRows {
		gameIDs[i] = r.GameID
	}

	var games []db.Game
	if err := h.DB.Preload("Console").Where("id IN ? AND is_primary = true", gameIDs).Find(&games).Error; err != nil {
		slog.Error("failed to load recommended games", "error", err)
		return nil, huma.Error500InternalServerError("failed to get recommendations")
	}

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

	return &GetPlayersLikeYouOutput{
		CacheControl: "private, max-age=120",
		Body: PlayersLikeYouResponse{
			Games:             ToGameResponses(sorted, h.DB, userID),
			SimilarUsersCount: len(similarUsers),
		},
	}, nil
}

