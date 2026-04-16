package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func TestResetEndpoint_NotRegisteredByDefault(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.TestMode = false
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestResetEndpoint_RegisteredInTestMode(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "reset", resp["status"])
}

func TestResetEndpoint_DeletesExtraUsers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create the seed users
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{Username: "admin", Email: "admin@spela.local", PasswordHash: adminHash, Role: "owner"})
	playerHash, _ := auth.HashPassword("player123")
	database.Create(&db.User{Username: "player", Email: "player@spela.local", PasswordHash: playerHash, Role: "user"})

	// Create extra users
	database.Create(&db.User{Username: "extra1", Email: "extra1@test.com", PasswordHash: "hash", Role: "user"})
	database.Create(&db.User{Username: "extra2", Email: "extra2@test.com", PasswordHash: "hash", Role: "user"})

	var countBefore int64
	database.Model(&db.User{}).Count(&countBefore)
	assert.Equal(t, int64(4), countBefore)

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var countAfter int64
	database.Model(&db.User{}).Count(&countAfter)
	assert.Equal(t, int64(2), countAfter)

	// Verify admin and player still exist
	var admin db.User
	require.NoError(t, database.Where("username = ?", "admin").First(&admin).Error)
	assert.Equal(t, db.RoleOwner, admin.Role)

	var player db.User
	require.NoError(t, database.Where("username = ?", "player").First(&player).Error)
	assert.Equal(t, db.RoleUser, player.Role)
}

func TestResetEndpoint_ResetsUserPreferences(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create admin with modified preferences
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{
		Username:        "admin",
		Email:           "admin@spela.local",
		PasswordHash:    adminHash,
		Role:            "owner",
		SelectedShader:  "crt",
		SelectedTheme:   "solarized",
		ShowPerfOverlay: true,
		Disabled:        true,
		TokenVersion:    5,
	})
	playerHash, _ := auth.HashPassword("player123")
	database.Create(&db.User{
		Username:     "player",
		Email:        "player@spela.local",
		PasswordHash: playerHash,
		Role:         "user",
	})

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var admin db.User
	database.Where("username = ?", "admin").First(&admin)
	assert.Equal(t, "none", admin.SelectedShader)
	assert.Equal(t, "default-dark", admin.SelectedTheme)
	assert.False(t, admin.ShowPerfOverlay)
	assert.False(t, admin.Disabled)
	assert.Equal(t, 0, admin.TokenVersion)
}

func TestResetEndpoint_ClearsTransientData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create seed users
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{Username: "admin", Email: "admin@spela.local", PasswordHash: adminHash, Role: "owner"})
	playerHash, _ := auth.HashPassword("player123")
	database.Create(&db.User{Username: "player", Email: "player@spela.local", PasswordHash: playerHash, Role: "user"})

	// Create some transient data
	database.Create(&db.Favorite{UserID: 1, GameID: 1})
	database.Create(&db.PlayHistory{UserID: 1, GameID: 1})
	database.Create(&db.DailyPlayActivity{UserID: 1, PlayTime: 100})
	database.Create(&db.ActivityEvent{UserID: 1, EventType: "test"})
	database.Create(&db.GameRating{UserID: 1, GameID: 1, Rating: 5})
	database.Create(&db.PlayLaterItem{UserID: 1, GameID: 1})
	database.Create(&db.ServerSetting{Key: "registration_enabled", Value: "false"})
	database.Create(&db.LoginAttempt{Username: "admin", FailedCount: 3})

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify all transient tables are empty
	assertTableEmpty(t, database, &db.Favorite{})
	assertTableEmpty(t, database, &db.PlayHistory{})
	assertTableEmpty(t, database, &db.DailyPlayActivity{})
	assertTableEmpty(t, database, &db.ActivityEvent{})
	assertTableEmpty(t, database, &db.GameRating{})
	assertTableEmpty(t, database, &db.PlayLaterItem{})
	assertTableEmpty(t, database, &db.ServerSetting{})
	assertTableEmpty(t, database, &db.LoginAttempt{})
}

func TestResetEndpoint_PreservesConsolesAndCores(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create seed users
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{Username: "admin", Email: "admin@spela.local", PasswordHash: adminHash, Role: "owner"})
	playerHash, _ := auth.HashPassword("player123")
	database.Create(&db.User{Username: "player", Email: "player@spela.local", PasswordHash: playerHash, Role: "user"})

	var consolesBefore, coresBefore int64
	database.Model(&db.Console{}).Count(&consolesBefore)
	database.Model(&db.Core{}).Count(&coresBefore)
	require.Greater(t, consolesBefore, int64(0))
	require.Greater(t, coresBefore, int64(0))

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var consolesAfter, coresAfter int64
	database.Model(&db.Console{}).Count(&consolesAfter)
	database.Model(&db.Core{}).Count(&coresAfter)
	assert.Equal(t, consolesBefore, consolesAfter)
	assert.Equal(t, coresBefore, coresAfter)
}

func TestResetEndpoint_PreservesGames(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create seed users
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{Username: "admin", Email: "admin@spela.local", PasswordHash: adminHash, Role: "owner"})
	playerHash, _ := auth.HashPassword("player123")
	database.Create(&db.User{Username: "player", Email: "player@spela.local", PasswordHash: playerHash, Role: "user"})

	// Create a game (simulating a scan)
	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	database.Create(&db.Game{ConsoleID: nes.ID, Title: "Test Game", FileName: "test.nes", FilePath: "nes/test.nes"})

	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var gameCount int64
	database.Model(&db.Game{}).Count(&gameCount)
	assert.Equal(t, int64(1), gameCount)
}

func TestResetEndpoint_AdminCanLoginAfterReset(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.TestMode = true
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// Create the seed users
	adminHash, _ := auth.HashPassword("admin123")
	database.Create(&db.User{Username: "admin", Email: "admin@spela.local", PasswordHash: adminHash, Role: "owner"})

	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Reset
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/test/reset", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Login as admin
	body, _ := json.Marshal(map[string]string{
		"username": "admin",
		"password": "admin123",
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/auth/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var loginResp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &loginResp))
	assert.NotEmpty(t, loginResp["accessToken"])
}

func assertTableEmpty(t *testing.T, database *gorm.DB, model interface{}) {
	t.Helper()
	var count int64
	database.Unscoped().Model(model).Count(&count)
	assert.Equal(t, int64(0), count, "table %T should be empty", model)
}
