package api

import (
	"crypto/sha256"
	"encoding/hex"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// CoreHandler handles libretro core management endpoints.
type CoreHandler struct {
	DB      *gorm.DB
	CoreDir string
}

// ensureCoreMetadata populates Sha256 / SizeBytes / FetchedAt / SourceURL
// on the core row if any are missing. This is a lazy backfill: the first
// serve of a core after this code lands (or after a fresh core binary
// lands on disk) computes the hash and records the metadata. Subsequent
// serves are no-ops.
//
// Errors hashing the file are logged and swallowed — a failure here must
// not block the user from downloading the core. #555.
//
// Limitations tracked for #555 Phase 2:
//   - Binary replacement staleness: if an admin replaces the on-disk
//     binary after the row is populated, the recorded sha256 goes stale
//     until a force-refresh admin endpoint lands.
//   - Concurrent first-serves may both run the hash and issue
//     identical UPDATEs — SQLite serialises writes so there's no
//     corruption, but the second hash is wasted I/O.
func (h *CoreHandler) ensureCoreMetadata(core *db.Core, corePath string) {
	// The `SizeBytes > 0` check both skips populated rows and avoids a
	// bogus skip on zero-byte files: `FetchedAt != nil` is the
	// authoritative "already ran" marker.
	if core.Sha256 != "" && core.SizeBytes > 0 && core.FetchedAt != nil {
		return
	}
	sum, size, err := hashFileSha256(corePath)
	if err != nil {
		slog.Error("failed to hash core binary for metadata", "core", core.Name, "path", corePath, "error", err)
		return
	}
	now := time.Now().UTC()
	updates := map[string]interface{}{
		"sha256":     sum,
		"size_bytes": size,
		"fetched_at": &now,
	}
	// Only overwrite SourceURL if we actually know where this binary came
	// from. DownloadURL is a template (contains {platform}); storing it as
	// the source URL is still better than leaving the field blank, since
	// the admin can tell at a glance whether the core is pinned or pulled
	// from the buildbot.
	if core.SourceURL == "" && core.DownloadURL != "" {
		updates["source_url"] = core.DownloadURL
	}
	if err := h.DB.Model(core).Updates(updates).Error; err != nil {
		slog.Error("failed to persist core metadata", "core", core.Name, "error", err)
	}
}

// hashFileSha256 returns the hex sha256 digest and byte length of a file.
func hashFileSha256(path string) (string, int64, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", 0, err
	}
	defer f.Close()
	hasher := sha256.New()
	size, err := io.Copy(hasher, f)
	if err != nil {
		return "", 0, err
	}
	return hex.EncodeToString(hasher.Sum(nil)), size, nil
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
