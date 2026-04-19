package api

import (
	"crypto/rand"
	"encoding/hex"
	"strconv"
	"strings"
	"time"

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
		Members:               members,
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
		ID:                strconv.FormatUint(uint64(inv.ID), 10),
		SharedSessionID:   strconv.FormatUint(uint64(inv.SharedSessionID), 10),
		SharedSessionName: sharedSessionName,
		GameTitle:         gameTitle,
		InviterID:         strconv.FormatUint(uint64(inv.InviterID), 10),
		InviterUsername:   inviterName,
		InviteeID:         strconv.FormatUint(uint64(inv.InviteeID), 10),
		InviteeUsername:   inviteeName,
		Status:            inv.Status,
		CreatedAt:         inv.CreatedAt,
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
		ID:              strconv.FormatUint(uint64(s.ID), 10),
		SharedSessionID: strconv.FormatUint(uint64(s.SharedSessionID), 10),
		UserID:          strconv.FormatUint(uint64(s.UserID), 10),
		Username:        username,
		Name:            s.Name,
		FileSize:        s.FileSize,
		ScreenshotURL:   s.ScreenshotURL,
		IsAuto:          s.IsAuto,
		CreatedAt:       s.CreatedAt,
		UpdatedAt:       s.UpdatedAt,
	}
}

func uintPtrToStringPtr(p *uint) *string {
	if p == nil {
		return nil
	}
	s := strconv.FormatUint(uint64(*p), 10)
	return &s
}
