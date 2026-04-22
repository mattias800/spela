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

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func itoa(u uint) string { return strconv.FormatUint(uint64(u), 10) }

func TestListCores_AzaharHasDownloadURL(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	require.Equal(t, http.StatusOK, w.Code)

	var cores []map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &cores))

	var azahar map[string]interface{}
	for _, c := range cores {
		if c["name"] == "azahar" {
			azahar = c
			break
		}
	}
	require.NotNil(t, azahar, "azahar core should be seeded")
	url, _ := azahar["downloadUrl"].(string)
	assert.Contains(t, url, "github.com/azahar-emu/azahar")
	assert.Contains(t, url, "{platform}")
}

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

	for _, c := range cores {
		if c.Name == "nestopia" || c.Name == "snes9x" || c.Name == "dolphin" {
			assert.Empty(t, c.DownloadURL, "buildbot core %s should have no downloadUrl", c.Name)
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
// populated DownloadURL (pinned cores like azahar) has its DownloadURL
// copied into SourceURL on the first-serve backfill.
func TestDownloadCore_PinnedCoreCopiesDownloadURL(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	require.NoError(t, os.MkdirAll(cfg.CoreDir, 0o755))
	payload := []byte("pinned-core-bytes")
	corePath := filepath.Join(cfg.CoreDir, "azahar_libretro.so")
	require.NoError(t, os.WriteFile(corePath, payload, 0o644))

	var core db.Core
	require.NoError(t, database.Where("name = ?", "azahar").First(&core).Error)
	require.NotEmpty(t, core.DownloadURL, "seeded azahar core should have a DownloadURL template")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores/"+itoa(core.ID)+"/download?platform=linux", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var refreshed db.Core
	require.NoError(t, database.First(&refreshed, core.ID).Error)
	assert.Equal(t, core.DownloadURL, refreshed.SourceURL,
		"pinned core's DownloadURL template should be copied to SourceURL on first serve")
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
