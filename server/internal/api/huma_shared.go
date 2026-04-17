package api

import (
	"context"
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

// ListSharedSavesInput is the input for GET /api/games/{id}/shared-saves.
type ListSharedSavesInput struct {
	ID       string `path:"id" doc:"Game ID."`
	Page     int    `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int    `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// ListSharedSavesOutput wraps the paginated shared-saves list.
type ListSharedSavesOutput struct {
	Body PaginatedResponse[SharedSaveResponse]
}

// DeleteSharedSaveInput is the input for DELETE /api/games/{id}/shared-saves/{saveId}.
type DeleteSharedSaveInput struct {
	ID     string `path:"id" doc:"Game ID."`
	SaveID string `path:"saveId" doc:"Shared save ID."`
}

// DeleteSharedSaveOutput wraps the delete success message.
type DeleteSharedSaveOutput struct {
	Body MessageResponse
}

// CreateSharedSessionInput is the input for POST /api/shared-sessions.
type CreateSharedSessionInput struct {
	Body CreateSharedSessionRequest
}

// CreateSharedSessionOutput wraps the created shared session response (201 Created).
type CreateSharedSessionOutput struct {
	Body SharedSessionDetailResponse
}

// GetSharedSessionInput is the input for GET /api/shared-sessions/{id}.
type GetSharedSessionInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// GetSharedSessionOutput wraps the shared session detail response.
type GetSharedSessionOutput struct {
	Body SharedSessionDetailResponse
}

// UpdateSharedSessionInput is the input for PUT /api/shared-sessions/{id}.
type UpdateSharedSessionInput struct {
	ID   string `path:"id" doc:"Shared session ID."`
	Body UpdateSharedSessionRequest
}

// UpdateSharedSessionOutput wraps the updated shared session response.
type UpdateSharedSessionOutput struct {
	Body SharedSessionResponse
}

// DeleteSharedSessionInput is the input for DELETE /api/shared-sessions/{id}.
type DeleteSharedSessionInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// DeleteSharedSessionOutput wraps the delete success message.
type DeleteSharedSessionOutput struct {
	Body MessageResponse
}

// RegisterSharedSaveRoutes wires the community shared-save endpoints into the
// huma API. Upload / download (file I/O) endpoints stay on raw gin.
func RegisterSharedSaveRoutes(api huma.API, h *SharedSaveHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID: "listSharedSaves",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/shared-saves",
		Summary:     "List community shared saves for a game",
		Description: "Returns a paginated list of community-shared save states for the specified game, newest first.",
		Tags:        []string{"shared-saves"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSharedSaves)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSharedSave",
		Method:      http.MethodDelete,
		Path:        "/api/games/{id}/shared-saves/{saveId}",
		Summary:     "Delete a community shared save",
		Description: "Removes a shared save state. Allowed for the owner or an admin/owner role.",
		Tags:        []string{"shared-saves"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteSharedSave)
}

// RegisterSharedSessionRoutes wires the shared-session CRUD endpoints into the
// huma API. Invite flow, turn control, heartbeat and file-based endpoints stay
// on raw gin in this batch.
func RegisterSharedSessionRoutes(api huma.API, h *SharedSessionHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "createSharedSession",
		Method:        http.MethodPost,
		Path:          "/api/shared-sessions",
		Summary:       "Create a shared session",
		Description:   "Creates a new shared session backed by a GameSession, auto-adds the caller as owner member, and broadcasts a shared-session-created event.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"shared-sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateSharedSession)

	huma.Register(api, huma.Operation{
		OperationID: "getSharedSession",
		Method:      http.MethodGet,
		Path:        "/api/shared-sessions/{id}",
		Summary:     "Get a shared session by ID",
		Description: "Returns shared-session details including member list. Requires membership.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetSharedSession)

	huma.Register(api, huma.Operation{
		OperationID: "updateSharedSession",
		Method:      http.MethodPut,
		Path:        "/api/shared-sessions/{id}",
		Summary:     "Update a shared session's name or status",
		Description: "Owner-only. Partial update — nil fields are left unchanged. Valid statuses: active, completed, archived.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateSharedSession)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSharedSession",
		Method:      http.MethodDelete,
		Path:        "/api/shared-sessions/{id}",
		Summary:     "Delete a shared session",
		Description: "Owner-only. Cleans up saves, invites, members and the shared-session record.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteSharedSession)
}

// --- Handlers ---

// HumaListSharedSaves is the huma handler for GET /api/games/{id}/shared-saves.
func (h *SharedSaveHandler) HumaListSharedSaves(ctx context.Context, in *ListSharedSavesInput) (*ListSharedSavesOutput, error) {
	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.SharedSaveState{}).Where("game_id = ?", in.ID).Count(&total)

	var saves []db.SharedSaveState
	if err := h.DB.Where("game_id = ?", in.ID).
		Preload("User").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&saves).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch shared saves")
	}

	uid := UserIDFromContext(ctx)
	result := make([]SharedSaveResponse, 0, len(saves))
	for _, s := range saves {
		result = append(result, h.toResponse(s, uid))
	}

	return &ListSharedSavesOutput{Body: PaginatedResponse[SharedSaveResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// HumaDeleteSharedSave is the huma handler for DELETE /api/games/{id}/shared-saves/{saveId}.
func (h *SharedSaveHandler) HumaDeleteSharedSave(ctx context.Context, in *DeleteSharedSaveInput) (*DeleteSharedSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	role := UserRoleFromContext(ctx)

	var save db.SharedSaveState
	if err := h.DB.First(&save, in.SaveID).Error; err != nil {
		return nil, huma.Error404NotFound("shared save not found")
	}

	if save.UserID != uid && !db.IsAdminOrOwner(role) {
		return nil, huma.Error403Forbidden("not authorized to delete this shared save")
	}

	if err := h.Storage.DeleteSave(save.FilePath); err != nil {
		return nil, huma.Error500InternalServerError("failed to delete save file")
	}

	h.DB.Delete(&save)
	return &DeleteSharedSaveOutput{Body: MessageResponse{Message: "shared save deleted"}}, nil
}

// HumaCreateSharedSession is the huma handler for POST /api/shared-sessions.
func (h *SharedSessionHandler) HumaCreateSharedSession(ctx context.Context, in *CreateSharedSessionInput) (*CreateSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)

	req := in.Body
	if req.GameID == "" || strings.TrimSpace(req.Name) == "" {
		return nil, huma.Error400BadRequest("invalid request: gameId and name are required")
	}
	if len(req.Name) > 255 {
		return nil, huma.Error400BadRequest("name must be 255 characters or fewer")
	}

	gid, err := strconv.ParseUint(req.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	coreName := game.CoreOverride
	if coreName == "" {
		coreName = game.Console.DefaultCore
	}

	session := db.GameSession{
		OwnerID:  uid,
		GameID:   uint(gid),
		Name:     "Shared Session: " + req.Name,
		CoreName: coreName,
	}
	if err := h.DB.Create(&session).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create session for shared session")
	}

	ss := db.SharedSession{
		OwnerID:   uid,
		GameID:    uint(gid),
		Name:      req.Name,
		Status:    "active",
		CoreName:  coreName,
		SessionID: &session.ID,
	}
	if err := h.DB.Create(&ss).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create shared session")
	}

	member := db.SharedSessionMember{
		SharedSessionID: ss.ID,
		UserID:          uid,
		Role:            "owner",
		JoinedAt:        time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to add owner as member")
	}

	h.DB.Preload("Members").Preload("Members.User").Preload("Game").Preload("Game.Console").Preload("Owner").First(&ss, ss.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "created_shared_session", uint(gid), CreatedSharedSessionMetadata{
		SharedSessionName: req.Name,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionCreated, Payload: h.toSharedSessionResponse(ss)})
	}

	return &CreateSharedSessionOutput{Body: h.toSharedSessionDetailResponse(ss)}, nil
}

// humaLoadSharedSessionWithMemberCheck is the huma-flavoured helper equivalent
// to loadSharedSessionWithMemberCheck, returning typed huma errors instead of
// writing to a gin context.
func (h *SharedSessionHandler) humaLoadSharedSessionWithMemberCheck(id string, uid uint) (db.SharedSession, error) {
	rid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		return db.SharedSession{}, huma.Error400BadRequest("invalid shared session ID")
	}

	var ss db.SharedSession
	if err := h.DB.Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").Preload("Owner").
		First(&ss, rid).Error; err != nil {
		return db.SharedSession{}, huma.Error404NotFound("shared session not found")
	}

	for _, m := range ss.Members {
		if m.UserID == uid {
			return ss, nil
		}
	}
	return db.SharedSession{}, huma.Error403Forbidden("not a member of this shared session")
}

// HumaGetSharedSession is the huma handler for GET /api/shared-sessions/{id}.
func (h *SharedSessionHandler) HumaGetSharedSession(ctx context.Context, in *GetSharedSessionInput) (*GetSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	return &GetSharedSessionOutput{Body: h.toSharedSessionDetailResponse(ss)}, nil
}

// HumaUpdateSharedSession is the huma handler for PUT /api/shared-sessions/{id}.
func (h *SharedSessionHandler) HumaUpdateSharedSession(ctx context.Context, in *UpdateSharedSessionInput) (*UpdateSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if ss.OwnerID != uid {
		return nil, huma.Error403Forbidden("only the shared session owner can perform this action")
	}

	req := in.Body
	if req.Name != nil {
		if len(*req.Name) > 255 {
			return nil, huma.Error400BadRequest("name must be 255 characters or fewer")
		}
		ss.Name = *req.Name
	}
	if req.Status != nil {
		allowed := map[string]bool{"active": true, "completed": true, "archived": true}
		if !allowed[*req.Status] {
			return nil, huma.Error400BadRequest("invalid status: must be active, completed, or archived")
		}
		ss.Status = *req.Status
	}

	if err := h.DB.Save(&ss).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to update shared session")
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionUpdated, Payload: h.toSharedSessionResponse(ss)})
	}

	return &UpdateSharedSessionOutput{Body: h.toSharedSessionResponse(ss)}, nil
}

// HumaDeleteSharedSession is the huma handler for DELETE /api/shared-sessions/{id}.
func (h *SharedSessionHandler) HumaDeleteSharedSession(ctx context.Context, in *DeleteSharedSessionInput) (*DeleteSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if ss.OwnerID != uid {
		return nil, huma.Error403Forbidden("only the shared session owner can perform this action")
	}

	sharedSessionID := ss.ID

	var saves []db.SharedSessionSave
	h.DB.Where("shared_session_id = ?", sharedSessionID).Find(&saves)
	for _, s := range saves {
		h.Storage.DeleteSharedSessionSave(s.FilePath)
	}

	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionSave{})
	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionInvite{})
	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionMember{})
	h.DB.Delete(&ss)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionDeleted, Payload: ws.SharedSessionDeletedPayload{
			SharedSessionID: strconv.FormatUint(uint64(sharedSessionID), 10),
		}})
	}

	return &DeleteSharedSessionOutput{Body: MessageResponse{Message: "shared session deleted"}}, nil
}
