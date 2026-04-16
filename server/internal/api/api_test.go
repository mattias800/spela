package api

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/scanner"
	"github.com/spela/server/internal/scraper"
	"github.com/spela/server/internal/storage"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const testJWTSecret = "test-secret-key"

func setupTestEnv(t *testing.T) (*gorm.DB, *Config) {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	err = database.AutoMigrate(
		&db.User{}, &db.Console{}, &db.Game{}, &db.GameDisc{},
		&db.Favorite{}, &db.PlayHistory{}, &db.RefreshToken{},
		&db.TokenBlacklist{}, &db.LoginAttempt{},
		&db.SystemEventCategory{},
		&db.SystemEvent{},
		&db.ServerSetting{}, &db.Core{},
		&db.ConsoleShaderPreference{},
		&db.ConsoleKeyMappingPreference{},
		&db.Device{},
		&db.DeviceShaderPreference{},
		&db.RetroAchievementCredential{},
		&db.GameAchievementCache{},
		&db.UserAchievementProgress{},
		&db.ActivityEvent{},
		&db.GameRating{},
		&db.SharedSaveState{},
		&db.GameCollection{},
		&db.CollectionItem{},
		&db.PlayLaterItem{},
		&db.SharedSession{},
		&db.SharedSessionMember{},
		&db.SharedSessionInvite{},
		&db.SharedSessionSave{},
		&db.NetplaySession{},
		&db.NetplayInvite{},
		&db.Challenge{},
		&db.ChallengeAttempt{},
		&db.GameKeyMappingPreference{},
		&db.GameScreenshot{},
		&db.StagedUpload{},
		&db.TopRatedGame{},
		&db.SimilarGame{},
		&db.CheatCode{},
		&db.GameSession{},
		&db.SessionSaveState{},
		&db.SessionSaveData{},
		&db.SessionCheatSetting{},
		&db.DailyPlayActivity{},
		&db.GameArtwork{},
		&db.GameTheme{},
		&db.GameKeyword{},
		&db.GamePlayerPerspective{},
		&db.GameFranchise{},
		&db.GameSeries{},
		&db.GameSeriesEntry{},
		&db.GameFranchiseGroup{},
		&db.GameFranchiseEntry{},
		&db.GameArtworkImage{},
		&db.SavedSearch{},
		&db.Company{},
		&db.GameReleaseDate{},
		&db.GameVideo{},
		&db.GameLanguageSupport{},
		&db.GameAgeRating{},
		&db.ScrapeJob{},
		&db.ScrapeQueueItem{},
	)
	require.NoError(t, err)
	err = db.SeedConsoles(database)
	require.NoError(t, err)
	err = db.SeedCores(database)
	require.NoError(t, err)
	// Seed system event categories for handler tests.
	database.Create(&db.SystemEventCategory{Code: db.CategorySecurity, Name: "Security"})
	database.Create(&db.SystemEventCategory{Code: db.CategoryOperational, Name: "Operational"})
	db.ResetCategoryIDCacheForTest()

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(tmpDir+"/saves", tmpDir+"/cores", tmpDir+"/images", tmpDir+"/bios")
	require.NoError(t, err)

	hub := ws.NewHub(nil)
	go hub.Run()
	t.Cleanup(hub.Close)

	cfg := &Config{
		DB:        database,
		JWTSecret: testJWTSecret,
		GameDirs:  []string{tmpDir},
		Storage:   store,
		Scanner:   scanner.NewScanner(database, []string{tmpDir}),
		Scraper:   scraper.NewScraper(database, store, t.TempDir(), []string{tmpDir}),
		Hub:       hub,
		CoreDir:   tmpDir + "/cores",
	}

	return database, cfg
}

func TestRegisterAndLogin(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Register
	body, _ := json.Marshal(map[string]string{
		"username": "testuser",
		"email":    "test@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var registerResp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &registerResp)
	require.NoError(t, err)
	assert.NotEmpty(t, registerResp["accessToken"])
	assert.NotEmpty(t, registerResp["refreshToken"])

	// First user should be owner
	user := registerResp["user"].(map[string]interface{})
	assert.Equal(t, "owner", user["role"])

	// Login
	body, _ = json.Marshal(map[string]string{
		"username": "testuser",
		"password": "password123",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/auth/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var loginResp map[string]interface{}
	err = json.Unmarshal(w.Body.Bytes(), &loginResp)
	require.NoError(t, err)
	assert.NotEmpty(t, loginResp["accessToken"])
}

func TestRegister_DuplicateUsername(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	body, _ := json.Marshal(map[string]string{
		"username": "dupeuser",
		"email":    "dupe1@example.com",
		"password": "password123",
	})

	// First registration
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Duplicate registration
	body, _ = json.Marshal(map[string]string{
		"username": "dupeuser",
		"email":    "dupe2@example.com",
		"password": "password123",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestLogin_InvalidCredentials(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	body, _ := json.Marshal(map[string]string{
		"username": "nonexistent",
		"password": "wrong",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestRefreshToken(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Register to get tokens
	body, _ := json.Marshal(map[string]string{
		"username": "refreshuser",
		"email":    "refresh@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var regResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &regResp)
	refreshToken := regResp["refreshToken"].(string)

	// Use refresh token
	body, _ = json.Marshal(map[string]string{
		"refreshToken": refreshToken,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/auth/refresh", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var refreshResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &refreshResp)
	assert.NotEmpty(t, refreshResp["accessToken"])
	// New refresh token should be different (rotation)
	assert.NotEqual(t, refreshToken, refreshResp["refreshToken"])
}

func TestProtectedEndpoint_NoAuth(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

// seedGameForEachConsole adds one test game per console so consoles appear in
// the API response (ListConsoles filters out empty consoles).
func seedGameForEachConsole(t *testing.T, database *gorm.DB) {
	t.Helper()
	var consoles []db.Console
	require.NoError(t, database.Find(&consoles).Error)
	for _, c := range consoles {
		game := db.Game{
			ConsoleID: c.ID,
			Title:     "Test Game for " + c.Abbreviation,
			FileName:  "test" + c.Abbreviation + ".rom",
			FilePath:  "/tmp/test" + c.Abbreviation + ".rom",
			FileSize:  1024,
			IsPrimary: true,
		}
		require.NoError(t, database.Create(&game).Error)
	}
}

func TestListConsoles(t *testing.T) {
	database, cfg := setupTestEnv(t)
	seedGameForEachConsole(t, database)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)
	assert.True(t, len(consoles) > 0, "should have seeded consoles with games")

	// Verify API contract: string ID, extensions as array, coverAspectRatio as number
	first := consoles[0]
	_, idIsString := first["id"].(string)
	assert.True(t, idIsString, "id should be a string")
	_, extsIsArray := first["extensions"].([]interface{})
	assert.True(t, extsIsArray, "extensions should be an array")
	_, ratioIsFloat := first["coverAspectRatio"].(float64)
	assert.True(t, ratioIsFloat, "coverAspectRatio should be a number")
	assert.NotNil(t, first["iconUrl"], "should have iconUrl field")
}

func TestListGames_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	// Pagination uses "data" key, not "games"
	games := resp["data"].([]interface{})
	assert.Len(t, games, 0)
	assert.NotNil(t, resp["pageSize"], "should have pageSize key")
	assert.NotNil(t, resp["total"], "should have total key")
}

func TestListGames_SQLInjectionOrder(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Attempt SQL injection via order parameter
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games?order=asc;DROP+TABLE+games--", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	// Should not crash, should default to "asc"
	assert.Equal(t, http.StatusOK, w.Code)

	// Attempt SQL injection via sort parameter
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games?sort=title;DROP+TABLE+games--", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestGetGame_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestUserProfile(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var user map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &user)
	assert.Equal(t, "apitest", user["username"])
}

func TestUpdateProfile(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"email":           "updated@example.com",
		"currentPassword": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/profile", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var user map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &user)
	assert.Equal(t, "updated@example.com", user["email"])
}

func TestUpdateProfile_EmailChangeRequiresPassword(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Try to change email without password
	body, _ := json.Marshal(map[string]string{
		"email": "hacker@example.com",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/profile", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	// Try with wrong password
	body, _ = json.Marshal(map[string]string{
		"email":           "hacker@example.com",
		"currentPassword": "wrongpassword",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/profile", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestUpdateProfile_AvatarWithoutPassword(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Updating avatar should NOT require password
	body, _ := json.Marshal(map[string]string{
		"avatarUrl": "https://example.com/avatar.png",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/profile", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestAdminEndpoint_NonAdmin(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Register first user as admin
	ownerToken := registerAndGetToken(t, router)

	// Register second user (will be regular user)
	userToken := createNonOwnerUser(t, router, ownerToken, "regularuser", "regular@example.com", "password123")

	// Verify user is not admin
	var user db.User
	database.Where("username = ?", "regularuser").First(&user)
	assert.Equal(t, "user", user.Role)

	// Try admin endpoint
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/users", nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestAdminListUsers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/users", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var users []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &users)
	assert.Len(t, users, 1)
}

func TestFavorites(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	// Add favorite
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/favorites/1", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// List favorites - should return Game[] with isFavorite=true
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/favorites", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var favs []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &favs)
	assert.Len(t, favs, 1)
	assert.Equal(t, true, favs[0]["isFavorite"])
	assert.Equal(t, "Test Game", favs[0]["title"])

	// Remove favorite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/user/favorites/1", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestListCores(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestHealthEndpoint(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.Version = "1.2.3"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/health", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "ok", resp["status"])
	assert.Equal(t, "1.2.3", resp["version"])
}

func TestGetPreferences_Defaults(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &prefs)
	require.NoError(t, err)
	assert.Equal(t, false, prefs["showPerformanceOverlay"])
	assert.Equal(t, true, prefs["autoSaveEnabled"])
	assert.Equal(t, true, prefs["autoLoadSaveEnabled"])
	assert.Equal(t, "none", prefs["selectedShader"])
}

func TestUpdatePreferences(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Update all preferences
	body, _ := json.Marshal(map[string]interface{}{
		"showPerformanceOverlay": true,
		"autoSaveEnabled":        false,
		"autoLoadSaveEnabled":    false,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &prefs)
	require.NoError(t, err)
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
	assert.Equal(t, false, prefs["autoSaveEnabled"])
	assert.Equal(t, false, prefs["autoLoadSaveEnabled"])
	assert.Equal(t, "none", prefs["selectedShader"])

	// GET again to verify persistence
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
	assert.Equal(t, false, prefs["autoSaveEnabled"])
	assert.Equal(t, false, prefs["autoLoadSaveEnabled"])
	assert.Equal(t, "none", prefs["selectedShader"])
}

func TestUpdatePreferences_PartialUpdate(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Only update one field
	body, _ := json.Marshal(map[string]interface{}{
		"showPerformanceOverlay": true,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
	// Others should remain at defaults
	assert.Equal(t, true, prefs["autoSaveEnabled"])
	assert.Equal(t, true, prefs["autoLoadSaveEnabled"])
	assert.Equal(t, "none", prefs["selectedShader"])
}

func TestUpdatePreferences_ShaderSelection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Set shader
	body, _ := json.Marshal(map[string]interface{}{
		"selectedShader": "crt-simple",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "crt-simple", prefs["selectedShader"])
	// Other prefs should remain at defaults
	assert.Equal(t, false, prefs["showPerformanceOverlay"])
	assert.Equal(t, true, prefs["autoSaveEnabled"])
	assert.Equal(t, true, prefs["autoLoadSaveEnabled"])

	// GET to verify persistence
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "crt-simple", prefs["selectedShader"])

	// Partial update of another field should not clear shader
	body, _ = json.Marshal(map[string]interface{}{
		"showPerformanceOverlay": true,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "crt-simple", prefs["selectedShader"])
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
}

func TestGetPreferences_IncludesConsoleShaders(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &prefs)
	require.NoError(t, err)
	consoleShaders, ok := prefs["consoleShaders"].(map[string]interface{})
	require.True(t, ok, "consoleShaders should be an object")
	assert.Empty(t, consoleShaders, "consoleShaders should be empty by default")
}

func TestUpdatePreferences_SetConsoleShader(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-royale"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	consoleShaders := prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", consoleShaders["nes"])

	// GET to verify persistence
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	consoleShaders = prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", consoleShaders["nes"])
}

func TestUpdatePreferences_RemoveConsoleShader(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// First set a console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-royale"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Remove by setting to "none"
	body, _ = json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "none"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	consoleShaders := prefs["consoleShaders"].(map[string]interface{})
	_, exists := consoleShaders["nes"]
	assert.False(t, exists, "console shader should be removed after setting to 'none'")
}

func TestUpdatePreferences_ConsoleShaderIndependentOfGlobal(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Set a per-console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"nes": "crt-royale"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	// Global shader should still be the default
	assert.Equal(t, "none", prefs["selectedShader"])
	// Per-console should be set
	consoleShaders := prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", consoleShaders["nes"])

	// Now set global shader — per-console should not change
	body, _ = json.Marshal(map[string]interface{}{
		"selectedShader": "crt-simple",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "crt-simple", prefs["selectedShader"])
	consoleShaders = prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", consoleShaders["nes"])
}

func TestUpdatePreferences_PartialUpdate_PreservesConsoleShaders(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Set a per-console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"snes": "lcd-grid"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Update a boolean pref only — console shaders should survive
	body, _ = json.Marshal(map[string]interface{}{
		"showPerformanceOverlay": true,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
	consoleShaders := prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "lcd-grid", consoleShaders["snes"], "console shader should be preserved after partial update")
}

func TestListConsoles_IncludesEmulatorJSCore(t *testing.T) {
	database, cfg := setupTestEnv(t)
	seedGameForEachConsole(t, database)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)

	// Build a map of abbreviation -> emulatorJsCore for verification
	coreMap := make(map[string]string)
	for _, c := range consoles {
		abbr := c["abbreviation"].(string)
		core, _ := c["emulatorJsCore"].(string)
		coreMap[abbr] = core
	}

	// Verify the EmulatorJS core mapping
	tests := []struct {
		abbreviation   string
		emulatorJsCore string
	}{
		{"NES", "nestopia"},
		{"SNES", "snes9x"},
		{"GB", "gambatte"},
		{"GBC", "gambatte"},
		{"GBA", "mgba"},
		{"N64", "mupen64plus_next"},
		{"NDS", "desmume"},
		{"SMS", "segaMS"},
		{"GEN", "segaMD"},
		{"GG", "segaGG"},
		{"SCD", "segaCD"},
		{"32X", "sega32x"},
		{"SAT", "yabause"},
		{"PSX", "mednafen_psx_hw"},
		{"PSP", "ppsspp"},
		{"NEOGEO", "fbneo"},
		{"ARCADE", "fbneo"},
		{"PCE", "mednafen_pce"},
		{"A26", "stella2014"},
	}

	for _, tt := range tests {
		t.Run(tt.abbreviation, func(t *testing.T) {
			assert.Equal(t, tt.emulatorJsCore, coreMap[tt.abbreviation],
				"EmulatorJS core mismatch for %s", tt.abbreviation)
		})
	}
}

func TestUpdatePlayTime(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Play Time Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// First play-time update should create a new PlayHistory
	body, _ := json.Marshal(map[string]interface{}{"seconds": 120})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(120), resp["playTime"])
	assert.NotNil(t, resp["lastPlayed"])

	// Second update should increment
	body, _ = json.Marshal(map[string]interface{}{"seconds": 60})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/games/"+gameID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(180), resp["playTime"])

	// Verify persistence in DB
	var ph db.PlayHistory
	database.Where("game_id = ?", game.ID).First(&ph)
	assert.Equal(t, int64(180), ph.PlayTime)
}

func TestUpdatePlayTime_InvalidInput(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Invalid Input Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	tests := []struct {
		name string
		body string
		code int
	}{
		{"missing seconds", `{}`, http.StatusOK},
		{"zero seconds", `{"seconds": 0}`, http.StatusOK},
		{"negative seconds", `{"seconds": -5}`, http.StatusBadRequest},
		{"invalid JSON", `not json`, http.StatusBadRequest},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/games/"+gameID+"/play-time", bytes.NewReader([]byte(tt.body)))
			req.Header.Set("Authorization", "Bearer "+token)
			req.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(w, req)
			assert.Equal(t, tt.code, w.Code)
		})
	}
}

func TestUpdatePlayTime_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"seconds": 60})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/99999/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetPreferences_DefaultTheme(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &prefs)
	require.NoError(t, err)
	assert.Equal(t, "default-dark", prefs["selectedTheme"])
}

func TestUpdatePreferences_ThemeSelection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Set theme
	body, _ := json.Marshal(map[string]interface{}{
		"selectedTheme": "nintendo-colorful",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "nintendo-colorful", prefs["selectedTheme"])

	// GET to verify persistence
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "nintendo-colorful", prefs["selectedTheme"])

	// Partial update of another field should not clear theme
	body, _ = json.Marshal(map[string]interface{}{
		"showPerformanceOverlay": true,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "nintendo-colorful", prefs["selectedTheme"])
	assert.Equal(t, true, prefs["showPerformanceOverlay"])
}

func TestGetPreferences_DefaultSecondScreenPage(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &prefs)
	require.NoError(t, err)
	assert.Equal(t, "art", prefs["defaultSecondScreenPage"])
}

func TestUpdatePreferences_DefaultSecondScreenPage(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Test each valid value
	for _, value := range []string{"art", "controls", "dashboard", "save_slots"} {
		body, _ := json.Marshal(map[string]interface{}{
			"defaultSecondScreenPage": value,
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code, "should accept value %q", value)

		var prefs map[string]interface{}
		json.Unmarshal(w.Body.Bytes(), &prefs)
		assert.Equal(t, value, prefs["defaultSecondScreenPage"])

		// GET to verify persistence
		w = httptest.NewRecorder()
		req = httptest.NewRequest("GET", "/api/user/preferences", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		json.Unmarshal(w.Body.Bytes(), &prefs)
		assert.Equal(t, value, prefs["defaultSecondScreenPage"])
	}
}

func TestUpdatePreferences_DefaultSecondScreenPage_InvalidValue(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	for _, value := range []string{"invalid", "ART", "Controls", "", "game_info"} {
		body, _ := json.Marshal(map[string]interface{}{
			"defaultSecondScreenPage": value,
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusBadRequest, w.Code, "should reject value %q", value)
	}

	// Verify the preference was not changed from default
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &prefs)
	assert.Equal(t, "art", prefs["defaultSecondScreenPage"])
}

func TestGetOnlineUsers_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/social/online", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	users := resp["users"].([]interface{})
	assert.Len(t, users, 0)
}

func TestGetPublicProfile(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Get the user ID
	var user db.User
	database.Where("username = ?", "apitest").First(&user)
	userID := fmt.Sprintf("%d", user.ID)

	// Create a test game and some data
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Profile Game", FileName: "test.nes", FilePath: "/tmp/profile-test.nes", FileSize: 100, CoverURL: "covers/profile.jpg"}
	database.Create(&game)

	// Add favorite
	database.Create(&db.Favorite{UserID: user.ID, GameID: game.ID})

	// Add play history
	database.Create(&db.PlayHistory{UserID: user.ID, GameID: game.ID, PlayTime: 3600})

	// Get public profile
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/"+userID+"/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.Equal(t, userID, resp["id"])
	assert.Equal(t, "apitest", resp["username"])
	assert.NotNil(t, resp["memberSince"])
	assert.Equal(t, float64(3600), resp["totalPlayTime"])
	assert.Equal(t, float64(1), resp["gamesPlayed"])

	favGames := resp["favoriteGames"].([]interface{})
	assert.Len(t, favGames, 1)
	fav := favGames[0].(map[string]interface{})
	assert.Equal(t, "Profile Game", fav["title"])
	assert.Equal(t, "/api/images/covers/profile.jpg", fav["coverUrl"])

	recentGames := resp["recentGames"].([]interface{})
	assert.Len(t, recentGames, 1)

	topGames := resp["topGames"].([]interface{})
	assert.Len(t, topGames, 1)
	top := topGames[0].(map[string]interface{})
	assert.Equal(t, float64(3600), top["playTime"])
}

func TestGetPublicProfile_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/99999/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetPublicProfile_EmptyStats(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Register a second user with no activity
	createNonOwnerUser(t, router, token, "emptyuser", "empty@example.com", "password123")

	database := cfg.DB
	var user db.User
	database.Where("username = ?", "emptyuser").First(&user)
	userID := fmt.Sprintf("%d", user.ID)

	// Get public profile of empty user
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/users/"+userID+"/profile", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.Equal(t, "emptyuser", resp["username"])
	assert.Equal(t, float64(0), resp["totalPlayTime"])
	assert.Equal(t, float64(0), resp["gamesPlayed"])
	assert.Equal(t, false, resp["isOnline"])
	assert.Nil(t, resp["currentGame"])

	favGames := resp["favoriteGames"].([]interface{})
	assert.Len(t, favGames, 0)
	recentGames := resp["recentGames"].([]interface{})
	assert.Len(t, recentGames, 0)
	topGames := resp["topGames"].([]interface{})
	assert.Len(t, topGames, 0)
}

func TestGetActivityFeed_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 0)
	assert.Equal(t, float64(0), resp["total"])
	assert.Equal(t, float64(1), resp["page"])
	assert.Equal(t, float64(20), resp["pageSize"])
}

func TestGetActivityFeed_AfterPlayTime(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Activity Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Post play time (triggers activity event)
	body, _ := json.Marshal(map[string]interface{}{"seconds": 60})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Get activity feed - should have one event
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 1)

	event := data[0].(map[string]interface{})
	assert.Equal(t, "started_playing", event["eventType"])
	assert.Equal(t, "apitest", event["username"])
	assert.Equal(t, "Activity Test Game", event["gameTitle"])
	assert.NotEmpty(t, event["id"])
	assert.NotEmpty(t, event["userId"])
	assert.NotEmpty(t, event["gameId"])
}

func TestGetActivityFeed_AfterFavorite(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Favorite Feed Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Add favorite (triggers activity event)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/favorites/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Get activity feed - should have one event
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 1)

	event := data[0].(map[string]interface{})
	assert.Equal(t, "favorited_game", event["eventType"])
	assert.Equal(t, "Favorite Feed Game", event["gameTitle"])
}

func TestGetActivityFeed_Pagination(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Pagination Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	// Create multiple activity events directly
	var user db.User
	database.Where("username = ?", "apitest").First(&user)
	for i := 0; i < 5; i++ {
		database.Create(&db.ActivityEvent{
			UserID:    user.ID,
			EventType: "started_playing",
			GameID:    game.ID,
		})
	}

	// Get page 1 with pageSize=2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/social/activity?page=1&pageSize=2", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 2)
	assert.Equal(t, float64(5), resp["total"])
	assert.Equal(t, float64(1), resp["page"])
	assert.Equal(t, float64(2), resp["pageSize"])

	// Get page 3 with pageSize=2 (should have 1 item)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity?page=3&pageSize=2", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	data = resp["data"].([]interface{})
	assert.Len(t, data, 1)
}

func TestCreateActivityEvent_BroadcastsWebSocket(t *testing.T) {
	database, cfg := setupTestEnv(t)

	var user db.User
	database.Create(&db.User{Username: "wsuser", Email: "ws@example.com", PasswordHash: "x", Role: db.RoleUser})
	database.Where("username = ?", "wsuser").First(&user)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "WS Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	// CreateActivityEvent should not panic even with an active hub
	CreateActivityEvent(database, cfg.Hub, user.ID, "started_playing", game.ID, map[string]interface{}{"seconds": 30})

	// Verify event was persisted
	var events []db.ActivityEvent
	database.Where("user_id = ?", user.ID).Find(&events)
	assert.Len(t, events, 1)
	assert.Equal(t, "started_playing", events[0].EventType)
	assert.Equal(t, game.ID, events[0].GameID)
}

func TestOnlineUserTracking(t *testing.T) {
	_, cfg := setupTestEnv(t)
	hub := cfg.Hub

	// Initially no one is playing
	assert.Equal(t, uint(0), hub.GetUserGame(1))

	// Set user as playing a game
	hub.SetUserGame(1, 42)
	assert.Equal(t, uint(42), hub.GetUserGame(1))

	// Clear the game
	hub.SetUserGame(1, 0)
	assert.Equal(t, uint(0), hub.GetUserGame(1))
}

func TestCreateRating(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 4, "review": "Great game!"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, float64(4), resp["rating"])
	assert.Equal(t, "Great game!", resp["review"])
	assert.Equal(t, "apitest", resp["username"])
	assert.NotEmpty(t, resp["id"])
}

func TestUpdateRating(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Update Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 3})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Update rating
	body, _ = json.Marshal(map[string]interface{}{"rating": 5, "review": "Changed my mind, amazing!"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(5), resp["rating"])
	assert.Equal(t, "Changed my mind, amazing!", resp["review"])
}

func TestCreateRating_InvalidInput(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Invalid Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	tests := []struct {
		name string
		body string
		code int
	}{
		{"rating too low", `{"rating": 0}`, http.StatusBadRequest},
		{"rating too high", `{"rating": 6}`, http.StatusBadRequest},
		{"missing rating", `{}`, http.StatusBadRequest},
		{"negative rating", `{"rating": -1}`, http.StatusBadRequest},
		{"invalid JSON", `not json`, http.StatusBadRequest},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader([]byte(tt.body)))
			req.Header.Set("Authorization", "Bearer "+token)
			req.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(w, req)
			assert.Equal(t, tt.code, w.Code)
		})
	}
}

func TestCreateRating_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"rating": 4})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/99999/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetRatings(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "List Ratings Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create a rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 5, "review": "Best game ever"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get ratings
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/ratings", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 1)
	assert.Equal(t, float64(1), resp["total"])

	rating := data[0].(map[string]interface{})
	assert.Equal(t, float64(5), rating["rating"])
	assert.Equal(t, "Best game ever", rating["review"])
	assert.Equal(t, "apitest", rating["username"])
}

func TestGetRatingSummary(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Summary Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create ratings from multiple users
	var user1 db.User
	database.Where("username = ?", "apitest").First(&user1)
	database.Create(&db.GameRating{UserID: user1.ID, GameID: game.ID, Rating: 5})

	user2 := db.User{Username: "user2", Email: "user2@example.com", PasswordHash: "x", Role: db.RoleUser}
	database.Create(&user2)
	database.Create(&db.GameRating{UserID: user2.ID, GameID: game.ID, Rating: 3})

	user3 := db.User{Username: "user3", Email: "user3@example.com", PasswordHash: "x", Role: db.RoleUser}
	database.Create(&user3)
	database.Create(&db.GameRating{UserID: user3.ID, GameID: game.ID, Rating: 4})

	// Get summary
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+gameID+"/ratings/summary", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(3), resp["totalRatings"])
	assert.Equal(t, float64(4), resp["averageRating"])

	dist := resp["distribution"].(map[string]interface{})
	assert.Equal(t, float64(0), dist["1"])
	assert.Equal(t, float64(0), dist["2"])
	assert.Equal(t, float64(1), dist["3"])
	assert.Equal(t, float64(1), dist["4"])
	assert.Equal(t, float64(1), dist["5"])
}

func TestGetMyRating(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "My Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// No rating yet
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+gameID+"/ratings/mine", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 4})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Now get my rating
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/ratings/mine", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(4), resp["rating"])
}

func TestDeleteRating(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 2})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Delete rating
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/games/"+gameID+"/ratings", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/ratings/mine", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteRating_NotFound(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "No Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+gameID+"/ratings", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGameResponse_IncludesRatingData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Enriched Rating Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 4})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get game - should include rating data
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(4), resp["averageRating"])
	assert.Equal(t, float64(1), resp["ratingCount"])
	assert.Equal(t, float64(4), resp["userRating"])
}

func TestRating_CreatesActivityEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Rating Activity Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create rating
	body, _ := json.Marshal(map[string]interface{}{"rating": 5})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/ratings", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Check activity feed has rated_game event
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var feedResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &feedResp)
	data := feedResp["data"].([]interface{})
	assert.True(t, len(data) >= 1)

	// Find the rated_game event
	found := false
	for _, item := range data {
		event := item.(map[string]interface{})
		if event["eventType"] == "rated_game" {
			found = true
			assert.Equal(t, "Rating Activity Game", event["gameTitle"])
			break
		}
	}
	assert.True(t, found, "should have a rated_game activity event")
}

func TestShareSave(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Share Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Create multipart form
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "mysave.sav")
	part.Write([]byte("fake save data"))
	writer.WriteField("name", "My Awesome Save")
	writer.WriteField("description", "Beat the final boss!")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "My Awesome Save", resp["name"])
	assert.Equal(t, "Beat the final boss!", resp["description"])
	assert.Equal(t, "apitest", resp["username"])
	assert.Equal(t, float64(14), resp["fileSize"]) // len("fake save data")
	assert.NotEmpty(t, resp["id"])
}

func TestShareSave_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "mysave.sav")
	part.Write([]byte("data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/99999/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestShareSave_NoFile(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "No File Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestListSharedSaves(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "List Saves Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Upload a shared save
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "shared.sav")
	part.Write([]byte("save data"))
	writer.WriteField("name", "Shared Save 1")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// List shared saves
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/shared-saves", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 1)
	assert.Equal(t, float64(1), resp["total"])

	save := data[0].(map[string]interface{})
	assert.Equal(t, "Shared Save 1", save["name"])
	assert.Equal(t, "apitest", save["username"])
}

func TestListSharedSaves_Empty(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Empty Saves Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+gameID+"/shared-saves", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 0)
	assert.Equal(t, float64(0), resp["total"])
}

func TestDownloadSharedSave(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Download Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Upload
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "download.sav")
	part.Write([]byte("downloadable save data"))
	writer.WriteField("name", "Download Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	saveID := createResp["id"].(string)

	// Download
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/shared-saves/"+saveID+"/download", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "downloadable save data", w.Body.String())

	// Verify download count incremented
	var save db.SharedSaveState
	database.First(&save, saveID)
	assert.Equal(t, 1, save.DownloadCount)
}

func TestDeleteSharedSave(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Upload
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "todelete.sav")
	part.Write([]byte("delete me"))
	writer.WriteField("name", "Delete Me Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	saveID := createResp["id"].(string)

	// Delete
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/games/"+gameID+"/shared-saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone from listing
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+gameID+"/shared-saves", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 0)
}

func TestDeleteSharedSave_NotOwner(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router) // owner user

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Ownership Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Upload as owner
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "owned.sav")
	part.Write([]byte("owned save"))
	writer.WriteField("name", "Owned Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	saveID := createResp["id"].(string)

	// Register a second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Try to delete as other user - should fail
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/games/"+gameID+"/shared-saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+otherToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestShareSave_CreatesActivityEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Activity Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// Upload
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "activity.sav")
	part.Write([]byte("activity save"))
	writer.WriteField("name", "Activity Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/shared-saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Check activity feed
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var feedResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &feedResp)
	data := feedResp["data"].([]interface{})

	found := false
	for _, item := range data {
		event := item.(map[string]interface{})
		if event["eventType"] == "shared_save" {
			found = true
			assert.Equal(t, "Activity Save Game", event["gameTitle"])
			break
		}
	}
	assert.True(t, found, "should have a shared_save activity event")
}

func TestSearchUsers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Register additional users
	for _, name := range []string{"alice", "alex", "bob", "charlie"} {
		createNonOwnerUser(t, router, token, name, name+"@example.com", "password123")
	}

	// Helper to extract []map[string]interface{} from PaginatedResponse.Data
	parseSearchResults := func(t *testing.T, body []byte) ([]map[string]interface{}, int64) {
		t.Helper()
		var resp map[string]interface{}
		require.NoError(t, json.Unmarshal(body, &resp))
		total := int64(resp["total"].(float64))
		dataRaw, _ := json.Marshal(resp["data"])
		var results []map[string]interface{}
		json.Unmarshal(dataRaw, &results)
		return results, total
	}

	t.Run("returns matching users by prefix", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=al", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		results, total := parseSearchResults(t, w.Body.Bytes())
		assert.Equal(t, int64(2), total)
		assert.Len(t, results, 2) // alice, alex
		usernames := []string{results[0]["username"].(string), results[1]["username"].(string)}
		assert.Contains(t, usernames, "alice")
		assert.Contains(t, usernames, "alex")
	})

	t.Run("excludes current user from results", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=api", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		results, total := parseSearchResults(t, w.Body.Bytes())
		assert.Equal(t, int64(0), total)
		assert.Len(t, results, 0) // "apitest" is the current user
	})

	t.Run("single char query now works", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=a", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		results, total := parseSearchResults(t, w.Body.Bytes())
		assert.Equal(t, int64(2), total) // alice, alex
		assert.Len(t, results, 2)
	})

	t.Run("returns empty for no match", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=zzz", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		results, total := parseSearchResults(t, w.Body.Bytes())
		assert.Equal(t, int64(0), total)
		assert.Len(t, results, 0)
	})

	t.Run("response includes id and username", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=bob", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		results, _ := parseSearchResults(t, w.Body.Bytes())
		assert.Len(t, results, 1)
		assert.Equal(t, "bob", results[0]["username"])
		assert.NotEmpty(t, results[0]["id"])
	})
}

func TestGetPendingInviteCount(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Register a second user
	inviteeToken := createNonOwnerUser(t, router, token, "invitee", "invitee@example.com", "password123")

	t.Run("returns zero when no invites", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/user/shared-session-invites/count", nil)
		req.Header.Set("Authorization", "Bearer "+inviteeToken)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp map[string]interface{}
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, float64(0), resp["count"])
	})

	t.Run("returns correct count after invite", func(t *testing.T) {
		// Create a game and shared session, then invite the second user
		var console db.Console
		database.First(&console)
		game := db.Game{ConsoleID: console.ID, Title: "Invite Count Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
		database.Create(&game)

		// Create shared session
		body, _ := json.Marshal(map[string]interface{}{"name": "Count Test Session", "gameId": fmt.Sprintf("%d", game.ID)})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		var ssResp map[string]interface{}
		json.Unmarshal(w.Body.Bytes(), &ssResp)
		ssID := ssResp["id"].(string)

		// Invite invitee
		body, _ = json.Marshal(map[string]string{"username": "invitee"})
		w = httptest.NewRecorder()
		req = httptest.NewRequest("POST", "/api/shared-sessions/"+ssID+"/invites", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)

		// Check count
		w = httptest.NewRecorder()
		req = httptest.NewRequest("GET", "/api/user/shared-session-invites/count", nil)
		req.Header.Set("Authorization", "Bearer "+inviteeToken)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)

		var resp map[string]interface{}
		json.Unmarshal(w.Body.Bytes(), &resp)
		assert.Equal(t, float64(1), resp["count"])
	})
}

func TestUpdateVerificationTag_AdminSuccess(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	// First user is owner (admin)
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]string{"tag": "No-Intro Verified"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", fmt.Sprintf("/api/admin/games/%d/verification-tag", game.ID), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "No-Intro Verified", resp["verificationTag"])

	// Verify it was persisted
	var updated db.Game
	database.First(&updated, game.ID)
	assert.Equal(t, "No-Intro Verified", updated.VerificationTag)
}

func TestUpdateVerificationTag_NonAdmin_Forbidden(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	// Register first user as owner
	ownerToken := registerAndGetToken(t, router)

	// Register second user (regular user)
	userToken := createNonOwnerUser(t, router, ownerToken, "regularuser", "regular@example.com", "password123")

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]string{"tag": "Hacked"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", fmt.Sprintf("/api/admin/games/%d/verification-tag", game.ID), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestUpdateVerificationTag_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{"tag": "test"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/admin/games/99999/verification-tag", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGameResponse_IncludesVerificationFields(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{
		ConsoleID:          console.ID,
		Title:              "Verified Game",
		FileName:           "verified.nes",
		FilePath:           "/tmp/verified.nes",
		FileSize:           100,
		VerificationStatus: "verified",
		VerificationTag:    "No-Intro",
	}
	database.Create(&game)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "verified", resp["verificationStatus"])
	assert.Equal(t, "No-Intro", resp["verificationTag"])
}

func TestScrapeStatus_Idle(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/scrape/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["active"])
	// When idle, no progress fields should be present
	_, hasTotal := resp["total"]
	assert.False(t, hasTotal)
}

func TestScrapeStatus_NonAdmin(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Register first user as admin (owner)
	ownerToken := registerAndGetToken(t, router)

	// Register second user (will be regular user)
	userToken := createNonOwnerUser(t, router, ownerToken, "regularuser2", "regular2@example.com", "password123")

	// Verify user is not admin
	var user db.User
	database.Where("username = ?", "regularuser2").First(&user)
	assert.Equal(t, "user", user.Role)

	// Non-admin should be rejected
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/scrape/status", nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// registerAndGetToken registers a user and returns an access token.
func registerAndGetToken(t *testing.T, router http.Handler) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": "apitest",
		"email":    "apitest@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp["accessToken"].(string)
}

// TestDownloadGame_CueBinServeTar verifies that downloading a .cue game
// returns a tar archive containing both the .cue and .bin files.
func TestDownloadGame_CueBinServeTar(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create .cue + .bin files in the game directory
	psxDir := filepath.Join(cfg.GameDirs[0], "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	binContent := []byte("fake binary disc data for testing")
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game.bin"), binContent, 0644))

	cueContent := "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game.cue"), []byte(cueContent), 0644))

	// Create a game entry pointing to the .cue file
	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	game := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Test Game",
		FileName:  "game.cue",
		FilePath:  filepath.Join("psx", "game.cue"),
		FileSize:  int64(len(cueContent)) + int64(len(binContent)),
	}
	require.NoError(t, database.Create(&game).Error)

	// Download the game — should return a tar archive
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/download", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "application/x-tar", w.Header().Get("Content-Type"))

	// Parse the tar and verify both files are present
	tarReader := tar.NewReader(w.Body)
	fileNames := make(map[string]bool)
	for {
		header, err := tarReader.Next()
		if err != nil {
			break
		}
		fileNames[header.Name] = true
	}
	assert.True(t, fileNames["game.cue"], "tar should contain game.cue")
	assert.True(t, fileNames["game.bin"], "tar should contain game.bin")
	assert.Len(t, fileNames, 2, "tar should contain exactly 2 files")
}

// TestDownloadGame_CueBinServeZip verifies the zip format option for .cue downloads.
func TestDownloadGame_CueBinServeZip(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	psxDir := filepath.Join(cfg.GameDirs[0], "psx")
	require.NoError(t, os.MkdirAll(psxDir, 0755))

	binContent := []byte("fake binary disc data")
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game.bin"), binContent, 0644))

	cueContent := "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
	require.NoError(t, os.WriteFile(filepath.Join(psxDir, "game.cue"), []byte(cueContent), 0644))

	var psxConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "PSX").First(&psxConsole).Error)

	game := db.Game{
		ConsoleID: psxConsole.ID,
		Title:     "Test Game Zip",
		FileName:  "game.cue",
		FilePath:  filepath.Join("psx", "game.cue"),
		FileSize:  int64(len(cueContent)) + int64(len(binContent)),
	}
	require.NoError(t, database.Create(&game).Error)

	// Download with format=zip
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/download?format=zip", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "application/zip", w.Header().Get("Content-Type"))

	// Parse the zip and verify both files are present
	zipReader, err := zip.NewReader(bytes.NewReader(w.Body.Bytes()), int64(w.Body.Len()))
	require.NoError(t, err)

	fileNames := make(map[string]bool)
	for _, f := range zipReader.File {
		fileNames[f.Name] = true
	}
	assert.True(t, fileNames["game.cue"], "zip should contain game.cue")
	assert.True(t, fileNames["game.bin"], "zip should contain game.bin")
	assert.Len(t, fileNames, 2, "zip should contain exactly 2 files")
}

// TestDownloadGame_GdiBinServeTar verifies that downloading a .gdi game
// returns a tar archive containing the .gdi and all track files.
func TestDownloadGame_GdiBinServeTar(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create .gdi + track files in the game directory
	dcDir := filepath.Join(cfg.GameDirs[0], "dreamcast")
	require.NoError(t, os.MkdirAll(dcDir, 0755))

	track1Content := []byte("fake track 1 binary data")
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track01.bin"), track1Content, 0644))
	track2Content := []byte("fake track 2 binary data here")
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track02.raw"), track2Content, 0644))
	track3Content := []byte("fake track 3 data")
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "track03.bin"), track3Content, 0644))

	gdiContent := "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin 0\n"
	require.NoError(t, os.WriteFile(filepath.Join(dcDir, "game.gdi"), []byte(gdiContent), 0644))

	// Create a game entry pointing to the .gdi file
	var dcConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "DC").First(&dcConsole).Error)

	game := db.Game{
		ConsoleID: dcConsole.ID,
		Title:     "Test DC Game",
		FileName:  "game.gdi",
		FilePath:  filepath.Join("dreamcast", "game.gdi"),
		FileSize:  int64(len(gdiContent)) + int64(len(track1Content)) + int64(len(track2Content)) + int64(len(track3Content)),
	}
	require.NoError(t, database.Create(&game).Error)

	// Download the game — should return a tar archive
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/download", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "application/x-tar", w.Header().Get("Content-Type"))

	// Parse the tar and verify all files are present
	tarReader := tar.NewReader(w.Body)
	tarFileNames := make(map[string]bool)
	for {
		header, err := tarReader.Next()
		if err != nil {
			break
		}
		tarFileNames[header.Name] = true
	}
	assert.True(t, tarFileNames["game.gdi"], "tar should contain game.gdi")
	assert.True(t, tarFileNames["track01.bin"], "tar should contain track01.bin")
	assert.True(t, tarFileNames["track02.raw"], "tar should contain track02.raw")
	assert.True(t, tarFileNames["track03.bin"], "tar should contain track03.bin")
	assert.Len(t, tarFileNames, 4, "tar should contain exactly 4 files")
}
