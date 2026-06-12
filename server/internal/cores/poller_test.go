package cores

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/safehttp"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// fakeBuildbot serves a zipped libretro binary at the expected nightly
// path layout, optionally swapping bytes between rounds so the poller
// observes a sha change on the second pass.
type fakeBuildbot struct {
	server *httptest.Server

	// served counts hits so tests can assert the poller fanned out to
	// every (platform, arch) tuple instead of, say, polling only linux.
	served int64

	// payload is the bytes to wrap inside the zip on the NEXT request.
	// Tests rotate this between RunOnce calls to simulate a buildbot
	// nightly being republished.
	payload []byte

	// statusCode lets the test simulate buildbot failures (5xx, etc.) on
	// the next request. Zero means 200.
	statusCode int
}

func newFakeBuildbot(payload []byte) *fakeBuildbot {
	fb := &fakeBuildbot{payload: payload}
	fb.server = httptest.NewServer(http.HandlerFunc(fb.handle))
	return fb
}

func (fb *fakeBuildbot) URL() string { return fb.server.URL }
func (fb *fakeBuildbot) Close()      { fb.server.Close() }

func (fb *fakeBuildbot) handle(w http.ResponseWriter, r *http.Request) {
	atomic.AddInt64(&fb.served, 1)
	if fb.statusCode != 0 && fb.statusCode != http.StatusOK {
		w.WriteHeader(fb.statusCode)
		return
	}
	// Derive the inner filename from the asset name. URL shapes:
	//   /nightly/android/latest/{arch}/{name}_libretro_android.so.zip
	//   /nightly/apple/osx/{arch}/latest/{name}_libretro.dylib.zip
	//   /nightly/linux/{arch}/latest/{name}_libretro.so.zip
	//   /nightly/windows/{arch}/latest/{name}_libretro.dll.zip
	//
	// The asset's basename minus `.zip` is what we put inside the archive.
	base := filepath.Base(r.URL.Path)
	if !strings.HasSuffix(base, ".zip") {
		w.WriteHeader(http.StatusNotFound)
		return
	}
	inner := strings.TrimSuffix(base, ".zip")

	var buf bytes.Buffer
	zw := zip.NewWriter(&buf)
	f, err := zw.Create(inner)
	if err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if _, err := f.Write(fb.payload); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	if err := zw.Close(); err != nil {
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "application/zip")
	_, _ = w.Write(buf.Bytes())
}

func openPollerTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.Core{}, &db.CorePlatformBinary{}, &db.SystemEvent{}, &db.SystemEventCategory{}))
	// system event recorder pre-fills category rows from a private map; we
	// don't need that here because the poller's RecordOperationalEvent path
	// looks up the category by code and inserts the row lazily.
	return database
}

func newPollerForTest(t *testing.T, database *gorm.DB, fb *fakeBuildbot, matrix []PlatformArch) (*Poller, string) {
	t.Helper()
	coreDir := t.TempDir()
	safehttp.SetAllowPrivateForTest(true)
	t.Cleanup(func() { safehttp.SetAllowPrivateForTest(false) })

	p := NewPoller(database, PollerOptions{
		CoreDir:         coreDir,
		PollMatrix:      matrix,
		Interval:        time.Hour, // unused by RunOnce
		HTTPClient:      &http.Client{Timeout: 5 * time.Second},
		BaseURLOverride: fb.URL(),
		Clock:           func() time.Time { return time.Date(2026, 5, 16, 12, 0, 0, 0, time.UTC) },
	})
	return p, coreDir
}

func sha256Hex(b []byte) string {
	h := sha256.Sum256(b)
	return hex.EncodeToString(h[:])
}

// linuxOnly is a small matrix that keeps tests fast while still exercising
// the multi-arch dimension (linux × {x86_64, aarch64}).
var linuxOnlyMatrix = []PlatformArch{
	{Platform: "linux", Arch: "x86_64"},
	{Platform: "linux", Arch: "aarch64"},
}

func TestPoller_PopulatesFreshPerPlatformRows(t *testing.T) {
	database := openPollerTestDB(t)
	require.NoError(t, database.Create(&db.Core{
		Name:      "nestopia",
		Platforms: "linux",
	}).Error)

	payload := []byte("nestopia-libretro-binary-bytes")
	fb := newFakeBuildbot(payload)
	defer fb.Close()

	p, coreDir := newPollerForTest(t, database, fb, linuxOnlyMatrix)
	require.NoError(t, p.RunOnce(context.Background()))

	var rows []db.CorePlatformBinary
	require.NoError(t, database.Order("platform_arch ASC").Find(&rows).Error)
	require.Len(t, rows, 2, "one row per (core, platform-arch) tuple")

	wantSha := sha256Hex(payload)
	for _, row := range rows {
		assert.Equal(t, wantSha, row.Sha256, "row sha must match payload sha")
		assert.Equal(t, int64(len(payload)), row.SizeBytes)
		require.NotNil(t, row.FetchedAt)
		// FilePath should land under {CoreDir}/{platform-arch}/<name>_libretro.so
		assert.True(t, strings.HasPrefix(row.FilePath, filepath.Join(coreDir, row.PlatformArch)+string(filepath.Separator)),
			"FilePath %q should live under %q", row.FilePath, filepath.Join(coreDir, row.PlatformArch))
		// SourceURL should preserve the buildbot path layout. The
		// BaseURLOverride substitutes the host, so we assert on the
		// path tail rather than the full URL.
		assert.Contains(t, row.SourceURL, "/linux/")
		assert.Contains(t, row.SourceURL, "/latest/nestopia_libretro.so.zip")
		// And the file should actually be on disk with the right bytes.
		on, err := os.ReadFile(row.FilePath)
		require.NoError(t, err)
		assert.Equal(t, payload, on)
	}
}

func TestPoller_NoOpWhenShaUnchanged(t *testing.T) {
	database := openPollerTestDB(t)
	require.NoError(t, database.Create(&db.Core{
		Name:      "nestopia",
		Platforms: "linux",
	}).Error)

	payload := []byte("steady-bytes")
	fb := newFakeBuildbot(payload)
	defer fb.Close()

	p, _ := newPollerForTest(t, database, fb, linuxOnlyMatrix)
	require.NoError(t, p.RunOnce(context.Background()))
	require.NoError(t, p.RunOnce(context.Background()))

	// Second pass must not produce a second core_updated event for any
	// row; both rows should still hold the original sha.
	var events []db.SystemEvent
	require.NoError(t, database.Where("event_type = ?", db.SystemEventCoreUpdated).Find(&events).Error)
	assert.Len(t, events, 2, "one event per platform on the FIRST pass; second pass is a no-op")
}

func TestPoller_EmitsCoreUpdatedOnChange(t *testing.T) {
	database := openPollerTestDB(t)
	require.NoError(t, database.Create(&db.Core{
		Name:      "nestopia",
		Platforms: "linux",
	}).Error)

	v1 := []byte("nightly-build-001")
	v2 := []byte("nightly-build-002-bigger-payload")
	fb := newFakeBuildbot(v1)
	defer fb.Close()

	p, _ := newPollerForTest(t, database, fb, linuxOnlyMatrix)
	require.NoError(t, p.RunOnce(context.Background()))
	fb.payload = v2
	require.NoError(t, p.RunOnce(context.Background()))

	var rows []db.CorePlatformBinary
	require.NoError(t, database.Find(&rows).Error)
	for _, row := range rows {
		assert.Equal(t, sha256Hex(v2), row.Sha256, "row should track the latest poll")
	}

	var events []db.SystemEvent
	require.NoError(t, database.Where("event_type = ?", db.SystemEventCoreUpdated).Order("id ASC").Find(&events).Error)
	require.Len(t, events, 4, "two platforms × two updates (initial create + version bump)")

	// Spot-check the metadata on the latest event — Trigger must say
	// buildbot_poll, not admin_refresh.
	last := events[len(events)-1]
	var meta map[string]any
	require.NoError(t, json.Unmarshal([]byte(last.Metadata), &meta))
	assert.Equal(t, "nestopia", meta["core"])
	assert.Equal(t, "buildbot_poll", meta["trigger"])
	assert.Equal(t, sha256Hex(v2), meta["newSha256"])
}

func TestPoller_EmitsCoreUpdateFailedOnHTTPError(t *testing.T) {
	database := openPollerTestDB(t)
	require.NoError(t, database.Create(&db.Core{
		Name:      "nestopia",
		Platforms: "linux",
	}).Error)

	fb := newFakeBuildbot([]byte("ignored-because-status-is-503"))
	fb.statusCode = http.StatusServiceUnavailable
	defer fb.Close()

	p, _ := newPollerForTest(t, database, fb, linuxOnlyMatrix)
	require.NoError(t, p.RunOnce(context.Background()))

	var failures []db.SystemEvent
	require.NoError(t, database.Where("event_type = ?", db.SystemEventCoreUpdateFailed).Find(&failures).Error)
	assert.Len(t, failures, 2, "one failure event per (core, platform) tuple")

	// And the row should NOT have been created — we don't write through
	// on a failed fetch.
	var rows []db.CorePlatformBinary
	require.NoError(t, database.Find(&rows).Error)
	assert.Empty(t, rows)
}

func TestPoller_SkipsCoresWithCustomDownloadURL(t *testing.T) {
	database := openPollerTestDB(t)
	require.NoError(t, database.Create(&db.Core{
		Name:              "pinned",
		Platforms:         "linux",
		CustomDownloadURL: "https://example.test/pinned-{platform}.zip",
	}).Error)
	require.NoError(t, database.Create(&db.Core{
		Name:      "buildbot_core",
		Platforms: "linux",
	}).Error)

	fb := newFakeBuildbot([]byte("buildbot-bytes"))
	defer fb.Close()

	p, _ := newPollerForTest(t, database, fb, linuxOnlyMatrix)
	require.NoError(t, p.RunOnce(context.Background()))

	// Only the buildbot_core's rows should exist — pinned cores own
	// their own update path and the poller must not race against admin
	// uploads.
	var rows []db.CorePlatformBinary
	require.NoError(t, database.Find(&rows).Error)
	require.Len(t, rows, 2)
	var buildbotCore db.Core
	require.NoError(t, database.Where("name = ?", "buildbot_core").First(&buildbotCore).Error)
	for _, row := range rows {
		assert.Equal(t, buildbotCore.ID, row.CoreID, "every row must belong to buildbot_core, not pinned")
	}
}

// TestFetchZipBody_RefusesNonHTTPSInProduction verifies that, outside the
// test-only BaseURLOverride path, the poller refuses to fetch a core binary
// over a non-https URL — defense in depth for #1315 so no future code path
// can pull an executable over cleartext.
func TestFetchZipBody_RefusesNonHTTPSInProduction(t *testing.T) {
	p := &Poller{opts: PollerOptions{HTTPClient: &http.Client{Timeout: time.Second}}}
	_, err := p.fetchZipBody(context.Background(), "http://buildbot.libretro.com/nightly/x.zip")
	require.Error(t, err)
	assert.Contains(t, err.Error(), "non-https")
}
