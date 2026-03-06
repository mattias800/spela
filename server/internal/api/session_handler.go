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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var req struct {
		Name string `json:"name" binding:"required,max=255"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name is required and must be 255 characters or fewer"})
		return
	}

	if strings.TrimSpace(req.Name) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be empty"})
		return
	}

	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    req.Name,
	}
	if err := h.DB.Create(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create session"})
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	sid, err := strconv.ParseUint(saveID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid save ID"})
		return
	}

	var game db.Game
	if err := h.DB.First(&game, gid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	var sharedSave db.SharedSaveState
	if err := h.DB.First(&sharedSave, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "shared save not found"})
		return
	}

	if sharedSave.GameID != uint(gid) {
		c.JSON(http.StatusBadRequest, gin.H{"error": "shared save does not belong to this game"})
		return
	}

	// Create session
	session := db.GameSession{
		OwnerID: uid,
		GameID:  uint(gid),
		Name:    fmt.Sprintf("From: %s", sharedSave.Name),
	}
	if err := h.DB.Create(&session).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create session"})
		return
	}

	// Copy the shared save file to the new session
	srcFile, err := os.Open(sharedSave.FilePath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to read shared save file"})
		return
	}
	defer srcFile.Close()

	filename := filepath.Base(sharedSave.FilePath)
	path, size, err := h.Storage.WriteSessionSave(session.ID, filename, srcFile)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to copy save file"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
		return
	}

	// Increment download count on the shared save
	h.DB.Model(&sharedSave).UpdateColumn("download_count", gorm.Expr("download_count + ?", 1))

	h.DB.Preload("Owner").First(&session, session.ID)
	c.JSON(http.StatusCreated, h.toSessionResponse(session, 1))
}

// ListSessions lists all sessions for a game belonging to the current user.
// GET /api/games/:id/sessions
func (h *SessionHandler) ListSessions(c *gin.Context) {
	uid := getUserID(c)
	gameID := c.Param("id")

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	var sessions []db.GameSession
	if err := h.DB.Where("owner_id = ? AND game_id = ?", uid, gid).
		Preload("Owner").
		Order("updated_at DESC").
		Find(&sessions).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch sessions"})
		return
	}

	// Batch-load save counts
	saveCounts := h.getSaveCounts(sessions)

	result := make([]GameSessionResponse, len(sessions))
	for i, s := range sessions {
		result[i] = h.toSessionResponse(s, saveCounts[s.ID])
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

	c.JSON(http.StatusOK, h.toSessionResponse(session, int(saveCount)))
}

// UpdateSession updates a session's name or settings.
// PUT /api/sessions/:id
func (h *SessionHandler) UpdateSession(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	var req struct {
		Name          *string `json:"name"`
		CheatsEnabled *bool   `json:"cheatsEnabled"`
		CoreName      *string `json:"coreName"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "name must be between 1 and 255 characters"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update session"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch saves"})
		return
	}

	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s)
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

	// If this session backs a relay, enforce turn ownership
	if ok := h.checkRelayTurn(c, session.ID, uid); !ok {
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
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
	c.JSON(http.StatusCreated, h.toSaveResponse(save))
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
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
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
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
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
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	var req struct {
		Name  *string `json:"name"`
		Notes *string `json:"notes"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if req.Name != nil {
		if len(*req.Name) > 255 || strings.TrimSpace(*req.Name) == "" {
			c.JSON(http.StatusBadRequest, gin.H{"error": "name must be between 1 and 255 characters"})
			return
		}
		save.Name = *req.Name
	}
	if req.Notes != nil {
		if len(*req.Notes) > 200 {
			c.JSON(http.StatusBadRequest, gin.H{"error": "notes must be 200 characters or less"})
			return
		}
		save.Notes = *req.Notes
	}

	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update save"})
		return
	}

	h.DB.Preload("User").First(&save, save.ID)
	c.JSON(http.StatusOK, h.toSaveResponse(save))
}

// UploadAutoSave uploads an auto-save to a session.
// POST /api/sessions/:id/saves/auto
func (h *SessionHandler) UploadAutoSave(c *gin.Context) {
	uid := getUserID(c)
	session, ok := h.loadSessionWithOwnerCheck(c, uid)
	if !ok {
		return
	}

	// If this session backs a relay, enforce turn ownership
	if ok := h.checkRelayTurn(c, session.ID, uid); !ok {
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
		return
	}

	// Prune old auto-saves (keep latest 5)
	var oldSaves []db.SessionSaveState
	h.DB.Where("session_id = ? AND is_auto = ? AND id != ?", session.ID, true, save.ID).
		Order("created_at DESC").Offset(4).Find(&oldSaves)
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
	c.JSON(http.StatusCreated, h.toSaveResponse(save))
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
		c.JSON(http.StatusNotFound, gin.H{"error": "no auto-save found"})
		return
	}

	// Serve the file directly
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "slot must be between 1 and 10"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, fileHeader, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, fileHeader.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
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
	c.JSON(http.StatusOK, h.toSaveResponse(save))
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "slot must be between 1 and 10"})
		return
	}

	var save db.SessionSaveState
	if err := h.DB.Where("session_id = ? AND slot = ?", session.ID, slotNum).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no save in this slot"})
		return
	}

	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
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
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch slot saves"})
		return
	}

	result := make([]SessionSaveResponse, len(saves))
	for i, s := range saves {
		result[i] = h.toSaveResponse(s)
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
		c.JSON(http.StatusBadRequest, gin.H{"error": "file required"})
		return
	}
	defer file.Close()

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
		return
	}

	path, size, err := h.Storage.WriteSessionSRAM(session.ID, header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save SRAM file"})
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
		c.JSON(http.StatusNotFound, gin.H{"error": "no SRAM data found"})
		return
	}

	if !storage.ValidateROMPath(sd.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "file access denied"})
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

	var req struct {
		Seconds int64 `json:"seconds" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "seconds is required"})
		return
	}

	if req.Seconds < 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "seconds must be non-negative"})
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

	var req struct {
		CheatsEnabled  bool  `json:"cheatsEnabled"`
		EnabledIndices []int `json:"enabledIndices"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
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

// --- Helper methods ---

func (h *SessionHandler) loadSessionWithOwnerCheck(c *gin.Context, uid uint) (db.GameSession, bool) {
	sessionID := c.Param("id")
	sid, err := strconv.ParseUint(sessionID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid session ID"})
		return db.GameSession{}, false
	}

	var session db.GameSession
	if err := h.DB.Preload("Owner").First(&session, sid).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "session not found"})
		return db.GameSession{}, false
	}

	if session.OwnerID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "not the session owner"})
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
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		OwnerID:       strconv.FormatUint(uint64(s.OwnerID), 10),
		OwnerUsername: s.Owner.Username,
		GameID:        strconv.FormatUint(uint64(s.GameID), 10),
		Name:          s.Name,
		LastPlayedAt:  s.LastPlayedAt,
		LastPlayedBy:  lastPlayedBy,
		TotalPlayTime: s.TotalPlayTime,
		ScreenshotURL: resolveImageURL(s.ScreenshotURL),
		CoreName:      s.CoreName,
		CheatsEnabled: s.CheatsEnabled,
		SaveCount:     saveCount,
		CreatedAt:     s.CreatedAt,
		UpdatedAt:     s.UpdatedAt,
	}
}

func (h *SessionHandler) toSaveResponse(s db.SessionSaveState) SessionSaveResponse {
	return SessionSaveResponse{
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
}

// checkRelayTurn checks if this session is backed by a relay, and if so, whether
// the user currently holds the turn. Returns true if the upload should proceed,
// false if a response has already been written.
func (h *SessionHandler) checkRelayTurn(c *gin.Context, sessionID, uid uint) bool {
	var relay db.Relay
	if err := h.DB.Where("session_id = ?", sessionID).First(&relay).Error; err != nil {
		// No relay backs this session — allow normal upload
		return true
	}

	// A relay backs this session — user must hold the turn
	if relay.ActiveUserID == nil || *relay.ActiveUserID != uid {
		c.JSON(http.StatusForbidden, gin.H{"error": "relay turn required: you must hold the turn to upload saves"})
		return false
	}
	return true
}
