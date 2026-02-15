package api

import (
	"crypto/rand"
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

// RelayTurnTimeout is the duration after which an inactive turn lock expires.
const RelayTurnTimeout = 15 * time.Minute

// RelayHandler handles relay (shared play session) endpoints.
type RelayHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Hub     *ws.Hub
}

// CreateRelay creates a new relay for a game.
func (h *RelayHandler) CreateRelay(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		GameID string `json:"gameId" binding:"required"`
		Name   string `json:"name" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: gameId and name are required"})
		return
	}

	gid, err := strconv.ParseUint(req.GameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	// Verify game exists
	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	relay := db.Relay{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    req.Name,
		Status:  "active",
	}
	if err := h.DB.Create(&relay).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create relay"})
		return
	}

	// Auto-add creator as owner member
	member := db.RelayMember{
		RelayID:  relay.ID,
		UserID:   uid,
		Role:     "owner",
		JoinedAt: time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to add owner as member"})
		return
	}

	// Reload with associations
	h.DB.Preload("Members").Preload("Members.User").Preload("Game").Preload("Game.Console").Preload("Owner").First(&relay, relay.ID)

	CreateActivityEvent(h.DB, h.Hub, uid, "created_relay", uint(gid), map[string]interface{}{
		"relayName": req.Name,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_created", Payload: h.toRelayResponse(relay)})
	}

	c.JSON(http.StatusCreated, h.toRelayDetailResponse(relay))
}

// ListRelays returns relays where the current user is a member.
func (h *RelayHandler) ListRelays(c *gin.Context) {
	uid := getUserID(c)

	// Find relay IDs where user is a member
	var memberRows []db.RelayMember
	if err := h.DB.Where("user_id = ?", uid).Find(&memberRows).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch relays"})
		return
	}

	relayIDs := make([]uint, 0, len(memberRows))
	for _, m := range memberRows {
		relayIDs = append(relayIDs, m.RelayID)
	}

	if len(relayIDs) == 0 {
		c.JSON(http.StatusOK, []RelayResponse{})
		return
	}

	var relays []db.Relay
	if err := h.DB.Where("id IN ?", relayIDs).
		Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").
		Preload("Owner").
		Order("updated_at DESC").
		Find(&relays).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch relays"})
		return
	}

	result := make([]RelayResponse, 0, len(relays))
	for _, r := range relays {
		result = append(result, h.toRelayResponse(r))
	}

	c.JSON(http.StatusOK, result)
}

// GetRelay returns details for a single relay.
func (h *RelayHandler) GetRelay(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	c.JSON(http.StatusOK, h.toRelayDetailResponse(relay))
}

// UpdateRelay updates a relay's name or status. Owner only.
func (h *RelayHandler) UpdateRelay(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req struct {
		Name   *string `json:"name"`
		Status *string `json:"status"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if req.Name != nil {
		relay.Name = *req.Name
	}
	if req.Status != nil {
		allowed := map[string]bool{"active": true, "completed": true, "archived": true}
		if !allowed[*req.Status] {
			c.JSON(http.StatusBadRequest, gin.H{"error": "invalid status: must be active, completed, or archived"})
			return
		}
		relay.Status = *req.Status
	}

	if err := h.DB.Save(&relay).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update relay"})
		return
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_updated", Payload: h.toRelayResponse(relay)})
	}

	c.JSON(http.StatusOK, h.toRelayResponse(relay))
}

// DeleteRelay deletes a relay. Owner only.
func (h *RelayHandler) DeleteRelay(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	relayID := relay.ID

	// Clean up save files from disk before deleting DB records
	var saves []db.RelaySave
	h.DB.Where("relay_id = ?", relayID).Find(&saves)
	for _, s := range saves {
		h.Storage.DeleteRelaySave(s.FilePath)
	}

	// Delete saves, invites, members, then the relay
	h.DB.Where("relay_id = ?", relayID).Delete(&db.RelaySave{})
	h.DB.Where("relay_id = ?", relayID).Delete(&db.RelayInvite{})
	h.DB.Where("relay_id = ?", relayID).Delete(&db.RelayMember{})
	h.DB.Delete(&relay)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_deleted", Payload: gin.H{"relayId": strconv.FormatUint(uint64(relayID), 10)}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "relay deleted"})
}

// InviteUser invites a user to a relay by username.
func (h *RelayHandler) InviteUser(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var req struct {
		Username string `json:"username" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: username is required"})
		return
	}

	// Find invitee
	var invitee db.User
	if err := h.DB.Where("username = ?", req.Username).First(&invitee).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "user not found"})
		return
	}

	// Cannot invite yourself
	if invitee.ID == uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot invite yourself"})
		return
	}

	// Check if already a member
	var existingMember db.RelayMember
	if err := h.DB.Where("relay_id = ? AND user_id = ?", relay.ID, invitee.ID).First(&existingMember).Error; err == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "user is already a member"})
		return
	}

	// Check if already invited (pending)
	var existingInvite db.RelayInvite
	if err := h.DB.Where("relay_id = ? AND invitee_id = ? AND status = ?", relay.ID, invitee.ID, "pending").First(&existingInvite).Error; err == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "user already has a pending invite"})
		return
	}

	// If previously declined, hard-delete the old invite so a new one can be created
	// (must be unscoped to bypass soft delete, otherwise the unique index blocks re-creation)
	h.DB.Unscoped().Where("relay_id = ? AND invitee_id = ? AND status = ?", relay.ID, invitee.ID, "declined").Delete(&db.RelayInvite{})

	invite := db.RelayInvite{
		RelayID:   relay.ID,
		InviterID: uid,
		InviteeID: invitee.ID,
		Status:    "pending",
	}
	if err := h.DB.Create(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create invite"})
		return
	}

	// Reload with associations
	h.DB.Preload("Inviter").Preload("Invitee").Preload("Relay").Preload("Relay.Game").First(&invite, invite.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_invite_sent", Payload: h.toInviteResponse(invite)})
	}

	c.JSON(http.StatusCreated, h.toInviteResponse(invite))
}

// ListMyInvites returns pending relay invites for the current user.
func (h *RelayHandler) ListMyInvites(c *gin.Context) {
	uid := getUserID(c)

	var invites []db.RelayInvite
	if err := h.DB.Where("invitee_id = ? AND status = ?", uid, "pending").
		Preload("Inviter").Preload("Invitee").
		Preload("Relay").Preload("Relay.Game").Preload("Relay.Game.Console").Preload("Relay.Owner").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch invites"})
		return
	}

	result := make([]RelayInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toInviteResponse(inv))
	}

	c.JSON(http.StatusOK, result)
}

// GetPendingInviteCount returns the number of pending relay invites for the current user.
func (h *RelayHandler) GetPendingInviteCount(c *gin.Context) {
	uid := getUserID(c)

	var count int64
	if err := h.DB.Model(&db.RelayInvite{}).Where("invitee_id = ? AND status = ?", uid, "pending").Count(&count).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to count invites"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"count": count})
}

// AcceptInvite accepts a relay invite.
func (h *RelayHandler) AcceptInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("id")

	var invite db.RelayInvite
	if err := h.DB.Preload("Relay").Preload("Relay.Game").First(&invite, inviteID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "invite not found"})
		return
	}

	if invite.InviteeID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	if invite.Status != "pending" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invite is no longer pending"})
		return
	}

	// Update invite status
	invite.Status = "accepted"
	if err := h.DB.Save(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to accept invite"})
		return
	}

	// Create membership
	member := db.RelayMember{
		RelayID:  invite.RelayID,
		UserID:   uid,
		Role:     "member",
		JoinedAt: time.Now(),
	}
	if err := h.DB.Create(&member).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to join relay"})
		return
	}

	CreateActivityEvent(h.DB, h.Hub, uid, "joined_relay", invite.Relay.GameID, map[string]interface{}{
		"relayName": invite.Relay.Name,
	})

	if h.Hub != nil {
		var user db.User
		h.DB.First(&user, uid)
		h.Hub.Broadcast(ws.Event{Type: "relay_invite_accepted", Payload: gin.H{
			"relayId":  strconv.FormatUint(uint64(invite.RelayID), 10),
			"userId":   strconv.FormatUint(uint64(uid), 10),
			"username": user.Username,
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "invite accepted"})
}

// DeclineInvite declines a relay invite.
func (h *RelayHandler) DeclineInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("id")

	var invite db.RelayInvite
	if err := h.DB.First(&invite, inviteID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "invite not found"})
		return
	}

	if invite.InviteeID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not authorized"})
		return
	}

	if invite.Status != "pending" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invite is no longer pending"})
		return
	}

	invite.Status = "declined"
	if err := h.DB.Save(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to decline invite"})
		return
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_invite_declined", Payload: gin.H{
			"relayId":   strconv.FormatUint(uint64(invite.RelayID), 10),
			"inviteeId": strconv.FormatUint(uint64(uid), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "invite declined"})
}

// LeaveRelay removes the current user from a relay.
func (h *RelayHandler) LeaveRelay(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if relay.OwnerID == uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "owner cannot leave the relay; transfer ownership or delete it"})
		return
	}

	result := h.DB.Where("relay_id = ? AND user_id = ?", relay.ID, uid).Delete(&db.RelayMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "membership not found"})
		return
	}

	// If the leaving user held the turn, release it
	if relay.ActiveUserID != nil && *relay.ActiveUserID == uid {
		h.DB.Model(&relay).Updates(map[string]interface{}{
			"active_user_id": nil,
			"turn_token":     "",
			"turn_taken_at":  nil,
		})
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_member_left", Payload: gin.H{
			"relayId": strconv.FormatUint(uint64(relay.ID), 10),
			"userId":  strconv.FormatUint(uint64(uid), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "left relay"})
}

// RemoveMember removes a member from a relay. Owner only.
func (h *RelayHandler) RemoveMember(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	userIDStr := c.Param("userId")
	targetUID, err := strconv.ParseUint(userIDStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid user ID"})
		return
	}

	if uint(targetUID) == uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot remove yourself; use leave or delete the relay"})
		return
	}

	result := h.DB.Where("relay_id = ? AND user_id = ?", relay.ID, targetUID).Delete(&db.RelayMember{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "member not found"})
		return
	}

	// If the removed user held the turn, release it
	if relay.ActiveUserID != nil && *relay.ActiveUserID == uint(targetUID) {
		h.DB.Model(&relay).Updates(map[string]interface{}{
			"active_user_id": nil,
			"turn_token":     "",
			"turn_taken_at":  nil,
		})
	}

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_member_removed", Payload: gin.H{
			"relayId": strconv.FormatUint(uint64(relay.ID), 10),
			"userId":  strconv.FormatUint(uint64(targetUID), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "member removed"})
}

// --- Turn control ---

// TakeTurn acquires the turn for the current user.
func (h *RelayHandler) TakeTurn(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if relay.Status != "active" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "relay is not active"})
		return
	}

	now := time.Now()

	// Check if turn is currently held by someone else
	if relay.ActiveUserID != nil && *relay.ActiveUserID != uid {
		// Check if the turn is stale (expired)
		if relay.TurnTakenAt != nil && relay.TurnTakenAt.Add(RelayTurnTimeout).After(now) {
			// Turn is still active and held by someone else
			c.JSON(http.StatusConflict, gin.H{"error": "turn is currently held by another user"})
			return
		}
		// Turn is stale — force-release and broadcast expiry
		previousUserID := *relay.ActiveUserID
		if h.Hub != nil {
			h.Hub.Broadcast(ws.Event{Type: "relay_turn_expired", Payload: gin.H{
				"relayId":        strconv.FormatUint(uint64(relay.ID), 10),
				"previousUserId": strconv.FormatUint(uint64(previousUserID), 10),
			}})
		}
	}

	// Grant the turn
	token := generateTurnToken()
	h.DB.Model(&relay).Updates(map[string]interface{}{
		"active_user_id": uid,
		"turn_token":     token,
		"turn_taken_at":  now,
	})

	var user db.User
	h.DB.First(&user, uid)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_turn_acquired", Payload: gin.H{
			"relayId":  strconv.FormatUint(uint64(relay.ID), 10),
			"userId":   strconv.FormatUint(uint64(uid), 10),
			"username": user.Username,
		}})
	}

	c.JSON(http.StatusOK, gin.H{
		"turnToken":   token,
		"turnTakenAt": now,
	})
}

// ReleaseTurn releases the current user's turn.
func (h *RelayHandler) ReleaseTurn(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if relay.ActiveUserID == nil || *relay.ActiveUserID != uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "you do not hold the turn"})
		return
	}

	h.DB.Model(&relay).Updates(map[string]interface{}{
		"active_user_id": nil,
		"turn_token":     "",
		"turn_taken_at":  nil,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_turn_released", Payload: gin.H{
			"relayId": strconv.FormatUint(uint64(relay.ID), 10),
			"userId":  strconv.FormatUint(uint64(uid), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "turn released"})
}

// Heartbeat extends the current user's turn timeout.
func (h *RelayHandler) Heartbeat(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	if relay.ActiveUserID == nil || *relay.ActiveUserID != uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "you do not hold the turn"})
		return
	}

	now := time.Now()
	h.DB.Model(&relay).Update("turn_taken_at", now)

	c.JSON(http.StatusOK, gin.H{
		"relayId":     strconv.FormatUint(uint64(relay.ID), 10),
		"turnTakenAt": now,
	})
}

// --- Relay saves ---

// ListSaves returns save states for a relay.
func (h *RelayHandler) ListSaves(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var saves []db.RelaySave
	if err := h.DB.Where("relay_id = ?", relay.ID).
		Preload("User").
		Order("created_at DESC").
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch saves"})
		return
	}

	result := make([]RelaySaveResponse, 0, len(saves))
	for _, s := range saves {
		result = append(result, h.toRelaySaveResponse(s))
	}

	c.JSON(http.StatusOK, result)
}

// UploadSave uploads a manual save to a relay. Requires active turn.
func (h *RelayHandler) UploadSave(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	// Verify the caller holds the turn
	if relay.ActiveUserID == nil || *relay.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "you must hold the turn to upload saves"})
		return
	}

	// Verify turn token
	turnToken := c.GetHeader("X-Turn-Token")
	if turnToken == "" || turnToken != relay.TurnToken {
		c.JSON(http.StatusForbidden, gin.H{"error": "invalid turn token"})
		return
	}

	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	name := c.DefaultPostForm("name", header.Filename)
	screenshotURL := c.PostForm("screenshotUrl")

	filePath, size, err := h.Storage.WriteRelaySave(relay.ID, header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	save := db.RelaySave{
		RelayID:       relay.ID,
		UserID:        uid,
		Name:          name,
		FilePath:      filePath,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
		return
	}

	h.DB.Preload("User").First(&save, save.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_save_uploaded", Payload: gin.H{
			"relayId": strconv.FormatUint(uint64(relay.ID), 10),
			"saveId":  strconv.FormatUint(uint64(save.ID), 10),
			"userId":  strconv.FormatUint(uint64(uid), 10),
			"isAuto":  false,
		}})
	}

	c.JSON(http.StatusCreated, h.toRelaySaveResponse(save))
}

// DownloadSave serves a relay save file.
func (h *RelayHandler) DownloadSave(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.RelaySave
	if err := h.DB.Where("id = ? AND relay_id = ?", saveID, relay.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", save.Name))
	c.File(save.FilePath)
}

// DeleteSave removes a relay save. Owner only.
func (h *RelayHandler) DeleteSave(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.RelaySave
	if err := h.DB.Where("id = ? AND relay_id = ?", saveID, relay.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	if err := h.Storage.DeleteRelaySave(save.FilePath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete save file"})
		return
	}

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "save deleted"})
}

// UploadAutoSave uploads or updates the relay's auto-save. Requires active turn.
func (h *RelayHandler) UploadAutoSave(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	// Verify the caller holds the turn
	if relay.ActiveUserID == nil || *relay.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "you must hold the turn to upload saves"})
		return
	}

	// Verify turn token
	turnToken := c.GetHeader("X-Turn-Token")
	if turnToken == "" || turnToken != relay.TurnToken {
		c.JSON(http.StatusForbidden, gin.H{"error": "invalid turn token"})
		return
	}

	file, _, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	filename := "autosave.sav"
	screenshotURL := c.PostForm("screenshotUrl")

	filePath, size, err := h.Storage.WriteRelaySave(relay.ID, filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	// Upsert: update existing auto-save or create new
	var save db.RelaySave
	result := h.DB.Where("relay_id = ? AND is_auto = ?", relay.ID, true).First(&save)
	if result.Error == gorm.ErrRecordNotFound {
		save = db.RelaySave{
			RelayID:       relay.ID,
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

	h.DB.Preload("User").First(&save, save.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "relay_save_uploaded", Payload: gin.H{
			"relayId": strconv.FormatUint(uint64(relay.ID), 10),
			"saveId":  strconv.FormatUint(uint64(save.ID), 10),
			"userId":  strconv.FormatUint(uint64(uid), 10),
			"isAuto":  true,
		}})
	}

	c.JSON(http.StatusOK, h.toRelaySaveResponse(save))
}

// GetAutoSave serves the relay's latest auto-save.
func (h *RelayHandler) GetAutoSave(c *gin.Context) {
	uid := getUserID(c)
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return
	}

	var save db.RelaySave
	if err := h.DB.Where("relay_id = ? AND is_auto = ?", relay.ID, true).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no auto-save found"})
		return
	}

	c.Header("Content-Disposition", "attachment; filename=\"autosave.sav\"")
	c.File(save.FilePath)
}

// --- Helpers ---

// loadRelayWithMemberCheck loads a relay by :id param and verifies the user is a member.
func (h *RelayHandler) loadRelayWithMemberCheck(c *gin.Context, uid uint) (db.Relay, bool) {
	id := c.Param("id")
	rid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid relay ID"})
		return db.Relay{}, false
	}

	var relay db.Relay
	if err := h.DB.Preload("Members").Preload("Members.User").
		Preload("Game").Preload("Game.Console").Preload("Owner").
		First(&relay, rid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "relay not found"})
		return db.Relay{}, false
	}

	// Check membership
	isMember := false
	for _, m := range relay.Members {
		if m.UserID == uid {
			isMember = true
			break
		}
	}
	if !isMember {
		c.JSON(http.StatusForbidden, gin.H{"error": "not a member of this relay"})
		return db.Relay{}, false
	}

	return relay, true
}

// loadRelayWithOwnerCheck loads a relay by :id param and verifies the user is the owner.
func (h *RelayHandler) loadRelayWithOwnerCheck(c *gin.Context, uid uint) (db.Relay, bool) {
	relay, ok := h.loadRelayWithMemberCheck(c, uid)
	if !ok {
		return db.Relay{}, false
	}

	if relay.OwnerID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "only the relay owner can perform this action"})
		return db.Relay{}, false
	}

	return relay, true
}

// generateTurnToken creates a cryptographically random turn token.
func generateTurnToken() string {
	b := make([]byte, 16)
	rand.Read(b)
	return hex.EncodeToString(b)
}

// --- Response converters ---

func (h *RelayHandler) toRelayResponse(r db.Relay) RelayResponse {
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

	return RelayResponse{
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
		MemberCount:    len(r.Members),
		CreatedAt:      r.CreatedAt,
		UpdatedAt:      r.UpdatedAt,
	}
}

func (h *RelayHandler) toRelayDetailResponse(r db.Relay) RelayDetailResponse {
	members := make([]RelayMemberResponse, 0, len(r.Members))
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
		members = append(members, RelayMemberResponse{
			ID:        strconv.FormatUint(uint64(m.ID), 10),
			UserID:    strconv.FormatUint(uint64(m.UserID), 10),
			Username:  username,
			AvatarURL: avatarURL,
			Role:      m.Role,
			JoinedAt:  m.JoinedAt,
		})
	}

	return RelayDetailResponse{
		RelayResponse: h.toRelayResponse(r),
		Members:       members,
	}
}

func (h *RelayHandler) toInviteResponse(inv db.RelayInvite) RelayInviteResponse {
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

	relayName := inv.Relay.Name
	gameTitle := inv.Relay.Game.Title

	return RelayInviteResponse{
		ID:              strconv.FormatUint(uint64(inv.ID), 10),
		RelayID:         strconv.FormatUint(uint64(inv.RelayID), 10),
		RelayName:       relayName,
		GameTitle:       gameTitle,
		InviterID:       strconv.FormatUint(uint64(inv.InviterID), 10),
		InviterUsername: inviterName,
		InviteeID:       strconv.FormatUint(uint64(inv.InviteeID), 10),
		InviteeUsername: inviteeName,
		Status:          inv.Status,
		CreatedAt:       inv.CreatedAt,
	}
}

func (h *RelayHandler) toRelaySaveResponse(s db.RelaySave) RelaySaveResponse {
	username := s.User.Username
	if username == "" && s.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, s.UserID).Error; err == nil {
			username = user.Username
		}
	}

	return RelaySaveResponse{
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		RelayID:       strconv.FormatUint(uint64(s.RelayID), 10),
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
