package api

import (
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
)

// Netplay invite request handlers all live in huma_netplay.go now. The two
// helpers below (expireNetplayInvites, toNetplayInviteResponse) are still
// referenced from there.

// expireNetplayInvites marks all pending invites for a session as expired (AC-5.1, AC-5.2).
func (h *NetplayHandler) expireNetplayInvites(sessionID uint) {
	h.DB.Model(&db.NetplayInvite{}).
		Where("netplay_session_id = ? AND status = ?", sessionID, "pending").
		Update("status", "expired")
}

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
