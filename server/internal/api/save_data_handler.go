package api

import (
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// SaveDataHandler handles SRAM/battery save data endpoints.
type SaveDataHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
}

// ListSaveData returns all save data for a user and game.
func (h *SaveDataHandler) ListSaveData(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var saves []db.SaveData
	if err := h.DB.Where("user_id = ? AND game_id = ?", userID, gameID).
		Order("created_at desc").Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch save data"})
		return
	}

	c.JSON(http.StatusOK, saves)
}

// UploadSaveData uploads a named save data file.
func (h *SaveDataHandler) UploadSaveData(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file required"})
		return
	}
	defer file.Close()

	name := c.DefaultPostForm("name", header.Filename)

	size, err := h.Storage.WriteSaveData(uid, uint(gid), header.Filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveDataPath(uid, uint(gid), header.Filename)
	sd := db.SaveData{
		UserID:   uid,
		GameID:   uint(gid),
		Name:     name,
		FilePath: savePath,
		FileSize: size,
		IsActive: false,
	}
	if err := h.DB.Create(&sd).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to create save data record"})
		return
	}

	c.JSON(http.StatusCreated, sd)
}

// UploadActiveSaveData upserts the active SRAM save for a user+game.
func (h *SaveDataHandler) UploadActiveSaveData(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")
	uid := userID.(uint)

	gid, err := strconv.ParseUint(gameID, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid game ID"})
		return
	}

	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxSaveUploadSize)
	file, _, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "file required"})
		return
	}
	defer file.Close()

	filename := "active_sram.sav"

	size, err := h.Storage.WriteSaveData(uid, uint(gid), filename, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to save file"})
		return
	}

	savePath := h.Storage.SaveDataPath(uid, uint(gid), filename)

	// Upsert: update existing active save data or create new
	var sd db.SaveData
	result := h.DB.Where("user_id = ? AND game_id = ? AND is_active = ?", uid, gid, true).First(&sd)
	if result.Error == gorm.ErrRecordNotFound {
		sd = db.SaveData{
			UserID:   uid,
			GameID:   uint(gid),
			Name:     "Active Save Data",
			FilePath: savePath,
			FileSize: size,
			IsActive: true,
		}
		h.DB.Create(&sd)
	} else {
		sd.FilePath = savePath
		sd.FileSize = size
		h.DB.Save(&sd)
	}

	c.JSON(http.StatusOK, sd)
}

// DownloadActiveSaveData serves the active save data file for a user+game.
func (h *SaveDataHandler) DownloadActiveSaveData(c *gin.Context) {
	gameID := c.Param("id")
	userID, _ := c.Get("userId")

	var sd db.SaveData
	if err := h.DB.Where("user_id = ? AND game_id = ? AND is_active = ?", userID, gameID, true).
		First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "no active save data found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(sd.FilePath)))
	c.File(sd.FilePath)
}

// DownloadSaveData serves a specific save data file.
func (h *SaveDataHandler) DownloadSaveData(c *gin.Context) {
	sdID := c.Param("sdId")
	userID, _ := c.Get("userId")

	var sd db.SaveData
	if err := h.DB.Where("id = ? AND user_id = ?", sdID, userID).First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save data not found"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(sd.FilePath)))
	c.File(sd.FilePath)
}

// ActivateSaveData sets the target save data as active and deactivates all others for the same user+game.
func (h *SaveDataHandler) ActivateSaveData(c *gin.Context) {
	sdID := c.Param("sdId")
	userID, _ := c.Get("userId")

	var sd db.SaveData
	if err := h.DB.Where("id = ? AND user_id = ?", sdID, userID).First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save data not found"})
		return
	}

	err := h.DB.Transaction(func(tx *gorm.DB) error {
		// Deactivate all save data for this user+game
		if err := tx.Model(&db.SaveData{}).
			Where("user_id = ? AND game_id = ?", sd.UserID, sd.GameID).
			Update("is_active", false).Error; err != nil {
			return fmt.Errorf("deactivating save data: %w", err)
		}
		// Activate the target
		if err := tx.Model(&sd).Update("is_active", true).Error; err != nil {
			return fmt.Errorf("activating save data: %w", err)
		}
		return nil
	})
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to activate save data"})
		return
	}

	sd.IsActive = true
	c.JSON(http.StatusOK, sd)
}

// RenameSaveData renames a save data entry.
func (h *SaveDataHandler) RenameSaveData(c *gin.Context) {
	sdID := c.Param("sdId")
	userID, _ := c.Get("userId")

	var sd db.SaveData
	if err := h.DB.Where("id = ? AND user_id = ?", sdID, userID).First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save data not found"})
		return
	}

	var body struct {
		Name string `json:"name" binding:"required"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "name is required"})
		return
	}

	sd.Name = body.Name
	if err := h.DB.Save(&sd).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to rename save data"})
		return
	}

	c.JSON(http.StatusOK, sd)
}

// DeleteSaveData removes a save data file and its DB record.
func (h *SaveDataHandler) DeleteSaveData(c *gin.Context) {
	sdID := c.Param("sdId")
	userID, _ := c.Get("userId")

	var sd db.SaveData
	if err := h.DB.Where("id = ? AND user_id = ?", sdID, userID).First(&sd).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "save data not found"})
		return
	}

	if err := h.Storage.DeleteSaveData(sd.FilePath); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to delete save data file"})
		return
	}

	h.DB.Delete(&sd)
	c.JSON(http.StatusOK, gin.H{"message": "save data deleted"})
}
