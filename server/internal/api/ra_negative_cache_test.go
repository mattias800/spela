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

// unmatchedROMContent hashes to something the mock RA server does not know,
// so lookups for it come back Success:true GameID:0 — RA's "no match" reply.
var unmatchedROMContent = []byte("rom content that RA has never seen")

// countingRATestEnv mirrors setupRATestEnv but counts dorequest.php gameid
// lookups, so tests can assert that a known no-match never hits RA twice
// (#1674). It also drops a second ROM on disk whose hash RA won't recognise.
func countingRATestEnv(t *testing.T) (*httptest.Server, http.Handler, *Config, *int64) {
	t.Helper()

	var lookups int64
	expectedHash := romHashForTest()

	mockRA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dorequest.php":
			action := r.URL.Query().Get("r")
			if action == "" {
				r.ParseForm()
				action = r.FormValue("r")
			}
			switch action {
			case "login":
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"Token":   "ra-test-token-123",
				})
			case "gameid":
				atomic.AddInt64(&lookups, 1)
				gameID := float64(0)
				if r.URL.Query().Get("m") == expectedHash {
					gameID = 42
				}
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"GameID":  gameID,
				})
			}
		case "/API/API_GetGameInfoAndUserProgress.php":
			json.NewEncoder(w).Encode(map[string]interface{}{
				"ID":           42,
				"Title":        "Test ROM Game",
				"Achievements": map[string]interface{}{},
			})
		}
	}))
	t.Cleanup(mockRA.Close)

	_, cfg := setupTestEnv(t)
	cfg.RAClient = &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()}

	romDir := filepath.Join(cfg.GameDirs[0], "roms")
	require.NoError(t, os.MkdirAll(romDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "testgame.nes"), romContentForTest, 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "unmatched.nes"), unmatchedROMContent, 0o644))

	router, cleanup := NewRouter(*cfg)
	t.Cleanup(cleanup)
	return mockRA, router, cfg, &lookups
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

func getProgress(t *testing.T, router http.Handler, token string, gameID uint) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", gameID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func getAchievements(t *testing.T, router http.Handler, token string, gameID uint) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", gameID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func TestGetAchievementProgress_PersistsNoMatch(t *testing.T) {
	_, router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, getProgress(t, router, token, game.ID).Code)

	var updated db.Game
	require.NoError(t, cfg.DB.First(&updated, game.ID).Error)
	assert.True(t, updated.RAHashChecked, "a completed no-match lookup must be persisted")
	assert.Equal(t, uint(0), updated.RAGameID)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups))

	// Every later page view must be served from the negative cache.
	assert.Equal(t, http.StatusOK, getProgress(t, router, token, game.ID).Code)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups), "second view must not re-query RA")
}

func TestGetAchievementProgress_SkipsLookupWhenHashCheckedNoMatch(t *testing.T) {
	_, router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)
	require.NoError(t, cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true, "ra_game_id": 0}).Error)

	w := getProgress(t, router, token, game.ID)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["raGameId"])
	assert.Empty(t, resp["progress"].([]interface{}))
	assert.Equal(t, int64(0), atomic.LoadInt64(lookups), "no dorequest.php traffic for a known no-match")
}

func TestGetAchievementProgress_SetsHashCheckedOnMatch(t *testing.T) {
	_, router, cfg, _ := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createGameWithROM(t, cfg)

	assert.Equal(t, http.StatusOK, getProgress(t, router, token, game.ID).Code)

	var updated db.Game
	require.NoError(t, cfg.DB.First(&updated, game.ID).Error)
	assert.Equal(t, uint(42), updated.RAGameID)
	assert.True(t, updated.RAHashChecked, "a successful match must also record the hash as checked")
}

func TestGetGameAchievements_PersistsNoMatch(t *testing.T) {
	_, router, cfg, lookups := countingRATestEnv(t)

	token := registerAndGetToken(t, router)
	linkRA(t, router, token)
	game := createUnmatchedGame(t, cfg)

	assert.Equal(t, http.StatusOK, getAchievements(t, router, token, game.ID).Code)

	var updated db.Game
	require.NoError(t, cfg.DB.First(&updated, game.ID).Error)
	assert.True(t, updated.RAHashChecked, "a completed no-match lookup must be persisted")
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups))

	// The existing RAHashChecked guard can now actually fire.
	assert.Equal(t, http.StatusOK, getAchievements(t, router, token, game.ID).Code)
	assert.Equal(t, int64(1), atomic.LoadInt64(lookups), "second view must not re-query RA")
}
