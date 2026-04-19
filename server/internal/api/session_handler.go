package api

import (
	"strconv"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// SessionHandler handles game session endpoints.
type SessionHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
}

// autoSaveRetentionLimit returns the maximum number of auto-saves to keep
// based on the size of the save being uploaded.
//
//	< 1 MB  → 5
//	1-10 MB → 3
//	> 10 MB → 1
func autoSaveRetentionLimit(sizeBytes int64) int {
	const mb = 1 << 20
	switch {
	case sizeBytes > 10*mb:
		return 1
	case sizeBytes >= 1*mb:
		return 3
	default:
		return 5
	}
}

// --- Helper methods ---

func (h *SessionHandler) getSaveCounts(sessions []db.GameSession) map[uint]int {
	if len(sessions) == 0 {
		return nil
	}

	ids := make([]uint, len(sessions))
	for i, s := range sessions {
		ids[i] = s.ID
	}

	type countRow struct {
		SessionID uint
		Count     int
	}
	var rows []countRow
	h.DB.Model(&db.SessionSaveState{}).
		Where("session_id IN ?", ids).
		Select("session_id, COUNT(*) as count").
		Group("session_id").
		Scan(&rows)

	result := make(map[uint]int, len(rows))
	for _, r := range rows {
		result[r.SessionID] = r.Count
	}
	return result
}

func (h *SessionHandler) toSessionResponse(s db.GameSession, saveCount int) GameSessionResponse {
	var lastPlayedBy *string
	if s.LastPlayedBy != nil {
		str := strconv.FormatUint(uint64(*s.LastPlayedBy), 10)
		lastPlayedBy = &str
	}

	return GameSessionResponse{
		ID:              strconv.FormatUint(uint64(s.ID), 10),
		OwnerID:         strconv.FormatUint(uint64(s.OwnerID), 10),
		OwnerUsername:   s.Owner.Username,
		GameID:          strconv.FormatUint(uint64(s.GameID), 10),
		Name:            s.Name,
		LastPlayedAt:    s.LastPlayedAt,
		LastPlayedBy:    lastPlayedBy,
		TotalPlayTime:   s.TotalPlayTime,
		ScreenshotURL:   resolveImageURL(s.ScreenshotURL),
		CoreName:        s.CoreName,
		CheatsEnabled:   s.CheatsEnabled,
		SaveCount:       saveCount,
		MemberUsernames: []string{},
		MemberAvatars:   []string{},
		CreatedAt:       s.CreatedAt,
		UpdatedAt:       s.UpdatedAt,
	}
}

// resolveLastPlayedByUsernames batch-loads usernames for sessions that have a LastPlayedBy user ID.
func (h *SessionHandler) resolveLastPlayedByUsernames(sessions []db.GameSession) map[uint]string {
	result := make(map[uint]string)
	userIDs := make(map[uint][]uint) // userID -> list of sessionIDs
	for _, s := range sessions {
		if s.LastPlayedBy != nil {
			userIDs[*s.LastPlayedBy] = append(userIDs[*s.LastPlayedBy], s.ID)
		}
	}
	if len(userIDs) == 0 {
		return result
	}

	ids := make([]uint, 0, len(userIDs))
	for uid := range userIDs {
		ids = append(ids, uid)
	}

	var users []db.User
	h.DB.Where("id IN ?", ids).Find(&users)
	usernameMap := make(map[uint]string, len(users))
	for _, u := range users {
		usernameMap[u.ID] = u.Username
	}

	for _, s := range sessions {
		if s.LastPlayedBy != nil {
			if username, ok := usernameMap[*s.LastPlayedBy]; ok {
				result[s.ID] = username
			}
		}
	}
	return result
}

func (h *SessionHandler) toSaveResponse(s db.SessionSaveState, currentCore string) SessionSaveResponse {
	resp := SessionSaveResponse{
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		SessionID:     strconv.FormatUint(uint64(s.SessionID), 10),
		UserID:        strconv.FormatUint(uint64(s.UserID), 10),
		Username:      s.User.Username,
		Name:          s.Name,
		FileSize:      s.FileSize,
		ScreenshotURL: resolveImageURL(s.ScreenshotURL),
		IsAuto:        s.IsAuto,
		IsCurrent:     s.IsCurrent,
		CoreName:      s.CoreName,
		Notes:         s.Notes,
		Slot:          s.Slot,
		CreatedAt:     s.CreatedAt,
		UpdatedAt:     s.UpdatedAt,
	}
	if currentCore != "" {
		resp.CurrentCore = currentCore
		if s.CoreName != "" {
			match := s.CoreName == currentCore
			resp.CoreMatch = &match
		}
	}
	return resp
}

// getRecommendedCoreName returns the recommended core name for a game,
// considering the game's core override and the console's default core.
func (h *SessionHandler) getRecommendedCoreName(gameID uint) string {
	var game db.Game
	if err := h.DB.Preload("Console").First(&game, gameID).Error; err != nil {
		return ""
	}
	if game.CoreOverride != "" {
		return game.CoreOverride
	}
	return game.Console.DefaultCore
}

// enrichWithSharedSessionData looks up whether a session backs a shared session
// and adds member info to the response if so.
func (h *SessionHandler) enrichWithSharedSessionData(resp *GameSessionResponse, sessionID uint) {
	var ss db.SharedSession
	if err := h.DB.Where("session_id = ?", sessionID).
		Preload("Members.User").
		First(&ss).Error; err != nil {
		return
	}
	ssID := strconv.FormatUint(uint64(ss.ID), 10)
	resp.IsSharedSession = true
	resp.SharedSessionID = &ssID
	resp.MemberCount = len(ss.Members)
	usernames := make([]string, 0, len(ss.Members))
	avatars := make([]string, 0, len(ss.Members))
	for _, m := range ss.Members {
		usernames = append(usernames, m.User.Username)
		if m.User.AvatarURL != "" {
			avatars = append(avatars, m.User.AvatarURL)
		}
	}
	resp.MemberUsernames = usernames
	resp.MemberAvatars = avatars
}
