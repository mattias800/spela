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
	)
	require.NoError(t, err)
	err = db.SeedConsoles(database)
	require.NoError(t, err)

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(tmpDir+"/saves", tmpDir+"/cores")
	require.NoError(t, err)

	hub := ws.NewHub()
	go hub.Run()

	cfg := &Config{
		DB:        database,
		JWTSecret: testJWTSecret,
		GameDirs:  []string{tmpDir},
		Storage:   store,
		Scanner:   scanner.NewScanner(database, []string{tmpDir}),
		Scraper:   scraper.NewScraper(database),
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

	// First user should be admin
	user := registerResp["user"].(map[string]interface{})
	assert.Equal(t, "admin", user["role"])

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
	games := resp["games"].([]interface{})
	assert.Len(t, games, 0)
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

	// List favorites
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/favorites", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var favs []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &favs)
	assert.Len(t, favs, 1)

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
