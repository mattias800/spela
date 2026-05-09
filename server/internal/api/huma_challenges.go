package api

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
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
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
}

// GetChallengeOutput wraps a single challenge response.
type GetChallengeOutput struct {
	Body ChallengeResponse
}

// UpdateChallengeInput is the input for PUT /api/challenges/{id}.
type UpdateChallengeInput struct {
	ID   string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
	Body UpdateChallengeRequest
}

// UpdateChallengeOutput wraps the updated challenge response.
type UpdateChallengeOutput struct {
	Body ChallengeResponse
}

// DeleteChallengeInput is the input for DELETE /api/challenges/{id}.
type DeleteChallengeInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
}

// DeleteChallengeOutput wraps the delete success message.
type DeleteChallengeOutput struct {
	Body MessageResponse
}

// StartChallengeAttemptInput is the input for POST /api/challenges/{id}/attempts/start.
type StartChallengeAttemptInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
}

// StartChallengeAttemptOutput wraps the created attempt response (201 Created).
type StartChallengeAttemptOutput struct {
	Body ChallengeAttemptResponse
}

// CompleteChallengeAttemptInput is the input for POST /api/challenges/{id}/attempts/{aid}/complete.
type CompleteChallengeAttemptInput struct {
	ID  string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
	AID string `path:"aid" pattern:"^[0-9]+$" maxLength:"20" doc:"Attempt ID."`
}

// CompleteChallengeAttemptOutput wraps the completed attempt response.
type CompleteChallengeAttemptOutput struct {
	Body ChallengeAttemptResponse
}

// AbandonChallengeAttemptInput is the input for POST /api/challenges/{id}/attempts/{aid}/abandon.
type AbandonChallengeAttemptInput struct {
	ID  string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
	AID string `path:"aid" pattern:"^[0-9]+$" maxLength:"20" doc:"Attempt ID."`
}

// AbandonChallengeAttemptOutput wraps the abandoned attempt response.
type AbandonChallengeAttemptOutput struct {
	Body ChallengeAttemptResponse
}

// ListMyChallengeAttemptsInput is the input for GET /api/challenges/{id}/attempts/mine.
type ListMyChallengeAttemptsInput struct {
	ID string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
}

// ListMyChallengeAttemptsOutput wraps the attempts list.
type ListMyChallengeAttemptsOutput struct {
	Body []ChallengeAttemptResponse
}

// GetChallengeLeaderboardInput is the input for GET /api/challenges/{id}/leaderboard.
type GetChallengeLeaderboardInput struct {
	ID       string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Challenge ID."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 50, clamped to 1-100)."`
}

// GetChallengeLeaderboardOutput wraps the leaderboard response.
type GetChallengeLeaderboardOutput struct {
	Body PaginatedResponse[ChallengeLeaderboardEntry]
}

// ListGameChallengesInput is the input for GET /api/games/{id}/challenges.
type ListGameChallengesInput struct {
	ID       string `path:"id" pattern:"^[0-9]+$" maxLength:"20" doc:"Game ID."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// ListGameChallengesOutput wraps the paginated game-challenges list.
type ListGameChallengesOutput struct {
	Body PaginatedResponse[ChallengeResponse]
}

// ListMyChallengesInput is the input for GET /api/user/challenges.
type ListMyChallengesInput struct {
	Page     int `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// ListMyChallengesOutput wraps the paginated challenges-by-me list.
type ListMyChallengesOutput struct {
	Body PaginatedResponse[ChallengeResponse]
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

	huma.Register(api, huma.Operation{
		OperationID:   "startChallengeAttempt",
		Method:        http.MethodPost,
		Path:          "/api/challenges/{id}/attempts/start",
		Summary:       "Start a challenge attempt",
		Description:   "Creates a new in-progress attempt for the caller. Any existing in-progress attempt for the same user/challenge is abandoned. Rate-limited per-user/per-challenge.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"challenges"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaStartChallengeAttempt)

	huma.Register(api, huma.Operation{
		OperationID: "completeChallengeAttempt",
		Method:      http.MethodPost,
		Path:        "/api/challenges/{id}/attempts/{aid}/complete",
		Summary:     "Complete a challenge attempt",
		Description: "Marks the attempt as completed and updates best-attempt / leaderboard state. Emits challenge_completed and optionally challenge_record activity events.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaCompleteChallengeAttempt)

	huma.Register(api, huma.Operation{
		OperationID: "abandonChallengeAttempt",
		Method:      http.MethodPost,
		Path:        "/api/challenges/{id}/attempts/{aid}/abandon",
		Summary:     "Abandon a challenge attempt",
		Description: "Marks the specified in-progress attempt as abandoned.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAbandonChallengeAttempt)

	huma.Register(api, huma.Operation{
		OperationID: "listMyChallengeAttempts",
		Method:      http.MethodGet,
		Path:        "/api/challenges/{id}/attempts/mine",
		Summary:     "List the caller's attempts for a challenge",
		Description: "Returns the caller's attempts (any status) for the specified challenge, newest first.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMyChallengeAttempts)

	huma.Register(api, huma.Operation{
		OperationID: "getChallengeLeaderboard",
		Method:      http.MethodGet,
		Path:        "/api/challenges/{id}/leaderboard",
		Summary:     "Get the leaderboard for a challenge",
		Description: "Returns the best attempts across users, ranked fastest-first (or longest-first for survival). Paginated.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetChallengeLeaderboard)

	huma.Register(api, huma.Operation{
		OperationID: "listGameChallenges",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/challenges",
		Summary:     "List challenges for a game",
		Description: "Paginated list of active challenges for the specified game, newest first.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListGameChallenges)

	huma.Register(api, huma.Operation{
		OperationID: "listMyChallenges",
		Method:      http.MethodGet,
		Path:        "/api/user/challenges",
		Summary:     "List challenges created by the caller",
		Description: "Paginated list of challenges authored by the caller, newest first.",
		Tags:        []string{"challenges"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMyChallenges)
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

// HumaStartChallengeAttempt is the huma handler for POST /api/challenges/{id}/attempts/start.
func (h *ChallengeHandler) HumaStartChallengeAttempt(ctx context.Context, in *StartChallengeAttemptInput) (*StartChallengeAttemptOutput, error) {
	uid := UserIDFromContext(ctx)

	cid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid challenge ID")
	}

	var challenge db.Challenge
	if err := h.DB.First(&challenge, cid).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}

	h.lazyExpire(&challenge, time.Now())
	if challenge.Status != ChallengeStatusActive {
		return nil, huma.Error400BadRequest("challenge is not active")
	}

	if h.AttemptRateLimitSeconds > 0 {
		rateLimitKey := fmt.Sprintf("%d:%d", uid, cid)
		h.attemptMu.Lock()
		if lastStart, ok := h.attemptLastStart[rateLimitKey]; ok {
			if time.Since(lastStart) < time.Duration(h.AttemptRateLimitSeconds)*time.Second {
				h.attemptMu.Unlock()
				return nil, huma.Error429TooManyRequests("please wait before starting another attempt")
			}
		}
		h.attemptLastStart[rateLimitKey] = time.Now()

		if len(h.attemptLastStart) > rateLimiterCleanupThreshold {
			cutoff := time.Now().Add(-time.Duration(h.AttemptRateLimitSeconds) * time.Second)
			for k, v := range h.attemptLastStart {
				if v.Before(cutoff) {
					delete(h.attemptLastStart, k)
				}
			}
		}
		h.attemptMu.Unlock()
	}

	h.DB.Model(&db.ChallengeAttempt{}).
		Where("challenge_id = ? AND user_id = ? AND status = ?", cid, uid, AttemptStatusInProgress).
		Update("status", AttemptStatusAbandoned)

	now := time.Now()
	attempt := db.ChallengeAttempt{
		ChallengeID: uint(cid),
		UserID:      uid,
		Status:      AttemptStatusInProgress,
		StartedAt:   now,
	}
	if err := h.DB.Create(&attempt).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to start attempt")
	}

	h.DB.Model(&challenge).UpdateColumn("attempt_count", gorm.Expr("attempt_count + ?", 1))

	h.DB.Preload("User").First(&attempt, attempt.ID)
	return &StartChallengeAttemptOutput{Body: h.toAttemptResponse(attempt)}, nil
}

// HumaCompleteChallengeAttempt is the huma handler for POST /api/challenges/{id}/attempts/{aid}/complete.
func (h *ChallengeHandler) HumaCompleteChallengeAttempt(ctx context.Context, in *CompleteChallengeAttemptInput) (*CompleteChallengeAttemptOutput, error) {
	uid := UserIDFromContext(ctx)

	var attempt db.ChallengeAttempt
	if err := h.DB.Preload("Challenge").First(&attempt, in.AID).Error; err != nil {
		return nil, huma.Error404NotFound("attempt not found")
	}

	if attempt.UserID != uid {
		return nil, huma.Error403Forbidden("not your attempt")
	}
	if strconv.FormatUint(uint64(attempt.ChallengeID), 10) != in.ID {
		return nil, huma.Error400BadRequest("attempt does not belong to this challenge")
	}
	if attempt.Status != AttemptStatusInProgress {
		return nil, huma.Error400BadRequest("attempt is not in progress")
	}

	now := time.Now()
	durationMs := now.Sub(attempt.StartedAt).Milliseconds()

	attempt.Status = AttemptStatusCompleted
	attempt.CompletedAt = &now
	attempt.DurationMs = durationMs

	isSurvival := attempt.Challenge.Type == ChallengeTypeSurvival

	var currentBest db.ChallengeAttempt
	hasPreviousBest := h.DB.Where("challenge_id = ? AND user_id = ? AND is_best = ? AND id != ?",
		attempt.ChallengeID, uid, true, attempt.ID).
		First(&currentBest).Error == nil

	if !hasPreviousBest {
		attempt.IsBest = true
	} else {
		isBetter := durationMs < currentBest.DurationMs
		if isSurvival {
			isBetter = durationMs > currentBest.DurationMs
		}
		if isBetter {
			h.DB.Model(&currentBest).Update("is_best", false)
			attempt.IsBest = true
		}
	}

	if err := h.DB.Save(&attempt).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to complete attempt")
	}

	h.DB.Model(&db.Challenge{}).Where("id = ?", attempt.ChallengeID).
		UpdateColumn("completion_count", gorm.Expr("completion_count + ?", 1))

	h.DB.Preload("User").First(&attempt, attempt.ID)

	var challenge db.Challenge
	h.DB.First(&challenge, attempt.ChallengeID)

	CreateActivityEvent(h.DB, h.Hub, uid, "challenge_completed", challenge.GameID, ChallengeCompletedMetadata{
		ChallengeID:   challenge.ID,
		ChallengeName: challenge.Name,
		DurationMs:    durationMs,
	})

	if attempt.IsBest {
		var betterCount int64
		comparison := "duration_ms < ?"
		if isSurvival {
			comparison = "duration_ms > ?"
		}
		h.DB.Model(&db.ChallengeAttempt{}).
			Where("challenge_id = ? AND is_best = ? AND "+comparison+" AND id != ?",
				attempt.ChallengeID, true, durationMs, attempt.ID).
			Count(&betterCount)

		if betterCount == 0 {
			CreateActivityEvent(h.DB, h.Hub, uid, "challenge_record", challenge.GameID, ChallengeRecordMetadata{
				ChallengeID:   challenge.ID,
				ChallengeName: challenge.Name,
				DurationMs:    durationMs,
			})
		}
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{
			Type: ws.EventChallengeLeaderboardUpdated,
			Payload: ws.ChallengeLeaderboardUpdatedPayload{
				ChallengeID: strconv.FormatUint(uint64(attempt.ChallengeID), 10),
			},
		})
	}

	return &CompleteChallengeAttemptOutput{Body: h.toAttemptResponse(attempt)}, nil
}

// HumaAbandonChallengeAttempt is the huma handler for POST /api/challenges/{id}/attempts/{aid}/abandon.
func (h *ChallengeHandler) HumaAbandonChallengeAttempt(ctx context.Context, in *AbandonChallengeAttemptInput) (*AbandonChallengeAttemptOutput, error) {
	uid := UserIDFromContext(ctx)

	var attempt db.ChallengeAttempt
	if err := h.DB.First(&attempt, in.AID).Error; err != nil {
		return nil, huma.Error404NotFound("attempt not found")
	}

	if attempt.UserID != uid {
		return nil, huma.Error403Forbidden("not your attempt")
	}
	if strconv.FormatUint(uint64(attempt.ChallengeID), 10) != in.ID {
		return nil, huma.Error400BadRequest("attempt does not belong to this challenge")
	}
	if attempt.Status != AttemptStatusInProgress {
		return nil, huma.Error400BadRequest("attempt is not in progress")
	}

	attempt.Status = AttemptStatusAbandoned
	if err := h.DB.Save(&attempt).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to abandon attempt")
	}

	h.DB.Preload("User").First(&attempt, attempt.ID)
	return &AbandonChallengeAttemptOutput{Body: h.toAttemptResponse(attempt)}, nil
}

// HumaListMyChallengeAttempts is the huma handler for GET /api/challenges/{id}/attempts/mine.
func (h *ChallengeHandler) HumaListMyChallengeAttempts(ctx context.Context, in *ListMyChallengeAttemptsInput) (*ListMyChallengeAttemptsOutput, error) {
	uid := UserIDFromContext(ctx)

	var attempts []db.ChallengeAttempt
	if err := h.DB.Where("challenge_id = ? AND user_id = ?", in.ID, uid).
		Preload("User").
		Order("created_at DESC").
		Find(&attempts).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch attempts")
	}

	result := make([]ChallengeAttemptResponse, 0, len(attempts))
	for _, a := range attempts {
		result = append(result, h.toAttemptResponse(a))
	}

	return &ListMyChallengeAttemptsOutput{Body: result}, nil
}

// HumaGetChallengeLeaderboard is the huma handler for GET /api/challenges/{id}/leaderboard.
func (h *ChallengeHandler) HumaGetChallengeLeaderboard(_ context.Context, in *GetChallengeLeaderboardInput) (*GetChallengeLeaderboardOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 50
	}

	var challenge db.Challenge
	if err := h.DB.First(&challenge, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("challenge not found")
	}

	var total int64
	h.DB.Model(&db.ChallengeAttempt{}).
		Where("challenge_id = ? AND is_best = ?", in.ID, true).
		Count(&total)

	orderClause := "duration_ms ASC, completed_at ASC"
	if challenge.Type == ChallengeTypeSurvival {
		orderClause = "duration_ms DESC, completed_at ASC"
	}

	var attempts []db.ChallengeAttempt
	if err := h.DB.Where("challenge_id = ? AND is_best = ?", in.ID, true).
		Preload("User").
		Order(orderClause).
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&attempts).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch leaderboard")
	}

	startRank := (page-1)*pageSize + 1
	result := make([]ChallengeLeaderboardEntry, 0, len(attempts))
	for i, a := range attempts {
		var completedAt time.Time
		if a.CompletedAt != nil {
			completedAt = *a.CompletedAt
		}
		result = append(result, ChallengeLeaderboardEntry{
			Rank:        startRank + i,
			UserID:      strconv.FormatUint(uint64(a.UserID), 10),
			Username:    a.User.Username,
			AvatarURL:   a.User.AvatarURL,
			DurationMs:  a.DurationMs,
			AttemptID:   strconv.FormatUint(uint64(a.ID), 10),
			CompletedAt: completedAt,
		})
	}

	return &GetChallengeLeaderboardOutput{Body: PaginatedResponse[ChallengeLeaderboardEntry]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaListGameChallenges is the huma handler for GET /api/games/{id}/challenges.
func (h *ChallengeHandler) HumaListGameChallenges(_ context.Context, in *ListGameChallengesInput) (*ListGameChallengesOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Challenge{}).Where("game_id = ? AND status = ?", in.ID, ChallengeStatusActive).Count(&total)

	var challenges []db.Challenge
	if err := h.DB.Where("game_id = ? AND status = ?", in.ID, ChallengeStatusActive).
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
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

	return &ListGameChallengesOutput{Body: PaginatedResponse[ChallengeResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaListMyChallenges is the huma handler for GET /api/user/challenges.
func (h *ChallengeHandler) HumaListMyChallenges(ctx context.Context, in *ListMyChallengesInput) (*ListMyChallengesOutput, error) {
	uid := UserIDFromContext(ctx)

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.Challenge{}).Where("creator_id = ?", uid).Count(&total)

	var challenges []db.Challenge
	if err := h.DB.Where("creator_id = ?", uid).
		Preload("Creator").Preload("Game").Preload("Game.Console").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&challenges).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch challenges")
	}

	result := make([]ChallengeResponse, 0, len(challenges))
	for _, ch := range challenges {
		result = append(result, h.toChallengeResponse(ch))
	}

	return &ListMyChallengesOutput{Body: PaginatedResponse[ChallengeResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}
