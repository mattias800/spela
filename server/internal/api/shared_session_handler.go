package api

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SharedSessionTurnTimeout is the duration after which an inactive turn lock expires.
const SharedSessionTurnTimeout = 15 * time.Minute

// SharedSessionHandler handles shared session endpoints.
type SharedSessionHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Hub     *ws.Hub
}

// CreateSharedSession creates a new shared session for a game.
func (h *SharedSessionHandler) CreateSharedSession(c *gin.Context) {
	uid := getUserID(c)

	var req CreateSharedSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request: gameId and name are required"})
		return
	}

	gid, err := strconv.ParseUint(req.GameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	// Verify game exists and load console for core resolution
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	// Determine the recommended core name for this game
	coreName := game.CoreOverride
	if coreName == "" {
		coreName = game.Console.DefaultCore
	}

	// Create a GameSession to back this shared session
	session := db.GameSession{
		OwnerID:  uid,
		GameID:   uint(gid),
		Name:     "Shared Session: " + req.Name,
		CoreName: coreName,
	}
	if err := h.DB.Create(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create session for shared session"})
		return
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
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create shared session"})
		return
	}

	// Auto-add creator as owner member
	member := db.SharedSessionMember{
		SharedSessionID:  ss.ID,
		UserID:   uid,
		Role:     "owner",
		JoinedAt: time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to add owner as member"})
		return
	}

	// Reload with associations
	h.DB.Preload("Members").Preload("Members.User").Preload("Game").Preload("Game.Console").Preload("Owner").First(&ss, ss.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "created_shared_session", uint(gid), CreatedSharedSessionMetadata{
		SharedSessionName: req.Name,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionCreated, Payload: h.toSharedSessionResponse(ss)})
	}

	c.JSON(http.StatusCreated, h.toSharedSessionDetailResponse(ss))
}

// ListGameSharedSessions returns all active shared sessions for a specific game.
func (h *SharedSessionHandler) ListGameSharedSessions(c *gin.Context) {
	gameID := c.Param("id")
	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	var sharedSessions []db.SharedSession
	if err := h.DB.Where("game_id = ? AND status = ?", gid, "active").
		Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").
		Preload("Owner").
		Order("updated_at DESC").
		Find(&sharedSessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch shared sessions"})
		return
	}

	result := make([]SharedSessionResponse, 0, len(sharedSessions))
	for _, r := range sharedSessions {
		result = append(result, h.toSharedSessionResponse(r))
	}

	c.JSON(http.StatusOK, result)
}

// ListSharedSessions returns shared sessions where the current user is a member.
func (h *SharedSessionHandler) ListSharedSessions(c *gin.Context) {
	uid := getUserID(c)

	// Find shared session IDs where user is a member
	var memberRows []db.SharedSessionMember
	if err := h.DB.Where("user_id = ?", uid).Find(&memberRows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch shared sessions"})
		return
	}

	sharedSessionIDs := make([]uint, 0, len(memberRows))
	for _, m := range memberRows {
		sharedSessionIDs = append(sharedSessionIDs, m.SharedSessionID)
	}

	if len(sharedSessionIDs) == 0 {
		c.JSON(http.StatusOK, []SharedSessionResponse{})
		return
	}

	var sharedSessions []db.SharedSession
	if err := h.DB.Where("id IN ?", sharedSessionIDs).
		Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").
		Preload("Owner").
		Order("updated_at DESC").
		Find(&sharedSessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch shared sessions"})
		return
	}

	result := make([]SharedSessionResponse, 0, len(sharedSessions))
	for _, r := range sharedSessions {
		result = append(result, h.toSharedSessionResponse(r))
	}

	c.JSON(http.StatusOK, result)
}

// GetSharedSession returns details for a single shared session.
func (h *SharedSessionHandler) GetSharedSession(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	c.JSON(http.StatusOK, h.toSharedSessionDetailResponse(ss))
}

// UpdateSharedSession updates a shared session's name or status. Owner only.
func (h *SharedSessionHandler) UpdateSharedSession(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req UpdateSharedSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request"})
		return
	}

	if req.Name != nil {
		if len(*req.Name) > 255 {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name must be 255 characters or fewer"})
			return
		}
		ss.Name = *req.Name
	}
	if req.Status != nil {
		allowed := map[string]bool{"active": true, "completed": true, "archived": true}
		if !allowed[*req.Status] {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid status: must be active, completed, or archived"})
			return
		}
		ss.Status = *req.Status
	}

	if err := h.DB.Save(&ss).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update shared session"})
		return
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionUpdated, Payload: h.toSharedSessionResponse(ss)})
	}

	c.JSON(http.StatusOK, h.toSharedSessionResponse(ss))
}

// DeleteSharedSession deletes a shared session. Owner only.
func (h *SharedSessionHandler) DeleteSharedSession(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	sharedSessionID := ss.ID

	// Clean up save files from disk before deleting DB records
	var saves []db.SharedSessionSave
	h.DB.Where("shared_session_id = ?", sharedSessionID).Find(&saves)
	for _, s := range saves {
		h.Storage.DeleteSharedSessionSave(s.FilePath)
	}

	// Delete saves, invites, members, then the shared session
	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionSave{})
	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionInvite{})
	h.DB.Where("shared_session_id = ?", sharedSessionID).Delete(&db.SharedSessionMember{})
	h.DB.Delete(&ss)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionDeleted, Payload: ws.SharedSessionDeletedPayload{SharedSessionID: strconv.FormatUint(uint64(sharedSessionID), 10)}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "shared session deleted"})
}

// InviteUser invites a user to a shared session by username.
func (h *SharedSessionHandler) InviteUser(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var req InviteToSharedSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request: username is required"})
		return
	}

	// Find invitee
	var invitee db.User
	if err := h.DB.Where("username = ?", req.Username).First(&invitee).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "user not found"})
		return
	}

	// Cannot invite yourself
	if invitee.ID == uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot invite yourself"})
		return
	}

	// Check if already a member
	var existingMember db.SharedSessionMember
	if err := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, invitee.ID).First(&existingMember).Error; err == nil {
		c.JSON(http.StatusConflict, ErrorResponse{Error: "user is already a member"})
		return
	}

	// Check if already invited (pending)
	var existingInvite db.SharedSessionInvite
	if err := h.DB.Where("shared_session_id = ? AND invitee_id = ? AND status = ?", ss.ID, invitee.ID, "pending").First(&existingInvite).Error; err == nil {
		c.JSON(http.StatusConflict, ErrorResponse{Error: "user already has a pending invite"})
		return
	}

	// If previously declined, hard-delete the old invite so a new one can be created
	// (must be unscoped to bypass soft delete, otherwise the unique index blocks re-creation)
	h.DB.Unscoped().Where("shared_session_id = ? AND invitee_id = ? AND status = ?", ss.ID, invitee.ID, "declined").Delete(&db.SharedSessionInvite{})

	invite := db.SharedSessionInvite{
		SharedSessionID:   ss.ID,
		InviterID: uid,
		InviteeID: invitee.ID,
		Status:    "pending",
	}
	if err := h.DB.Create(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create invite"})
		return
	}

	// Reload with associations
	h.DB.Preload("Inviter").Preload("Invitee").Preload("SharedSession").Preload("SharedSession.Game").First(&invite, invite.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionInviteSent, Payload: h.toSharedSessionInviteResponse(invite)})
	}

	c.JSON(http.StatusCreated, h.toSharedSessionInviteResponse(invite))
}

// ListMyInvites returns pending shared session invites for the current user.
func (h *SharedSessionHandler) ListMyInvites(c *gin.Context) {
	uid := getUserID(c)

	var invites []db.SharedSessionInvite
	if err := h.DB.Where("invitee_id = ? AND status = ?", uid, "pending").
		Preload("Inviter").Preload("Invitee").
		Preload("SharedSession").Preload("SharedSession.Game").Preload("SharedSession.Game.Console").Preload("SharedSession.Owner").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch invites"})
		return
	}

	result := make([]SharedSessionInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toSharedSessionInviteResponse(inv))
	}

	c.JSON(http.StatusOK, result)
}

// GetPendingInviteCount returns the number of pending shared session invites for the current user.
func (h *SharedSessionHandler) GetPendingInviteCount(c *gin.Context) {
	uid := getUserID(c)

	var count int64
	if err := h.DB.Model(&db.SharedSessionInvite{}).Where("invitee_id = ? AND status = ?", uid, "pending").Count(&count).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to count invites"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"count": count})
}

// AcceptInvite accepts a shared session invite.
func (h *SharedSessionHandler) AcceptInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("id")

	var invite db.SharedSessionInvite
	if err := h.DB.Preload("SharedSession").Preload("SharedSession.Game").First(&invite, inviteID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "invite not found"})
		return
	}

	if invite.InviteeID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not authorized"})
		return
	}

	if invite.Status != "pending" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invite is no longer pending"})
		return
	}

	// Update invite status
	invite.Status = "accepted"
	if err := h.DB.Save(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to accept invite"})
		return
	}

	// Create membership
	member := db.SharedSessionMember{
		SharedSessionID:  invite.SharedSessionID,
		UserID:   uid,
		Role:     "member",
		JoinedAt: time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to join shared session"})
		return
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

	c.JSON(http.StatusOK, gin.H{"message": "invite accepted"})
}

// DeclineInvite declines a shared session invite.
func (h *SharedSessionHandler) DeclineInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("id")

	var invite db.SharedSessionInvite
	if err := h.DB.First(&invite, inviteID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "invite not found"})
		return
	}

	if invite.InviteeID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not authorized"})
		return
	}

	if invite.Status != "pending" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invite is no longer pending"})
		return
	}

	invite.Status = "declined"
	if err := h.DB.Save(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to decline invite"})
		return
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionInviteDeclined, Payload: ws.SharedSessionInviteDeclinedPayload{
			SharedSessionID: strconv.FormatUint(uint64(invite.SharedSessionID), 10),
			InviteeID:       strconv.FormatUint(uint64(uid), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "invite declined"})
}

// LeaveSharedSession removes the current user from a shared session.
func (h *SharedSessionHandler) LeaveSharedSession(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if ss.OwnerID == uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "owner cannot leave the shared session; transfer ownership or delete it"})
		return
	}

	result := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, uid).Delete(&db.SharedSessionMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "membership not found"})
		return
	}

	// If the leaving user held the turn, release it
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

	c.JSON(http.StatusOK, gin.H{"message": "left shared session"})
}

// RemoveMember removes a member from a shared session. Owner only.
func (h *SharedSessionHandler) RemoveMember(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	userIDStr := c.Param("userId")
	targetUID, err := strconv.ParseUint(userIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid user ID"})
		return
	}

	if uint(targetUID) == uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot remove yourself; use leave or delete the shared session"})
		return
	}

	result := h.DB.Where("shared_session_id = ? AND user_id = ?", ss.ID, targetUID).Delete(&db.SharedSessionMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "member not found"})
		return
	}

	// If the removed user held the turn, release it
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

	c.JSON(http.StatusOK, gin.H{"message": "member removed"})
}

// --- Turn control ---

// TakeTurn acquires the turn for the current user.
func (h *SharedSessionHandler) TakeTurn(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if ss.Status != "active" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "shared session is not active"})
		return
	}

	now := time.Now()
	token := generateTurnToken()

	// Atomically acquire the turn inside a transaction. The WHERE clause
	// ensures that only one concurrent request can succeed — the loser sees
	// RowsAffected == 0 and gets a conflict response.
	var previousUserID *uint
	err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Re-read the shared session inside the transaction for consistency
		var fresh db.SharedSession
		if err := tx.First(&fresh, ss.ID).Error; err != nil {
			return err
		}

		if fresh.ActiveUserID != nil && *fresh.ActiveUserID != uid {
			if fresh.TurnTakenAt != nil && fresh.TurnTakenAt.Add(SharedSessionTurnTimeout).After(now) {
				return fmt.Errorf("turn_held")
			}
			// Turn is stale — record previous holder for broadcast
			prev := *fresh.ActiveUserID
			previousUserID = &prev
		}

		// Atomic update: only succeed if shared session state hasn't changed
		result := tx.Model(&db.SharedSession{}).
			Where("id = ? AND updated_at = ?", fresh.ID, fresh.UpdatedAt).
			Updates(map[string]interface{}{
				"active_user_id": uid,
				"turn_token":     token,
				"turn_taken_at":  now,
			})
		if result.RowsAffected == 0 {
			return fmt.Errorf("turn_conflict")
		}
		return nil
	})
	if err != nil {
		if err.Error() == "turn_held" {
			c.JSON(http.StatusConflict, ErrorResponse{Error: "turn is currently held by another user"})
			return
		}
		if err.Error() == "turn_conflict" {
			c.JSON(http.StatusConflict, ErrorResponse{Error: "turn was acquired by another user, please retry"})
			return
		}
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to acquire turn"})
		return
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

	c.JSON(http.StatusOK, gin.H{
		"turnToken":   token,
		"turnTakenAt": now,
	})
}

// ReleaseTurn releases the current user's turn.
func (h *SharedSessionHandler) ReleaseTurn(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "you do not hold the turn"})
		return
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

	c.JSON(http.StatusOK, gin.H{"message": "turn released"})
}

// Heartbeat extends the current user's turn timeout.
func (h *SharedSessionHandler) Heartbeat(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "you do not hold the turn"})
		return
	}

	now := time.Now()
	h.DB.Model(&ss).Update("turn_taken_at", now)

	c.JSON(http.StatusOK, gin.H{
		"sharedSessionId":     strconv.FormatUint(uint64(ss.ID), 10),
		"turnTakenAt": now,
	})
}

// --- Shared session saves ---

// ListSaves returns save states for a shared session.
func (h *SharedSessionHandler) ListSaves(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var saves []db.SharedSessionSave
	if err := h.DB.Where("shared_session_id = ?", ss.ID).
		Preload("User").
		Order("created_at DESC").
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch saves"})
		return
	}

	result := make([]SharedSessionSaveResponse, 0, len(saves))
	for _, s := range saves {
		result = append(result, h.toSharedSessionSaveResponse(s))
	}

	c.JSON(http.StatusOK, result)
}

// UploadSave uploads a manual save to a shared session. Requires active turn.
func (h *SharedSessionHandler) UploadSave(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	// Verify the caller holds the turn
	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "you must hold the turn to upload saves"})
		return
	}

	// Verify turn token (constant-time comparison to prevent timing attacks)
	turnToken := c.GetHeader("X-Turn-Token")
	if turnToken == "" || subtle.ConstantTimeCompare([]byte(turnToken), []byte(ss.TurnToken)) != 1 {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "invalid turn token"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file required"})
		return
	}
	defer file.Close()

	name := c.DefaultPostForm("name", header.Filename)
	screenshotURL := c.PostForm("screenshotUrl")

	filePath, size, err := h.Storage.WriteSharedSessionSave(ss.ID, header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save file"})
		return
	}

	save := db.SharedSessionSave{
		SharedSessionID:       ss.ID,
		UserID:        uid,
		Name:          name,
		FilePath:      filePath,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create save record"})
		return
	}

	// Also create a SessionSaveState if the shared session has an associated session
	if ss.SessionID != nil {
		sessionSave := db.SessionSaveState{
			SessionID:     *ss.SessionID,
			UserID:        uid,
			Name:          name,
			FilePath:      filePath,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			IsAuto:        false,
		}
		h.DB.Create(&sessionSave)
	}

	h.DB.Preload("User").First(&save, save.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionSaveUploaded, Payload: ws.SharedSessionSaveUploadedPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			SaveID:          strconv.FormatUint(uint64(save.ID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
			IsAuto:          false,
		}})
	}

	c.JSON(http.StatusCreated, h.toSharedSessionSaveResponse(save))
}

// DownloadSave serves a shared session save file.
func (h *SharedSessionHandler) DownloadSave(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", saveID, ss.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	// Validate the file path is within the save directory
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", save.Name))
	c.File(save.FilePath)
}

// DeleteSave removes a shared session save. Owner only.
func (h *SharedSessionHandler) DeleteSave(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", saveID, ss.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	if err := h.Storage.DeleteSharedSessionSave(save.FilePath); err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to delete save file"})
		return
	}

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "save deleted"})
}

// UploadAutoSave uploads or updates the shared session's auto-save. Requires active turn.
func (h *SharedSessionHandler) UploadAutoSave(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	// Verify the caller holds the turn
	if ss.ActiveUserID == nil || *ss.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "you must hold the turn to upload saves"})
		return
	}

	// Verify turn token (constant-time comparison to prevent timing attacks)
	turnToken := c.GetHeader("X-Turn-Token")
	if turnToken == "" || subtle.ConstantTimeCompare([]byte(turnToken), []byte(ss.TurnToken)) != 1 {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "invalid turn token"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, _, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file required"})
		return
	}
	defer file.Close()

	filename := "autosave.sav"
	screenshotURL := c.PostForm("screenshotUrl")

	filePath, size, err := h.Storage.WriteSharedSessionSave(ss.ID, filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save file"})
		return
	}

	// Upsert: update existing auto-save or create new
	var save db.SharedSessionSave
	result := h.DB.Where("shared_session_id = ? AND is_auto = ?", ss.ID, true).First(&save)
	if result.Error == gorm.ErrRecordNotFound {
		save = db.SharedSessionSave{
			SharedSessionID:       ss.ID,
			UserID:        uid,
			Name:          "Auto Save",
			FilePath:      filePath,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			IsAuto:        true,
		}
		h.DB.Create(&save)
	} else {
		save.UserID = uid
		save.FilePath = filePath
		save.FileSize = size
		save.ScreenshotURL = screenshotURL
		h.DB.Save(&save)
	}

	// Also create a SessionSaveState if the shared session has an associated session
	if ss.SessionID != nil {
		sessionSave := db.SessionSaveState{
			SessionID:     *ss.SessionID,
			UserID:        uid,
			Name:          "Auto Save",
			FilePath:      filePath,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			IsAuto:        true,
		}
		h.DB.Create(&sessionSave)
	}

	h.DB.Preload("User").First(&save, save.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: ws.EventSharedSessionSaveUploaded, Payload: ws.SharedSessionSaveUploadedPayload{
			SharedSessionID: strconv.FormatUint(uint64(ss.ID), 10),
			SaveID:          strconv.FormatUint(uint64(save.ID), 10),
			UserID:          strconv.FormatUint(uint64(uid), 10),
			IsAuto:          true,
		}})
	}

	c.JSON(http.StatusOK, h.toSharedSessionSaveResponse(save))
}

// GetAutoSave serves the shared session's latest auto-save.
func (h *SharedSessionHandler) GetAutoSave(c *gin.Context) {
	uid := getUserID(c)
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("shared_session_id = ? AND is_auto = ?", ss.ID, true).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no auto-save found"})
		return
	}

	// Validate the file path is within the save directory
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	c.Header("Content-Disposition", "attachment; filename=\"autosave.sav\"")
	c.File(save.FilePath)
}

// RenameSharedSessionSave renames a shared session save state.
func (h *SharedSessionHandler) RenameSharedSessionSave(c *gin.Context) {
	uid := getUserID(c)
	sharedSessionID := c.Param("id")
	saveID := c.Param("saveId")

	rid, err := strconv.ParseUint(sharedSessionID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid shared session ID"})
		return
	}

	// Check membership
	var member db.SharedSessionMember
	if err := h.DB.Where("shared_session_id = ? AND user_id = ?", rid, uid).First(&member).Error; err != nil {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not a member of this shared session"})
		return
	}

	var save db.SharedSessionSave
	if err := h.DB.Where("id = ? AND shared_session_id = ?", saveID, rid).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	var body struct {
		Name string `json:"name" binding:"required,max=255"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name is required and must be 255 characters or fewer"})
		return
	}

	if strings.TrimSpace(body.Name) == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name cannot be empty"})
		return
	}

	save.Name = body.Name
	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to rename save"})
		return
	}

	c.JSON(http.StatusOK, save)
}

// --- Helpers ---

// loadSharedSessionWithMemberCheck loads a shared session by :id param and verifies the user is a member.
func (h *SharedSessionHandler) loadSharedSessionWithMemberCheck(c *gin.Context, uid uint) (db.SharedSession, bool) {
	id := c.Param("id")
	rid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid shared session ID"})
		return db.SharedSession{}, false
	}

	var ss db.SharedSession
	if err := h.DB.Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").Preload("Owner").
		First(&ss, rid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "shared session not found"})
		return db.SharedSession{}, false
	}

	// Check membership
	isMember := false
	for _, m := range ss.Members {
		if m.UserID == uid {
			isMember = true
			break
		}
	}
	if !isMember {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not a member of this shared session"})
		return db.SharedSession{}, false
	}

	return ss, true
}

// loadSharedSessionWithOwnerCheck loads a shared session by :id param and verifies the user is the owner.
func (h *SharedSessionHandler) loadSharedSessionWithOwnerCheck(c *gin.Context, uid uint) (db.SharedSession, bool) {
	ss, ok := h.loadSharedSessionWithMemberCheck(c, uid)
	if !ok {
		return db.SharedSession{}, false
	}

	if ss.OwnerID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "only the shared session owner can perform this action"})
		return db.SharedSession{}, false
	}

	return ss, true
}

// generateTurnToken creates a cryptographically random turn token.
func generateTurnToken() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// --- Response converters ---

func (h *SharedSessionHandler) toSharedSessionResponse(r db.SharedSession) SharedSessionResponse {
	ownerUsername := r.Owner.Username
	if ownerUsername == "" && r.OwnerID != 0 {
		var user db.User
		if err := h.DB.First(&user, r.OwnerID).Error; err == nil {
			ownerUsername = user.Username
		}
	}

	coverURL := r.Game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	consoleName := ""
	if r.Game.Console.ID != 0 {
		consoleName = r.Game.Console.Name
	}

	var activeUsername string
	if r.ActiveUserID != nil {
		for _, m := range r.Members {
			if m.UserID == *r.ActiveUserID {
				activeUsername = m.User.Username
				break
			}
		}
	}

	return SharedSessionResponse{
		ID:             strconv.FormatUint(uint64(r.ID), 10),
		OwnerID:        strconv.FormatUint(uint64(r.OwnerID), 10),
		OwnerUsername:  ownerUsername,
		GameID:         strconv.FormatUint(uint64(r.GameID), 10),
		GameTitle:      r.Game.Title,
		GameCoverURL:   coverURL,
		ConsoleName:    consoleName,
		Name:           r.Name,
		Status:         r.Status,
		ActiveUserID:   uintPtrToStringPtr(r.ActiveUserID),
		ActiveUsername: activeUsername,
		TurnTakenAt:    r.TurnTakenAt,
		CoreName:       r.CoreName,
		MemberCount:    len(r.Members),
		SessionID:      uintPtrToStringPtr(r.SessionID),
		CreatedAt:      r.CreatedAt,
		UpdatedAt:      r.UpdatedAt,
	}
}

func (h *SharedSessionHandler) toSharedSessionDetailResponse(r db.SharedSession) SharedSessionDetailResponse {
	members := make([]SharedSessionMemberResponse, 0, len(r.Members))
	for _, m := range r.Members {
		username := m.User.Username
		avatarURL := m.User.AvatarURL
		if username == "" && m.UserID != 0 {
			var user db.User
			if err := h.DB.First(&user, m.UserID).Error; err == nil {
				username = user.Username
				avatarURL = user.AvatarURL
			}
		}
		members = append(members, SharedSessionMemberResponse{
			ID:        strconv.FormatUint(uint64(m.ID), 10),
			UserID:    strconv.FormatUint(uint64(m.UserID), 10),
			Username:  username,
			AvatarURL: avatarURL,
			Role:      m.Role,
			JoinedAt:  m.JoinedAt,
		})
	}

	return SharedSessionDetailResponse{
		SharedSessionResponse: h.toSharedSessionResponse(r),
		Members:       members,
	}
}

func (h *SharedSessionHandler) toSharedSessionInviteResponse(inv db.SharedSessionInvite) SharedSessionInviteResponse {
	inviterName := inv.Inviter.Username
	inviteeName := inv.Invitee.Username

	if inviterName == "" && inv.InviterID != 0 {
		var user db.User
		if err := h.DB.First(&user, inv.InviterID).Error; err == nil {
			inviterName = user.Username
		}
	}
	if inviteeName == "" && inv.InviteeID != 0 {
		var user db.User
		if err := h.DB.First(&user, inv.InviteeID).Error; err == nil {
			inviteeName = user.Username
		}
	}

	sharedSessionName := inv.SharedSession.Name
	gameTitle := inv.SharedSession.Game.Title

	return SharedSessionInviteResponse{
		ID:              strconv.FormatUint(uint64(inv.ID), 10),
		SharedSessionID:         strconv.FormatUint(uint64(inv.SharedSessionID), 10),
		SharedSessionName:       sharedSessionName,
		GameTitle:       gameTitle,
		InviterID:       strconv.FormatUint(uint64(inv.InviterID), 10),
		InviterUsername: inviterName,
		InviteeID:       strconv.FormatUint(uint64(inv.InviteeID), 10),
		InviteeUsername: inviteeName,
		Status:          inv.Status,
		CreatedAt:       inv.CreatedAt,
	}
}

func (h *SharedSessionHandler) toSharedSessionSaveResponse(s db.SharedSessionSave) SharedSessionSaveResponse {
	username := s.User.Username
	if username == "" && s.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, s.UserID).Error; err == nil {
			username = user.Username
		}
	}

	return SharedSessionSaveResponse{
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		SharedSessionID:       strconv.FormatUint(uint64(s.SharedSessionID), 10),
		UserID:        strconv.FormatUint(uint64(s.UserID), 10),
		Username:      username,
		Name:          s.Name,
		FileSize:      s.FileSize,
		ScreenshotURL: s.ScreenshotURL,
		IsAuto:        s.IsAuto,
		CreatedAt:     s.CreatedAt,
		UpdatedAt:     s.UpdatedAt,
	}
}

func uintPtrToStringPtr(p *uint) *string {
	if p == nil {
		return nil
	}
	s := strconv.FormatUint(uint64(*p), 10)
	return &s
}
