package api

import (
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// SharedSaveHandler handles shared save state endpoints.
type SharedSaveHandler struct {
	DB      *gorm.DB
	Storage *storage.Storage
	Hub     *ws.Hub
}

// ShareSave has been migrated to huma — see HumaShareSave in
// huma_shared_uploads.go.

// ListSharedSaves returns paginated shared saves for a game.
func (h *SharedSaveHandler) ListSharedSaves(c *gin.Context) {
	gameID := c.Param("id")

	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("pageSize", "20"))
	if page < 1 {
		page = 1
	}
	if pageSize < 1 || pageSize > 100 {
		pageSize = 20
	}

	var total int64
	h.DB.Model(&db.SharedSaveState{}).Where("game_id = ?", gameID).Count(&total)

	var saves []db.SharedSaveState
	if err := h.DB.Where("game_id = ?", gameID).
		Preload("User").
		Order("created_at DESC").
		Offset((page - 1) * pageSize).
		Limit(pageSize).
		Find(&saves).Error; err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to fetch shared saves"})
		return
	}

	uid := getUserID(c)
	result := make([]SharedSaveResponse, 0, len(saves))
	for _, s := range saves {
		result = append(result, h.toResponse(s, uid))
	}

	c.JSON(http.StatusOK, PaginatedResponse[SharedSaveResponse]{
		Data:     result,
		Total:    total,
		Page:     page,
		PageSize: pageSize,
	})
}

// DownloadSharedSave serves a shared save state file.
func (h *SharedSaveHandler) DownloadSharedSave(c *gin.Context) {
	saveID := c.Param("saveId")

	var save db.SharedSaveState
	if err := h.DB.First(&save, saveID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "shared save not found"})
		return
	}

	// Validate the file path is within the save directory
	if !storage.ValidateROMPath(save.FilePath, []string{h.Storage.SaveDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "access denied"})
		return
	}

	// Increment download count
	h.DB.Model(&save).UpdateColumn("download_count", gorm.Expr("download_count + ?", 1))

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", filepath.Base(save.FilePath)))
	c.File(save.FilePath)
}

// DeleteSharedSave removes a shared save state (owner or admin only).
func (h *SharedSaveHandler) DeleteSharedSave(c *gin.Context) {
	uid := getUserID(c)
	saveID := c.Param("saveId")

	var save db.SharedSaveState
	if err := h.DB.First(&save, saveID).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "shared save not found"})
		return
	}

	// Check ownership or admin
	role, _ := c.Get("role")
	if save.UserID != uid && !db.IsAdminOrOwner(role.(db.UserRole)) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "not authorized to delete this shared save"})
		return
	}

	// Delete file
	if err := h.Storage.DeleteSave(save.FilePath); err != nil {
		c.JSON(http.StatusInternalServerError, ErrorResponse{Error: "failed to delete save file"})
		return
	}

	h.DB.Delete(&save)
	c.JSON(http.StatusOK, gin.H{"message": "shared save deleted"})
}

// toResponse converts a db.SharedSaveState to its API response.
func (h *SharedSaveHandler) toResponse(s db.SharedSaveState, _ uint) SharedSaveResponse {
	username := s.User.Username
	avatarURL := s.User.AvatarURL

	if username == "" && s.UserID != 0 {
		var user db.User
		if err := h.DB.First(&user, s.UserID).Error; err == nil {
			username = user.Username
			avatarURL = user.AvatarURL
		}
	}

	return SharedSaveResponse{
		ID:            strconv.FormatUint(uint64(s.ID), 10),
		UserID:        strconv.FormatUint(uint64(s.UserID), 10),
		Username:      username,
		AvatarURL:     avatarURL,
		GameID:        strconv.FormatUint(uint64(s.GameID), 10),
		Name:          s.Name,
		Description:   s.Description,
		FileSize:      s.FileSize,
		ScreenshotURL: s.ScreenshotURL,
		DownloadCount: s.DownloadCount,
		CreatedAt:     s.CreatedAt,
	}
}
