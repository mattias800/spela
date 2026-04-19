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
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/spela/server/internal/auth"
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

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
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

	// Verify in DB — token should be encrypted, not plaintext
	var cred db.RetroAchievementCredential
	err := cfg.DB.First(&cred).Error
	require.NoError(t, err)
	assert.Equal(t, "rauser", cred.RAUsername)
	assert.True(t, strings.HasPrefix(cred.RAToken, "enc:"), "stored RA token should be encrypted")
	assert.NotEqual(t, "ra-test-token-123", cred.RAToken)

	// Verify decryption produces the original token
	encKey := auth.DeriveEncryptionKey(cfg.JWTSecret)
	decrypted, err := auth.Decrypt(cred.RAToken, encKey)
	require.NoError(t, err)
	assert.Equal(t, "ra-test-token-123", decrypted)
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
	assert.Equal(t, http.StatusOK, w.Code)

	// Should return empty achievements when not linked
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["raGameId"])
	assert.Equal(t, float64(0), resp["totalCount"])
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

	// Verify playTimeAtUnlock is included in response (0 since no play history)
	entry := progress[0].(map[string]interface{})
	assert.Equal(t, float64(0), entry["playTimeAtUnlock"])

	// Verify stored in DB
	var dbProgress []db.UserAchievementProgress
	cfg.DB.Find(&dbProgress)
	assert.Len(t, dbProgress, 1)
	assert.Equal(t, uint(501), dbProgress[0].AchievementRAID)
	assert.Equal(t, uint(42), dbProgress[0].RAGameID)
	assert.Equal(t, int64(0), dbProgress[0].PlayTimeAtUnlock)
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
	assert.Equal(t, http.StatusOK, w.Code)

	// Should return an empty-progress object when not linked (not a bare array).
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["raGameId"])
	assert.Empty(t, resp["progress"].([]interface{}))
}

func TestGetAchievementProgress_IncludesPlayTime(t *testing.T) {
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

	// Create play history with 3600 seconds
	var user db.User
	cfg.DB.First(&user)
	cfg.DB.Create(&db.PlayHistory{
		UserID:   user.ID,
		GameID:   game.ID,
		PlayTime: 3600,
	})

	// Get progress — should include play time
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)

	progress := resp["progress"].([]interface{})
	assert.Len(t, progress, 1)

	entry := progress[0].(map[string]interface{})
	assert.Equal(t, float64(3600), entry["playTimeAtUnlock"])

	// Verify stored in DB
	var dbProgress []db.UserAchievementProgress
	cfg.DB.Find(&dbProgress)
	assert.Len(t, dbProgress, 1)
	assert.Equal(t, int64(3600), dbProgress[0].PlayTimeAtUnlock)
}

func TestGetAchievementProgress_PreservesPlayTimeOnResync(t *testing.T) {
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

	// Create play history with 1800 seconds
	var user db.User
	cfg.DB.First(&user)
	cfg.DB.Create(&db.PlayHistory{
		UserID:   user.ID,
		GameID:   game.ID,
		PlayTime: 1800,
	})

	// First sync — should capture PlayTimeAtUnlock as 1800
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var dbProgress []db.UserAchievementProgress
	cfg.DB.Find(&dbProgress)
	require.Len(t, dbProgress, 1)
	assert.Equal(t, int64(1800), dbProgress[0].PlayTimeAtUnlock)

	// Increase play time to 7200 and re-sync
	cfg.DB.Model(&db.PlayHistory{}).Where("user_id = ? AND game_id = ?", user.ID, game.ID).
		Update("play_time", 7200)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/progress", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// PlayTimeAtUnlock should still be 1800 (preserved from first sync)
	var dbProgressAfter []db.UserAchievementProgress
	cfg.DB.Find(&dbProgressAfter)
	require.Len(t, dbProgressAfter, 1)
	assert.Equal(t, int64(1800), dbProgressAfter[0].PlayTimeAtUnlock,
		"PlayTimeAtUnlock should be preserved from initial sync, not overwritten")

	// Response should also reflect the preserved value
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	progress := resp["progress"].([]interface{})
	entry := progress[0].(map[string]interface{})
	assert.Equal(t, float64(1800), entry["playTimeAtUnlock"])
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

// seedAchievementData creates test achievement cache and progress records.
func seedAchievementData(t *testing.T, cfg *Config, gameID uint, userID uint) {
	t.Helper()
	achievements := []map[string]interface{}{
		{"id": 501, "title": "First Achievement", "description": "Do the thing", "points": 10, "badgeUrl": "https://example.com/badge1.png", "type": "core"},
		{"id": 502, "title": "Second Achievement", "description": "Do another thing", "points": 20, "badgeUrl": "https://example.com/badge2.png", "type": "core"},
	}
	achJSON, _ := json.Marshal(achievements)
	cfg.DB.Create(&db.GameAchievementCache{
		RAGameID:        42,
		GameID:          gameID,
		Title:           "Test ROM Game",
		AchievementJSON: string(achJSON),
		TotalCount:      2,
		TotalPoints:     30,
	})

	// Create progress for one achievement
	cfg.DB.Create(&db.UserAchievementProgress{
		UserID:           userID,
		AchievementRAID:  501,
		RAGameID:         42,
		UnlockedAt:       time.Date(2025, 1, 10, 8, 0, 0, 0, time.UTC),
		IsHardcore:       true,
		PlayTimeAtUnlock: 1200,
	})
}

func TestGetRecentAchievements_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	var user db.User
	cfg.DB.First(&user)
	seedAchievementData(t, cfg, game.ID, user.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/achievements/recent", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Len(t, achievements, 1)

	entry := achievements[0].(map[string]interface{})
	assert.Equal(t, float64(501), entry["achievementRaId"])
	assert.Equal(t, "First Achievement", entry["title"])
	assert.Equal(t, "Do the thing", entry["description"])
	assert.Equal(t, float64(10), entry["points"])
	assert.Equal(t, true, entry["isHardcore"])
	assert.Equal(t, float64(1200), entry["playTimeAtUnlock"])
	assert.Equal(t, "Test ROM Game", entry["gameTitle"])
}

func TestGetRecentAchievements_Empty(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/achievements/recent", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Empty(t, achievements)
}

func TestGetAchievementTimeline_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	var user db.User
	cfg.DB.First(&user)
	seedAchievementData(t, cfg, game.ID, user.ID)

	// Add play history
	cfg.DB.Create(&db.PlayHistory{
		UserID:   user.ID,
		GameID:   game.ID,
		PlayTime: 7200,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/timeline", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(42), resp["raGameId"])
	assert.Equal(t, "Test ROM Game", resp["gameTitle"])
	assert.Equal(t, float64(7200), resp["totalPlayTime"])
	assert.Equal(t, float64(2), resp["totalAchievements"])
	assert.Equal(t, float64(1), resp["unlockedCount"])
	assert.Equal(t, float64(30), resp["totalPoints"])
	assert.Equal(t, float64(10), resp["earnedPoints"])

	timeline := resp["timeline"].([]interface{})
	assert.Len(t, timeline, 1)

	entry := timeline[0].(map[string]interface{})
	assert.Equal(t, float64(501), entry["achievementRaId"])
	assert.Equal(t, "First Achievement", entry["title"])
	assert.Equal(t, float64(1200), entry["playTimeAtUnlock"])
	assert.Equal(t, true, entry["isHardcore"])
}

func TestGetAchievementTimeline_GameNotFound(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/achievements/timeline", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetAchievementTimeline_NoCache(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/timeline", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["raGameId"])
	assert.Equal(t, float64(0), resp["totalAchievements"])
	timeline := resp["timeline"].([]interface{})
	assert.Empty(t, timeline)
}

func TestGetAchievementLeaderboard_Success(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	var user db.User
	cfg.DB.First(&user)
	seedAchievementData(t, cfg, game.ID, user.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/leaderboard", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(42), resp["raGameId"])
	assert.Equal(t, float64(2), resp["totalAchievements"])

	leaderboard := resp["leaderboard"].([]interface{})
	assert.Len(t, leaderboard, 1)

	entry := leaderboard[0].(map[string]interface{})
	assert.Equal(t, strconv.FormatUint(uint64(user.ID), 10), entry["userId"])
	assert.Equal(t, "apitest", entry["username"])
	assert.Equal(t, float64(1), entry["unlockedCount"])
	assert.Equal(t, float64(10), entry["earnedPoints"])
	assert.Equal(t, false, entry["isComplete"])
}

func TestGetAchievementLeaderboard_GameNotFound(t *testing.T) {
	mockRA, router, _ := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/99999/achievements/leaderboard", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetAchievementLeaderboard_NoCache(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/leaderboard", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["raGameId"])
	leaderboard := resp["leaderboard"].([]interface{})
	assert.Empty(t, leaderboard)
}

func TestGetAchievementLeaderboard_MultipleUsers(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	var user1 db.User
	cfg.DB.First(&user1)

	// Seed cache
	achievements := []map[string]interface{}{
		{"id": 501, "title": "First", "description": "desc", "points": 10, "badgeUrl": "", "type": "core"},
		{"id": 502, "title": "Second", "description": "desc", "points": 20, "badgeUrl": "", "type": "core"},
	}
	achJSON, _ := json.Marshal(achievements)
	cfg.DB.Create(&db.GameAchievementCache{
		RAGameID:        42,
		GameID:          game.ID,
		Title:           "Test ROM Game",
		AchievementJSON: string(achJSON),
		TotalCount:      2,
		TotalPoints:     30,
	})

	// User 1: unlocked 1 achievement
	cfg.DB.Create(&db.UserAchievementProgress{
		UserID:          user1.ID,
		AchievementRAID: 501,
		RAGameID:        42,
		UnlockedAt:      time.Date(2025, 1, 10, 8, 0, 0, 0, time.UTC),
		IsHardcore:      false,
	})

	// Create second user and give them 2 achievements
	user2 := db.User{Username: "player2", Email: "p2@example.com", PasswordHash: "hash"}
	cfg.DB.Create(&user2)
	cfg.DB.Create(&db.UserAchievementProgress{
		UserID:          user2.ID,
		AchievementRAID: 501,
		RAGameID:        42,
		UnlockedAt:      time.Date(2025, 1, 5, 8, 0, 0, 0, time.UTC),
		IsHardcore:      false,
	})
	cfg.DB.Create(&db.UserAchievementProgress{
		UserID:          user2.ID,
		AchievementRAID: 502,
		RAGameID:        42,
		UnlockedAt:      time.Date(2025, 1, 8, 8, 0, 0, 0, time.UTC),
		IsHardcore:      false,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements/leaderboard", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)

	leaderboard := resp["leaderboard"].([]interface{})
	assert.Len(t, leaderboard, 2)

	// First entry should be user2 (2 unlocks)
	first := leaderboard[0].(map[string]interface{})
	assert.Equal(t, float64(2), first["unlockedCount"])
	assert.Equal(t, float64(30), first["earnedPoints"])
	assert.Equal(t, true, first["isComplete"]) // 2/2

	// Second entry should be user1 (1 unlock)
	second := leaderboard[1].(map[string]interface{})
	assert.Equal(t, float64(1), second["unlockedCount"])
	assert.Equal(t, false, second["isComplete"])
}

func TestGetGameAchievements_AutoEnqueuesRAFetch(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Set server RA API key so auto-fetch is enabled
	cfg.DB.Create(&db.ServerSetting{Key: "ra_api_key", Value: "test-server-key"})
	// Configure the scraper's RA API key so it's passed to RAHandler
	cfg.Scraper.RAAPIKey = "test-server-key"
	// Rebuild router to pick up the new RA API key
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 202 with pending status (no cache, no user RA creds)
	assert.Equal(t, http.StatusAccepted, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "pending", resp["status"])
}

func TestGetGameAchievements_ReturnsPendingWhenAlreadyQueued(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	cfg.Scraper.RAAPIKey = "test-server-key"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Manually enqueue an ra_fetch item
	cfg.Scraper.Queue.EnqueueGameWithType(game.ID, nil, 100, "ra_fetch")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "pending", resp["status"])
}

func TestGetGameAchievements_ReturnsCachedData(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Pre-populate the RA game ID and cache
	cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_game_id": 42, "ra_hash_checked": true})
	achJSON, _ := json.Marshal([]map[string]interface{}{
		{"ID": 501, "Title": "Test Achievement", "Description": "Do thing", "Points": 10, "BadgeName": "badge1", "type": "core"},
	})
	cfg.DB.Create(&db.GameAchievementCache{
		RAGameID: 42, GameID: game.ID, Title: "Test ROM Game",
		AchievementJSON: string(achJSON), TotalCount: 1, TotalPoints: 10,
		CachedAt: time.Now(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 with cached data
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(1), resp["totalCount"])
}

func TestGetGameAchievements_NoAutoFetchWithoutRAKey(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Pre-set RAGameID so we skip the inline hash lookup (which would call the mock RA server)
	cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_game_id": 42, "ra_hash_checked": true})

	// No server RA API key configured, no user RA credentials

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 with empty response (no auto-fetch without RA key)
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Empty(t, achievements)
}

func TestGetGameAchievements_SkipsAutoFetchWhenHashCheckedNoMatch(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Mark game as checked but no RA match
	cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true, "ra_game_id": 0})

	cfg.Scraper.RAAPIKey = "test-server-key"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 empty — no enqueue, no pending
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Empty(t, achievements)
}
