package api

import (
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// CoreHandler handles libretro core management endpoints.
type CoreHandler struct {
	DB      *gorm.DB
	CoreDir string
}

// coreHistorySubdir is the on-disk subdirectory (under CoreDir) that holds
// past versions of each core binary keyed by sha256. Exposed as a constant
// so tests can assert against it without hard-coding the string.
const coreHistorySubdir = "history"

// historyBinaryPath returns the expected path of the core binary for the
// given sha256 under the history directory.
func (h *CoreHandler) historyBinaryPath(core db.Core, sum, ext string) string {
	return filepath.Join(h.CoreDir, coreHistorySubdir, sum, core.Name+"_libretro"+ext)
}

// ensureCoreMetadata populates Sha256 / SizeBytes / FetchedAt / SourceURL
// on the core row if any are missing, and keeps a copy of the binary under
// {CoreDir}/history/{sha256}/{name}_libretro{ext} so older saves pinned to
// a previous SHA can still be served via the ?sha256= download query. This
// is a lazy backfill: the first serve of a core after this code lands (or
// after a fresh core binary lands on disk) computes the hash, records the
// metadata, and snapshots the binary into the history directory. Subsequent
// serves only re-verify the history copy — same SHA == same file, a no-op.
//
// Errors hashing the file or copying into history are logged and swallowed
// — a failure here must not block the user from downloading the core.
// See #555 Phase 3.
//
// Limitations tracked for #555 Phase 2:
//   - Binary replacement staleness: if an admin replaces the on-disk
//     binary after the row is populated, the recorded sha256 goes stale
//     until a force-refresh admin endpoint lands.
//   - Concurrent first-serves may both run the hash and issue
//     identical UPDATEs — SQLite serialises writes so there's no
//     corruption, but the second hash is wasted I/O.
func (h *CoreHandler) ensureCoreMetadata(core *db.Core, corePath string) {
	// Remember whether we need to backfill the metadata row. We always run
	// the history copy (it's idempotent) but only hash+update when needed.
	needsMeta := core.Sha256 == "" || core.SizeBytes == 0 || core.FetchedAt == nil

	// Determine the SHA. For already-populated rows, trust the DB value so
	// we don't re-hash every download; for fresh rows, hash the on-disk
	// binary now so we can persist + snapshot in one pass.
	var sum string
	var size int64
	if needsMeta {
		var err error
		sum, size, err = hashFileSha256(corePath)
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
		// Only overwrite SourceURL if we actually know where this binary
		// came from. DownloadURL is a template (contains {platform}); storing
		// it as the source URL is still better than leaving the field blank,
		// since the admin can tell at a glance whether the core is pinned or
		// pulled from the buildbot.
		if core.SourceURL == "" && core.DownloadURL != "" {
			updates["source_url"] = core.DownloadURL
		}
		if err := h.DB.Model(core).Updates(updates).Error; err != nil {
			slog.Error("failed to persist core metadata", "core", core.Name, "error", err)
			return
		}
		core.Sha256 = sum
		core.SizeBytes = size
	} else {
		sum = core.Sha256
	}

	h.snapshotCoreToHistory(*core, corePath, sum)
}

// snapshotCoreToHistory copies the binary at corePath into
// {CoreDir}/history/{sum}/{name}_libretro{ext} if no file exists there yet.
// Idempotent: same sum means same directory, same filename, no-op. Errors
// are logged and swallowed — history is an enhancement, not critical path.
func (h *CoreHandler) snapshotCoreToHistory(core db.Core, corePath, sum string) {
	if h.CoreDir == "" || sum == "" || corePath == "" {
		return
	}
	ext := filepath.Ext(corePath)
	dst := h.historyBinaryPath(core, sum, ext)
	if _, err := os.Stat(dst); err == nil {
		return // already snapshotted
	}
	if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
		slog.Warn("failed to create core history directory", "core", core.Name, "sha", sum, "error", err)
		return
	}
	in, err := os.Open(corePath)
	if err != nil {
		slog.Warn("failed to open core binary for history snapshot", "core", core.Name, "error", err)
		return
	}
	defer in.Close()
	tmp, err := os.CreateTemp(filepath.Dir(dst), ".snapshot-*.tmp")
	if err != nil {
		slog.Warn("failed to create core history temp file", "core", core.Name, "error", err)
		return
	}
	tmpName := tmp.Name()
	if _, err := io.Copy(tmp, in); err != nil {
		tmp.Close()
		_ = os.Remove(tmpName)
		slog.Warn("failed to copy core binary into history", "core", core.Name, "error", err)
		return
	}
	if err := tmp.Close(); err != nil {
		_ = os.Remove(tmpName)
		slog.Warn("failed to close core history temp file", "core", core.Name, "error", err)
		return
	}
	if err := os.Rename(tmpName, dst); err != nil {
		_ = os.Remove(tmpName)
		slog.Warn("failed to finalise core history file", "core", core.Name, "error", err)
		return
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

// coreHistoryMinVersions is the floor of the retention policy: for each
// core, keep at least this many most-recent versions (by mtime) even if
// they're older than the age cutoff below.
const coreHistoryMinVersions = 3

// coreHistoryMaxAge is the time-based part of the retention policy: keep
// every history binary newer than this, in addition to the mtime floor.
// The two sets are unioned — a binary is kept if it's in either.
const coreHistoryMaxAge = 90 * 24 * time.Hour

// PruneCoreHistory sweeps {CoreDir}/history/... and removes binaries that
// fall outside the retention policy: keep the last coreHistoryMinVersions
// per-core PLUS everything newer than coreHistoryMaxAge, union of the two
// sets. Returns the number of files deleted and any error encountered
// walking the directory (errors during individual deletes are logged and
// swallowed so a single permissions problem doesn't abort the whole prune).
//
// Grouping is by core name: the subdirectory layout is
// history/{sha256}/{name}_libretro{ext}, so we read every subdirectory,
// stat its binary, and bucket entries by the derived core name prefix.
func (h *CoreHandler) PruneCoreHistory() (int, error) {
	if h.CoreDir == "" {
		return 0, nil
	}
	root := filepath.Join(h.CoreDir, coreHistorySubdir)
	entries, err := os.ReadDir(root)
	if err != nil {
		if os.IsNotExist(err) {
			return 0, nil
		}
		return 0, fmt.Errorf("reading core history dir: %w", err)
	}

	type entry struct {
		path  string // absolute path of the binary
		dir   string // the sha256 directory (parent of the binary)
		core  string // derived core name
		mtime time.Time
	}
	byCore := make(map[string][]entry)
	now := time.Now()

	for _, ent := range entries {
		if !ent.IsDir() {
			continue
		}
		shaDir := filepath.Join(root, ent.Name())
		children, cerr := os.ReadDir(shaDir)
		if cerr != nil {
			slog.Warn("failed to read core history sha dir", "dir", shaDir, "error", cerr)
			continue
		}
		for _, c := range children {
			if c.IsDir() {
				continue
			}
			name := c.Name()
			core := strings.TrimSuffix(name, filepath.Ext(name))
			core = strings.TrimSuffix(core, "_libretro")
			info, ierr := c.Info()
			if ierr != nil {
				slog.Warn("failed to stat core history entry", "path", filepath.Join(shaDir, name), "error", ierr)
				continue
			}
			byCore[core] = append(byCore[core], entry{
				path:  filepath.Join(shaDir, name),
				dir:   shaDir,
				core:  core,
				mtime: info.ModTime(),
			})
		}
	}

	deleted := 0
	for _, list := range byCore {
		// Sort newest first so indices 0..coreHistoryMinVersions-1 are the
		// "last N" keep set.
		sort.Slice(list, func(i, j int) bool { return list[i].mtime.After(list[j].mtime) })
		for idx, e := range list {
			keepByCount := idx < coreHistoryMinVersions
			keepByAge := now.Sub(e.mtime) <= coreHistoryMaxAge
			if keepByCount || keepByAge {
				continue
			}
			if err := os.Remove(e.path); err != nil {
				slog.Warn("failed to prune core history file", "path", e.path, "error", err)
				continue
			}
			// Best-effort: also drop the now-empty sha directory.
			_ = os.Remove(e.dir)
			deleted++
		}
	}
	return deleted, nil
}

// StartCoreHistoryPruner runs the prune job immediately and then every 24
// hours until ctx is cancelled. Safe to call from main() as a goroutine;
// logs runs at info level so admins can confirm the policy is in effect.
func (h *CoreHandler) StartCoreHistoryPruner(ctx contextLike) {
	go func() {
		h.runPruneAndLog("startup")
		ticker := time.NewTicker(24 * time.Hour)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				h.runPruneAndLog("scheduled")
			}
		}
	}()
}

func (h *CoreHandler) runPruneAndLog(trigger string) {
	n, err := h.PruneCoreHistory()
	if err != nil {
		slog.Error("core history prune failed", "trigger", trigger, "error", err)
		return
	}
	if n > 0 {
		slog.Info("core history prune complete", "trigger", trigger, "deleted", n)
	}
}

// contextLike is the subset of context.Context we need for the prune
// scheduler. Keeping this as a small interface avoids pulling context into
// this file's API while still letting main() pass a real context.Context.
type contextLike interface {
	Done() <-chan struct{}
}
