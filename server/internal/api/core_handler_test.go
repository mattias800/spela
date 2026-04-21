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
