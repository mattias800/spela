package api

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// SessionHandler handles game session endpoints.
type SessionHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
}

// CreateSession creates a new game session.
// POST /api/games/:id/sessions
func (h *SessionHandler) CreateSession(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var req CreateSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name is required and must be 255 characters or fewer"})
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name cannot be empty"})
		return
	}

	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    req.Name,
	}
	if err := h.DB.Create(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create session"})
		return
	}

	h.DB.Preload("Owner").First(&session, session.ID)
	c.JSON(http.StatusCreated, h.toSessionResponse(session, 0))
}

// CreateFromSharedSave creates a new game session from a community shared save.
// POST /api/games/:id/sessions/from-shared-save/:saveId
func (h *SessionHandler) CreateFromSharedSave(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")
	saveID := c.Param("saveId")

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	sid, err := strconv.ParseUint(saveID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid save ID"})
		return
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "game not found"})
		return
	}

	var sharedSave db.SharedSaveState
	if err := h.DB.First(&sharedSave, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "shared save not found"})
		return
	}

	if sharedSave.GameID != uint(gid) {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "shared save does not belong to this game"})
		return
	}

	// Create session
	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    fmt.Sprintf("From: %s", sharedSave.Name),
	}
	if err := h.DB.Create(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create session"})
		return
	}

	// Copy the shared save file to the new session
	srcFile, err := os.Open(sharedSave.FilePath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to read shared save file"})
		return
	}
	defer srcFile.Close()

	filename := filepath.Base(sharedSave.FilePath)
	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, srcFile)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to copy save file"})
		return
	}

	// Create session save state record
	save := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          sharedSave.Name,
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: sharedSave.ScreenshotURL,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create save record"})
		return
	}

	// Increment download count on the shared save
	h.DB.Model(&sharedSave).UpdateColumn("download_count", gorm.Expr("download_count + ?", 1))

	h.DB.Preload("Owner").First(&session, session.ID)
	c.JSON(http.StatusCreated, h.toSessionResponse(session, 1))
}

// ListSessions lists all sessions for a game belonging to the current user,
// including backing sessions from shared sessions where the user is a member.
// GET /api/games/:id/sessions
func (h *SessionHandler) ListSessions(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid game ID"})
		return
	}

	// 1. Get user's own sessions
	var ownSessions []db.GameSession
	if err := h.DB.Where("owner_id = ? AND game_id = ?", uid, gid).
		Preload("Owner").
		Order("updated_at DESC").
		Find(&ownSessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch sessions"})
		return
	}

	ownSessionIDs := make(map[uint]bool, len(ownSessions))
	for _, s := range ownSessions {
		ownSessionIDs[s.ID] = true
	}

	// 2. Find shared sessions where user is a member and game matches
	var sharedSessions []db.SharedSession
	h.DB.Joins("JOIN shared_session_members ON shared_session_members.shared_session_id = shared_sessions.id").
		Where("shared_session_members.user_id = ? AND shared_sessions.game_id = ? AND shared_sessions.session_id IS NOT NULL AND shared_session_members.deleted_at IS NULL", uid, gid).
		Preload("Members.User").
		Find(&sharedSessions)

	// 3. Build session ID -> shared session map
	sharedSessionMap := make(map[uint]*db.SharedSession, len(sharedSessions))
	for i := range sharedSessions {
		if sharedSessions[i].SessionID != nil {
			sharedSessionMap[*sharedSessions[i].SessionID] = &sharedSessions[i]
		}
	}

	// 4. Load backing sessions that the user doesn't own (to avoid duplicates)
	var extraSessionIDs []uint
	for sid := range sharedSessionMap {
		if !ownSessionIDs[sid] {
			extraSessionIDs = append(extraSessionIDs, sid)
		}
	}

	var extraSessions []db.GameSession
	if len(extraSessionIDs) > 0 {
		h.DB.Where("id IN ?", extraSessionIDs).
			Preload("Owner").
			Find(&extraSessions)
	}

	// 5. Merge and sort
	allSessions := append(ownSessions, extraSessions...)

	// Batch-load save counts
	saveCounts := h.getSaveCounts(allSessions)

	// 6. Resolve lastPlayedBy usernames
	lastPlayedByUsernames := h.resolveLastPlayedByUsernames(allSessions)

	// 7. Build responses with shared session metadata
	result := make([]GameSessionResponse, len(allSessions))
	for i, s := range allSessions {
		resp := h.toSessionResponse(s, saveCounts[s.ID])
		if username, ok := lastPlayedByUsernames[s.ID]; ok {
			resp.LastPlayedByUsername = &username
		}
		if ss, ok := sharedSessionMap[s.ID]; ok {
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
		result[i] = resp
	}

	c.JSON(http.StatusOK, result)
}

// GetSession returns a single session.
// GET /api/sessions/:id
func (h *SessionHandler) GetSession(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)

	resp := h.toSessionResponse(session, int(saveCount))
	h.enrichWithSharedSessionData(&resp, session.ID)
	c.JSON(http.StatusOK, resp)
}

// UpdateSession updates a session's name or settings.
// PUT /api/sessions/:id
func (h *SessionHandler) UpdateSession(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req UpdateSessionRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request"})
		return
	}

	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name must be between 1 and 255 characters"})
			return
		}
		session.Name = *req.Name
	}
	if req.CheatsEnabled != nil {
		session.CheatsEnabled = *req.CheatsEnabled
	}
	if req.CoreName != nil {
		session.CoreName = *req.CoreName
	}

	if err := h.DB.Save(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update session"})
		return
	}

	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)

	c.JSON(http.StatusOK, h.toSessionResponse(session, int(saveCount)))
}

// DeleteSession soft-deletes a session and its saves.
// DELETE /api/sessions/:id
func (h *SessionHandler) DeleteSession(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	// Delete save state files and records
	var saves []db.SessionSaveState
	h.DB.Where("session_id = ?", session.ID).Find(&saves)
	for _, save := range saves {
		h.Storage.DeleteSave(save.FilePath)
		if save.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
		}
	}
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveState{})

	// Delete SRAM data files and records
	var sramData []db.SessionSaveData
	h.DB.Where("session_id = ?", session.ID).Find(&sramData)
	for _, sd := range sramData {
		h.Storage.DeleteSave(sd.FilePath)
	}
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveData{})

	// Delete session directory
	h.Storage.DeleteSessionDir(session.ID)

	// Delete session record
	h.DB.Delete(&session)

	c.JSON(http.StatusOK, gin.H{"message": "session deleted"})
}

// DuplicateSession creates a copy of a session including saves, SRAM, and cheats.
// POST /api/sessions/:id/duplicate
func (h *SessionHandler) DuplicateSession(c *gin.Context) {
	uid := getUserID(c)
	sessionID := c.Param("id")
	sid, err := strconv.ParseUint(sessionID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid session ID"})
		return
	}

	var session db.GameSession
	if err := h.DB.Preload("Owner").First(&session, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "session not found"})
		return
	}

	// Access check: owner OR member of shared session that backs this session
	if session.OwnerID != uid {
		var count int64
		h.DB.Model(&db.SharedSessionMember{}).
			Joins("JOIN shared_sessions ON shared_sessions.id = shared_session_members.shared_session_id").
			Where("shared_sessions.session_id = ? AND shared_session_members.user_id = ?", session.ID, uid).
			Count(&count)
		if count == 0 {
			c.JSON(http.StatusForbidden, ErrorResponse{Error: "not the session owner or a shared session member"})
			return
		}
	}

	var req DuplicateSessionRequest
	_ = c.ShouldBindJSON(&req)
	if strings.TrimSpace(req.Name) == "" {
		req.Name = session.Name + " (Copy)"
	}
	if len(req.Name) > 255 {
		req.Name = req.Name[:255]
	}

	// Create new session owned by requesting user
	newSession := db.GameSession{
		OwnerID:       uid,
		GameID:        session.GameID,
		Name:          req.Name,
		CoreName:      session.CoreName,
		CheatsEnabled: session.CheatsEnabled,
	}
	if err := h.DB.Create(&newSession).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create session"})
		return
	}

	// Copy save states
	var saves []db.SessionSaveState
	h.DB.Where("session_id = ?", session.ID).Find(&saves)
	for _, save := range saves {
		newPath := h.Storage.SessionSaveStatePath(newSession.ID, filepath.Base(save.FilePath))
		if _, copyErr := h.Storage.CopyFile(save.FilePath, newPath); copyErr != nil {
			continue // skip files that fail to copy
		}
		var screenshotURL string
		if save.ScreenshotURL != "" {
			dstSubpath := h.Storage.SessionScreenshotSubpath(newSession.ID, filepath.Base(save.ScreenshotURL))
			if _, cpErr := h.Storage.CopyImageFile(save.ScreenshotURL, dstSubpath); cpErr == nil {
				screenshotURL = dstSubpath
			}
		}
		slot := save.Slot
		newSave := db.SessionSaveState{
			SessionID:     newSession.ID,
			UserID:        uid,
			Name:          save.Name,
			FilePath:      newPath,
			FileSize:      save.FileSize,
			ScreenshotURL: screenshotURL,
			IsAuto:        save.IsAuto,
			CoreName:      save.CoreName,
			Notes:         save.Notes,
			Slot:          slot,
		}
		h.DB.Create(&newSave)
	}

	// Copy SRAM
	var sram db.SessionSaveData
	if err := h.DB.Where("session_id = ?", session.ID).First(&sram).Error; err == nil {
		newSramPath := h.Storage.SessionSRAMPath(newSession.ID, filepath.Base(sram.FilePath))
		if size, copyErr := h.Storage.CopyFile(sram.FilePath, newSramPath); copyErr == nil {
			newSram := db.SessionSaveData{
				SessionID: newSession.ID,
				FilePath:  newSramPath,
				FileSize:  size,
			}
			h.DB.Create(&newSram)
		}
	}

	// Copy cheat settings
	var cheats []db.SessionCheatSetting
	h.DB.Where("session_id = ?", session.ID).Find(&cheats)
	for _, cheat := range cheats {
		newCheat := db.SessionCheatSetting{
			SessionID:  newSession.ID,
			CheatIndex: cheat.CheatIndex,
			Enabled:    cheat.Enabled,
		}
		h.DB.Create(&newCheat)
	}

	// Copy session screenshot
	if session.ScreenshotURL != "" {
		dstSubpath := h.Storage.SessionScreenshotSubpath(newSession.ID, filepath.Base(session.ScreenshotURL))
		if _, cpErr := h.Storage.CopyImageFile(session.ScreenshotURL, dstSubpath); cpErr == nil {
			h.DB.Model(&newSession).Update("screenshot_url", dstSubpath)
		}
	}

	h.DB.Preload("Owner").First(&newSession, newSession.ID)
	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", newSession.ID).Count(&saveCount)

	resp := h.toSessionResponse(newSession, int(saveCount))
	h.enrichWithSharedSessionData(&resp, newSession.ID)
	c.JSON(http.StatusCreated, resp)
}

// ListSessionSaves lists all save states in a session.
// GET /api/sessions/:id/saves
func (h *SessionHandler) ListSessionSaves(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ?", session.ID).
		Preload("User").
		Order("created_at DESC").
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch saves"})
		return
	}

	currentCore := h.getRecommendedCoreName(session.GameID)

	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s, currentCore)
	}

	c.JSON(http.StatusOK, result)
}

// UploadSessionSave uploads a save state to a session.
// POST /api/sessions/:id/saves
func (h *SessionHandler) UploadSessionSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	// If this session backs a shared session, enforce turn ownership
	if ok := h.checkSharedSessionTurn(c, session.ID, uid); !ok {
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, ErrorResponse{Error: "storage quota exceeded"})
		return
	}

	name := c.DefaultPostForm("name", header.Filename)
	coreName := c.PostForm("coreName")

	// Handle optional screenshot upload
	var screenshotURL string
	if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
		defer ssFile.Close()
		if stored, writeErr := h.Storage.WriteSessionScreenshot(session.ID, ssHeader.Filename, ssFile); writeErr == nil {
			screenshotURL = stored
		}
	}

	path, size, err := h.Storage.WriteSessionSave(session.ID, header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save file"})
		return
	}

	save := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          name,
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		CoreName:      coreName,
		IsAuto:        false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create save record"})
		return
	}

	// Update session screenshot and last played
	now := time.Now()
	updates := map[string]interface{}{"last_played_at": now, "last_played_by": uid}
	if screenshotURL != "" {
		updates["screenshot_url"] = screenshotURL
	}
	if coreName != "" {
		updates["core_name"] = coreName
	}
	h.DB.Model(&session).Updates(updates)

	h.DB.Preload("User").First(&save, save.ID)
	c.JSON(http.StatusCreated, h.toSaveResponse(save, h.getRecommendedCoreName(session.GameID)))
}

// DownloadSessionSave serves a session save state file.
// GET /api/sessions/:id/saves/:saveId
func (h *SessionHandler) DownloadSessionSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", saveID, session.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "file access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// DeleteSessionSave removes a session save state.
// DELETE /api/sessions/:id/saves/:saveId
func (h *SessionHandler) DeleteSessionSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", saveID, session.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	h.Storage.DeleteSave(save.FilePath)
	if save.ScreenshotURL != "" {
		h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
	}

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "save deleted"})
}

// UpdateSessionSave updates a session save state's name or notes.
// PUT /api/sessions/:id/saves/:saveId
func (h *SessionHandler) UpdateSessionSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	saveID := c.Param("saveId")
	var save db.SessionSaveState
	if err := h.DB.Where("id = ? AND session_id = ?", saveID, session.ID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "save not found"})
		return
	}

	var req UpdateSessionSaveRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request"})
		return
	}

	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "name must be between 1 and 255 characters"})
			return
		}
		save.Name = *req.Name
	}
	if req.Notes != nil {
		if len(*req.Notes) > 200 {
			c.JSON(http.StatusBadRequest, ErrorResponse{Error: "notes must be 200 characters or less"})
			return
		}
		save.Notes = *req.Notes
	}

	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to update save"})
		return
	}

	h.DB.Preload("User").First(&save, save.ID)
	c.JSON(http.StatusOK, h.toSaveResponse(save, h.getRecommendedCoreName(session.GameID)))
}

// UploadAutoSave uploads an auto-save to a session.
// POST /api/sessions/:id/saves/auto
func (h *SessionHandler) UploadAutoSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	// If this session backs a shared session, enforce turn ownership
	if ok := h.checkSharedSessionTurn(c, session.ID, uid); !ok {
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, ErrorResponse{Error: "storage quota exceeded"})
		return
	}

	filename := fmt.Sprintf("autosave_%d.sav", time.Now().UnixNano())
	coreName := c.PostForm("coreName")

	// Handle optional screenshot upload
	var screenshotURL string
	if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
		defer ssFile.Close()
		if stored, writeErr := h.Storage.WriteSessionScreenshot(session.ID, ssHeader.Filename, ssFile); writeErr == nil {
			screenshotURL = stored
		}
	}

	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save file"})
		return
	}

	save := db.SessionSaveState{
		SessionID:     session.ID,
		UserID:        uid,
		Name:          "Auto Save",
		FilePath:      path,
		FileSize:      size,
		ScreenshotURL: screenshotURL,
		CoreName:      coreName,
		IsAuto:        true,
		IsCurrent:     true,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to create save record"})
		return
	}

	// Prune old auto-saves — retention count depends on save size
	maxAutoSaves := autoSaveRetentionLimit(size)
	var oldSaves []db.SessionSaveState
	h.DB.Where("session_id = ? AND is_auto = ? AND id != ?", session.ID, true, save.ID).
		Order("created_at DESC").Offset(maxAutoSaves - 1).Find(&oldSaves)
	for _, old := range oldSaves {
		h.Storage.DeleteSave(old.FilePath)
		if old.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(old.ScreenshotURL))
		}
		h.DB.Delete(&old)
	}

	// Update session screenshot and last played
	now := time.Now()
	updates := map[string]interface{}{"last_played_at": now, "last_played_by": uid}
	if screenshotURL != "" {
		updates["screenshot_url"] = screenshotURL
	}
	if coreName != "" {
		updates["core_name"] = coreName
	}
	h.DB.Model(&session).Updates(updates)

	h.DB.Preload("User").First(&save, save.ID)
	c.JSON(http.StatusCreated, h.toSaveResponse(save, h.getRecommendedCoreName(session.GameID)))
}

// GetAutoSave returns the latest auto-save for a session.
// GET /api/sessions/:id/saves/auto
func (h *SessionHandler) GetAutoSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var save db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND is_auto = ?", session.ID, true).
		Preload("User").
		Order("created_at DESC").
		First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no auto-save found"})
		return
	}

	// Serve the file directly
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "file access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// UpsertSlotSave saves or overwrites a save to a specific slot (1-10).
// PUT /api/sessions/:id/saves/slot/:slot
func (h *SessionHandler) UpsertSlotSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	slotStr := c.Param("slot")
	slotNum, err := strconv.Atoi(slotStr)
	if err != nil || slotNum < 1 || slotNum > 10 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "slot must be between 1 and 10"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, fileHeader, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, fileHeader.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, ErrorResponse{Error: "storage quota exceeded"})
		return
	}

	filename := fmt.Sprintf("slot_%d.sav", slotNum)
	coreName := c.PostForm("coreName")

	// Handle optional screenshot upload
	var screenshotURL string
	if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
		defer ssFile.Close()
		if stored, writeErr := h.Storage.WriteSessionScreenshot(session.ID, ssHeader.Filename, ssFile); writeErr == nil {
			screenshotURL = stored
		}
	}

	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save file"})
		return
	}

	// Upsert: update existing slot save or create new
	var save db.SessionSaveState
	result := h.DB.Where("session_id = ? AND slot = ?", session.ID, slotNum).First(&save)
	if result.Error == gorm.ErrRecordNotFound {
		save = db.SessionSaveState{
			SessionID:     session.ID,
			UserID:        uid,
			Name:          fmt.Sprintf("Slot %d", slotNum),
			FilePath:      path,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			CoreName:      coreName,
			IsAuto:        false,
			Slot:          &slotNum,
		}
		h.DB.Create(&save)
	} else {
		save.FilePath = path
		save.FileSize = size
		save.ScreenshotURL = screenshotURL
		save.CoreName = coreName
		h.DB.Save(&save)
	}

	// Update session last played
	now := time.Now()
	updates := map[string]interface{}{"last_played_at": now, "last_played_by": uid}
	if screenshotURL != "" {
		updates["screenshot_url"] = screenshotURL
	}
	h.DB.Model(&session).Updates(updates)

	h.DB.Preload("User").First(&save, save.ID)
	c.JSON(http.StatusOK, h.toSaveResponse(save, h.getRecommendedCoreName(session.GameID)))
}

// DownloadSlotSave downloads the save from a specific slot.
// GET /api/sessions/:id/saves/slot/:slot
func (h *SessionHandler) DownloadSlotSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	slotStr := c.Param("slot")
	slotNum, err := strconv.Atoi(slotStr)
	if err != nil || slotNum < 1 || slotNum > 10 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "slot must be between 1 and 10"})
		return
	}

	var save db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND slot = ?", session.ID, slotNum).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no save in this slot"})
		return
	}

	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "file access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// ListSlotSaves returns all slot saves for a session.
// GET /api/sessions/:id/saves/slots
func (h *SessionHandler) ListSlotSaves(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND slot IS NOT NULL", session.ID).
		Preload("User").
		Order("slot ASC").
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch slot saves"})
		return
	}

	currentCore := h.getRecommendedCoreName(session.GameID)

	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s, currentCore)
	}

	c.JSON(http.StatusOK, result)
}

// UploadSRAM uploads SRAM/battery save data to a session.
// POST /api/sessions/:id/sram
func (h *SessionHandler) UploadSRAM(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, ErrorResponse{Error: "storage quota exceeded"})
		return
	}

	path, size, err := h.Storage.WriteSessionSRAM(session.ID, header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to save SRAM file"})
		return
	}

	// Upsert: only keep one SRAM file per session
	var existing db.SessionSaveData
	result := h.DB.Where("session_id = ?", session.ID).First(&existing)
	if result.Error == gorm.ErrRecordNotFound {
		sd := db.SessionSaveData{
			SessionID: session.ID,
			FilePath:  path,
			FileSize:  size,
		}
		h.DB.Create(&sd)
		c.JSON(http.StatusCreated, sd)
	} else {
		// Delete old file only if the path changed (same filename overwrites in place)
		if existing.FilePath != path {
			h.Storage.DeleteSave(existing.FilePath)
		}
		existing.FilePath = path
		existing.FileSize = size
		h.DB.Save(&existing)
		c.JSON(http.StatusOK, existing)
	}
}

// DownloadSRAM serves the SRAM/battery save data for a session.
// GET /api/sessions/:id/sram
func (h *SessionHandler) DownloadSRAM(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var sd db.SessionSaveData
	if err := h.DB.Where("session_id = ?", session.ID).First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "no SRAM data found"})
		return
	}

	if !storage.ValidateROMPath(sd.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "file access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(sd.FilePath)))
	c.File(sd.FilePath)
}

// UpdatePlayTime records play time for a session.
// POST /api/sessions/:id/play-time
func (h *SessionHandler) UpdatePlayTime(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req UpdateSessionPlayTimeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "seconds is required"})
		return
	}

	if req.Seconds < 0 {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "seconds must be non-negative"})
		return
	}

	now := time.Now()
	h.DB.Model(&session).Updates(map[string]interface{}{
		"total_play_time": gorm.Expr("total_play_time + ?", req.Seconds),
		"last_played_at":  now,
		"last_played_by":  uid,
	})

	h.DB.First(&session, session.ID)
	var saveCount int64
	h.DB.Model(&db.SessionSaveState{}).Where("session_id = ?", session.ID).Count(&saveCount)

	h.DB.Preload("Owner").First(&session, session.ID)
	c.JSON(http.StatusOK, h.toSessionResponse(session, int(saveCount)))
}

// StopPlaying clears the last played by field.
// DELETE /api/sessions/:id/play-time
func (h *SessionHandler) StopPlaying(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	h.DB.Model(&session).Update("last_played_by", nil)
	c.JSON(http.StatusOK, gin.H{"message": "stopped playing"})
}

// GetSessionCheats returns the cheat configuration for a session.
// GET /api/sessions/:id/cheats
func (h *SessionHandler) GetSessionCheats(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var settings []db.SessionCheatSetting
	h.DB.Where("session_id = ? AND enabled = ?", session.ID, true).Find(&settings)

	indices := make([]int, len(settings))
	for i, s := range settings {
		indices[i] = s.CheatIndex
	}

	c.JSON(http.StatusOK, gin.H{
		"cheatsEnabled":  session.CheatsEnabled,
		"enabledIndices": indices,
	})
}

// UpdateSessionCheats updates the cheat configuration for a session.
// PUT /api/sessions/:id/cheats
func (h *SessionHandler) UpdateSessionCheats(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req UpdateSessionCheatsRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid request"})
		return
	}

	// Update session cheats enabled flag
	h.DB.Model(&session).Update("cheats_enabled", req.CheatsEnabled)

	// Delete all existing cheat settings for this session
	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionCheatSetting{})

	// Create new settings for each enabled index
	for _, idx := range req.EnabledIndices {
		setting := db.SessionCheatSetting{
			SessionID:  session.ID,
			CheatIndex: idx,
			Enabled:    true,
		}
		h.DB.Create(&setting)
	}

	c.JSON(http.StatusOK, gin.H{
		"cheatsEnabled":  req.CheatsEnabled,
		"enabledIndices": req.EnabledIndices,
	})
}

// GetStorageUsage returns storage usage statistics for the authenticated user.
// GET /api/user/storage
func (h *SessionHandler) GetStorageUsage(c *gin.Context) {
	uid := getUserID(c)

	// Total used bytes
	var usedBytes int64
	if err := h.DB.Model(&db.SessionSaveState{}).Where("user_id = ?", uid).
		Select("COALESCE(SUM(file_size), 0)").Scan(&usedBytes).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to query storage usage"})
		return
	}

	// Per-console breakdown via JOINs:
	// session_save_states → game_sessions → games → consoles
	type consoleRow struct {
		ConsoleID   string `json:"consoleId"`
		ConsoleName string `json:"consoleName"`
		Bytes       int64  `json:"bytes"`
		SaveCount   int64  `json:"saveCount"`
	}
	var rows []consoleRow
	h.DB.Model(&db.SessionSaveState{}).
		Joins("JOIN game_sessions ON game_sessions.id = session_save_states.session_id AND game_sessions.deleted_at IS NULL").
		Joins("JOIN games ON games.id = game_sessions.game_id AND games.deleted_at IS NULL").
		Joins("JOIN consoles ON consoles.id = games.console_id AND consoles.deleted_at IS NULL").
		Where("session_save_states.user_id = ? AND session_save_states.deleted_at IS NULL", uid).
		Select("consoles.abbreviation AS console_id, consoles.name AS console_name, COALESCE(SUM(session_save_states.file_size), 0) AS bytes, COUNT(*) AS save_count").
		Group("consoles.id").
		Order("bytes DESC").
		Scan(&rows)

	if rows == nil {
		rows = []consoleRow{}
	}

	c.JSON(http.StatusOK, gin.H{
		"usedBytes":  usedBytes,
		"quotaBytes": maxSaveStorageBytes(),
		"byConsole":  rows,
	})
}

// BulkDeleteSessionSaves deletes all save states for a session (both auto and manual),
// removing files from disk but keeping the session itself.
// DELETE /api/sessions/:id/saves
func (h *SessionHandler) BulkDeleteSessionSaves(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var saves []db.SessionSaveState
	if err := h.DB.Where("session_id = ?", session.ID).Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch saves"})
		return
	}

	var freedBytes int64
	for _, save := range saves {
		freedBytes += save.FileSize
		h.Storage.DeleteSave(save.FilePath)
		if save.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
		}
	}

	h.DB.Where("session_id = ?", session.ID).Delete(&db.SessionSaveState{})

	c.JSON(http.StatusOK, gin.H{
		"deletedCount": len(saves),
		"freedBytes":   freedBytes,
	})
}

// CompactSaves keeps only the most recent auto-save and most recent manual save
// per session for the authenticated user. Everything else is deleted from disk and DB,
// including slot saves. This is an aggressive cleanup — use when quota is tight.
// POST /api/user/saves/compact
func (h *SessionHandler) CompactSaves(c *gin.Context) {
	uid := getUserID(c)

	// Find all sessions owned by this user
	var sessions []db.GameSession
	if err := h.DB.Where("owner_id = ?", uid).Find(&sessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch sessions"})
		return
	}

	var totalDeleted int
	var totalFreed int64

	for _, session := range sessions {
		// Find the most recent auto-save
		var keepIDs []uint

		var latestAuto db.SessionSaveState
		if err := h.DB.Where("session_id = ? AND is_auto = ?", session.ID, true).
			Order("created_at DESC").First(&latestAuto).Error; err == nil {
			keepIDs = append(keepIDs, latestAuto.ID)
		}

		// Find the most recent manual save (non-auto, non-slot)
		var latestManual db.SessionSaveState
		if err := h.DB.Where("session_id = ? AND is_auto = ? AND slot IS NULL", session.ID, false).
			Order("created_at DESC").First(&latestManual).Error; err == nil {
			keepIDs = append(keepIDs, latestManual.ID)
		}

		// Find all saves to delete (everything except the kept ones)
		query := h.DB.Where("session_id = ?", session.ID)
		if len(keepIDs) > 0 {
			query = query.Where("id NOT IN ?", keepIDs)
		}

		var toDelete []db.SessionSaveState
		if err := query.Find(&toDelete).Error; err != nil {
			continue
		}

		for _, save := range toDelete {
			totalFreed += save.FileSize
			h.Storage.DeleteSave(save.FilePath)
			if save.ScreenshotURL != "" {
				h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
			}
		}

		if len(toDelete) > 0 {
			deleteQuery := h.DB.Where("session_id = ?", session.ID)
			if len(keepIDs) > 0 {
				deleteQuery = deleteQuery.Where("id NOT IN ?", keepIDs)
			}
			deleteQuery.Delete(&db.SessionSaveState{})
		}

		totalDeleted += len(toDelete)
	}

	c.JSON(http.StatusOK, gin.H{
		"deletedCount": totalDeleted,
		"freedBytes":   totalFreed,
	})
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

func (h *SessionHandler) loadSessionWithOwnerCheck(c *gin.Context, uid uint) (db.GameSession, bool) {
	sessionID := c.Param("id")
	sid, err := strconv.ParseUint(sessionID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, ErrorResponse{Error: "invalid session ID"})
		return db.GameSession{}, false
	}

	var session db.GameSession
	if err := h.DB.Preload("Owner").First(&session, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "session not found"})
		return db.GameSession{}, false
	}

	if session.OwnerID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not the session owner"})
		return db.GameSession{}, false
	}

	return session, true
}

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

// checkSharedSessionTurn checks if this session is backed by a shared session, and if so, whether
// the user currently holds the turn. Returns true if the upload should proceed,
// false if a response has already been written.
func (h *SessionHandler) checkSharedSessionTurn(c *gin.Context, sessionID, uid uint) bool {
	var sharedSession db.SharedSession
	if err := h.DB.Where("session_id = ?", sessionID).First(&sharedSession).Error; err != nil {
		// No shared session backs this session — allow normal upload
		return true
	}

	// A shared session backs this session — user must hold the turn
	if sharedSession.ActiveUserID == nil || *sharedSession.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "shared session turn required: you must hold the turn to upload saves"})
		return false
	}
	return true
}
