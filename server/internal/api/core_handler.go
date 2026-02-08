package api

import (
	"fmt"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"gorm.io/gorm"
)

// CoreHandler handles libretro core management endpoints.
type CoreHandler struct {
	DB      *gorm.DB
	CoreDir string
}

// ListCores returns all available libretro cores.
func (h *CoreHandler) ListCores(c *gin.Context) {
	var cores []db.Core
	if err := h.DB.Find(&cores).Error; err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to fetch cores"})
		return
	}
	c.JSON(http.StatusOK, cores)
}

// DownloadCore serves a core binary for the requested platform.
func (h *CoreHandler) DownloadCore(c *gin.Context) {
	id := c.Param("id")
	platform := c.DefaultQuery("platform", "linux")

	var core db.Core
	if err := h.DB.First(&core, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "core not found"})
		return
	}

	if core.FilePath == "" {
		c.JSON(http.StatusNotFound, gin.H{"error": "core binary not available"})
		return
	}

	// Security: validate the file path is within the allowed core directory
	if h.CoreDir == "" || !storage.ValidateROMPath(core.FilePath, []string{h.CoreDir}) {
		c.JSON(http.StatusForbidden, gin.H{"error": "core file access denied"})
		return
	}

	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", core.Name+"_"+platform))
	c.File(core.FilePath)
}
