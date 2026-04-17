package api

import (
	"context"
	"log/slog"
	"net/http"
	"strconv"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// GetShowcaseInput is the input for GET /api/user/achievements/showcase.
type GetShowcaseInput struct{}

// GetShowcaseOutput wraps the showcase response.
type GetShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// UpdateShowcaseInput is the input for PUT /api/user/achievements/showcase.
type UpdateShowcaseInput struct {
	Body []ShowcaseEntryInput
}

// UpdateShowcaseOutput wraps the updated showcase response.
type UpdateShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// GetPublicShowcaseInput is the input for GET /api/users/{id}/achievements/showcase.
type GetPublicShowcaseInput struct {
	ID string `path:"id" doc:"User ID."`
}

// GetPublicShowcaseOutput wraps the public showcase response.
type GetPublicShowcaseOutput struct {
	Body []ShowcaseEntryResponse
}

// RegisterAchievementShowcaseRoutes wires achievement showcase endpoints into the huma API.
func RegisterAchievementShowcaseRoutes(api huma.API, h *AchievementShowcaseHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "getAchievementShowcase",
		Method:      http.MethodGet,
		Path:        "/api/user/achievements/showcase",
		Summary:     "Get achievement showcase",
		Description: "Returns the caller's showcased achievements in display order.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetShowcase)

	huma.Register(api, huma.Operation{
		OperationID: "updateAchievementShowcase",
		Method:      http.MethodPut,
		Path:        "/api/user/achievements/showcase",
		Summary:     "Replace the achievement showcase",
		Description: "Replaces the caller's showcased achievements. Maximum 5 entries.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateShowcase)

	huma.Register(api, huma.Operation{
		OperationID: "getPublicAchievementShowcase",
		Method:      http.MethodGet,
		Path:        "/api/users/{id}/achievements/showcase",
		Summary:     "Get a user's achievement showcase",
		Description: "Returns another user's showcased achievements.",
		Tags:        []string{"retroachievements"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPublicShowcase)
}

// --- Handlers ---

// HumaGetShowcase is the huma handler for GET /api/user/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaGetShowcase(ctx context.Context, _ *GetShowcaseInput) (*GetShowcaseOutput, error) {
	uid := UserIDFromContext(ctx)

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load achievement showcase", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to load showcase")
	}
	return &GetShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}

// HumaGetPublicShowcase is the huma handler for GET /api/users/{id}/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaGetPublicShowcase(_ context.Context, in *GetPublicShowcaseInput) (*GetPublicShowcaseOutput, error) {
	userID, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}

	var entries []db.UserAchievementShowcase
	if err := h.DB.Where("user_id = ?", userID).Order("showcase_order ASC").Find(&entries).Error; err != nil {
		slog.Error("failed to load public achievement showcase", "user_id", userID, "error", err)
		return nil, huma.Error500InternalServerError("failed to load showcase")
	}
	return &GetPublicShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}

// HumaUpdateShowcase is the huma handler for PUT /api/user/achievements/showcase.
func (h *AchievementShowcaseHandler) HumaUpdateShowcase(ctx context.Context, in *UpdateShowcaseInput) (*UpdateShowcaseOutput, error) {
	uid := UserIDFromContext(ctx)
	req := in.Body

	if len(req) > maxShowcaseEntries {
		return nil, huma.Error400BadRequest("maximum 5 showcase entries allowed")
	}

	if err := h.DB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("user_id = ?", uid).Delete(&db.UserAchievementShowcase{}).Error; err != nil {
			return err
		}
		for i, item := range req {
			entry := db.UserAchievementShowcase{
				UserID:          uid,
				AchievementRAID: item.AchievementRAID,
				RAGameID:        item.RAGameID,
				ShowcaseOrder:   i,
			}
			if err := tx.Create(&entry).Error; err != nil {
				return err
			}
		}
		return nil
	}); err != nil {
		slog.Error("failed to update achievement showcase", "user_id", uid, "error", err)
		return nil, huma.Error500InternalServerError("failed to update showcase")
	}

	var entries []db.UserAchievementShowcase
	h.DB.Where("user_id = ?", uid).Order("showcase_order ASC").Find(&entries)
	return &UpdateShowcaseOutput{Body: h.enrichShowcaseEntries(entries)}, nil
}
