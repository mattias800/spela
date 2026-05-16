package api

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func itoa(u uint) string { return strconv.FormatUint(uint64(u), 10) }

func TestListCores_BuildbotCoresHaveNoDownloadURL(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)

	var cores []db.Core
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &cores))

	// `azahar` joined this set in #1188 — pre-#1188 it was pinned to a
	// GitHub release that broke on Apple Silicon and 404'd on Android.
	// Guard against a regression that re-adds the override.
	for _, c := range cores {
		if c.Name == "nestopia" || c.Name == "snes9x" || c.Name == "dolphin" || c.Name == "azahar" {
			assert.Empty(t, c.CustomDownloadURL, "buildbot core %s should have no downloadUrl", c.Name)
		}
	}
}

// TestDownloadCore_BackfillsMetadata verifies that the first serve of a
// core binary computes and persists the factual metadata (sha256, size,
// fetchedAt) — #555 Phase 1.
func TestDownloadCore_BackfillsMetadata(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Ensure the CoreDir exists and place a deterministic binary for nestopia.
	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	payload := []byte("libretro-core-bytes-for-test")
	corePath := filepath.Join(cfg.CoreDir, "nestopia_libretro.so")
	require.NoError(t, os.WriteFile(corePath, payload, 0o644))

	// Look up the seeded nestopia core ID.
	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)
	// Metadata fields should be empty before the first serve.
	assert.Empty(t, core.Sha256)
	assert.Zero(t, core.SizeBytes)
	assert.Nil(t, core.FetchedAt)

	// First download triggers the metadata backfill.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, payload, w.Body.Bytes())

	// Row should now have populated metadata.
	var refreshed db.Core
	require.NoError(t, database.First(&refreshed, core.ID).Error)

	expectedSum := sha256.Sum256(payload)
	assert.Equal(t, hex.EncodeToString(expectedSum[:]), refreshed.Sha256)
	assert.Equal(t, int64(len(payload)), refreshed.SizeBytes)
	require.NotNil(t, refreshed.FetchedAt)
	assert.False(t, refreshed.FetchedAt.IsZero())
	// Nestopia is a buildbot core (DownloadURL is empty in the seed), so
	// SourceURL must stay empty — the helper only copies a non-empty
	// DownloadURL into SourceURL. An admin cores UI follow-up can assert
	// the complementary path for pinned cores like azahar.
	assert.Empty(t, refreshed.SourceURL)

	// A second serve must not re-hash (the row is already populated, so the
	// FetchedAt timestamp should remain stable).
	firstFetchedAt := *refreshed.FetchedAt

	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req2.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w2, req2)
	require.Equal(t, http.StatusOK, w2.Code)

	var refreshedAgain db.Core
	require.NoError(t, database.First(&refreshedAgain, core.ID).Error)
	require.NotNil(t, refreshedAgain.FetchedAt)
	assert.Equal(t, firstFetchedAt.Unix(), refreshedAgain.FetchedAt.Unix(),
		"fetchedAt should not change on subsequent serves")
}

// TestDownloadCore_PinnedCoreCopiesDownloadURL verifies that a core with a
// populated CustomDownloadURL has it copied into SourceURL on the first-
// serve metadata backfill. Spela no longer ships any seeded pinned cores
// (azahar moved to the buildbot default in #1188), so the fixture is a
// synthetic row written into the DB just for this test.
func TestDownloadCore_PinnedCoreCopiesDownloadURL(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	payload := []byte("pinned-core-bytes")
	corePath := filepath.Join(cfg.CoreDir, "fake_pinned_libretro.so")
	require.NoError(t, os.WriteFile(corePath, payload, 0o644))

	pinnedURL := "https://example.test/cores/fake_pinned-{platform}.zip"
	core := db.Core{
		Name:              "fake_pinned",
		DisplayName:       "Fake Pinned Core",
		Platforms:         "linux",
		CustomDownloadURL: pinnedURL,
	}
	require.NoError(t, database.Create(&core).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var refreshed db.Core
	require.NoError(t, database.First(&refreshed, core.ID).Error)
	assert.Equal(t, pinnedURL, refreshed.SourceURL,
		"pinned core's CustomDownloadURL should be copied to SourceURL on first serve")
}

// TestHashFileSha256_Errors verifies that hashFileSha256 returns an error
// for unreadable paths. This pins the contract that `ensureCoreMetadata`
// relies on: a hash failure surfaces as a Go error, not a panic, which
// lets the download path log-and-continue without blocking the user.
func TestHashFileSha256_Errors(t *testing.T) {
	_, _, err := hashFileSha256(filepath.Join(t.TempDir(), "does-not-exist"))
	assert.Error(t, err, "hashing a missing file must return an error, not panic")
}

// TestGetCoreManifest_PristineCore verifies that GET /api/cores/{id}/manifest
// on a row the server hasn't yet fingerprinted returns zero-ish values
// instead of erroring — the player must be able to poll the endpoint
// before its first download. #555 Phase 2.
func TestGetCoreManifest_PristineCore(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)
	require.Empty(t, core.Sha256, "seeded nestopia must be pristine at start")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/manifest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var manifest struct {
		Sha256    string  `json:"sha256"`
		SizeBytes int64   `json:"sizeBytes"`
		FetchedAt *string `json:"fetchedAt"`
		SourceURL string  `json:"sourceUrl"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &manifest))

	assert.Empty(t, manifest.Sha256)
	assert.Zero(t, manifest.SizeBytes)
	assert.Nil(t, manifest.FetchedAt)
	assert.Empty(t, manifest.SourceURL)
}

// TestGetCoreManifest_FingerprintedCore verifies that after the first
// download populates the sha256/size/fetchedAt fields, the manifest
// endpoint serves the same fingerprint the download path persists —
// the player uses this to decide whether its cached binary is current.
func TestGetCoreManifest_FingerprintedCore(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	payload := []byte("manifest-fingerprint-payload")
	corePath := filepath.Join(cfg.CoreDir, "nestopia_libretro.so")
	require.NoError(t, os.WriteFile(corePath, payload, 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	// Prime metadata by hitting /download once.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Manifest must match what was persisted.
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/manifest", nil)
	req2.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w2, req2)
	require.Equal(t, http.StatusOK, w2.Code)

	var manifest struct {
		Sha256    string  `json:"sha256"`
		SizeBytes int64   `json:"sizeBytes"`
		FetchedAt *string `json:"fetchedAt"`
		SourceURL string  `json:"sourceUrl"`
	}
	require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &manifest))

	expectedSum := sha256.Sum256(payload)
	assert.Equal(t, hex.EncodeToString(expectedSum[:]), manifest.Sha256)
	assert.Equal(t, int64(len(payload)), manifest.SizeBytes)
	require.NotNil(t, manifest.FetchedAt)
	assert.NotEmpty(t, *manifest.FetchedAt)
}

// TestGetCoreManifest_UnknownCore verifies that the endpoint returns a
// proper 404 for a core row that doesn't exist rather than leaking a
// zero-valued body.
func TestGetCoreManifest_UnknownCore(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/99999/manifest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// TestGetCoreManifest_RequiresAuth verifies that unauthenticated callers
// cannot poll the manifest. Fingerprint data is low-sensitivity but still
// lives behind the same auth wall as the rest of the cores API.
func TestGetCoreManifest_RequiresAuth(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/manifest", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// TestRefreshCore_PicksUpReplacedBinary is the canonical #555 Phase 2b
// test: Phase 1 backfill is lazy and only runs when the row has missing
// metadata. If an admin drops a newer core binary onto disk after the
// row is populated, the stored sha256 goes stale. The refresh endpoint
// must re-hash and update the row.
func TestRefreshCore_PicksUpReplacedBinary(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	adminToken := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	corePath := filepath.Join(cfg.CoreDir, "nestopia_libretro.so")
	originalPayload := []byte("nestopia-v1-bytes")
	require.NoError(t, os.WriteFile(corePath, originalPayload, 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	// Prime metadata via a download — Phase 1 backfill records the
	// original sha256.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var afterDownload db.Core
	require.NoError(t, database.First(&afterDownload, core.ID).Error)
	originalSum := sha256.Sum256(originalPayload)
	require.Equal(t, hex.EncodeToString(originalSum[:]), afterDownload.Sha256)

	// Simulate the admin replacing the binary out-of-band.
	newPayload := []byte("nestopia-v2-newer-bytes-with-different-length")
	require.NoError(t, os.WriteFile(corePath, newPayload, 0o644))

	// POST the refresh. Stored sha should move to the new payload's
	// digest and `changed` should be true.
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("POST", "/api/cores/"+itoa(core.ID)+"/refresh?platform=linux", nil)
	req2.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w2, req2)
	require.Equal(t, http.StatusOK, w2.Code)

	var resp struct {
		Changed   bool   `json:"changed"`
		OldSha256 string `json:"oldSha256"`
		Sha256    string `json:"sha256"`
		SizeBytes int64  `json:"sizeBytes"`
	}
	require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &resp))

	newSum := sha256.Sum256(newPayload)
	expectedNew := hex.EncodeToString(newSum[:])
	expectedOld := hex.EncodeToString(originalSum[:])

	assert.True(t, resp.Changed, "refresh must flag changed=true when sha differs")
	assert.Equal(t, expectedOld, resp.OldSha256)
	assert.Equal(t, expectedNew, resp.Sha256)
	assert.Equal(t, int64(len(newPayload)), resp.SizeBytes)

	// DB should reflect the new sha too.
	var refreshed db.Core
	require.NoError(t, database.First(&refreshed, core.ID).Error)
	assert.Equal(t, expectedNew, refreshed.Sha256)
	assert.Equal(t, int64(len(newPayload)), refreshed.SizeBytes)

	// And a system event must have been recorded so admins can audit
	// the replacement.
	var events []db.SystemEvent
	require.NoError(t, database.Where("event_type = ?", db.SystemEventCoreUpdated).Find(&events).Error)
	require.Len(t, events, 1, "refresh must emit exactly one core_updated event")
	assert.Contains(t, events[0].Metadata, expectedNew)
	assert.Contains(t, events[0].Metadata, expectedOld)
}

// TestRefreshCore_UnchangedBinarySkipsSystemEvent verifies that
// re-hashing the same bytes is a no-op at the audit level — we don't
// want the system events feed spammed by idle admin-refresh clicks.
func TestRefreshCore_UnchangedBinarySkipsSystemEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	adminToken := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	corePath := filepath.Join(cfg.CoreDir, "nestopia_libretro.so")
	payload := []byte("unchanged-bytes")
	require.NoError(t, os.WriteFile(corePath, payload, 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	// Prime via download.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Refresh without touching the file.
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("POST", "/api/cores/"+itoa(core.ID)+"/refresh?platform=linux", nil)
	req2.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w2, req2)
	require.Equal(t, http.StatusOK, w2.Code)

	var resp struct {
		Changed bool `json:"changed"`
	}
	require.NoError(t, json.Unmarshal(w2.Body.Bytes(), &resp))
	assert.False(t, resp.Changed, "refresh must flag changed=false when sha matches")

	// No core_updated event.
	var count int64
	require.NoError(t, database.Model(&db.SystemEvent{}).Where("event_type = ?", db.SystemEventCoreUpdated).Count(&count).Error)
	assert.Equal(t, int64(0), count, "refresh on identical bytes must not emit an audit event")
}

// TestRefreshCore_MissingBinaryReturns404 verifies the admin gets a
// useful error when the on-disk binary is missing (e.g. the CoreDir
// hasn't been populated yet for this platform).
func TestRefreshCore_MissingBinaryReturns404(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	adminToken := registerAndGetToken(t, router)

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	// No binary on disk.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/cores/"+itoa(core.ID)+"/refresh?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// TestRefreshCore_RequiresAdmin verifies non-admin callers are rejected.
// Refresh is a privileged mutation — it changes persistent server state
// and emits an audit event — so it must sit behind RequireAdmin.
func TestRefreshCore_RequiresAdmin(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Direct DB insert — mirrors the pattern in admin_deleted_users_test.go.
	user := db.User{
		Username:     "regular-core",
		Email:        "regular-core@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	var core db.Core
	require.NoError(t, database.Where("name = ?", "nestopia").First(&core).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/cores/"+itoa(core.ID)+"/refresh", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}
