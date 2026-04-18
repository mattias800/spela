package api

import (
	"strings"

	"gorm.io/gorm"
)

// RomHackHandler handles ROM hack creation endpoints.
type RomHackHandler struct {
	DB       *gorm.DB
	GameDirs []string
}

// maxPatchUploadSize limits patch file uploads to 100 MB.
const maxPatchUploadSize = 100 << 20

// CreateRomHack has been migrated to huma — see (*RomHackHandler).HumaCreateRomHack
// in huma_admin_multipart.go. The gin handler was removed once nothing referenced
// it; the OpenAPI spec is now the single source of truth for this endpoint.

// sanitizePatchedFilename creates a safe filename from a title.
func sanitizePatchedFilename(title string) string {
	// Replace characters that are problematic in filenames
	replacer := strings.NewReplacer(
		"/", "-",
		"\\", "-",
		":", "-",
		"*", "",
		"?", "",
		"\"", "",
		"<", "",
		">", "",
		"|", "",
	)
	safe := replacer.Replace(title)
	safe = strings.TrimSpace(safe)
	if safe == "" {
		safe = "rom-hack"
	}
	return safe
}
