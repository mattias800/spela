package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// nonPlayableTestEnv holds fixtures for non-playable console gate tests.
type nonPlayableTestEnv struct {
	database       *gorm.DB
	router         http.Handler
	token          string
	playableGameID string // game on a playable console (NES)
	nonPlayGameID  string // game on a non-playable console (PS3)
}

func setupNonPlayableTestEnv(t *testing.T) *nonPlayableTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token := registerAndGetToken(t, router)

	// Find a playable console (NES)
	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)
	require.True(t, nes.Playable, "NES should be playable")

	// Find a non-playable console (PS3)
	var ps3 db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PS3").First(&ps3).Error)
	require.False(t, ps3.Playable, "PS3 should be non-playable")

	// Create a game on the playable console
	playableGame := db.Game{
		ConsoleID: nes.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  100,
	}
	database.Create(&playableGame)

	// Create a game on the non-playable console
	nonPlayGame := db.Game{
		ConsoleID: ps3.ID,
		Title:     "The Last of Us",
		FileName:  "tlou.iso",
		FilePath:  "/tmp/tlou.iso",
		FileSize:  100,
	}
	database.Create(&nonPlayGame)

	return &nonPlayableTestEnv{
		database:       database,
		router:         router,
		token:          token,
		playableGameID: fmt.Sprintf("%d", playableGame.ID),
		nonPlayGameID:  fmt.Sprintf("%d", nonPlayGame.ID),
	}
}

func TestRequirePlayableConsole(t *testing.T) {
	database, _ := setupTestEnv(t)

	// Find playable and non-playable consoles
	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	var ps3 db.Console
	database.Where("abbreviation = ?", "PS3").First(&ps3)

	playableGame := db.Game{ConsoleID: nes.ID, Title: "Test", FileName: "test.nes", FilePath: "/tmp/test.nes"}
	database.Create(&playableGame)

	nonPlayableGame := db.Game{ConsoleID: ps3.ID, Title: "Test PS3", FileName: "test.iso", FilePath: "/tmp/test.iso"}
	database.Create(&nonPlayableGame)

	t.Run("returns nil for playable console", func(t *testing.T) {
		err := requirePlayableConsole(database, playableGame.ID)
		assert.NoError(t, err)
	})

	t.Run("returns error for non-playable console", func(t *testing.T) {
		err := requirePlayableConsole(database, nonPlayableGame.ID)
		assert.Equal(t, errNonPlayableConsole, err)
	})

	t.Run("returns error for non-existent game", func(t *testing.T) {
		err := requirePlayableConsole(database, 99999)
		assert.Error(t, err)
	})
}

// --- Challenge creation ---

func TestNonPlayable_CreateChallenge_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Speed Run")
	writer.WriteField("gameId", env.nonPlayGameID)
	writer.WriteField("type", "speedrun")
	writer.WriteField("difficulty", "medium")
	part, _ := writer.CreateFormFile("save", "save.state")
	part.Write([]byte("fake-save-state"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

func TestNonPlayable_CreateChallenge_Allowed(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Speed Run")
	writer.WriteField("gameId", env.playableGameID)
	writer.WriteField("type", "speedrun")
	writer.WriteField("difficulty", "medium")
	part, _ := writer.CreateFormFile("save", "save.state")
	part.Write([]byte("fake-save-state"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/challenges", &buf)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code, w.Body.String())
}

// --- Netplay session creation ---

func TestNonPlayable_CreateNetplaySession_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	body, _ := json.Marshal(map[string]interface{}{
		"gameId": env.nonPlayGameID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

// --- RetroAchievements endpoints ---

func TestNonPlayable_GetGameAchievements_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.nonPlayGameID+"/achievements", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

func TestNonPlayable_GetAchievementProgress_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.nonPlayGameID+"/achievements/progress", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

func TestNonPlayable_GetAchievementTimeline_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.nonPlayGameID+"/achievements/timeline", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

func TestNonPlayable_GetAchievementLeaderboard_Blocked(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.nonPlayGameID+"/achievements/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
	var resp map[string]string
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "feature not available for non-playable consoles", resp["error"])
}

// --- Download game (should NOT be blocked) ---

func TestNonPlayable_DownloadGame_Allowed(t *testing.T) {
	env := setupNonPlayableTestEnv(t)

	// DownloadGame will return 404 for file-not-found (the file doesn't actually exist),
	// but importantly it should NOT return 400 for non-playable console.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.nonPlayGameID+"/download", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)

	// It should be 404 (file not found on disk) NOT 400 (non-playable)
	assert.NotEqual(t, http.StatusBadRequest, w.Code, "DownloadGame must not block non-playable consoles")
}
