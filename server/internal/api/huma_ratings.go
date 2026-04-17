package api

import (
	"context"
	"fmt"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// CreateOrUpdateRatingInput is the input for POST /api/games/{id}/ratings.
type CreateOrUpdateRatingInput struct {
	ID   string `path:"id" doc:"Game ID."`
	Body CreateOrUpdateRatingRequest
}

// CreateOrUpdateRatingOutput wraps the rating response. The Status int field
// dynamically swaps the HTTP status: 201 for new ratings, 200 for updates —
// matching the raw gin handler wire format.
type CreateOrUpdateRatingOutput struct {
	Status int
	Body   GameRatingResponse
}

// GetRatingsInput is the input for GET /api/games/{id}/ratings. Bounds on
// page/pageSize are enforced in the handler so out-of-range values fall back
// to defaults instead of returning huma's 422 "validation failed".
type GetRatingsInput struct {
	ID       string `path:"id" doc:"Game ID."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// GetRatingsOutput wraps the paginated ratings list.
type GetRatingsOutput struct {
	Body PaginatedResponse[GameRatingResponse]
}

// GetRatingSummaryInput is the input for GET /api/games/{id}/ratings/summary.
type GetRatingSummaryInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GetRatingSummaryOutput wraps the rating-summary response.
type GetRatingSummaryOutput struct {
	Body RatingSummaryResponse
}

// GetMyRatingInput is the input for GET /api/games/{id}/ratings/mine.
type GetMyRatingInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// GetMyRatingOutput wraps the current user's rating response.
type GetMyRatingOutput struct {
	Body GameRatingResponse
}

// DeleteRatingInput is the input for DELETE /api/games/{id}/ratings.
type DeleteRatingInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// DeleteRatingOutput wraps the delete-rating success message.
type DeleteRatingOutput struct {
	Body MessageResponse
}

// RegisterRatingRoutes wires the game-rating endpoints into the huma API.
func RegisterRatingRoutes(api huma.API, h *RatingHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "createOrUpdateGameRating",
		Method:      http.MethodPost,
		Path:        "/api/games/{id}/ratings",
		Summary:     "Create or update the caller's rating for a game",
		Description: "Upserts the authenticated user's rating for the specified game. Returns 201 on create, 200 on update.",
		Tags:        []string{"ratings"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaCreateOrUpdateRating)

	huma.Register(api, huma.Operation{
		OperationID: "listGameRatings",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/ratings",
		Summary:     "List ratings for a game",
		Description: "Returns a paginated list of all user ratings for the specified game, newest first.",
		Tags:        []string{"ratings"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRatings)

	huma.Register(api, huma.Operation{
		OperationID: "getGameRatingSummary",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/ratings/summary",
		Summary:     "Get the aggregated rating summary for a game",
		Description: "Returns the average rating, total count and 1-5 distribution for the specified game.",
		Tags:        []string{"ratings"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetRatingSummary)

	huma.Register(api, huma.Operation{
		OperationID: "getMyGameRating",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/ratings/mine",
		Summary:     "Get the caller's rating for a game",
		Description: "Returns the authenticated user's rating for the specified game, or 404 if none is set.",
		Tags:        []string{"ratings"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetMyRating)

	huma.Register(api, huma.Operation{
		OperationID: "deleteMyGameRating",
		Method:      http.MethodDelete,
		Path:        "/api/games/{id}/ratings",
		Summary:     "Delete the caller's rating for a game",
		Description: "Removes the authenticated user's rating for the specified game.",
		Tags:        []string{"ratings"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteRating)
}

// HumaCreateOrUpdateRating is the huma handler for POST /api/games/{id}/ratings.
func (h *RatingHandler) HumaCreateOrUpdateRating(ctx context.Context, in *CreateOrUpdateRatingInput) (*CreateOrUpdateRatingOutput, error) {
	uid := UserIDFromContext(ctx)

	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	req := in.Body
	if req.Rating < 1 || req.Rating > 5 {
		return nil, huma.Error400BadRequest("invalid request: rating must be between 1 and 5")
	}

	var existing db.GameRating
	result := h.DB.Where("user_id = ? AND game_id = ?", uid, gid).First(&existing)
	if result.Error == gorm.ErrRecordNotFound {
		rating := db.GameRating{
			UserID: uid,
			GameID: uint(gid),
			Rating: req.Rating,
			Review: req.Review,
		}
		if err := h.DB.Create(&rating).Error; err != nil {
			return nil, huma.Error500InternalServerError("failed to create rating")
		}

		CreateActivityEvent(h.DB, h.Hub, uid, "rated_game", uint(gid), RatedGameMetadata{
			Rating: req.Rating,
		})

		return &CreateOrUpdateRatingOutput{
			Status: http.StatusCreated,
			Body:   h.toRatingResponse(rating, uid),
		}, nil
	}

	existing.Rating = req.Rating
	existing.Review = req.Review
	if err := h.DB.Save(&existing).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update rating")
	}
	return &CreateOrUpdateRatingOutput{
		Status: http.StatusOK,
		Body:   h.toRatingResponse(existing, uid),
	}, nil
}

// HumaGetRatings is the huma handler for GET /api/games/{id}/ratings.
func (h *RatingHandler) HumaGetRatings(ctx context.Context, in *GetRatingsInput) (*GetRatingsOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.GameRating{}).Where("game_id = ?", in.ID).Count(&total)

	var ratings []db.GameRating
	if err := h.DB.Where("game_id = ?", in.ID).
		Preload("User").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&ratings).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch ratings")
	}

	uid := UserIDFromContext(ctx)
	result := make([]GameRatingResponse, 0, len(ratings))
	for _, r := range ratings {
		result = append(result, h.toRatingResponse(r, uid))
	}

	return &GetRatingsOutput{
		Body: PaginatedResponse[GameRatingResponse]{
			Data:     result,
			Total:    total,
			Page:     page,
			PageSize: pageSize,
		},
	}, nil
}

// HumaGetRatingSummary is the huma handler for GET /api/games/{id}/ratings/summary.
func (h *RatingHandler) HumaGetRatingSummary(_ context.Context, in *GetRatingSummaryInput) (*GetRatingSummaryOutput, error) {
	var agg struct {
		AverageRating float64
		TotalRatings  int64
	}
	if err := h.DB.Model(&db.GameRating{}).
		Where("game_id = ?", in.ID).
		Select("COALESCE(AVG(rating), 0) as average_rating, COUNT(*) as total_ratings").
		Scan(&agg).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch rating summary")
	}

	type distRow struct {
		Rating int
		Count  int
	}
	var distRows []distRow
	h.DB.Model(&db.GameRating{}).
		Where("game_id = ?", in.ID).
		Select("rating, COUNT(*) as count").
		Group("rating").
		Scan(&distRows)

	distribution := map[string]int{
		"1": 0, "2": 0, "3": 0, "4": 0, "5": 0,
	}
	for _, r := range distRows {
		distribution[fmt.Sprintf("%d", r.Rating)] = r.Count
	}

	return &GetRatingSummaryOutput{Body: RatingSummaryResponse{
		AverageRating: agg.AverageRating,
		TotalRatings:  agg.TotalRatings,
		Distribution:  distribution,
	}}, nil
}

// HumaGetMyRating is the huma handler for GET /api/games/{id}/ratings/mine.
func (h *RatingHandler) HumaGetMyRating(ctx context.Context, in *GetMyRatingInput) (*GetMyRatingOutput, error) {
	uid := UserIDFromContext(ctx)

	var rating db.GameRating
	if err := h.DB.Where("user_id = ? AND game_id = ?", uid, in.ID).
		Preload("User").
		First(&rating).Error; err != nil {
		return nil, huma.Error404NotFound("no rating found")
	}

	return &GetMyRatingOutput{Body: h.toRatingResponse(rating, uid)}, nil
}

// HumaDeleteRating is the huma handler for DELETE /api/games/{id}/ratings.
func (h *RatingHandler) HumaDeleteRating(ctx context.Context, in *DeleteRatingInput) (*DeleteRatingOutput, error) {
	uid := UserIDFromContext(ctx)

	result := h.DB.Where("user_id = ? AND game_id = ?", uid, in.ID).Delete(&db.GameRating{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("no rating found")
	}

	return &DeleteRatingOutput{Body: MessageResponse{Message: "rating deleted"}}, nil
}
