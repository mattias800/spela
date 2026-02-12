package api

import (
	"bytes"
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// romContentForTest is deterministic test ROM content.
var romContentForTest = []byte("fake rom content for testing")

func romHashForTest() string {
	h := md5.Sum(romContentForTest)
	return hex.EncodeToString(h[:])
}

// newMockRAServer creates an httptest server that mimics the RA API.
func newMockRAServer(t *testing.T) *httptest.Server {
	t.Helper()
	expectedHash := romHashForTest()

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dorequest.php":
			action := r.URL.Query().Get("r")
			if action == "" {
				r.ParseForm()
				action = r.FormValue("r")
			}
			switch action {
			case "login":
				r.ParseForm()
				if r.FormValue("u") == "rauser" && r.FormValue("p") == "rapass" {
					json.NewEncoder(w).Encode(map[string]interface{}{
						"Success": true,
						"Token":   "ra-test-token-123",
					})
				} else {
					json.NewEncoder(w).Encode(map[string]interface{}{
						"Success": false,
						"Error":   "Invalid credentials",
					})
				}
			case "gameid":
				hash := r.URL.Query().Get("m")
				if hash == expectedHash {
					json.NewEncoder(w).Encode(map[string]interface{}{
						"Success": true,
						"GameID":  float64(42),
					})
				} else {
					json.NewEncoder(w).Encode(map[string]interface{}{
						"Success": true,
						"GameID":  float64(0),
					})
				}
			}
		case "/API/API_GetGameInfoAndUserProgress.php":
			json.NewEncoder(w).Encode(map[string]interface{}{
				"ID":    42,
				"Title": "Test ROM Game",
				"Achievements": map[string]interface{}{
					"501": map[string]interface{}{
						"ID":                 501,
						"Title":              "First Achievement",
						"Description":        "Do the thing",
						"Points":             10,
						"BadgeName":          "badge1",
						"type":               3,
						"DateEarned":         "2024-03-01 10:00:00",
						"DateEarnedHardcore": "",
					},
					"502": map[string]interface{}{
						"ID":                 502,
						"Title":              "Second Achievement",
						"Description":        "Do another thing",
						"Points":             20,
						"BadgeName":          "badge2",
						"type":               3,
						"DateEarned":         "",
						"DateEarnedHardcore": "",
					},
				},
			})
		}
	}))
}

// setupRATestEnv creates a test environment with a mock RA server injected via Config.RAClient.
func setupRATestEnv(t *testing.T) (*httptest.Server, http.Handler, *Config) {
	t.Helper()

	mockRA := newMockRAServer(t)

	raClient := &retroachievements.RAClient{
		BaseURL:    mockRA.URL,
		HTTPClient: mockRA.Client(),
	}

	_, cfg := setupTestEnv(t)
	cfg.RAClient = raClient

	// Write a fake ROM file
	romDir := filepath.Join(cfg.GameDirs[0], "roms")
	os.MkdirAll(romDir, 0o755)
	romPath := filepath.Join(romDir, "testgame.nes")
	os.WriteFile(romPath, romContentForTest, 0o644)

	router := NewRouter(*cfg)
	return mockRA, router, cfg
}

// createGameWithROM creates a game in the test DB pointing at the test ROM file.
func createGameWithROM(t *testing.T, cfg *Config) db.Game {
	t.Helper()
	var console db.Console
	cfg.DB.First(&console)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Test ROM Game",
		FileName:  "testgame.nes",
		FilePath:  "roms/testgame.nes",
		FileSize:  int64(len(romContentForTest)),
	}
	require.NoError(t, cfg.DB.Create(&game).Error)
	return game
}

func TestLinkAccount_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["linked"])
	assert.Equal(t, "rauser", resp["username"])

	// Verify in DB
	var cred db.RetroAchievementCredential
	err := cfg.DB.First(&cred).Error
	require.NoError(t, err)
	assert.Equal(t, "rauser", cred.RAUsername)
	assert.Equal(t, "ra-test-token-123", cred.RAToken)
}

func TestLinkAccount_InvalidCredentials(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{"username": "bad", "password": "wrong"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestLinkAccount_MissingFields(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	tests := []struct {
		name string
		body map[string]string
	}{
		{"missing password", map[string]string{"username": "rauser"}},
		{"missing username", map[string]string{"password": "rapass"}},
		{"empty body", map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
			req.Header.Set("Authorization", "Bearer "+token)
			req.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusBadRequest, w.Code)
		})
	}
}

func TestLinkAccount_Upsert(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})

	// Link first time
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Link again (should upsert, not duplicate)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var count int64
	cfg.DB.Model(&db.RetroAchievementCredential{}).Count(&count)
	assert.Equal(t, int64(1), count)
}

func TestUnlinkAccount_Success(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Unlink
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/user/ra/link", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["linked"])
}

func TestUnlinkAccount_NotLinked(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/ra/link", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetStatus_Linked(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Status
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/ra/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["linked"])
	assert.Equal(t, "rauser", resp["username"])
	assert.Equal(t, false, resp["hardcoreEnabled"])
}

func TestGetStatus_NotLinked(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/ra/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["linked"])
	assert.Equal(t, "", resp["username"])
}

func TestUpdateSettings_HardcoreEnabled(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link first
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Enable hardcore
	body, _ = json.Marshal(map[string]interface{}{"hardcoreEnabled": true})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/ra/settings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["hardcoreEnabled"])

	// Verify via status endpoint
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/ra/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["hardcoreEnabled"])
}

func TestUpdateSettings_NotLinked(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"hardcoreEnabled": true})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/ra/settings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetToken_Success(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Get token
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/ra/token", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "rauser", resp["username"])
	assert.Equal(t, "ra-test-token-123", resp["token"])
}

func TestGetToken_NotLinked(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/ra/token", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameAchievements_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link RA
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	game := createGameWithROM(t, cfg)

	// Get achievements
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(42), resp["raGameId"])
	assert.Equal(t, "Test ROM Game", resp["title"])

	achievements := resp["achievements"].([]interface{})
	assert.Len(t, achievements, 2)
	assert.Equal(t, float64(2), resp["totalCount"])
	assert.Equal(t, float64(30), resp["totalPoints"]) // 10 + 20

	// Verify cache was created
	var cache db.GameAchievementCache
	err := cfg.DB.Where("ra_game_id = ?", 42).First(&cache).Error
	require.NoError(t, err)
	assert.Equal(t, "Test ROM Game", cache.Title)
	assert.Equal(t, 2, cache.TotalCount)
}

func TestGetGameAchievements_UsesCache(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link RA
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	game := createGameWithROM(t, cfg)

	// First call populates cache
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Second call should also succeed (using cache)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(42), resp["raGameId"])
}

func TestGetGameAchievements_GameNotFound(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/achievements", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetGameAchievements_NotLinked(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestGetAchievementProgress_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Link RA
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	game := createGameWithROM(t, cfg)

	// Get progress
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(42), resp["raGameId"])

	progress := resp["progress"].([]interface{})
	assert.Len(t, progress, 1) // Only achievement 501 was earned

	// Verify stored in DB
	var dbProgress []db.UserAchievementProgress
	cfg.DB.Find(&dbProgress)
	assert.Len(t, dbProgress, 1)
	assert.Equal(t, uint(501), dbProgress[0].AchievementRAID)
	assert.Equal(t, uint(42), dbProgress[0].RAGameID)
}

func TestGetAchievementProgress_NotLinked(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestPreferences_IncludesRAFields(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	// Not linked — check defaults
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, false, prefs["raLinked"])
	assert.Equal(t, "", prefs["raUsername"])
	assert.Equal(t, false, prefs["raHardcoreEnabled"])

	// Link
	body, _ := json.Marshal(map[string]string{"username": "rauser", "password": "rapass"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/ra/link", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Now linked — check updated fields
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, true, prefs["raLinked"])
	assert.Equal(t, "rauser", prefs["raUsername"])
	assert.Equal(t, false, prefs["raHardcoreEnabled"])
}
