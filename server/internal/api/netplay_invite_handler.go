package api

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SendInvite sends a netplay session invite to a user by username.
func (h *NetplayHandler) SendInvite(c *gin.Context) {
	uid := getUserID(c)

	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	// Only the host can send invites (AC-1.7)
	if session.HostUserID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "only the host can send invites"})
		return
	}

	// Session must be waiting (AC-1.1)
	if session.Status != "waiting" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "can only invite players to a waiting session"})
		return
	}

	var req struct {
		Username string `json:"username" binding:"required"`
	}
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

	// Cannot invite yourself (AC-1.3 — exclude myself)
	if invitee.ID == uid {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "cannot invite yourself"})
		return
	}

	// Check if already has a pending invite (AC-1.8)
	var existingInvite db.NetplayInvite
	if err := h.DB.Where("netplay_session_id = ? AND invitee_id = ? AND status = ?", session.ID, invitee.ID, "pending").First(&existingInvite).Error; err == nil {
		c.JSON(http.StatusConflict, ErrorResponse{Error: "user already has a pending invite for this session"})
		return
	}

	// If previously declined, hard-delete the old invite and create the new one
	// atomically to avoid unique constraint violations under concurrent requests.
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
		c.JSON(http.StatusConflict, ErrorResponse{Error: "failed to create invite"})
		return
	}

	// Reload with associations
	h.DB.Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		First(&invite, invite.ID)

	resp := h.toNetplayInviteResponse(invite)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_invite_sent", Payload: resp})
	}

	c.JSON(http.StatusCreated, resp)
}

// ListSessionInvites lists all invites for a session (host view, AC-3.1).
func (h *NetplayHandler) ListSessionInvites(c *gin.Context) {
	uid := getUserID(c)

	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	// Only the host can view session invites
	if session.HostUserID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "only the host can view session invites"})
		return
	}

	var invites []db.NetplayInvite
	if err := h.DB.Where("netplay_session_id = ?", session.ID).
		Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch invites"})
		return
	}

	result := make([]NetplayInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toNetplayInviteResponse(inv))
	}

	c.JSON(http.StatusOK, result)
}

// ListMyNetplayInvites returns pending netplay invites for the current user (AC-2.3).
func (h *NetplayHandler) ListMyNetplayInvites(c *gin.Context) {
	uid := getUserID(c)

	var invites []db.NetplayInvite
	if err := h.DB.Where("invitee_id = ? AND status = ?", uid, "pending").
		Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		Order("created_at DESC").
		Find(&invites).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch invites"})
		return
	}

	result := make([]NetplayInviteResponse, 0, len(invites))
	for _, inv := range invites {
		result = append(result, h.toNetplayInviteResponse(inv))
	}

	c.JSON(http.StatusOK, gin.H{"data": result, "total": len(result)})
}

// GetPendingNetplayInviteCount returns the number of pending netplay invites for the current user (AC-2.2).
func (h *NetplayHandler) GetPendingNetplayInviteCount(c *gin.Context) {
	uid := getUserID(c)

	var count int64
	if err := h.DB.Model(&db.NetplayInvite{}).Where("invitee_id = ? AND status = ?", uid, "pending").Count(&count).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to count invites"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"count": count})
}

// AcceptNetplayInvite accepts a netplay invite (AC-2.5, AC-2.6).
func (h *NetplayHandler) AcceptNetplayInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("inviteId")

	var invite db.NetplayInvite
	if err := h.DB.Preload("NetplaySession").First(&invite, inviteID).Error; err != nil {
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

	// Check session is still waiting
	if invite.NetplaySession.Status != "waiting" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "session is no longer accepting players"})
		return
	}

	// Update invite status
	invite.Status = "accepted"
	if err := h.DB.Save(&invite).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to accept invite"})
		return
	}

	// Expire all other pending invites for this session (AC-5.1)
	h.DB.Model(&db.NetplayInvite{}).
		Where("netplay_session_id = ? AND id != ? AND status = ?", invite.NetplaySessionID, invite.ID, "pending").
		Update("status", "expired")

	// Reload with associations for response
	h.DB.Preload("Inviter").Preload("Invitee").
		Preload("NetplaySession").Preload("NetplaySession.HostUser").
		Preload("NetplaySession.Game").Preload("NetplaySession.Game.Console").
		First(&invite, invite.ID)

	resp := h.toNetplayInviteResponse(invite)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_invite_accepted", Payload: resp})
	}

	c.JSON(http.StatusOK, resp)
}

// DeclineNetplayInvite declines a netplay invite (AC-2.5, AC-2.7).
func (h *NetplayHandler) DeclineNetplayInvite(c *gin.Context) {
	uid := getUserID(c)
	inviteID := c.Param("inviteId")

	var invite db.NetplayInvite
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
		h.Hub.Broadcast(ws.Event{Type: "netplay_invite_declined", Payload: gin.H{
			"netplaySessionId": strconv.FormatUint(uint64(invite.NetplaySessionID), 10),
			"inviteeId":        strconv.FormatUint(uint64(uid), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "invite declined"})
}

// expireNetplayInvites marks all pending invites for a session as expired (AC-5.1, AC-5.2).
func (h *NetplayHandler) expireNetplayInvites(sessionID uint) {
	h.DB.Model(&db.NetplayInvite{}).
		Where("netplay_session_id = ? AND status = ?", sessionID, "pending").
		Update("status", "expired")
}

// --- Response converter ---

func (h *NetplayHandler) toNetplayInviteResponse(inv db.NetplayInvite) NetplayInviteResponse {
	coverURL := inv.NetplaySession.Game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	consoleName := ""
	if inv.NetplaySession.Game.Console.ID != 0 {
		consoleName = inv.NetplaySession.Game.Console.Name
	}

	hostUsername := inv.NetplaySession.HostUser.Username

	return NetplayInviteResponse{
		ID:               strconv.FormatUint(uint64(inv.ID), 10),
		NetplaySessionID: strconv.FormatUint(uint64(inv.NetplaySessionID), 10),
		InviterID:        strconv.FormatUint(uint64(inv.InviterID), 10),
		InviterUsername:  inv.Inviter.Username,
		InviterAvatarURL: inv.Inviter.AvatarURL,
		InviteeID:        strconv.FormatUint(uint64(inv.InviteeID), 10),
		InviteeUsername:  inv.Invitee.Username,
		InviteeAvatarURL: inv.Invitee.AvatarURL,
		GameID:           strconv.FormatUint(uint64(inv.NetplaySession.GameID), 10),
		GameTitle:        inv.NetplaySession.Game.Title,
		GameCoverURL:     coverURL,
		ConsoleName:      consoleName,
		HostUsername:     hostUsername,
		InputDelay:       inv.NetplaySession.InputDelay,
		Status:           inv.Status,
		CreatedAt:        inv.CreatedAt,
	}
}
