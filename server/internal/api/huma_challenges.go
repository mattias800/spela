package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// --- Input / output types ---

// ListChallengesInput is the input for GET /api/challenges.
type ListChallengesInput struct {
	Page       int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize   int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
	GameID     string `query:"gameId" doc:"Filter by game ID."`
	ConsoleID  string `query:"consoleId" doc:"Filter by console ID (JOINs on games)."`
	Difficulty string `query:"difficulty" doc:"Filter by difficulty (easy/medium/hard)."`
	Type       string `query:"type" doc:"Filter by type (completion/speedrun/survival)."`
	Status     string `query:"status" doc:"Filter by status (active/closed/expired). Defaults to active."`
	CreatorID  string `query:"creatorId" doc:"Filter by creator user ID."`
	Sort       string `query:"sort" doc:"Sort order (newest/popular/oldest)."`
}

// ListChallengesOutput wraps the paginated challenges list.
type ListChallengesOutput struct {
	Body PaginatedResponse[ChallengeResponse]
}

// GetChallengeInput is the input for GET /api/challenges/{id}.
type GetChallengeInput struct {
	ID string `path:"id" doc:"Challenge ID."`
}

// GetChallengeOutput wraps a single challenge response.
type GetChallengeOutput struct {
	Body ChallengeResponse
}

// UpdateChallengeInput is the input for PUT /api/challenges/{id}.
type UpdateChallengeInput struct {
	ID   string `path:"id" doc:"Challenge ID."`
	Body UpdateChallengeRequest
}

// UpdateChallengeOutput wraps the updated challenge response.
type UpdateChallengeOutput struct {
	Body ChallengeResponse
}

// DeleteChallengeInput is the input for DELETE /api/challenges/{id}.
type DeleteChallengeInput struct {
	ID string `path:"id" doc:"Challenge ID."`
}

// DeleteChallengeOutput wraps the delete success message.
type DeleteChallengeOutput struct {
	Body MessageResponse
}

// RegisterChallengeRoutes wires the challenge CRUD endpoints into the huma API.
// Creation stays on raw gin (multipart upload) as do the attempt + download
// endpoints.
func RegisterChallengeRoutes(api huma.API, h *ChallengeHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listChallenges",
		Method:      http.MethodGet,
		Path:        "/api/challenges",
		Summary:     "List challenges",
		Description: "Paginated list of challenges with gameId/consoleId/difficulty/type/status/creatorId filters and newest/popular/oldest sort.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListChallenges)

	huma.Register(api, huma.Operation{
		OperationID: "getChallenge",
		Method:      http.MethodGet,
		Path:        "/api/challenges/{id}",
		Summary:     "Get a challenge by ID",
		Description: "Returns a single challenge including creator, game and console metadata. Expired challenges are lazily closed on read.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetChallenge)

	huma.Register(api, huma.Operation{
		OperationID: "updateChallenge",
		Method:      http.MethodPut,
		Path:        "/api/challenges/{id}",
		Summary:     "Update a challenge",
		Description: "Updates name/description/status on a challenge. Allowed for the creator or an admin/owner.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateChallenge)

	huma.Register(api, huma.Operation{
		OperationID: "deleteChallenge",
		Method:      http.MethodDelete,
		Path:        "/api/challenges/{id}",
		Summary:     "Delete a challenge",
		Description: "Deletes a challenge and all its attempts and stored save/screenshot files. Allowed for the creator or an admin/owner.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteChallenge)
}

// --- Handlers ---

// HumaListChallenges is the huma handler for GET /api/challenges.
func (h *ChallengeHandler) HumaListChallenges(_ context.Context, in *ListChallengesInput) (*ListChallengesOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	query := h.DB.Model(&db.Challenge{})

	if in.GameID != "" {
		query = query.Where("game_id = ?", in.GameID)
	}
	if in.ConsoleID != "" {
		query = query.Joins("JOIN games ON games.id = challenges.game_id").
			Where("games.console_id = ?", in.ConsoleID)
	}
	if in.Difficulty != "" {
		query = query.Where("challenges.difficulty = ?", in.Difficulty)
	}
	if in.Type != "" {
		query = query.Where("challenges.type = ?", in.Type)
	}
	if in.Status != "" {
		query = query.Where("challenges.status = ?", in.Status)
	} else {
		query = query.Where("challenges.status = ?", ChallengeStatusActive)
	}
	if in.CreatorID != "" {
		query = query.Where("challenges.creator_id = ?", in.CreatorID)
	}

	var total int64
	query.Count(&total)

	sort := in.Sort
	if sort == "" {
		sort = "newest"
	}
	switch sort {
	case "popular":
		query = query.Order("challenges.attempt_count DESC, challenges.created_at DESC")
	case "oldest":
		query = query.Order("challenges.created_at ASC")
	default:
		query = query.Order("challenges.created_at DESC")
	}

	var challenges []db.Challenge
	if err := query.
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&challenges).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch challenges")
	}

	now := time.Now()
	for i := range challenges {
		h.lazyExpire(&challenges[i], now)
	}

	result := make([]ChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		result = append(result, h.toChallengeResponse(ch))
	}

	return &ListChallengesOutput{Body: PaginatedResponse[ChallengeResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaGetChallenge is the huma handler for GET /api/challenges/{id}.
func (h *ChallengeHandler) HumaGetChallenge(_ context.Context, in *GetChallengeInput) (*GetChallengeOutput, error) {
	var challenge db.Challenge
	if err := h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").
		First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}
	h.lazyExpire(&challenge, time.Now())
	return &GetChallengeOutput{Body: h.toChallengeResponse(challenge)}, nil
}

// HumaUpdateChallenge is the huma handler for PUT /api/challenges/{id}.
func (h *ChallengeHandler) HumaUpdateChallenge(ctx context.Context, in *UpdateChallengeInput) (*UpdateChallengeOutput, error) {
	uid := UserIDFromContext(ctx)
	role := UserRoleFromContext(ctx)

	var challenge db.Challenge
	if err := h.DB.First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}

	if challenge.CreatorID != uid && !db.IsAdminOrOwner(role) {
		return nil, huma.Error403Forbidden("not authorized to update this challenge")
	}

	req := in.Body
	if req.Name != nil {
		name := strings.TrimSpace(*req.Name)
		if name == "" {
			return nil, huma.Error400BadRequest("name cannot be empty")
		}
		if len(name) > maxChallengeNameLength {
			return nil, huma.Error400BadRequest(fmt.Sprintf("name must be %d characters or fewer", maxChallengeNameLength))
		}
		challenge.Name = name
	}
	if req.Description != nil {
		if len(*req.Description) > 2048 {
			return nil, huma.Error400BadRequest("description must be 2048 characters or fewer")
		}
		challenge.Description = *req.Description
	}
	if req.Status != nil {
		s := *req.Status
		if s != ChallengeStatusActive && s != ChallengeStatusClosed {
			return nil, huma.Error400BadRequest("status must be active or closed")
		}
		challenge.Status = s
	}

	if err := h.DB.Save(&challenge).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update challenge")
	}

	h.DB.Preload("Creator").Preload("Game").Preload("Game.Console").First(&challenge, challenge.ID)
	return &UpdateChallengeOutput{Body: h.toChallengeResponse(challenge)}, nil
}

// HumaDeleteChallenge is the huma handler for DELETE /api/challenges/{id}.
func (h *ChallengeHandler) HumaDeleteChallenge(ctx context.Context, in *DeleteChallengeInput) (*DeleteChallengeOutput, error) {
	uid := UserIDFromContext(ctx)
	role := UserRoleFromContext(ctx)

	var challenge db.Challenge
	if err := h.DB.First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}

	if challenge.CreatorID != uid && !db.IsAdminOrOwner(role) {
		return nil, huma.Error403Forbidden("not authorized to delete this challenge")
	}

	if err := h.DB.Where("challenge_id = ?", challenge.ID).Delete(&db.ChallengeAttempt{}).Error; err != nil {
		slog.Error("failed to delete challenge attempts", "challengeId", challenge.ID, "error", err)
	}

	if err := h.Storage.DeleteChallengeSave(challenge.ID); err != nil {
		slog.Error("failed to delete challenge save files", "challengeId", challenge.ID, "error", err)
	}

	h.DB.Delete(&challenge)
	return &DeleteChallengeOutput{Body: MessageResponse{Message: "challenge deleted"}}, nil
}
