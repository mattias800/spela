package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/scraper"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

const igdbTestJWTSecret = "igdb-test-secret"

func setupIGDBTestEnv(t *testing.T, twitchServer *httptest.Server) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.User{}, &db.ServerSetting{}))

	if twitchServer != nil {
		origURL := igdb.TwitchTokenURLForTest()
		igdb.SetTwitchTokenURLForTest(twitchServer.URL)
		t.Cleanup(func() { igdb.SetTwitchTokenURLForTest(origURL) })
	}

	hub := ws.NewHub(nil)
	go hub.Run()

	s := scraper.NewScraper(database, nil)
	adminHandler := &AdminHandler{DB: database, Scraper: s, Hub: hub}
	igdbHandler := &IGDBHandler{DB: database}

	gin.SetMode(gin.TestMode)
	router := gin.New()
	api := router.Group("/api")
	api.Use(AuthMiddleware(igdbTestJWTSecret))
	{
		admin := api.Group("/admin")
		admin.Use(AdminMiddleware())
		{
			admin.POST("/igdb/test", igdbHandler.TestIGDB)
			admin.GET("/igdb/status", igdbHandler.GetIGDBStatus)
			admin.GET("/settings", adminHandler.GetSettings)
			admin.PUT("/settings", adminHandler.UpdateSettings)
		}
	}

	return database, router
}

func createIGDBTestUser(t *testing.T, database *gorm.DB, role string) string {
	t.Helper()
	user := db.User{
		Username:     "igdb-test-" + role,
		Email:        "igdb-test-" + role + "@test.com",
		PasswordHash: "unused",
		Role:         role,
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, igdbTestJWTSecret)
	require.NoError(t, err)
	return token
}

func TestIGDBTest_Success(t *testing.T) {
	twitchServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, "goodid", r.FormValue("client_id"))
		assert.Equal(t, "goodsecret", r.FormValue("client_secret"))
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer twitchServer.Close()

	database, router := setupIGDBTestEnv(t, twitchServer)
	token := createIGDBTestUser(t, database, "admin")

	body, _ := json.Marshal(map[string]string{
		"clientId":     "goodid",
		"clientSecret": "goodsecret",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/igdb/test", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, true, resp["success"])
	assert.Nil(t, resp["error"])
}

func TestIGDBTest_AuthFailure(t *testing.T) {
	twitchServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
		w.Write([]byte(`{"status":401,"message":"invalid client"}`))
	}))
	defer twitchServer.Close()

	database, router := setupIGDBTestEnv(t, twitchServer)
	token := createIGDBTestUser(t, database, "admin")

	body, _ := json.Marshal(map[string]string{
		"clientId":     "badid",
		"clientSecret": "badsecret",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/igdb/test", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, false, resp["success"])
	assert.NotNil(t, resp["error"])
}

func TestIGDBTest_EmptyFields(t *testing.T) {
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	tests := []struct {
		name string
		body map[string]string
	}{
		{"both empty", map[string]string{"clientId": "", "clientSecret": ""}},
		{"missing clientId", map[string]string{"clientSecret": "secret"}},
		{"missing clientSecret", map[string]string{"clientId": "id"}},
		{"empty body", map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/admin/igdb/test", bytes.NewReader(body))
			req.Header.Set("Content-Type", "application/json")
			req.Header.Set("Authorization", "Bearer "+token)
			router.ServeHTTP(w, req)

			assert.Equal(t, http.StatusBadRequest, w.Code)
			var resp map[string]interface{}
			require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
			assert.Equal(t, "clientId and clientSecret are required", resp["error"])
		})
	}
}

func TestIGDBStatus_NotConfigured(t *testing.T) {
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/igdb/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, false, resp["configured"])
	assert.Equal(t, "not_configured", resp["status"])
}

func TestIGDBStatus_Connected(t *testing.T) {
	// No twitch server needed — status only checks DB.
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	// Set up IGDB credentials in DB
	database.Create(&db.ServerSetting{Key: "igdb_client_id", Value: "configured-id"})
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "configured-secret"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/igdb/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, true, resp["configured"])
	assert.Equal(t, "connected", resp["status"])
}

func TestIGDBStatus_ConfiguredNoLiveCheck(t *testing.T) {
	// Status endpoint only checks DB, does not make live API calls.
	// Even with invalid credentials, it returns "connected" if credentials exist.
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	database.Create(&db.ServerSetting{Key: "igdb_client_id", Value: "any-id"})
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "any-secret"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/igdb/status", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, true, resp["configured"])
	assert.Equal(t, "connected", resp["status"])
}

func TestSettingsMasking_IGDBSecret(t *testing.T) {
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	// Store settings including IGDB secret
	database.Create(&db.ServerSetting{Key: "igdb_client_id", Value: "my-client-id"})
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "super-secret-value"})
	database.Create(&db.ServerSetting{Key: "registration_enabled", Value: "true"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/settings", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Client ID should be visible
	assert.Equal(t, "my-client-id", resp["igdb_client_id"])
	// Client secret should be masked
	assert.Equal(t, "********", resp["igdb_client_secret"])
	// Other settings should be unaffected
	assert.Equal(t, "true", resp["registration_enabled"])
}

func TestSettingsMasking_RoundTrip(t *testing.T) {
	// BUG-1 regression: sending "********" back via PUT must NOT overwrite the real secret.
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	// Store the real secret
	database.Create(&db.ServerSetting{Key: "igdb_client_id", Value: "my-client-id"})
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "real-secret-value"})

	// Simulate the frontend: GET returns masked secret, PUT sends it back along with other settings
	body, _ := json.Marshal(map[string]string{
		"igdb_client_id":     "my-client-id",
		"igdb_client_secret": "********",
		"allowRegistration":  "true",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/admin/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the real secret was NOT overwritten
	var setting db.ServerSetting
	require.NoError(t, database.Where("key = ?", "igdb_client_secret").First(&setting).Error)
	assert.Equal(t, "real-secret-value", setting.Value, "masked value must not overwrite real secret")
}

func TestSettingsMasking_NewSecretOverwrites(t *testing.T) {
	// When admin provides a real new secret (not the mask), it should be saved.
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: "old-secret"})

	body, _ := json.Marshal(map[string]string{
		"igdb_client_secret": "brand-new-secret",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/admin/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var setting db.ServerSetting
	require.NoError(t, database.Where("key = ?", "igdb_client_secret").First(&setting).Error)
	assert.Equal(t, "brand-new-secret", setting.Value)
}

func TestSettingsMasking_EmptySecret(t *testing.T) {
	database, router := setupIGDBTestEnv(t, nil)
	token := createIGDBTestUser(t, database, "admin")

	// Store empty secret
	database.Create(&db.ServerSetting{Key: "igdb_client_secret", Value: ""})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/settings", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	// Empty secret should NOT be masked (returns empty string)
	assert.Equal(t, "", resp["igdb_client_secret"])
}
