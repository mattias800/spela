package api

import (
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// defaultMaxSaveStorageMB is the default per-user storage quota in MB.
// Can be overridden via SPELA_MAX_SAVE_STORAGE_MB environment variable.
const defaultMaxSaveStorageMB = 1024

// maxSaveStorageBytes returns the configured per-user storage quota in bytes.
func maxSaveStorageBytes() int64 {
	mb := int64(defaultMaxSaveStorageMB)
	if envVal := os.Getenv("SPELA_MAX_SAVE_STORAGE_MB"); envVal != "" {
		if parsed, err := strconv.ParseInt(envVal, 10, 64); err == nil && parsed > 0 {
			mb = parsed
		}
	}
	return mb << 20
}

// checkStorageQuota verifies that adding additionalBytes would not exceed the user's quota.
func checkStorageQuota(database *gorm.DB, userID uint, additionalBytes int64) error {
	var totalBytes int64
	database.Model(&db.SaveState{}).Where("user_id = ?", userID).
		Select("COALESCE(SUM(file_size), 0)").Scan(&totalBytes)
	if totalBytes+additionalBytes > maxSaveStorageBytes() {
		return fmt.Errorf("storage quota exceeded")
	}
	return nil
}

// SaveHandler handles save state management endpoints (rename, notes, slots, bulk, etc.).
type SaveHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
}

// RenameSave renames a save state.
// PUT /api/games/:id/saves/:saveId
func (h *SaveHandler) RenameSave(c *gin.Context) {
	userID, _ := c.Get("userId")
	saveID := c.Param("saveId")

	var save db.SaveState
	if err := h.DB.Where("id = ? AND user_id = ?", saveID, userID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	var body struct {
		Name string `json:"name" binding:"required"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name is required"})
		return
	}

	if strings.TrimSpace(body.Name) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be empty"})
		return
	}

	save.Name = body.Name
	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to rename save"})
		return
	}

	c.JSON(http.StatusOK, save)
}

// UpdateNotes updates the notes on a save state.
// PUT /api/games/:id/saves/:saveId/notes
func (h *SaveHandler) UpdateNotes(c *gin.Context) {
	userID, _ := c.Get("userId")
	saveID := c.Param("saveId")

	var save db.SaveState
	if err := h.DB.Where("id = ? AND user_id = ?", saveID, userID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	var body struct {
		Notes string `json:"notes"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid request"})
		return
	}

	if len(body.Notes) > 200 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "notes must be 200 characters or less"})
		return
	}

	save.Notes = body.Notes
	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to update notes"})
		return
	}

	c.JSON(http.StatusOK, save)
}

// GetAutoSaveHistory returns the auto-save history (up to 5) for a game.
// GET /api/games/:id/saves/auto/history
func (h *SaveHandler) GetAutoSaveHistory(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var saves []db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ? AND is_auto = ?", userID, gameID, true).
		Order("created_at DESC").
		Limit(5).
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch auto-save history"})
		return
	}

	c.JSON(http.StatusOK, saves)
}

// BulkDeleteSaves deletes multiple save states at once.
// DELETE /api/games/:id/saves/bulk
func (h *SaveHandler) BulkDeleteSaves(c *gin.Context) {
	userID, _ := c.Get("userId")

	var body struct {
		IDs []uint `json:"ids" binding:"required"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ids array is required"})
		return
	}

	if len(body.IDs) == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "ids array cannot be empty"})
		return
	}
	if len(body.IDs) > 100 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "too many IDs, maximum is 100"})
		return
	}

	// Find all saves owned by user with given IDs (excluding auto-saves)
	var saves []db.SaveState
	if err := h.DB.Where("id IN ? AND user_id = ? AND is_auto = ?", body.IDs, userID, false).
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch saves"})
		return
	}

	// Check if any requested IDs are auto-saves
	var autoCount int64
	h.DB.Model(&db.SaveState{}).Where("id IN ? AND user_id = ? AND is_auto = ?", body.IDs, userID, true).Count(&autoCount)
	if autoCount > 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "auto-saves cannot be bulk deleted"})
		return
	}

	// Clean up files (save data + screenshots)
	for _, save := range saves {
		h.Storage.DeleteSave(save.FilePath)
		if save.ScreenshotURL != "" {
			h.Storage.DeleteSave(h.Storage.ImagePath(save.ScreenshotURL))
		}
	}

	// Batch delete from DB
	ids := make([]uint, len(saves))
	for i, save := range saves {
		ids[i] = save.ID
	}
	deleted := 0
	if len(ids) > 0 {
		result := h.DB.Where("id IN ?", ids).Delete(&db.SaveState{})
		if result.Error != nil {
			c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete saves"})
			return
		}
		deleted = int(result.RowsAffected)
	}

	c.JSON(http.StatusOK, gin.H{"deleted": deleted})
}

// GetStorageUsage returns the storage usage for the authenticated user.
// GET /api/user/storage-usage
func (h *SaveHandler) GetStorageUsage(c *gin.Context) {
	userID, _ := c.Get("userId")

	type gameUsage struct {
		GameID         uint   `json:"gameId"`
		GameTitle      string `json:"gameTitle"`
		SaveStateBytes int64  `json:"saveStateBytes"`
		SramBytes      int64  `json:"sramBytes"`
		TotalBytes     int64  `json:"totalBytes"`
	}

	// Get save state usage per game
	type saveRow struct {
		GameID   uint
		Title    string
		TotalSize int64
	}
	var saveRows []saveRow
	h.DB.Model(&db.SaveState{}).
		Select("save_states.game_id, games.title, COALESCE(SUM(save_states.file_size), 0) as total_size").
		Joins("JOIN games ON games.id = save_states.game_id").
		Where("save_states.user_id = ?", userID).
		Group("save_states.game_id").
		Scan(&saveRows)

	// Get SRAM usage per game
	var sramRows []saveRow
	h.DB.Model(&db.SaveData{}).
		Select("save_data.game_id, games.title, COALESCE(SUM(save_data.file_size), 0) as total_size").
		Joins("JOIN games ON games.id = save_data.game_id").
		Where("save_data.user_id = ?", userID).
		Group("save_data.game_id").
		Scan(&sramRows)

	// Merge into a map
	usageMap := make(map[uint]*gameUsage)
	for _, r := range saveRows {
		usageMap[r.GameID] = &gameUsage{
			GameID:         r.GameID,
			GameTitle:      r.Title,
			SaveStateBytes: r.TotalSize,
		}
	}
	for _, r := range sramRows {
		if u, ok := usageMap[r.GameID]; ok {
			u.SramBytes = r.TotalSize
		} else {
			usageMap[r.GameID] = &gameUsage{
				GameID:    r.GameID,
				GameTitle: r.Title,
				SramBytes: r.TotalSize,
			}
		}
	}

	// Calculate totals and build sorted list
	var totalBytes int64
	games := make([]gameUsage, 0, len(usageMap))
	for _, u := range usageMap {
		u.TotalBytes = u.SaveStateBytes + u.SramBytes
		totalBytes += u.TotalBytes
		games = append(games, *u)
	}

	// Sort by totalBytes descending
	sort.Slice(games, func(i, j int) bool {
		return games[i].TotalBytes > games[j].TotalBytes
	})

	c.JSON(http.StatusOK, gin.H{
		"totalBytes": totalBytes,
		"games":      games,
	})
}

// ImportSave imports a save state from an uploaded file.
// POST /api/games/:id/saves/import
func (h *SaveHandler) ImportSave(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
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

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("save")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file required"})
		return
	}
	defer file.Close()

	if header.Size == 0 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "save file cannot be empty"})
		return
	}

	if err := checkStorageQuota(h.DB, uid, header.Size); err != nil {
		c.JSON(http.StatusRequestEntityTooLarge, gin.H{"error": "storage quota exceeded"})
		return
	}

	name := c.DefaultPostForm("name", header.Filename)

	size, err := h.Storage.WriteSave(uid, uint(gid), header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), header.Filename)
	save := db.SaveState{
		UserID:   uid,
		GameID:   uint(gid),
		Name:     name,
		FilePath: savePath,
		FileSize: size,
		IsAuto:   false,
	}
	if err := h.DB.Create(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save record"})
		return
	}

	c.JSON(http.StatusCreated, save)
}

// UpsertSlotSave saves or overwrites a save to a specific slot (1-10).
// PUT /api/games/:id/saves/slot/:slot
func (h *SaveHandler) UpsertSlotSave(c *gin.Context) {
	gameID := c.Param("id")
	slotStr := c.Param("slot")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

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

	// Handle optional screenshot upload
	var screenshotURL string
	if ssFile, ssHeader, ssErr := c.Request.FormFile("screenshot"); ssErr == nil {
		defer ssFile.Close()
		subpath := fmt.Sprintf("save-screenshots/user_%d/game_%d/%s", uid, gid, filepath.Base(ssHeader.Filename))
		if stored, writeErr := h.Storage.WriteImage(subpath, ssFile); writeErr == nil {
			screenshotURL = stored
		}
	}

	size, err := h.Storage.WriteSave(uid, uint(gid), filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveStatePath(uid, uint(gid), filename)

	// Upsert: update existing slot save or create new
	var save db.SaveState
	result := h.DB.Where("user_id = ? AND game_id = ? AND slot = ?", uid, gid, slotNum).First(&save)
	if result.Error == gorm.ErrRecordNotFound {
		save = db.SaveState{
			UserID:        uid,
			GameID:        uint(gid),
			Name:          fmt.Sprintf("Slot %d", slotNum),
			FilePath:      savePath,
			FileSize:      size,
			ScreenshotURL: screenshotURL,
			IsAuto:        false,
			Slot:          &slotNum,
		}
		h.DB.Create(&save)
	} else {
		save.FilePath = savePath
		save.FileSize = size
		save.ScreenshotURL = screenshotURL
		h.DB.Save(&save)
	}

	c.JSON(http.StatusOK, save)
}

// DownloadSlotSave downloads the save from a specific slot.
// GET /api/games/:id/saves/slot/:slot
func (h *SaveHandler) DownloadSlotSave(c *gin.Context) {
	gameID := c.Param("id")
	slotStr := c.Param("slot")
	userID, _ := c.Get("userId")

	slotNum, err := strconv.Atoi(slotStr)
	if err != nil || slotNum < 1 || slotNum > 10 {
		c.JSON(http.StatusBadRequest, gin.H{"error": "slot must be between 1 and 10"})
		return
	}

	var save db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ? AND slot = ?", userID, gameID, slotNum).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no save in this slot"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// ListSlotSaves returns all slot saves for a game.
// GET /api/games/:id/saves/slots
func (h *SaveHandler) ListSlotSaves(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var saves []db.SaveState
	if err := h.DB.Where("user_id = ? AND game_id = ? AND slot IS NOT NULL", userID, gameID).
		Order("slot ASC").
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch slot saves"})
		return
	}

	c.JSON(http.StatusOK, saves)
}

// GetSaveScreenshot serves the screenshot image for a save state.
// GET /api/games/:id/saves/:saveId/screenshot
func (h *SaveHandler) GetSaveScreenshot(c *gin.Context) {
	saveID := c.Param("saveId")
	userID, _ := c.Get("userId")

	var save db.SaveState
	if err := h.DB.Where("id = ? AND user_id = ?", saveID, userID).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	if save.ScreenshotURL == "" {
		c.JSON(http.StatusNotFound, gin.H{"error": "no screenshot for this save"})
		return
	}

	// ScreenshotURL is stored as a relative path within the save directory
	screenshotPath := save.ScreenshotURL
	if !filepath.IsAbs(screenshotPath) {
		screenshotPath = h.Storage.ImagePath(save.ScreenshotURL)
	}

	c.Header("Content-Type", "image/png")
	c.File(screenshotPath)
}

// RenameRelaySave renames a relay save state.
// PUT /api/relays/:id/saves/:saveId/rename
func (h *SaveHandler) RenameRelaySave(c *gin.Context) {
	uid := getUserID(c)
	relayID := c.Param("id")
	saveID := c.Param("saveId")

	rid, err := strconv.ParseUint(relayID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid relay ID"})
		return
	}

	// Check membership
	var member db.RelayMember
	if err := h.DB.Where("relay_id = ? AND user_id = ?", rid, uid).First(&member).Error; err != nil {
		c.JSON(http.StatusForbidden, gin.H{"error": "not a member of this relay"})
		return
	}

	var save db.RelaySave
	if err := h.DB.Where("id = ? AND relay_id = ?", saveID, rid).First(&save).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save not found"})
		return
	}

	var body struct {
		Name string `json:"name" binding:"required"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name is required"})
		return
	}

	if strings.TrimSpace(body.Name) == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name cannot be empty"})
		return
	}

	save.Name = body.Name
	if err := h.DB.Save(&save).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to rename save"})
		return
	}

	c.JSON(http.StatusOK, save)
}
