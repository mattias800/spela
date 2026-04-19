package api

import (
	"log/slog"
	"os"
	"path/filepath"

	"github.com/spela/server/internal/db"
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

// DownloadCore has been migrated to huma — see HumaDownloadCore in
// huma_downloads.go.
