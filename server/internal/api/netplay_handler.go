package api

import (
	"crypto/rand"
	"log/slog"
	"math/big"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// netplaySupportedConsoles lists console abbreviations that support netplay in Phase 1.
var netplaySupportedConsoles = map[string]bool{
	"NES": true, "SNES": true, "GB": true, "GBC": true, "GBA": true, "GEN": true,
}

// NetplayHandler handles netplay session endpoints.
type NetplayHandler struct {
	DB         *gorm.DB
	Hub        *ws.Hub
	NetplayHub *ws.NetplayHub
}

// CreateSession creates a new netplay session.
func (h *NetplayHandler) CreateSession(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		GameID     string `json:"gameId" binding:"required"`
		InputDelay *int   `json:"inputDelay"`
		CoreName   string `json:"coreName"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request: gameId is required"})
		return
	}

	gid, err := strconv.ParseUint(req.GameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	if !game.Console.Playable {
		c.JSON(http.StatusBadRequest, gin.H{"error": errNonPlayableConsole.Error()})
		return
	}

	// Validate console is supported for netplay
	if !netplaySupportedConsoles[game.Console.Abbreviation] {
		c.JSON(http.StatusBadRequest, gin.H{"error": "netplay is not supported for " + game.Console.Name + " yet"})
		return
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create session"})
		return
	}

	h.DB.Preload("HostUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_session_created", Payload: h.toSessionResponse(session)})
	}

	c.JSON(http.StatusCreated, h.toSessionResponse(session))
}

// ListSessions returns the user's sessions (as host or client) plus waiting sessions.
func (h *NetplayHandler) ListSessions(c *gin.Context) {
	uid := getUserID(c)

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	// Exclude stale waiting sessions (older than 15 minutes)
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch sessions"})
		return
	}

	result := make([]NetplaySessionResponse, 0, len(sessions))
	for _, s := range sessions {
		result = append(result, h.toSessionResponse(s))
	}

	c.JSON(http.StatusOK, PaginatedResponse{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// GetSession returns session details.
func (h *NetplayHandler) GetSession(c *gin.Context) {
	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	c.JSON(http.StatusOK, h.toSessionResponse(session))
}

// JoinByInviteCode joins a session using an invite code.
func (h *NetplayHandler) JoinByInviteCode(c *gin.Context) {
	uid := getUserID(c)

	var req struct {
		InviteCode string `json:"inviteCode" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invite code is required"})
		return
	}

	// Normalize to uppercase for case-insensitive matching
	code := strings.ToUpper(strings.TrimSpace(req.InviteCode))

	var session db.NetplaySession
	if err := h.DB.
		Preload("HostUser").Preload("ClientUser").
		Preload("Game").Preload("Game.Console").
		Where("invite_code = ?", code).First(&session).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "session not found. The code may have expired or the host may have cancelled."})
		return
	}

	// Can't join your own session
	if session.HostUserID == uid {
		c.JSON(http.StatusBadRequest, gin.H{"error": "you can't join your own session"})
		return
	}

	if session.Status == "ended" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "this session has already ended"})
		return
	}

	if session.Status != "waiting" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "this session is no longer accepting players"})
		return
	}

	if session.ClientUserID != nil {
		c.JSON(http.StatusConflict, gin.H{"error": "this session already has two players"})
		return
	}

	// Join the session atomically — conditional UPDATE prevents race where two
	// players pass the nil check concurrently.
	now := time.Now()
	result := h.DB.Model(&db.NetplaySession{}).
		Where("id = ? AND client_user_id IS NULL AND status = ?", session.ID, "waiting").
		Updates(map[string]interface{}{
			"client_user_id": uid,
			"status":         "in_progress",
			"started_at":     now,
		})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusConflict, gin.H{"error": "this session already has two players"})
		return
	}
	session.ClientUserID = &uid
	session.Status = "in_progress"
	session.StartedAt = &now

	// Reload with associations
	h.DB.Preload("HostUser").Preload("ClientUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_player_joined", Payload: h.toSessionResponse(session)})
	}

	c.JSON(http.StatusOK, h.toSessionResponse(session))
}

// LeaveSession removes a player from a session and ends it.
func (h *NetplayHandler) LeaveSession(c *gin.Context) {
	uid := getUserID(c)

	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	isHost := session.HostUserID == uid
	isClient := session.ClientUserID != nil && *session.ClientUserID == uid

	if !isHost && !isClient {
		c.JSON(http.StatusBadRequest, gin.H{"error": "not in this session"})
		return
	}

	if session.Status == "ended" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "session has already ended"})
		return
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

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_session_ended", Payload: gin.H{
			"sessionId": strconv.FormatUint(uint64(session.ID), 10),
			"endReason": endReason,
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "session ended", "endReason": endReason})
}

// DeleteSession cancels a waiting session. Host only.
func (h *NetplayHandler) DeleteSession(c *gin.Context) {
	uid := getUserID(c)

	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	if session.HostUserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "only the host can cancel the session"})
		return
	}

	if session.Status != "waiting" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "can only cancel a waiting session. Use leave to end an active session."})
		return
	}

	now := time.Now()
	h.DB.Model(&session).Updates(map[string]interface{}{
		"status":     "ended",
		"end_reason": "host_left",
		"ended_at":   now,
	})

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_session_deleted", Payload: gin.H{
			"sessionId": strconv.FormatUint(uint64(session.ID), 10),
		}})
	}

	c.JSON(http.StatusOK, gin.H{"message": "session cancelled"})
}

// UpdateSettings updates the input delay for a session. Host only.
func (h *NetplayHandler) UpdateSettings(c *gin.Context) {
	uid := getUserID(c)

	session, ok := h.loadSession(c)
	if !ok {
		return
	}

	if session.HostUserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "only the host can change settings"})
		return
	}

	if session.Status == "ended" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot change settings of an ended session"})
		return
	}

	var req struct {
		InputDelay *int `json:"inputDelay"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if req.InputDelay != nil {
		if *req.InputDelay < 1 || *req.InputDelay > 10 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "input delay must be between 1 and 10 frames"})
			return
		}
		h.DB.Model(&session).Update("input_delay", *req.InputDelay)
		session.InputDelay = *req.InputDelay
	}

	h.DB.Preload("HostUser").Preload("ClientUser").Preload("Game").Preload("Game.Console").First(&session, session.ID)

	if h.Hub != nil {
		h.Hub.Broadcast(ws.Event{Type: "netplay_settings_updated", Payload: h.toSessionResponse(session)})
	}

	c.JSON(http.StatusOK, h.toSessionResponse(session))
}

// HandleWebSocket upgrades the connection and joins a netplay room.
func (h *NetplayHandler) HandleWebSocket(c *gin.Context) {
	uid := getUserID(c)

	idStr := c.Param("id")
	sessionID, err := strconv.ParseUint(idStr, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid session ID"})
		return
	}

	var session db.NetplaySession
	if err := h.DB.First(&session, sessionID).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "session not found"})
		return
	}

	// Verify the user is a participant
	isHost := session.HostUserID == uid
	isClient := session.ClientUserID != nil && *session.ClientUserID == uid
	if !isHost && !isClient {
		c.JSON(http.StatusForbidden, gin.H{"error": "not a player in this session"})
		return
	}

	if session.Status == "ended" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "session has ended"})
		return
	}

	h.NetplayHub.HandleNetplayWebSocket(c, uint(sessionID), uid)
}

// --- Helpers ---

// loadSession loads a session by :id param with all associations.
func (h *NetplayHandler) loadSession(c *gin.Context) (db.NetplaySession, bool) {
	id := c.Param("id")
	sid, err := strconv.ParseUint(id, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid session ID"})
		return db.NetplaySession{}, false
	}

	var session db.NetplaySession
	if err := h.DB.Preload("HostUser").Preload("ClientUser").
		Preload("Game").Preload("Game.Console").
		First(&session, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "session not found"})
		return db.NetplaySession{}, false
	}

	return session, true
}

// inviteCodeAlphabet contains uppercase alphanumeric characters excluding ambiguous ones (0/O, 1/I/L).
const inviteCodeAlphabet = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"

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
	var coverAspect float64
	if s.Game.Console.ID != 0 {
		consoleName = s.Game.Console.Name
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
