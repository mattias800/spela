package api

import (
	"crypto/rand"
	"log/slog"
	"math/big"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// netplaySupportedConsoles lists console abbreviations that support netplay in Phase 1.
var netplaySupportedConsoles = map[string]bool{
	"NES": true, "SNES": true, "GB": true, "GBC": true, "GBA": true, "GEN": true,
}

// inviteCodeAlphabet contains uppercase alphanumeric characters excluding ambiguous ones (0/O, 1/I/L).
const inviteCodeAlphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

// NetplayHandler handles netplay session endpoints.
type NetplayHandler struct {
	DB         *gorm.DB
	Hub        *ws.Hub
	NetplayHub *ws.NetplayHub
}
func (h *NetplayHandler) HandleWebSocket(c *gin.Context) {
	uid := getUserID(c)

	idStr := c.Param("id")
	sessionID, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid session ID"})
		return
	}

	var session db.NetplaySession
	if err := h.DB.First(&session, sessionID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "session not found"})
		return
	}

	// Verify the user is a participant
	isHost := session.HostUserID == uid
	isClient := session.ClientUserID != nil && *session.ClientUserID == uid
	if !isHost && !isClient {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not a player in this session"})
		return
	}

	if session.Status == "ended" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "session has ended"})
		return
	}

	h.NetplayHub.HandleNetplayWebSocket(c, uint(sessionID), uid)
}
// generateInviteCode creates a 6-character invite code using safe alphanumeric characters.
func generateInviteCode() string {
	code := make([]byte, 6)
	alphabetLen := big.NewInt(int64(len(inviteCodeAlphabet)))
	for i := range code {
		n, err := rand.Int(rand.Reader, alphabetLen)
		if err != nil {
			slog.Error("failed to generate random invite code char", "error", err)
			code[i] = inviteCodeAlphabet[i]
			continue
		}
		code[i] = inviteCodeAlphabet[n.Int64()]
	}
	return string(code)
}

// --- Response converter ---

func (h *NetplayHandler) toSessionResponse(s db.NetplaySession) NetplaySessionResponse {
	hostUsername := s.HostUser.Username
	hostAvatarURL := s.HostUser.AvatarURL
	if hostUsername == "" && s.HostUserID != 0 {
		var user db.User
		if err := h.DB.First(&user, s.HostUserID).Error; err == nil {
			hostUsername = user.Username
			hostAvatarURL = user.AvatarURL
		}
	}

	var clientUserID *string
	var clientUsername, clientAvatarURL string
	if s.ClientUserID != nil {
		cidStr := strconv.FormatUint(uint64(*s.ClientUserID), 10)
		clientUserID = &cidStr
		clientUsername = s.ClientUser.Username
		clientAvatarURL = s.ClientUser.AvatarURL
		if clientUsername == "" && *s.ClientUserID != 0 {
			var user db.User
			if err := h.DB.First(&user, *s.ClientUserID).Error; err == nil {
				clientUsername = user.Username
				clientAvatarURL = user.AvatarURL
			}
		}
	}

	coverURL := s.Game.CoverURL
	if coverURL != "" && !strings.HasPrefix(coverURL, "http") {
		coverURL = "/api/images/" + coverURL
	}

	consoleName := ""
	consoleID := ""
	var coverAspect float64
	if s.Game.Console.ID != 0 {
		consoleName = s.Game.Console.Name
		if s.Game.Console.Code != nil {
			consoleID = *s.Game.Console.Code
		}
		coverAspect = parseAspectRatio(s.Game.Console.CoverAspect)
	} else {
		coverAspect = 0.75
	}

	return NetplaySessionResponse{
		ID:               strconv.FormatUint(uint64(s.ID), 10),
		HostUserID:       strconv.FormatUint(uint64(s.HostUserID), 10),
		HostUsername:     hostUsername,
		HostAvatarURL:    hostAvatarURL,
		ClientUserID:     clientUserID,
		ClientUsername:   clientUsername,
		ClientAvatarURL:  clientAvatarURL,
		GameID:           strconv.FormatUint(uint64(s.GameID), 10),
		GameTitle:        s.Game.Title,
		GameCoverURL:     coverURL,
		ConsoleName:      consoleName,
		ConsoleID:        consoleID,
		CoverAspectRatio: coverAspect,
		Status:           s.Status,
		EndReason:       s.EndReason,
		InputDelay:      s.InputDelay,
		CoreName:        s.CoreName,
		InviteCode:      s.InviteCode,
		CreatedAt:       s.CreatedAt,
		StartedAt:       s.StartedAt,
		EndedAt:         s.EndedAt,
	}
}
