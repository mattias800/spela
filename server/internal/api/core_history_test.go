package api

import (
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// buildCoreHistoryEnv bootstraps the CoreHandler + CoreDir layout the
// history tests need. We deliberately do not use setupTestEnv / NewRouter
// here because the prune job cases don't need the full router, and wiring
// our own CoreHandler keeps the tests fast and focused.
func buildCoreHistoryEnv(t *testing.T) (*CoreHandler, string, *gorm.DB) {
	t.Helper()
	database, cfg := setupTestEnv(t)
	_ = cfg
	coreDir := t.TempDir()
	require.NoError(t, os.MkdirAll(coreDir, 0o755))
	return &CoreHandler{DB: database, CoreDir: coreDir}, coreDir, database
}

// writeHistoryBinary writes a fake core binary under the given core name
// and sha256 directory with the supplied mtime. Returns the absolute path.
func writeHistoryBinary(t *testing.T, coreDir, coreName, sum, ext string, mtime time.Time) string {
	t.Helper()
	path := filepath.Join(coreDir, coreHistorySubdir, sum, coreName+"_libretro"+ext)
	require.NoError(t, os.MkdirAll(filepath.Dir(path), 0o755))
	require.NoError(t, os.WriteFile(path, []byte("fake-"+sum), 0o644))
	require.NoError(t, os.Chtimes(path, mtime, mtime))
	return path
}

// TestDownloadCore_VersionedHashServesHistory verifies that when a core
// binary has been rotated on disk but the old one still lives under
// history/, a client can re-fetch the old binary by passing its sha256.
func TestDownloadCore_VersionedHashServesHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))

	// "Old" binary: write it to the live location, serve it once so the
	// metadata backfill snapshots it into history/{sha}/.
	oldPayload := []byte("old-core-binary")
	corePath := filepath.Join(cfg.CoreDir, "nestopia_libretro.so")
	require.NoError(t, os.WriteFile(corePath, oldPayload, 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, oldPayload, w.Body.Bytes())

	// Confirm the snapshot landed.
	oldSum := sha256.Sum256(oldPayload)
	oldHex := hex.EncodeToString(oldSum[:])
	historyPath := filepath.Join(cfg.CoreDir, coreHistorySubdir, oldHex, "nestopia_libretro.so")
	_, err := os.Stat(historyPath)
	require.NoError(t, err, "first serve should snapshot into history")

	// Overwrite the live binary — the "new" version. Also clear the
	// DB-cached metadata so the backfill recognises the new binary.
	newPayload := []byte("new-core-binary-v2")
	require.NoError(t, os.WriteFile(corePath, newPayload, 0o644))
	require.NoError(t, database.Model(&core).Updates(map[string]interface{}{
		"sha256": "", "size_bytes": 0, "fetched_at": nil,
	}).Error)

	// Fetch without sha256 → returns the new bytes.
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req2.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w2, req2)
	require.Equal(t, http.StatusOK, w2.Code)
	assert.Equal(t, newPayload, w2.Body.Bytes())

	// Fetch WITH the old sha256 → must return the old bytes from history.
	w3 := httptest.NewRecorder()
	req3 := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux&sha256="+oldHex, nil)
	req3.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w3, req3)
	require.Equal(t, http.StatusOK, w3.Code, "historical fetch should succeed: %s", w3.Body.String())
	assert.Equal(t, oldPayload, w3.Body.Bytes(),
		"?sha256= must serve the snapshot from history, not the live binary")
}

// TestDownloadCore_UnknownHashReturns404 verifies that requesting a sha256
// that was never snapshotted (or has been pruned) returns 404 rather than
// falling back to the current binary.
func TestDownloadCore_UnknownHashReturns404(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(cfg.CoreDir, "nestopia_libretro.so"), []byte("anything"), 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	unknown := "0000000000000000000000000000000000000000000000000000000000000000"
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux&sha256="+unknown, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// TestPrune_KeepsLast3 verifies the count-based retention: even when every
// binary is old, the three most-recent per-core survive.
func TestPrune_KeepsLast3(t *testing.T) {
	h, coreDir, _ := buildCoreHistoryEnv(t)

	// Five snapshots for the same core, all older than the age cutoff so
	// only the last-3 policy is keeping anything.
	base := time.Now().Add(-120 * 24 * time.Hour)
	paths := []string{
		writeHistoryBinary(t, coreDir, "nestopia", "shaA", ".so", base.Add(-4*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaB", ".so", base.Add(-3*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaC", ".so", base.Add(-2*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaD", ".so", base.Add(-1*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaE", ".so", base),
	}

	deleted, err := h.PruneCoreHistory()
	require.NoError(t, err)
	assert.Equal(t, 2, deleted)

	// Two oldest gone, three newest kept.
	for _, p := range paths[:2] {
		_, err := os.Stat(p)
		assert.True(t, os.IsNotExist(err), "expected %s to be pruned", p)
	}
	for _, p := range paths[2:] {
		_, err := os.Stat(p)
		assert.NoError(t, err, "expected %s to be kept", p)
	}
}

// TestPrune_Keeps90DayWindow verifies the age-based retention: files newer
// than 90 days survive regardless of count.
func TestPrune_Keeps90DayWindow(t *testing.T) {
	h, coreDir, _ := buildCoreHistoryEnv(t)

	now := time.Now()
	// Five snapshots all newer than the 90-day cutoff. All must be kept.
	paths := []string{
		writeHistoryBinary(t, coreDir, "nestopia", "shaV", ".so", now.Add(-80*24*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaW", ".so", now.Add(-60*24*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaX", ".so", now.Add(-30*24*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaY", ".so", now.Add(-7*24*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaZ", ".so", now.Add(-1*time.Hour)),
	}

	deleted, err := h.PruneCoreHistory()
	require.NoError(t, err)
	assert.Equal(t, 0, deleted)

	for _, p := range paths {
		_, err := os.Stat(p)
		assert.NoError(t, err, "expected %s to be kept by 90-day policy", p)
	}
}

// TestPrune_KeepsUnionOfBoth verifies that the two policies union — a
// binary is kept if EITHER rule keeps it. We set up a per-core mix of
// fresh and stale entries so the behaviour shows in both directions.
func TestPrune_KeepsUnionOfBoth(t *testing.T) {
	h, coreDir, _ := buildCoreHistoryEnv(t)

	now := time.Now()
	old := now.Add(-200 * 24 * time.Hour) // older than the 90-day cutoff
	fresh := now.Add(-10 * 24 * time.Hour) // newer than the cutoff

	// Layout, newest first:
	//   shaN1 (fresh) — kept by age AND by count
	//   shaN2 (fresh) — kept by age AND by count
	//   shaN3 (old)   — kept by count (index 2)
	//   shaN4 (old)   — kept by NEITHER → pruned
	//   shaN5 (fresh) — kept by age (even though count would drop it)
	paths := []string{
		writeHistoryBinary(t, coreDir, "nestopia", "shaN1", ".so", now.Add(-1*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaN2", ".so", now.Add(-2*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaN3", ".so", old.Add(-1*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaN4", ".so", old.Add(-2*time.Hour)),
		writeHistoryBinary(t, coreDir, "nestopia", "shaN5", ".so", fresh),
	}
	// Note: shaN5 has mtime `fresh` = -10 days which is NEWER than shaN2/N1.
	// Re-bucket the expectations for clarity:
	//   sort order newest→oldest: N1, N2, N5, N3, N4
	//   kept by last-3:           N1, N2, N5
	//   kept by age (<=90d):      N1, N2, N5
	//   kept by NEITHER:          N3, N4 → both deleted

	deleted, err := h.PruneCoreHistory()
	require.NoError(t, err)
	assert.Equal(t, 2, deleted)

	_, err = os.Stat(paths[2])
	assert.True(t, os.IsNotExist(err), "shaN3 must be pruned (old AND not in last-3)")
	_, err = os.Stat(paths[3])
	assert.True(t, os.IsNotExist(err), "shaN4 must be pruned (old AND not in last-3)")
	for _, p := range []string{paths[0], paths[1], paths[4]} {
		_, err := os.Stat(p)
		assert.NoError(t, err, "expected %s kept by union of rules", p)
	}
}
