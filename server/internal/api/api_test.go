package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
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
		&db.User{}, &db.Console{}, &db.Game{}, &db.SaveState{},
		&db.Favorite{}, &db.PlayHistory{}, &db.RefreshToken{},
		&db.ServerSetting{}, &db.Core{},
		&db.ConsoleShaderPreference{},
		&db.Device{},
		&db.DeviceShaderPreference{},
	)
	require.NoError(t, err)
	err = db.SeedConsoles(database)
	require.NoError(t, err)

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(tmpDir+"/saves", tmpDir+"/cores", tmpDir+"/images")
	require.NoError(t, err)

	hub := ws.NewHub(nil)
	go hub.Run()

	cfg := &Config{
		DB:        database,
		JWTSecret: testJWTSecret,
		GameDirs:  []string{tmpDir},
		Storage:   store,
		Scanner:   scanner.NewScanner(database, []string{tmpDir}),
		Scraper:   scraper.NewScraper(database, store),
		Hub:       hub,
		CoreDir:   tmpDir + "/cores",
	}

	return database, cfg
}

func TestRegisterAndLogin(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

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
	router := NewRouter(*cfg)

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
	router := NewRouter(*cfg)

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
	router := NewRouter(*cfg)

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
	router := NewRouter(*cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestListConsoles(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)
	assert.True(t, len(consoles) > 0, "should have seeded consoles")

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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestUserProfile(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"email": "updated@example.com",
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

func TestAdminEndpoint_NonAdmin(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Register first user as admin
	registerAndGetToken(t, router)

	// Register second user (will be regular user)
	body, _ := json.Marshal(map[string]string{
		"username": "regularuser",
		"email":    "regular@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var regResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &regResp)
	userToken := regResp["accessToken"].(string)

	// Verify user is not admin
	var user db.User
	database.Where("username = ?", "regularuser").First(&user)
	assert.Equal(t, "user", user.Role)

	// Try admin endpoint
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/admin/users", nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestAdminListUsers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/cores", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

func TestHealthEndpoint(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/health", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "ok", resp["status"])
	assert.Equal(t, "ok", resp["database"])
	assert.Equal(t, "0.1.0", resp["version"])
}

func TestGetPreferences_Defaults(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
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
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"1": "crt-royale"},
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
	assert.Equal(t, "crt-royale", consoleShaders["1"])

	// GET to verify persistence
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &prefs)
	consoleShaders = prefs["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "crt-royale", consoleShaders["1"])
}

func TestUpdatePreferences_RemoveConsoleShader(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// First set a console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"1": "crt-royale"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Remove by setting to "none"
	body, _ = json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"1": "none"},
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
	_, exists := consoleShaders["1"]
	assert.False(t, exists, "console shader should be removed after setting to 'none'")
}

func TestUpdatePreferences_ConsoleShaderIndependentOfGlobal(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Set a per-console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"1": "crt-royale"},
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
	assert.Equal(t, "crt-royale", consoleShaders["1"])

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
	assert.Equal(t, "crt-royale", consoleShaders["1"])
}

func TestUpdatePreferences_PartialUpdate_PreservesConsoleShaders(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Set a per-console shader
	body, _ := json.Marshal(map[string]interface{}{
		"consoleShaders": map[string]string{"2": "lcd-grid"},
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
	assert.Equal(t, "lcd-grid", consoleShaders["2"], "console shader should be preserved after partial update")
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
