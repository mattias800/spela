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

// ListGameSharedSessionsInput is the input for GET /api/games/{id}/shared-sessions.
type ListGameSharedSessionsInput struct {
	ID string `path:"id" doc:"Game ID."`
}

// ListGameSharedSessionsOutput wraps the game shared sessions list.
type ListGameSharedSessionsOutput struct {
	Body []SharedSessionResponse
}

// ListMySharedSessionsInput is the input for GET /api/shared-sessions.
type ListMySharedSessionsInput struct{}

// ListMySharedSessionsOutput wraps the caller's shared sessions list.
type ListMySharedSessionsOutput struct {
	Body []SharedSessionResponse
}

// InviteToSharedSessionInput is the input for POST /api/shared-sessions/{id}/invites.
type InviteToSharedSessionInput struct {
	ID   string `path:"id" doc:"Shared session ID."`
	Body InviteToSharedSessionRequest
}

// InviteToSharedSessionOutput wraps the invite response (201 Created).
type InviteToSharedSessionOutput struct {
	Body SharedSessionInviteResponse
}

// LeaveSharedSessionInput is the input for POST /api/shared-sessions/{id}/leave.
type LeaveSharedSessionInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// LeaveSharedSessionOutput wraps the leave success message.
type LeaveSharedSessionOutput struct {
	Body MessageResponse
}

// RemoveSharedSessionMemberInput is the input for DELETE /api/shared-sessions/{id}/members/{userId}.
type RemoveSharedSessionMemberInput struct {
	ID     string `path:"id" doc:"Shared session ID."`
	UserID string `path:"userId" doc:"User ID to remove."`
}

// RemoveSharedSessionMemberOutput wraps the remove-member success message.
type RemoveSharedSessionMemberOutput struct {
	Body MessageResponse
}

// SharedSessionTurnInput is the input for POST /api/shared-sessions/{id}/take-turn and release-turn.
type SharedSessionTurnInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// SharedSessionTakeTurnResponse is the wire format for take-turn.
type SharedSessionTakeTurnResponse struct {
	TurnToken   string    `json:"turnToken"`
	TurnTakenAt time.Time `json:"turnTakenAt"`
}

// SharedSessionTakeTurnOutput wraps the take-turn response.
type SharedSessionTakeTurnOutput struct {
	Body SharedSessionTakeTurnResponse
}

// SharedSessionReleaseTurnOutput wraps the release-turn success message.
type SharedSessionReleaseTurnOutput struct {
	Body MessageResponse
}

// SharedSessionHeartbeatResponse is the wire format for the heartbeat response.
type SharedSessionHeartbeatResponse struct {
	SharedSessionID string    `json:"sharedSessionId"`
	TurnTakenAt     time.Time `json:"turnTakenAt"`
}

// SharedSessionHeartbeatOutput wraps the heartbeat response.
type SharedSessionHeartbeatOutput struct {
	Body SharedSessionHeartbeatResponse
}

// ListSharedSessionSavesInput is the input for GET /api/shared-sessions/{id}/saves.
type ListSharedSessionSavesInput struct {
	ID string `path:"id" doc:"Shared session ID."`
}

// ListSharedSessionSavesOutput wraps the saves list.
type ListSharedSessionSavesOutput struct {
	Body []SharedSessionSaveResponse
}

// DeleteSharedSessionSaveInput is the input for DELETE /api/shared-sessions/{id}/saves/{saveId}.
type DeleteSharedSessionSaveInput struct {
	ID     string `path:"id" doc:"Shared session ID."`
	SaveID string `path:"saveId" doc:"Save ID."`
}

// DeleteSharedSessionSaveOutput wraps the delete success message.
type DeleteSharedSessionSaveOutput struct {
	Body MessageResponse
}

// RenameSharedSessionSaveRequest is the body for PUT /api/shared-sessions/{id}/saves/{saveId}/rename.
// Name is optional in the huma schema so handler-side validation owns the 400
// response shape.
type RenameSharedSessionSaveRequest struct {
	Name string `json:"name,omitempty" required:"false"`
}

// RenameSharedSessionSaveInput is the input for PUT /api/shared-sessions/{id}/saves/{saveId}/rename.
type RenameSharedSessionSaveInput struct {
	ID     string `path:"id" doc:"Shared session ID."`
	SaveID string `path:"saveId" doc:"Save ID."`
	Body   RenameSharedSessionSaveRequest
}

// RenameSharedSessionSaveOutput wraps the updated save record.
type RenameSharedSessionSaveOutput struct {
	Body SharedSessionSaveResponse
}

// ListSharedSessionInvitesInput is the input for GET /api/user/shared-session-invites.
type ListSharedSessionInvitesInput struct{}

// ListSharedSessionInvitesOutput wraps the invites list.
type ListSharedSessionInvitesOutput struct {
	Body []SharedSessionInviteResponse
}

// SharedSessionInviteCountInput is the input for GET /api/user/shared-session-invites/count.
type SharedSessionInviteCountInput struct{}

// SharedSessionInviteCountResponse is the wire format for the count endpoint.
type SharedSessionInviteCountResponse struct {
	Count int64 `json:"count"`
}

// SharedSessionInviteCountOutput wraps the count response.
type SharedSessionInviteCountOutput struct {
	Body SharedSessionInviteCountResponse
}

// AcceptSharedSessionInviteInput is the input for POST /api/user/shared-session-invites/{id}/accept.
type AcceptSharedSessionInviteInput struct {
	ID string `path:"id" doc:"Invite ID."`
}

// AcceptSharedSessionInviteOutput wraps the accept success message.
type AcceptSharedSessionInviteOutput struct {
	Body MessageResponse
}

// DeclineSharedSessionInviteInput is the input for POST /api/user/shared-session-invites/{id}/decline.
type DeclineSharedSessionInviteInput struct {
	ID string `path:"id" doc:"Invite ID."`
}

// DeclineSharedSessionInviteOutput wraps the decline success message.
type DeclineSharedSessionInviteOutput struct {
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

	huma.Register(api, huma.Operation{
		OperationID: "listGameSharedSessions",
		Method:      http.MethodGet,
		Path:        "/api/games/{id}/shared-sessions",
		Summary:     "List active shared sessions for a game",
		Description: "Returns active shared sessions for the specified game, newest updated first.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListGameSharedSessions)

	huma.Register(api, huma.Operation{
		OperationID: "listMySharedSessions",
		Method:      http.MethodGet,
		Path:        "/api/shared-sessions",
		Summary:     "List shared sessions the caller is a member of",
		Description: "Returns shared sessions where the caller is a member, newest updated first.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMySharedSessions)

	huma.Register(api, huma.Operation{
		OperationID:   "inviteToSharedSession",
		Method:        http.MethodPost,
		Path:          "/api/shared-sessions/{id}/invites",
		Summary:       "Invite a user to a shared session",
		Description:   "Requires membership. Invites a user by username. Duplicate active invites return 409. Previously declined invites are cleared before creating the new one.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"shared-sessions"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaInviteToSharedSession)

	huma.Register(api, huma.Operation{
		OperationID: "leaveSharedSession",
		Method:      http.MethodPost,
		Path:        "/api/shared-sessions/{id}/leave",
		Summary:     "Leave a shared session",
		Description: "Removes the caller from the shared session. Owners cannot leave — they must transfer ownership or delete the session.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaLeaveSharedSession)

	huma.Register(api, huma.Operation{
		OperationID: "removeSharedSessionMember",
		Method:      http.MethodDelete,
		Path:        "/api/shared-sessions/{id}/members/{userId}",
		Summary:     "Remove a member from a shared session",
		Description: "Owner-only. Removes the specified member. If the removed user held the turn, it's released.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRemoveSharedSessionMember)

	huma.Register(api, huma.Operation{
		OperationID: "sharedSessionTakeTurn",
		Method:      http.MethodPost,
		Path:        "/api/shared-sessions/{id}/take-turn",
		Summary:     "Take the turn on a shared session",
		Description: "Atomically acquires the turn. Returns a turnToken that save-upload requests must provide. Fails with 409 if another member holds the turn.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSharedSessionTakeTurn)

	huma.Register(api, huma.Operation{
		OperationID: "sharedSessionReleaseTurn",
		Method:      http.MethodPost,
		Path:        "/api/shared-sessions/{id}/release-turn",
		Summary:     "Release the caller's turn on a shared session",
		Description: "Clears the active turn for the shared session. Only the current turn holder can call this.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSharedSessionReleaseTurn)

	huma.Register(api, huma.Operation{
		OperationID: "sharedSessionHeartbeat",
		Method:      http.MethodPost,
		Path:        "/api/shared-sessions/{id}/heartbeat",
		Summary:     "Extend the caller's turn",
		Description: "Refreshes turn_taken_at so the timeout doesn't expire. Only the current turn holder can call this.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaSharedSessionHeartbeat)

	huma.Register(api, huma.Operation{
		OperationID: "listSharedSessionSaves",
		Method:      http.MethodGet,
		Path:        "/api/shared-sessions/{id}/saves",
		Summary:     "List save states for a shared session",
		Description: "Requires membership. Returns saves newest first.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListSharedSessionSaves)

	huma.Register(api, huma.Operation{
		OperationID: "deleteSharedSessionSave",
		Method:      http.MethodDelete,
		Path:        "/api/shared-sessions/{id}/saves/{saveId}",
		Summary:     "Delete a shared session save",
		Description: "Owner-only. Removes the save's file and DB record.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteSharedSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "renameSharedSessionSave",
		Method:      http.MethodPut,
		Path:        "/api/shared-sessions/{id}/saves/{saveId}/rename",
		Summary:     "Rename a shared session save",
		Description: "Requires membership. Updates the save's display name.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaRenameSharedSessionSave)

	huma.Register(api, huma.Operation{
		OperationID: "listSharedSessionInvites",
		Method:      http.MethodGet,
		Path:        "/api/user/shared-session-invites",
		Summary:     "List pending shared session invites for the caller",
		Description: "Returns pending shared session invites for the authenticated user.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMyInvites)

	huma.Register(api, huma.Operation{
		OperationID: "getPendingSharedSessionInviteCount",
		Method:      http.MethodGet,
		Path:        "/api/user/shared-session-invites/count",
		Summary:     "Count pending shared session invites for the caller",
		Description: "Returns the number of pending shared session invites for the authenticated user.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPendingInviteCount)

	huma.Register(api, huma.Operation{
		OperationID: "acceptSharedSessionInvite",
		Method:      http.MethodPost,
		Path:        "/api/user/shared-session-invites/{id}/accept",
		Summary:     "Accept a shared session invite",
		Description: "Marks the invite as accepted and adds the caller as a member. Emits a joined_shared_session activity event.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAcceptSharedSessionInvite)

	huma.Register(api, huma.Operation{
		OperationID: "declineSharedSessionInvite",
		Method:      http.MethodPost,
		Path:        "/api/user/shared-session-invites/{id}/decline",
		Summary:     "Decline a shared session invite",
		Description: "Marks the invite as declined.",
		Tags:        []string{"shared-sessions"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeclineSharedSessionInvite)
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

// HumaListGameSharedSessions is the huma handler for GET /api/games/{id}/shared-sessions.
func (h *SharedSessionHandler) HumaListGameSharedSessions(_ context.Context, in *ListGameSharedSessionsInput) (*ListGameSharedSessionsOutput, error) {
	gid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var sharedSessions []db.SharedSession
	if err := h.DB.Where("game_id = ? AND status = ?", gid, "active").
		Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").
		Preload("Owner").
		Order("updated_at DESC").
		Find(&sharedSessions).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch shared sessions")
	}

	result := make([]SharedSessionResponse, 0, len(sharedSessions))
	for _, r := range sharedSessions {
		result = append(result, h.toSharedSessionResponse(r))
	}
	return &ListGameSharedSessionsOutput{Body: result}, nil
}

// HumaListMySharedSessions is the huma handler for GET /api/shared-sessions.
func (h *SharedSessionHandler) HumaListMySharedSessions(ctx context.Context, _ *ListMySharedSessionsInput) (*ListMySharedSessionsOutput, error) {
	uid := UserIDFromContext(ctx)

	var memberRows []db.SharedSessionMember
	if err := h.DB.Where("user_id = ?", uid).Find(&memberRows).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch shared sessions")
	}

	sharedSessionIDs := make([]uint, 0, len(memberRows))
	for _, m := range memberRows {
		sharedSessionIDs = append(sharedSessionIDs, m.SharedSessionID)
	}

	if len(sharedSessionIDs) == 0 {
		return &ListMySharedSessionsOutput{Body: []SharedSessionResponse{}}, nil
	}

	var sharedSessions []db.SharedSession
	if err := h.DB.Where("id IN ?", sharedSessionIDs).
		Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").
		Preload("Owner").
		Order("updated_at DESC").
		Find(&sharedSessions).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch shared sessions")
	}

	result := make([]SharedSessionResponse, 0, len(sharedSessions))
	for _, r := range sharedSessions {
		result = append(result, h.toSharedSessionResponse(r))
	}
	return &ListMySharedSessionsOutput{Body: result}, nil
}

// HumaInviteToSharedSession is the huma handler for POST /api/shared-sessions/{id}/invites.
func (h *SharedSessionHandler) HumaInviteToSharedSession(ctx context.Context, in *InviteToSharedSessionInput) (*InviteToSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if in.Body.Username == "" {
		return nil, huma.Error400BadRequest("invalid request: username is required")
	}

	var invitee db.User
	if err := h.DB.Where("username = ?", in.Body.Username).First(&invitee).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}

	if invitee.ID == uid {
		return nil, huma.Error400BadRequest("cannot invite yourself")
	}

	var existingMember db.SharedSessionMember
	if err := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, invitee.ID).First(&existingMember).Error; err == nil {
		return nil, huma.Error409Conflict("user is already a member")
	}

	var existingInvite db.SharedSessionInvite
	if err := h.DB.Where("shared_session_id = ? AND invitee_id = ? AND status = ?", ss.ID, invitee.ID, "pending").First(&existingInvite).Error; err == nil {
		return nil, huma.Error409Conflict("user already has a pending invite")
	}

	h.DB.Unscoped().Where("shared_session_id = ? AND invitee_id = ? AND status = ?", ss.ID, invitee.ID, "declined").Delete(&db.SharedSessionInvite{})

	invite := db.SharedSessionInvite{
		SharedSessionID: ss.ID,
		InviterID:       uid,
		InviteeID:       invitee.ID,
		Status:          "pending",
	}
	if err := h.DB.Create(&invite).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to create invite")
	}

	h.DB.Preload("Inviter").Preload("Invitee").Preload("SharedSession").Preload("SharedSession.Game").First(&invite, invite.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionInviteSent, Payload: h.toSharedSessionInviteResponse(invite)})
	}

	return &InviteToSharedSessionOutput{Body: h.toSharedSessionInviteResponse(invite)}, nil
}

// HumaLeaveSharedSession is the huma handler for POST /api/shared-sessions/{id}/leave.
func (h *SharedSessionHandler) HumaLeaveSharedSession(ctx context.Context, in *LeaveSharedSessionInput) (*LeaveSharedSessionOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if ss.OwnerID == uid {
		return nil, huma.Error400BadRequest("owner cannot leave the shared session; transfer ownership or delete it")
	}

	result := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, uid).Delete(&db.SharedSessionMember{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("membership not found")
	}

	if ss.ActiveUserID != nil && *ss.ActiveUserID == uid {
		h.DB.Model(&ss).Updates(map[string]interface{}{
			"active_user_id": nil,
			"turn_token":     "",
			"turn_taken_at":  nil,
		})
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionMemberLeft, Payload: ws.SharedSessionMemberLeftPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
		}})
	}

	return &LeaveSharedSessionOutput{Body: MessageResponse{Message: "left shared session"}}, nil
}

// HumaRemoveSharedSessionMember is the huma handler for DELETE /api/shared-sessions/{id}/members/{userId}.
func (h *SharedSessionHandler) HumaRemoveSharedSessionMember(ctx context.Context, in *RemoveSharedSessionMemberInput) (*RemoveSharedSessionMemberOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if ss.OwnerID != uid {
		return nil, huma.Error403Forbidden("only the shared session owner can perform this action")
	}

	targetUID, err := strconv.ParseUint(in.UserID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid user ID")
	}

	if uint(targetUID) == uid {
		return nil, huma.Error400BadRequest("cannot remove yourself; use leave or delete the shared session")
	}

	result := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, targetUID).Delete(&db.SharedSessionMember{})
	if result.RowsAffected == 0 {
		return nil, huma.Error404NotFound("member not found")
	}

	if ss.ActiveUserID != nil && *ss.ActiveUserID == uint(targetUID) {
		h.DB.Model(&ss).Updates(map[string]interface{}{
			"active_user_id": nil,
			"turn_token":     "",
			"turn_taken_at":  nil,
		})
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionMemberRemoved, Payload: ws.SharedSessionMemberRemovedPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			UserID:          strconv.FormatUint(uint64(targetUID), 10),
		}})
	}

	return &RemoveSharedSessionMemberOutput{Body: MessageResponse{Message: "member removed"}}, nil
}

// HumaSharedSessionTakeTurn is the huma handler for POST /api/shared-sessions/{id}/take-turn.
func (h *SharedSessionHandler) HumaSharedSessionTakeTurn(ctx context.Context, in *SharedSessionTurnInput) (*SharedSessionTakeTurnOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if ss.Status != "active" {
		return nil, huma.Error400BadRequest("shared session is not active")
	}

	now := time.Now()
	token := generateTurnToken()

	var previousUserID *uint
	err = h.DB.Transaction(func(tx *gorm.DB) error {
		var fresh db.SharedSession
		if err := tx.First(&fresh, ss.ID).Error; err != nil {
			return err
		}

		if fresh.ActiveUserID != nil && *fresh.ActiveUserID != uid {
			if fresh.TurnTakenAt != nil && fresh.TurnTakenAt.Add(SharedSessionTurnTimeout).After(now) {
				return errTurnHeld
			}
			prev := *fresh.ActiveUserID
			previousUserID = &prev
		}

		result := tx.Model(&db.SharedSession{}).
			Where("id = ? AND updated_at = ?", fresh.ID, fresh.UpdatedAt).
			Updates(map[string]interface{}{
				"active_user_id": uid,
				"turn_token":     token,
				"turn_taken_at":  now,
			})
		if result.RowsAffected == 0 {
			return errTurnConflict
		}
		return nil
	})
	if err != nil {
		if err == errTurnHeld {
			return nil, huma.Error409Conflict("turn is currently held by another user")
		}
		if err == errTurnConflict {
			return nil, huma.Error409Conflict("turn was acquired by another user, please retry")
		}
		return nil, huma.Error500InternalServerError("failed to acquire turn")
	}

	if previousUserID != nil && h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionTurnExpired, Payload: ws.SharedSessionTurnExpiredPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			PreviousUserID:  strconv.FormatUint(uint64(*previousUserID), 10),
		}})
	}

	var user db.User
	h.DB.First(&user, uid)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionTurnAcquired, Payload: ws.SharedSessionTurnAcquiredPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
			Username:        user.Username,
		}})
	}

	return &SharedSessionTakeTurnOutput{Body: SharedSessionTakeTurnResponse{
		TurnToken:   token,
		TurnTakenAt: now,
	}}, nil
}

// HumaSharedSessionReleaseTurn is the huma handler for POST /api/shared-sessions/{id}/release-turn.
func (h *SharedSessionHandler) HumaSharedSessionReleaseTurn(ctx context.Context, in *SharedSessionTurnInput) (*SharedSessionReleaseTurnOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		return nil, huma.Error400BadRequest("you do not hold the turn")
	}

	h.DB.Model(&ss).Updates(map[string]interface{}{
		"active_user_id": nil,
		"turn_token":     "",
		"turn_taken_at":  nil,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionTurnReleased, Payload: ws.SharedSessionTurnReleasedPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
		}})
	}

	return &SharedSessionReleaseTurnOutput{Body: MessageResponse{Message: "turn released"}}, nil
}

// HumaSharedSessionHeartbeat is the huma handler for POST /api/shared-sessions/{id}/heartbeat.
func (h *SharedSessionHandler) HumaSharedSessionHeartbeat(ctx context.Context, in *SharedSessionTurnInput) (*SharedSessionHeartbeatOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		return nil, huma.Error400BadRequest("you do not hold the turn")
	}

	now := time.Now()
	h.DB.Model(&ss).Update("turn_taken_at", now)

	return &SharedSessionHeartbeatOutput{Body: SharedSessionHeartbeatResponse{
		SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
		TurnTakenAt:     now,
	}}, nil
}

// HumaListSharedSessionSaves is the huma handler for GET /api/shared-sessions/{id}/saves.
func (h *SharedSessionHandler) HumaListSharedSessionSaves(ctx context.Context, in *ListSharedSessionSavesInput) (*ListSharedSessionSavesOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}

	var saves []db.SharedSessionSave
	if err := h.DB.Where("shared_session_id = ?", ss.ID).
		Preload("User").
		Order("created_at DESC").
		Find(&saves).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch saves")
	}

	result := make([]SharedSessionSaveResponse, 0, len(saves))
	for _, s := range saves {
		result = append(result, h.toSharedSessionSaveResponse(s))
	}
	return &ListSharedSessionSavesOutput{Body: result}, nil
}

// HumaDeleteSharedSessionSave is the huma handler for DELETE /api/shared-sessions/{id}/saves/{saveId}.
func (h *SharedSessionHandler) HumaDeleteSharedSessionSave(ctx context.Context, in *DeleteSharedSessionSaveInput) (*DeleteSharedSessionSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	ss, err := h.humaLoadSharedSessionWithMemberCheck(in.ID, uid)
	if err != nil {
		return nil, err
	}
	if ss.OwnerID != uid {
		return nil, huma.Error403Forbidden("only the shared session owner can perform this action")
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", in.SaveID, ss.ID).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}

	if err := h.Storage.DeleteSharedSessionSave(save.FilePath); err != nil {
		return nil, huma.Error500InternalServerError("failed to delete save file")
	}

	h.DB.Delete(&save)
	return &DeleteSharedSessionSaveOutput{Body: MessageResponse{Message: "save deleted"}}, nil
}

// HumaRenameSharedSessionSave is the huma handler for PUT /api/shared-sessions/{id}/saves/{saveId}/rename.
func (h *SharedSessionHandler) HumaRenameSharedSessionSave(ctx context.Context, in *RenameSharedSessionSaveInput) (*RenameSharedSessionSaveOutput, error) {
	uid := UserIDFromContext(ctx)
	rid, err := strconv.ParseUint(in.ID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid shared session ID")
	}

	var member db.SharedSessionMember
	if err := h.DB.Where("shared_session_id = ? AND user_id = ?", rid, uid).First(&member).Error; err != nil {
		return nil, huma.Error403Forbidden("not a member of this shared session")
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", in.SaveID, rid).First(&save).Error; err != nil {
		return nil, huma.Error404NotFound("save not found")
	}

	name := in.Body.Name
	if name == "" {
		return nil, huma.Error400BadRequest("name is required and must be 255 characters or fewer")
	}
	if len(name) > 255 {
		return nil, huma.Error400BadRequest("name is required and must be 255 characters or fewer")
	}
	if strings.TrimSpace(name) == "" {
		return nil, huma.Error400BadRequest("name cannot be empty")
	}

	save.Name = name
	if err := h.DB.Save(&save).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to rename save")
	}

	h.DB.Preload("User").First(&save, save.ID)
	return &RenameSharedSessionSaveOutput{Body: h.toSharedSessionSaveResponse(save)}, nil
}

// HumaListMyInvites is the huma handler for GET /api/user/shared-session-invites.
func (h *SharedSessionHandler) HumaListMyInvites(ctx context.Context, _ *ListSharedSessionInvitesInput) (*ListSharedSessionInvitesOutput, error) {
	uid := UserIDFromContext(ctx)

	var invites []db.SharedSessionInvite
	if err := h.DB.Where("invitee_id = ? AND status = ?", uid, "pending").
		Preload("Inviter").Preload("Invitee").
		Preload("SharedSession").Preload("SharedSession.Game").Preload("SharedSession.Game.Console").Preload("SharedSession.Owner").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch invites")
	}

	result := make([]SharedSessionInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toSharedSessionInviteResponse(inv))
	}
	return &ListSharedSessionInvitesOutput{Body: result}, nil
}

// HumaGetPendingInviteCount is the huma handler for GET /api/user/shared-session-invites/count.
func (h *SharedSessionHandler) HumaGetPendingInviteCount(ctx context.Context, _ *SharedSessionInviteCountInput) (*SharedSessionInviteCountOutput, error) {
	uid := UserIDFromContext(ctx)

	var count int64
	if err := h.DB.Model(&db.SharedSessionInvite{}).Where("invitee_id = ? AND status = ?", uid, "pending").Count(&count).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to count invites")
	}

	return &SharedSessionInviteCountOutput{Body: SharedSessionInviteCountResponse{Count: count}}, nil
}

// HumaAcceptSharedSessionInvite is the huma handler for POST /api/user/shared-session-invites/{id}/accept.
func (h *SharedSessionHandler) HumaAcceptSharedSessionInvite(ctx context.Context, in *AcceptSharedSessionInviteInput) (*AcceptSharedSessionInviteOutput, error) {
	uid := UserIDFromContext(ctx)

	var invite db.SharedSessionInvite
	if err := h.DB.Preload("SharedSession").Preload("SharedSession.Game").First(&invite, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("invite not found")
	}

	if invite.InviteeID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	if invite.Status != "pending" {
		return nil, huma.Error400BadRequest("invite is no longer pending")
	}

	invite.Status = "accepted"
	if err := h.DB.Save(&invite).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to accept invite")
	}

	member := db.SharedSessionMember{
		SharedSessionID: invite.SharedSessionID,
		UserID:          uid,
		Role:            "member",
		JoinedAt:        time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to join shared session")
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "joined_shared_session", invite.SharedSession.GameID, JoinedSharedSessionMetadata{
		SharedSessionName: invite.SharedSession.Name,
	})

	if h.Hub != nil {
		var user db.User
		h.DB.First(&user, uid)
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionInviteAccepted, Payload: ws.SharedSessionInviteAcceptedPayload{
			SharedSessionID: strconv.FormatUint(uint64(invite.SharedSessionID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
			Username:        user.Username,
		}})
	}

	return &AcceptSharedSessionInviteOutput{Body: MessageResponse{Message: "invite accepted"}}, nil
}

// HumaDeclineSharedSessionInvite is the huma handler for POST /api/user/shared-session-invites/{id}/decline.
func (h *SharedSessionHandler) HumaDeclineSharedSessionInvite(ctx context.Context, in *DeclineSharedSessionInviteInput) (*DeclineSharedSessionInviteOutput, error) {
	uid := UserIDFromContext(ctx)

	var invite db.SharedSessionInvite
	if err := h.DB.First(&invite, in.ID).Error; err != nil {
		return nil, huma.Error404NotFound("invite not found")
	}

	if invite.InviteeID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}

	if invite.Status != "pending" {
		return nil, huma.Error400BadRequest("invite is no longer pending")
	}

	invite.Status = "declined"
	if err := h.DB.Save(&invite).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to decline invite")
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionInviteDeclined, Payload: ws.SharedSessionInviteDeclinedPayload{
			SharedSessionID: strconv.FormatUint(uint64(invite.SharedSessionID), 10),
			InviteeID:       strconv.FormatUint(uint64(uid), 10),
		}})
	}

	return &DeclineSharedSessionInviteOutput{Body: MessageResponse{Message: "invite declined"}}, nil
}

// Sentinels for the turn-control transaction.
var (
	errTurnHeld     = &turnError{msg: "turn_held"}
	errTurnConflict = &turnError{msg: "turn_conflict"}
)

type turnError struct{ msg string }

func (e *turnError) Error() string { return e.msg }
