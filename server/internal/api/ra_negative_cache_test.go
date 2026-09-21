package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync/atomic"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// unmatchedROMContent hashes to something newMockRAServerCounting does not
// know, so lookups for it come back Success:true GameID:0 — RA's "no match".
var unmatchedROMContent = []byte("rom content that RA has never seen")

// raEnv wires a Config + router around an RA mock, and drops both a ROM the
// mock recognises and one it doesn't onto disk.
func raEnv(t *testing.T, mockRA *httptest.Server) (http.Handler, *Config) {
	t.Helper()

	_, cfg := setupTestEnv(t)
	cfg.RAClient = &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()}

	romDir := filepath.Join(cfg.GameDirs[0], "roms")
	require.NoError(t, os.MkdirAll(romDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "testgame.nes"), romContentForTest, 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "unmatched.nes"), unmatchedROMContent, 0o644))

	router, cleanup := NewRouter(*cfg)
	t.Cleanup(cleanup)
	return router, cfg
}

// countingRATestEnv serves the canonical RA mock and counts gameid lookups.
func countingRATestEnv(t *testing.T) (http.Handler, *Config, *int64) {
	t.Helper()
	var lookups int64
	mockRA := newMockRAServerCounting(t, &lookups)
	t.Cleanup(mockRA.Close)
	router, cfg := raEnv(t, mockRA)
	return router, cfg, &lookups
}

// failingRATestEnv serves a mock whose gameid lookups always fail with a 500,
// i.e. a transient upstream failure rather than a definitive "no match".
// Login still succeeds so the caller can link an RA account.
func failingRATestEnv(t *testing.T) (http.Handler, *Config) {
	t.Helper()
	mockRA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		action := r.URL.Query().Get("r")
		if action == "" {
			r.ParseForm()
			action = r.FormValue("r")
		}
		if action == "login" {
			json.NewEncoder(w).Encode(map[string]interface{}{"Success": true, "Token": "ra-test-token-123"})
			return
		}
		http.Error(w, "upstream exploded", http.StatusInternalServerError)
	}))
	t.Cleanup(mockRA.Close)
	router, cfg := raEnv(t, mockRA)
	return router, cfg
}

// createUnmatchedGame creates a game whose ROM hash RA does not recognise.
func createUnmatchedGame(t *testing.T, cfg *Config) db.Game {
	t.Helper()
	var console db.Console
	cfg.DB.First(&console)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Unmatched Game",
		FileName:  "unmatched.nes",
		FilePath:  "roms/unmatched.nes",
		FileSize:  int64(len(unmatchedROMContent)),
	}
	require.NoError(t, cfg.DB.Create(&game).Error)
	return game
}

// linkRA links the calling user's RA account against the mock server.
func linkRA(t *testing.T, router http.Handler, token string) {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
}

func raGet(t *testing.T, router http.Handler, token, path string, gameID uint) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/%s", gameID, path), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func reloadGame(t *testing.T, cfg *Config, id uint) db.Game {
	t.Helper()
	var g db.Game
	require.NoError(t, cfg.DB.First(&g, id).Error)
	return g
}

func TestGetAchievementProgress_PersistsNoMatch(t *testing.T) {
	router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements/progress", game.ID).Code)

	updated := reloadGame(t, cfg, game.ID)
	assert.True(t, updated.RAHashChecked, "a completed no-match lookup must be persisted")
	assert.Equal(t, uint(0), updated.RAGameID)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups))

	// Every later page view must be served from the negative cache.
	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements/progress", game.ID).Code)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups), "second view must not re-query RA")
}

func TestGetAchievementProgress_SkipsLookupWhenHashCheckedNoMatch(t *testing.T) {
	router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)
	require.NoError(t, cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true, "ra_game_id": 0}).Error)

	w := raGet(t, router, token, "achievements/progress", game.ID)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, float64(0), resp["raGameId"])
	assert.Empty(t, resp["progress"])
	assert.Equal(t, int64(0), atomic.LoadInt64(lookups), "no dorequest.php traffic for a known no-match")
}

func TestGetAchievementProgress_SetsHashCheckedOnMatch(t *testing.T) {
	router, cfg, _ := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createGameWithROM(t, cfg)

	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements/progress", game.ID).Code)

	updated := reloadGame(t, cfg, game.ID)
	assert.Equal(t, uint(42), updated.RAGameID)
	assert.True(t, updated.RAHashChecked, "a successful match must also record the hash as checked")
}

func TestGetGameAchievements_PersistsNoMatch(t *testing.T) {
	router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements", game.ID).Code)

	updated := reloadGame(t, cfg, game.ID)
	assert.True(t, updated.RAHashChecked, "a completed no-match lookup must be persisted")
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups))

	// The existing RAHashChecked guard can now actually fire.
	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements", game.ID).Code)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups), "second view must not re-query RA")
}

// The transient branch is the dangerous one: nothing ever clears RAHashChecked,
// so marking a game on a network blip hides it permanently. Both handlers must
// leave the flag alone unless RA gave a definitive answer.

func TestGetAchievementProgress_TransientFailureStaysRetryable(t *testing.T) {
	router, cfg := failingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements/progress", game.ID).Code)

	updated := reloadGame(t, cfg, game.ID)
	assert.False(t, updated.RAHashChecked, "a transient RA failure must NOT be negative-cached")
	assert.Equal(t, uint(0), updated.RAGameID)
}

func TestGetGameAchievements_TransientFailureStaysRetryable(t *testing.T) {
	router, cfg := failingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, raGet(t, router, token, "achievements", game.ID).Code)

	updated := reloadGame(t, cfg, game.ID)
	assert.False(t, updated.RAHashChecked, "a transient RA failure must NOT be negative-cached")
}
