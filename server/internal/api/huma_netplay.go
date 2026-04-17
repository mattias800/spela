package api

import (
	"context"
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

// CreateNetplaySessionInput is the input for POST /api/netplay/sessions.
type CreateNetplaySessionInput struct {
	Body CreateNetplaySessionRequest
}

// CreateNetplaySessionOutput wraps the created session response (201 Created).
type CreateNetplaySessionOutput struct {
	Body NetplaySessionResponse
}

// JoinNetplayByInviteCodeInput is the input for POST /api/netplay/sessions/join.
type JoinNetplayByInviteCodeInput struct {
	Body JoinByInviteCodeRequest
}

// JoinNetplayByInviteCodeOutput wraps the joined session response.
type JoinNetplayByInviteCodeOutput struct {
	Body NetplaySessionResponse
}

// ListNetplaySessionsInput is the input for GET /api/netplay/sessions.
type ListNetplaySessionsInput struct {
	Page     int `query:"page" doc:"1-based page number (defaults to 1)."`
	PageSize int `query:"pageSize" doc:"Page size (defaults to 20, clamped to 1-100)."`
}

// ListNetplaySessionsOutput wraps the paginated session list.
type ListNetplaySessionsOutput struct {
	Body PaginatedResponse[NetplaySessionResponse]
}

// GetNetplaySessionInput is the input for GET /api/netplay/sessions/{id}.
type GetNetplaySessionInput struct {
	ID string `path:"id" doc:"Netplay session ID."`
}

// GetNetplaySessionOutput wraps the session response.
type GetNetplaySessionOutput struct {
	Body NetplaySessionResponse
}

// DeleteNetplaySessionInput is the input for DELETE /api/netplay/sessions/{id}.
type DeleteNetplaySessionInput struct {
	ID string `path:"id" doc:"Netplay session ID."`
}

// DeleteNetplaySessionOutput wraps the delete success message.
type DeleteNetplaySessionOutput struct {
	Body MessageResponse
}

// LeaveNetplaySessionInput is the input for POST /api/netplay/sessions/{id}/leave.
type LeaveNetplaySessionInput struct {
	ID string `path:"id" doc:"Netplay session ID."`
}

// LeaveNetplaySessionResponse is the wire format for the leave endpoint.
type LeaveNetplaySessionResponse struct {
	Message   string `json:"message"`
	EndReason string `json:"endReason"`
}

// LeaveNetplaySessionOutput wraps the leave response.
type LeaveNetplaySessionOutput struct {
	Body LeaveNetplaySessionResponse
}

// UpdateNetplaySettingsInput is the input for PUT /api/netplay/sessions/{id}/settings.
type UpdateNetplaySettingsInput struct {
	ID   string `path:"id" doc:"Netplay session ID."`
	Body UpdateNetplaySettingsRequest
}

// UpdateNetplaySettingsOutput wraps the updated session response.
type UpdateNetplaySettingsOutput struct {
	Body NetplaySessionResponse
}

// NetplayInviteUserInput is the input for POST /api/netplay/sessions/{id}/invites.
type NetplayInviteUserInput struct {
	ID   string `path:"id" doc:"Netplay session ID."`
	Body NetplayInviteUserRequest
}

// NetplayInviteUserOutput wraps the invite response (201 Created).
type NetplayInviteUserOutput struct {
	Body NetplayInviteResponse
}

// AcceptNetplayInviteByCodeInput is the input for POST /api/netplay/invites/{inviteId}/accept.
// The path parameter is the numeric invite ID (kept at its historical
// inviteId name to preserve wire compatibility with existing clients).
type AcceptNetplayInviteByCodeInput struct {
	Code string `path:"inviteId" doc:"Invite ID."`
}

// AcceptNetplayInviteByCodeOutput wraps the accepted invite response.
type AcceptNetplayInviteByCodeOutput struct {
	Body NetplayInviteResponse
}

// DeclineNetplayInviteByCodeInput is the input for POST /api/netplay/invites/{inviteId}/decline.
type DeclineNetplayInviteByCodeInput struct {
	Code string `path:"inviteId" doc:"Invite ID."`
}

// DeclineNetplayInviteByCodeOutput wraps the decline success message.
type DeclineNetplayInviteByCodeOutput struct {
	Body MessageResponse
}

// ListNetplaySessionInvitesInput is the input for GET /api/netplay/sessions/{id}/invites.
type ListNetplaySessionInvitesInput struct {
	ID string `path:"id" doc:"Netplay session ID."`
}

// ListNetplaySessionInvitesOutput wraps the session invites list.
type ListNetplaySessionInvitesOutput struct {
	Body []NetplayInviteResponse
}

// ListMyNetplayInvitesInput is the input for GET /api/netplay/invites.
type ListMyNetplayInvitesInput struct{}

// ListMyNetplayInvitesResponse mirrors the raw gin wire format which wraps
// the invite list with a total counter.
type ListMyNetplayInvitesResponse struct {
	Data  []NetplayInviteResponse `json:"data"`
	Total int                     `json:"total"`
}

// ListMyNetplayInvitesOutput wraps the invites-with-total response.
type ListMyNetplayInvitesOutput struct {
	Body ListMyNetplayInvitesResponse
}

// PendingNetplayInviteCountInput is the input for GET /api/netplay/invites/count.
type PendingNetplayInviteCountInput struct{}

// PendingNetplayInviteCountResponse is the wire format for the count endpoint.
type PendingNetplayInviteCountResponse struct {
	Count int64 `json:"count"`
}

// PendingNetplayInviteCountOutput wraps the pending-count response.
type PendingNetplayInviteCountOutput struct {
	Body PendingNetplayInviteCountResponse
}

// RegisterNetplayRoutes wires the netplay session + invite endpoints into the
// huma API. The websocket handler stays on raw gin.
func RegisterNetplayRoutes(api huma.API, h *NetplayHandler, jwtSecret string, database *gorm.DB, userLimiter *RateLimiter) {
	requireAuth := RequireAuth(jwtSecret, database)
	rateLimit := UserRateLimitMiddleware(userLimiter)
	mw := huma.Middlewares{requireAuth, rateLimit}
	sec := []map[string][]string{{"bearer": {}}}

	huma.Register(api, huma.Operation{
		OperationID:   "createNetplaySession",
		Method:        http.MethodPost,
		Path:          "/api/netplay/sessions",
		Summary:       "Create a netplay session",
		Description:   "Creates a new netplay session with a generated 6-character invite code. Only supported on a curated set of consoles (NES, SNES, GB/GBC/GBA, GEN).",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"netplay"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaCreateNetplaySession)

	huma.Register(api, huma.Operation{
		OperationID: "joinNetplayByInviteCode",
		Method:      http.MethodPost,
		Path:        "/api/netplay/sessions/join",
		Summary:     "Join a netplay session via invite code",
		Description: "Atomically joins a waiting session by its 6-character invite code. Fails with 409 if the session is already full.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaJoinNetplayByInviteCode)

	huma.Register(api, huma.Operation{
		OperationID: "listNetplaySessions",
		Method:      http.MethodGet,
		Path:        "/api/netplay/sessions",
		Summary:     "List netplay sessions for the caller",
		Description: "Returns the caller's sessions (as host or client) plus currently-waiting sessions (under 15 minutes old). Paginated.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListNetplaySessions)

	huma.Register(api, huma.Operation{
		OperationID: "getNetplaySession",
		Method:      http.MethodGet,
		Path:        "/api/netplay/sessions/{id}",
		Summary:     "Get a netplay session by ID",
		Description: "Returns netplay session details with host, client, game and console metadata.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetNetplaySession)

	huma.Register(api, huma.Operation{
		OperationID: "deleteNetplaySession",
		Method:      http.MethodDelete,
		Path:        "/api/netplay/sessions/{id}",
		Summary:     "Delete a netplay session",
		Description: "Host can delete any session they own; admin/owner can force-delete. Associated invites are expired and records cleaned up.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeleteNetplaySession)

	huma.Register(api, huma.Operation{
		OperationID: "leaveNetplaySession",
		Method:      http.MethodPost,
		Path:        "/api/netplay/sessions/{id}/leave",
		Summary:     "Leave a netplay session",
		Description: "Ends the session. endReason is host_left or client_left depending on who leaves.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaLeaveNetplaySession)

	huma.Register(api, huma.Operation{
		OperationID: "updateNetplaySettings",
		Method:      http.MethodPut,
		Path:        "/api/netplay/sessions/{id}/settings",
		Summary:     "Update netplay session settings",
		Description: "Host-only. Updates the input delay (1-10 frames) for the session.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaUpdateNetplaySettings)

	huma.Register(api, huma.Operation{
		OperationID:   "netplayInviteUser",
		Method:        http.MethodPost,
		Path:          "/api/netplay/sessions/{id}/invites",
		Summary:       "Invite a user to a netplay session",
		Description:   "Host-only. Invites a user by username. The session must be in the waiting state and the invitee must not already have a pending invite.",
		DefaultStatus: http.StatusCreated,
		Tags:          []string{"netplay"},
		Middlewares:   mw,
		Security:      sec,
	}, h.HumaNetplayInviteUser)

	huma.Register(api, huma.Operation{
		OperationID: "acceptNetplayInvite",
		Method:      http.MethodPost,
		Path:        "/api/netplay/invites/{inviteId}/accept",
		Summary:     "Accept a netplay invite",
		Description: "Marks the invite as accepted and expires other pending invites for the same session.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaAcceptNetplayInviteByCode)

	huma.Register(api, huma.Operation{
		OperationID: "declineNetplayInvite",
		Method:      http.MethodPost,
		Path:        "/api/netplay/invites/{inviteId}/decline",
		Summary:     "Decline a netplay invite",
		Description: "Marks the invite as declined.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaDeclineNetplayInviteByCode)

	huma.Register(api, huma.Operation{
		OperationID: "listNetplaySessionInvites",
		Method:      http.MethodGet,
		Path:        "/api/netplay/sessions/{id}/invites",
		Summary:     "List invites for a netplay session",
		Description: "Host-only. Returns every invite ever sent for the specified netplay session, newest first.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListNetplaySessionInvites)

	huma.Register(api, huma.Operation{
		OperationID: "listMyNetplayInvites",
		Method:      http.MethodGet,
		Path:        "/api/netplay/invites",
		Summary:     "List pending netplay invites for the caller",
		Description: "Returns the caller's pending netplay invites wrapped in a { data, total } envelope.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaListMyNetplayInvites)

	huma.Register(api, huma.Operation{
		OperationID: "getPendingNetplayInviteCount",
		Method:      http.MethodGet,
		Path:        "/api/netplay/invites/count",
		Summary:     "Count pending netplay invites for the caller",
		Description: "Returns the number of pending netplay invites for the authenticated user.",
		Tags:        []string{"netplay"},
		Middlewares: mw,
		Security:    sec,
	}, h.HumaGetPendingNetplayInviteCount)
}

// --- Handlers ---

// HumaCreateNetplaySession is the huma handler for POST /api/netplay/sessions.
func (h *NetplayHandler) HumaCreateNetplaySession(ctx context.Context, in *CreateNetplaySessionInput) (*CreateNetplaySessionOutput, error) {
	uid := UserIDFromContext(ctx)

	req := in.Body
	if req.GameID == "" {
		return nil, huma.Error400BadRequest("invalid request: gameId is required")
	}

	gid, err := strconv.ParseUint(req.GameID, 10, 64)
	if err != nil {
		return nil, huma.Error400BadRequest("invalid game ID")
	}

	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gid).Error; err != nil {
		return nil, huma.Error404NotFound("game not found")
	}

	if !game.Console.Playable {
		return nil, huma.Error400BadRequest(errNonPlayableConsole.Error())
	}
	if !netplaySupportedConsoles[game.Console.Abbreviation] {
		return nil, huma.Error400BadRequest("netplay is not supported for " + game.Console.Name + " yet")
	}

	inputDelay := 3
	if req.InputDelay != nil && *req.InputDelay >= 1 && *req.InputDelay <= 10 {
		inputDelay = *req.InputDelay
	}

	var session db.NetplaySession
	var createErr error
	for attempt := 0; attempt < 3; attempt++ {
		session = db.NetplaySession{
			HostUserID: uid,
			GameID:     uint(gid),
			Status:     "waiting",
			InputDelay: inputDelay,
			CoreName:   req.CoreName,
			InviteCode: generateInviteCode(),
		}
		createErr = h.DB.Create(&session).Error
		if createErr == nil {
			break
		}
		slog.Warn("invite code collision, retrying", "attempt", attempt+1, "error", createErr)
	}
	if createErr != nil {
		return nil, huma.Error500InternalServerError("failed to create session")
	}

	h.DB.Preload("HostUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplaySessionCreated, Payload: h.toSessionResponse(session)})
	}

	return &CreateNetplaySessionOutput{Body: h.toSessionResponse(session)}, nil
}

// HumaJoinNetplayByInviteCode is the huma handler for POST /api/netplay/sessions/join.
func (h *NetplayHandler) HumaJoinNetplayByInviteCode(ctx context.Context, in *JoinNetplayByInviteCodeInput) (*JoinNetplayByInviteCodeOutput, error) {
	uid := UserIDFromContext(ctx)

	if strings.TrimSpace(in.Body.InviteCode) == "" {
		return nil, huma.Error400BadRequest("invite code is required")
	}

	code := strings.ToUpper(strings.TrimSpace(in.Body.InviteCode))

	var session db.NetplaySession
	if err := h.DB.
		Preload("HostUser").Preload("ClientUser").
		Preload("Game").Preload("Game.Console").
		Where("invite_code = ?", code).First(&session).Error; err != nil {
		return nil, huma.Error404NotFound("session not found. The code may have expired or the host may have cancelled.")
	}

	if session.HostUserID == uid {
		return nil, huma.Error400BadRequest("you can't join your own session")
	}
	if session.Status == "ended" {
		return nil, huma.Error400BadRequest("this session has already ended")
	}
	if session.Status != "waiting" {
		return nil, huma.Error400BadRequest("this session is no longer accepting players")
	}
	if session.ClientUserID != nil {
		return nil, huma.Error409Conflict("this session already has two players")
	}

	now := time.Now()
	result := h.DB.Model(&db.NetplaySession{}).
		Where("id = ? AND client_user_id IS NULL AND status = ?", session.ID, "waiting").
		Updates(map[string]interface{}{
			"client_user_id": uid,
			"status":         "in_progress",
			"started_at":     now,
		})
	if result.RowsAffected == 0 {
		return nil, huma.Error409Conflict("this session already has two players")
	}
	session.ClientUserID = &uid
	session.Status = "in_progress"
	session.StartedAt = &now

	h.DB.Preload("HostUser").Preload("ClientUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)
	h.expireNetplayInvites(session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplayPlayerJoined, Payload: h.toSessionResponse(session)})
	}

	return &JoinNetplayByInviteCodeOutput{Body: h.toSessionResponse(session)}, nil
}

// HumaListNetplaySessions is the huma handler for GET /api/netplay/sessions.
func (h *NetplayHandler) HumaListNetplaySessions(ctx context.Context, in *ListNetplaySessionsInput) (*ListNetplaySessionsOutput, error) {
	uid := UserIDFromContext(ctx)

	page := in.Page
	if page < 1 {
		page = 1
	}
	pageSize := in.PageSize
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	staleThreshold := time.Now().Add(-15 * time.Minute)

	baseQuery := h.DB.Model(&db.NetplaySession{}).
		Where(
			"(host_user_id = ? OR client_user_id = ?) OR (status = ? AND created_at > ?)",
			uid, uid, "waiting", staleThreshold,
		)

	var total int64
	baseQuery.Count(&total)

	var sessions []db.NetplaySession
	if err := h.DB.
		Preload("HostUser").Preload("ClientUser").
		Preload("Game").Preload("Game.Console").
		Where(
			"(host_user_id = ? OR client_user_id = ?) OR (status = ? AND created_at > ?)",
			uid, uid, "waiting", staleThreshold,
		).
		Order("updated_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&sessions).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch sessions")
	}

	result := make([]NetplaySessionResponse, 0, len(sessions))
	for _, s := range sessions {
		result = append(result, h.toSessionResponse(s))
	}

	return &ListNetplaySessionsOutput{Body: PaginatedResponse[NetplaySessionResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	}}, nil
}

// humaLoadNetplaySession loads a netplay session by id, returning typed huma
// errors on failure.
func (h *NetplayHandler) humaLoadNetplaySession(id string) (db.NetplaySession, error) {
	sid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		return db.NetplaySession{}, huma.Error400BadRequest("invalid session ID")
	}
	var session db.NetplaySession
	if err := h.DB.Preload("HostUser").Preload("ClientUser").
		Preload("Game").Preload("Game.Console").
		First(&session, sid).Error; err != nil {
		return db.NetplaySession{}, huma.Error404NotFound("session not found")
	}
	return session, nil
}

// HumaGetNetplaySession is the huma handler for GET /api/netplay/sessions/{id}.
func (h *NetplayHandler) HumaGetNetplaySession(_ context.Context, in *GetNetplaySessionInput) (*GetNetplaySessionOutput, error) {
	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}
	return &GetNetplaySessionOutput{Body: h.toSessionResponse(session)}, nil
}

// HumaDeleteNetplaySession is the huma handler for DELETE /api/netplay/sessions/{id}.
func (h *NetplayHandler) HumaDeleteNetplaySession(ctx context.Context, in *DeleteNetplaySessionInput) (*DeleteNetplaySessionOutput, error) {
	uid := UserIDFromContext(ctx)
	role := UserRoleFromContext(ctx)
	isAdmin := db.IsAdminOrOwner(role)

	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}

	if session.HostUserID != uid && !isAdmin {
		return nil, huma.Error403Forbidden("only the host or an admin can delete the session")
	}

	if session.Status != "ended" {
		now := time.Now()
		endReason := "host_left"
		if isAdmin && session.HostUserID != uid {
			endReason = "admin_deleted"
		}
		h.DB.Model(&session).Updates(map[string]interface{}{
			"status":     "ended",
			"end_reason": endReason,
			"ended_at":   now,
		})
	}

	h.expireNetplayInvites(session.ID)
	h.DB.Where("netplay_session_id = ?", session.ID).Delete(&db.NetplayInvite{})
	h.DB.Delete(&session)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplaySessionDeleted, Payload: ws.NetplaySessionDeletedPayload{
			SessionID: strconv.FormatUint(uint64(session.ID), 10),
		}})
	}

	return &DeleteNetplaySessionOutput{Body: MessageResponse{Message: "session deleted"}}, nil
}

// HumaLeaveNetplaySession is the huma handler for POST /api/netplay/sessions/{id}/leave.
func (h *NetplayHandler) HumaLeaveNetplaySession(ctx context.Context, in *LeaveNetplaySessionInput) (*LeaveNetplaySessionOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}

	isHost := session.HostUserID == uid
	isClient := session.ClientUserID != nil && *session.ClientUserID == uid
	if !isHost && !isClient {
		return nil, huma.Error400BadRequest("not in this session")
	}
	if session.Status == "ended" {
		return nil, huma.Error400BadRequest("session has already ended")
	}

	now := time.Now()
	endReason := "client_left"
	if isHost {
		endReason = "host_left"
	}

	h.DB.Model(&session).Updates(map[string]interface{}{
		"status":     "ended",
		"end_reason": endReason,
		"ended_at":   now,
	})

	h.expireNetplayInvites(session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplaySessionEnded, Payload: ws.NetplaySessionEndedPayload{
			SessionID: strconv.FormatUint(uint64(session.ID), 10),
			EndReason: endReason,
		}})
	}

	return &LeaveNetplaySessionOutput{Body: LeaveNetplaySessionResponse{
		Message:   "session ended",
		EndReason: endReason,
	}}, nil
}

// HumaUpdateNetplaySettings is the huma handler for PUT /api/netplay/sessions/{id}/settings.
func (h *NetplayHandler) HumaUpdateNetplaySettings(ctx context.Context, in *UpdateNetplaySettingsInput) (*UpdateNetplaySettingsOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}

	if session.HostUserID != uid {
		return nil, huma.Error403Forbidden("only the host can change settings")
	}
	if session.Status == "ended" {
		return nil, huma.Error400BadRequest("cannot change settings of an ended session")
	}

	if in.Body.InputDelay != nil {
		if *in.Body.InputDelay < 1 || *in.Body.InputDelay > 10 {
			return nil, huma.Error400BadRequest("input delay must be between 1 and 10 frames")
		}
		h.DB.Model(&session).Update("input_delay", *in.Body.InputDelay)
		session.InputDelay = *in.Body.InputDelay
	}

	h.DB.Preload("HostUser").Preload("ClientUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplaySettingsUpdated, Payload: h.toSessionResponse(session)})
	}

	return &UpdateNetplaySettingsOutput{Body: h.toSessionResponse(session)}, nil
}

// HumaNetplayInviteUser is the huma handler for POST /api/netplay/sessions/{id}/invite.
func (h *NetplayHandler) HumaNetplayInviteUser(ctx context.Context, in *NetplayInviteUserInput) (*NetplayInviteUserOutput, error) {
	uid := UserIDFromContext(ctx)
	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}

	if session.HostUserID != uid {
		return nil, huma.Error403Forbidden("only the host can send invites")
	}
	if session.Status != "waiting" {
		return nil, huma.Error400BadRequest("can only invite players to a waiting session")
	}
	if strings.TrimSpace(in.Body.Username) == "" {
		return nil, huma.Error400BadRequest("invalid request: username is required")
	}

	var invitee db.User
	if err := h.DB.Where("username = ?", in.Body.Username).First(&invitee).Error; err != nil {
		return nil, huma.Error404NotFound("user not found")
	}
	if invitee.ID == uid {
		return nil, huma.Error400BadRequest("cannot invite yourself")
	}

	var existingInvite db.NetplayInvite
	if err := h.DB.Where("netplay_session_id = ? AND invitee_id = ? AND status = ?", session.ID, invitee.ID, "pending").First(&existingInvite).Error; err == nil {
		return nil, huma.Error409Conflict("user already has a pending invite for this session")
	}

	var invite db.NetplayInvite
	txErr := h.DB.Transaction(func(tx *gorm.DB) error {
		tx.Unscoped().Where("netplay_session_id = ? AND invitee_id = ? AND status = ?", session.ID, invitee.ID, "declined").Delete(&db.NetplayInvite{})
		invite = db.NetplayInvite{
			NetplaySessionID: session.ID,
			InviterID:        uid,
			InviteeID:        invitee.ID,
			Status:           "pending",
		}
		return tx.Create(&invite).Error
	})
	if txErr != nil {
		return nil, huma.Error409Conflict("failed to create invite")
	}

	h.DB.Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		First(&invite, invite.ID)

	resp := h.toNetplayInviteResponse(invite)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplayInviteSent, Payload: resp})
	}

	return &NetplayInviteUserOutput{Body: resp}, nil
}

// HumaAcceptNetplayInviteByCode is the huma handler for POST /api/netplay/invites/{code}/accept.
func (h *NetplayHandler) HumaAcceptNetplayInviteByCode(ctx context.Context, in *AcceptNetplayInviteByCodeInput) (*AcceptNetplayInviteByCodeOutput, error) {
	uid := UserIDFromContext(ctx)

	var invite db.NetplayInvite
	if err := h.DB.Preload("NetplaySession").First(&invite, in.Code).Error; err != nil {
		return nil, huma.Error404NotFound("invite not found")
	}
	if invite.InviteeID != uid {
		return nil, huma.Error403Forbidden("not authorized")
	}
	if invite.Status != "pending" {
		return nil, huma.Error400BadRequest("invite is no longer pending")
	}
	if invite.NetplaySession.Status != "waiting" {
		return nil, huma.Error400BadRequest("session is no longer accepting players")
	}

	invite.Status = "accepted"
	if err := h.DB.Save(&invite).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to accept invite")
	}

	h.DB.Model(&db.NetplayInvite{}).
		Where("netplay_session_id = ? AND id != ? AND status = ?", invite.NetplaySessionID, invite.ID, "pending").
		Update("status", "expired")

	h.DB.Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		First(&invite, invite.ID)

	resp := h.toNetplayInviteResponse(invite)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplayInviteAccepted, Payload: resp})
	}

	return &AcceptNetplayInviteByCodeOutput{Body: resp}, nil
}

// HumaDeclineNetplayInviteByCode is the huma handler for POST /api/netplay/invites/{code}/decline.
func (h *NetplayHandler) HumaDeclineNetplayInviteByCode(ctx context.Context, in *DeclineNetplayInviteByCodeInput) (*DeclineNetplayInviteByCodeOutput, error) {
	uid := UserIDFromContext(ctx)

	var invite db.NetplayInvite
	if err := h.DB.First(&invite, in.Code).Error; err != nil {
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
		h.Hub.Broadcast(ws.Event{Type: ws.EventNetplayInviteDeclined, Payload: ws.NetplayInviteDeclinedPayload{
			NetplaySessionID: strconv.FormatUint(uint64(invite.NetplaySessionID), 10),
			InviteeID:        strconv.FormatUint(uint64(uid), 10),
		}})
	}

	return &DeclineNetplayInviteByCodeOutput{Body: MessageResponse{Message: "invite declined"}}, nil
}

// HumaListNetplaySessionInvites is the huma handler for GET /api/netplay/sessions/{id}/invites.
func (h *NetplayHandler) HumaListNetplaySessionInvites(ctx context.Context, in *ListNetplaySessionInvitesInput) (*ListNetplaySessionInvitesOutput, error) {
	uid := UserIDFromContext(ctx)

	session, err := h.humaLoadNetplaySession(in.ID)
	if err != nil {
		return nil, err
	}

	if session.HostUserID != uid {
		return nil, huma.Error403Forbidden("only the host can view session invites")
	}

	var invites []db.NetplayInvite
	if err := h.DB.Where("netplay_session_id = ?", session.ID).
		Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch invites")
	}

	result := make([]NetplayInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toNetplayInviteResponse(inv))
	}

	return &ListNetplaySessionInvitesOutput{Body: result}, nil
}

// HumaListMyNetplayInvites is the huma handler for GET /api/netplay/invites.
func (h *NetplayHandler) HumaListMyNetplayInvites(ctx context.Context, _ *ListMyNetplayInvitesInput) (*ListMyNetplayInvitesOutput, error) {
	uid := UserIDFromContext(ctx)

	var invites []db.NetplayInvite
	if err := h.DB.Where("invitee_id = ? AND status = ?", uid, "pending").
		Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to fetch invites")
	}

	result := make([]NetplayInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toNetplayInviteResponse(inv))
	}

	return &ListMyNetplayInvitesOutput{Body: ListMyNetplayInvitesResponse{
		Data:  result,
		Total: len(result),
	}}, nil
}

// HumaGetPendingNetplayInviteCount is the huma handler for GET /api/netplay/invites/count.
func (h *NetplayHandler) HumaGetPendingNetplayInviteCount(ctx context.Context, _ *PendingNetplayInviteCountInput) (*PendingNetplayInviteCountOutput, error) {
	uid := UserIDFromContext(ctx)

	var count int64
	if err := h.DB.Model(&db.NetplayInvite{}).Where("invitee_id = ? AND status = ?", uid, "pending").Count(&count).Error; err != nil {
		return nil, huma.Error500InternalServerError("failed to count invites")
	}

	return &PendingNetplayInviteCountOutput{Body: PendingNetplayInviteCountResponse{Count: count}}, nil
}
