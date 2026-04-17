package api

import (
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"

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

// platformExtension returns the shared library extension for the given platform.
func platformExtension(platform string) string {
	switch platform {
	case "macos":
		return ".dylib"
	case "windows":
		return ".dll"
	default:
		return ".so"
	}
}

// resolveCorePath finds the core binary file path, checking the database FilePath
// first, then falling back to discovery by name in the CoreDir using standard
// libretro naming conventions (e.g., nestopia_libretro.so).
func (h *CoreHandler) resolveCorePath(core db.Core, platform string) string {
	if core.FilePath != "" {
		return core.FilePath
	}
	if h.CoreDir == "" {
		return ""
	}

	ext := platformExtension(platform)

	// Try standard libretro naming: {name}_libretro{ext}
	candidates := []string{
		filepath.Join(h.CoreDir, core.Name+"_libretro"+ext),
		filepath.Join(h.CoreDir, platform, core.Name+"_libretro"+ext),
		filepath.Join(h.CoreDir, core.Name+ext),
	}

	for _, path := range candidates {
		if info, err := os.Stat(path); err == nil && !info.IsDir() {
			slog.Info("discovered core binary", "core", core.Name, "path", path)
			return path
		}
	}

	return ""
}

// DownloadCore serves a core binary for the requested platform.
func (h *CoreHandler) DownloadCore(c *gin.Context) {
	id := c.Param("id")
	platform := c.DefaultQuery("platform", "linux")

	var core db.Core
	if err := h.DB.First(&core, id).Error; err != nil {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "core not found"})
		return
	}

	corePath := h.resolveCorePath(core, platform)
	if corePath == "" {
		c.JSON(http.StatusNotFound, ErrorResponse{Error: "core binary not available"})
		return
	}

	// Security: validate the file path is within the allowed core directory
	if h.CoreDir == "" || !storage.ValidateROMPath(corePath, []string{h.CoreDir}) {
		c.JSON(http.StatusForbidden, ErrorResponse{Error: "core file access denied"})
		return
	}

	ext := platformExtension(platform)
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", core.Name+"_libretro"+ext))
	c.File(corePath)
}
