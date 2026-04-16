package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func createSetupTestUser(t *testing.T, database *gorm.DB, role db.UserRole) string {
	t.Helper()
	user := db.User{
		Username:     "setup-test-" + string(role),
		Email:        "setup-test-" + string(role) + "@test.com",
		PasswordHash: "unused",
		Role:         role,
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)
	return token
}

func TestDiagnostics_PublicWhenNoUsers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/setup/diagnostics", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string][]DiagnosticCheck
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	checks := resp["checks"]
	assert.NotEmpty(t, checks)

	// Verify expected check IDs are present
	ids := make(map[string]bool)
	for _, c := range checks {
		ids[c.ID] = true
	}
	assert.True(t, ids["database"])
	assert.True(t, ids["jwt_secret"])
	assert.True(t, ids["encryption_key"])
	assert.True(t, ids["game_dirs"])
	assert.True(t, ids["storage_writable"])
	assert.True(t, ids["igdb"])
}

func TestDiagnostics_RequiresAuthWhenUsersExist(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Create a user so setup is no longer needed
	createSetupTestUser(t, database, "owner")

	// No auth header
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/setup/diagnostics", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestDiagnostics_AdminCanAccess(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token := createSetupTestUser(t, database, "owner")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/setup/diagnostics", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string][]DiagnosticCheck
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.NotEmpty(t, resp["checks"])
}

func TestDiagnostics_RegularUserForbidden(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Create an owner first (so users exist), then a regular user
	createSetupTestUser(t, database, "owner")

	regularUser := db.User{
		Username:     "regular-user",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&regularUser).Error)
	regularToken, err := auth.GenerateAccessToken(regularUser.ID, regularUser.Username, string(regularUser.Role), testJWTSecret)
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/setup/diagnostics", nil)
	req.Header.Set("Authorization", "Bearer "+regularToken)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDiagnostics_InvalidTokenRejected(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	createSetupTestUser(t, database, "owner")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/setup/diagnostics", nil)
	req.Header.Set("Authorization", "Bearer invalid-token-value")
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestDiagnostics_CheckDatabaseOK(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:        cfg.DB,
		JWTSecret: cfg.JWTSecret,
		GameDirs:  cfg.GameDirs,
		Storage:   cfg.Storage,
	}

	check := handler.checkDatabase()
	assert.Equal(t, "database", check.ID)
	assert.Equal(t, "ok", check.Status)
	assert.Equal(t, "Connected", check.Detail)
}

func TestDiagnostics_CheckJWTSecret(t *testing.T) {
	_, cfg := setupTestEnv(t)

	// Test short secret (the testJWTSecret is short)
	handler := &SetupHandler{
		DB:        cfg.DB,
		JWTSecret: "short",
		GameDirs:  cfg.GameDirs,
		Storage:   cfg.Storage,
	}

	check := handler.checkJWTSecret()
	assert.Equal(t, "jwt_secret", check.ID)
	assert.Equal(t, "warning", check.Status)
	assert.NotEmpty(t, check.Fix)

	// Test long secret
	handler.JWTSecret = "this-is-a-very-long-secret-key-that-is-at-least-32-chars"
	check = handler.checkJWTSecret()
	assert.Equal(t, "ok", check.Status)
	assert.Empty(t, check.Fix)
}

func TestDiagnostics_CheckGameDirs_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:       cfg.DB,
		GameDirs: []string{},
		Storage:  cfg.Storage,
	}

	check := handler.checkGameDirs()
	assert.Equal(t, "game_dirs", check.ID)
	assert.Equal(t, "error", check.Status)
	assert.NotEmpty(t, check.Fix)
}

func TestDiagnostics_CheckGameDirs_MissingDir(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:       cfg.DB,
		GameDirs: []string{"/nonexistent/path/abc123"},
		Storage:  cfg.Storage,
	}

	check := handler.checkGameDirs()
	assert.Equal(t, "error", check.Status)
	assert.Contains(t, check.Detail, "/nonexistent/path/abc123")
}

func TestDiagnostics_CheckGameDirs_ValidDirNoGames(t *testing.T) {
	_, cfg := setupTestEnv(t)
	tmpDir := t.TempDir()

	handler := &SetupHandler{
		DB:       cfg.DB,
		GameDirs: []string{tmpDir},
		Storage:  cfg.Storage,
	}

	check := handler.checkGameDirs()
	assert.Equal(t, "warning", check.Status)
	assert.Contains(t, check.Detail, "no game files found")
}

func TestDiagnostics_CheckStorageWritable(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:      cfg.DB,
		Storage: cfg.Storage,
	}

	check := handler.checkStorageWritable()
	assert.Equal(t, "storage_writable", check.ID)
	assert.Equal(t, "ok", check.Status)
}

func TestDiagnostics_CheckIGDB_NotConfigured(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:      cfg.DB,
		Storage: cfg.Storage,
	}

	check := handler.checkIGDB()
	assert.Equal(t, "igdb", check.ID)
	assert.Equal(t, "warning", check.Status)
	assert.NotEmpty(t, check.Fix)
}

func TestDiagnostics_CheckIGDB_ConfiguredViaDB(t *testing.T) {
	database, cfg := setupTestEnv(t)

	// Set IGDB credentials in the database
	database.Create(&db.ServerSetting{Key: "igdb_client_id", Value: "test-client-id"})
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "test-client-secret"})

	handler := &SetupHandler{
		DB:      database,
		Storage: cfg.Storage,
	}

	check := handler.checkIGDB()
	assert.Equal(t, "ok", check.Status)
	assert.Equal(t, "Configured via settings", check.Detail)
}

func TestSetupStatus_IncludesGameCount(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Seed a console and some games
	var console db.Console
	database.Where("abbreviation = ?", "NES").First(&console)

	database.Create(&db.Game{ConsoleID: console.ID, Title: "Game 1", FileName: "game1.nes", FilePath: "/games/game1.nes"})
	database.Create(&db.Game{ConsoleID: console.ID, Title: "Game 2", FileName: "game2.nes", FilePath: "/games/game2.nes"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/auth/setup-status", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)

	assert.Equal(t, true, resp["needsSetup"])
	assert.Equal(t, float64(2), resp["gameCount"])
}

func TestDiagnostics_RunDiagnostics_ReturnsAllChecks(t *testing.T) {
	_, cfg := setupTestEnv(t)

	handler := &SetupHandler{
		DB:        cfg.DB,
		JWTSecret: cfg.JWTSecret,
		GameDirs:  cfg.GameDirs,
		Storage:   cfg.Storage,
	}

	checks := handler.runDiagnostics()
	assert.Len(t, checks, 7)

	expectedIDs := []string{"database", "jwt_secret", "encryption_key", "game_dirs", "storage_writable", "igdb", "steamgriddb"}
	for i, id := range expectedIDs {
		assert.Equal(t, id, checks[i].ID)
	}

	// Every check should have a valid status
	for _, c := range checks {
		assert.Contains(t, []string{"ok", "warning", "error"}, c.Status,
			"check %s has invalid status %q", c.ID, c.Status)
	}
}
