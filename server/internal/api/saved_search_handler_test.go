package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func setupSavedSearchEnv(t *testing.T) (*gorm.DB, http.Handler, string) {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)
	return database, router, token
}

func createSavedSearch(t *testing.T, router http.Handler, token, name string, filters interface{}) (int, map[string]interface{}) {
	t.Helper()
	body, _ := json.Marshal(map[string]interface{}{
		"name":    name,
		"filters": filters,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/saved-searches", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return w.Code, resp
}

func TestCreateSavedSearch_Success(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	filters := map[string]interface{}{
		"consoles":  "SNES,NES",
		"genres":    "Action,RPG",
		"ratingMin": 70,
	}

	code, resp := createSavedSearch(t, router, token, "My RPG Filter", filters)
	assert.Equal(t, http.StatusCreated, code)
	assert.Equal(t, "My RPG Filter", resp["name"])
	assert.NotEmpty(t, resp["id"])
	assert.NotNil(t, resp["filters"])

	// Verify filters is valid JSON that can be parsed back
	filtersJSON, ok := resp["filters"].(map[string]interface{})
	require.True(t, ok, "filters should be a JSON object")
	assert.Equal(t, "SNES,NES", filtersJSON["consoles"])
}

func TestCreateSavedSearch_MissingName(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	body, _ := json.Marshal(map[string]interface{}{
		"filters": map[string]string{"genre": "Action"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/saved-searches", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateSavedSearch_InvalidJSON(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	// Send raw body with invalid JSON in filters
	body := []byte(`{"name":"test","filters":"not json"}`)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/saved-searches", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	// "not json" is a valid JSON string, so the request should succeed
	// but actual non-JSON would fail at binding. Let's test with truly bad JSON:
	body = []byte(`{"name":"test","filters":invalid}`)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/saved-searches", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestListSavedSearches_Empty(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/saved-searches", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp []interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)
}

func TestListSavedSearches_WithData(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	// Create two saved searches
	createSavedSearch(t, router, token, "Search One", map[string]string{"genre": "Action"})
	createSavedSearch(t, router, token, "Search Two", map[string]string{"developer": "Nintendo"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/saved-searches", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp []map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Len(t, resp, 2)

	// Should be ordered by created_at DESC (newest first)
	assert.Equal(t, "Search Two", resp[0]["name"])
	assert.Equal(t, "Search One", resp[1]["name"])
}

func TestDeleteSavedSearch_Success(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	// Create a saved search
	code, resp := createSavedSearch(t, router, token, "To Delete", map[string]string{"genre": "RPG"})
	require.Equal(t, http.StatusCreated, code)
	searchID := resp["id"].(string)

	// Delete it
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/saved-searches/"+searchID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone from the list
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/saved-searches", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var list []interface{}
	json.Unmarshal(w.Body.Bytes(), &list)
	assert.Empty(t, list)
}

func TestDeleteSavedSearch_NotOwned(t *testing.T) {
	database, router, token := setupSavedSearchEnv(t)

	// Create a saved search as user 1
	code, resp := createSavedSearch(t, router, token, "User1 Search", map[string]string{"genre": "Action"})
	require.Equal(t, http.StatusCreated, code)
	searchID := resp["id"].(string)

	// Create a second user
	token2 := createNonOwnerUser(t, router, token, "user2", "SecureTestPass!2024")
	_ = database // suppress unused

	// Try to delete user1's search as user2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/saved-searches/"+searchID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestSavedSearches_Limit50(t *testing.T) {
	database, router, token := setupSavedSearchEnv(t)

	// Directly insert 50 saved searches to avoid slow HTTP round-trips
	var user db.User
	database.First(&user)
	for i := 0; i < 50; i++ {
		database.Create(&db.SavedSearch{
			UserID:  user.ID,
			Name:    fmt.Sprintf("Search %d", i),
			Filters: `{"genre":"Action"}`,
		})
	}

	// The 51st should be rejected
	code, resp := createSavedSearch(t, router, token, "One Too Many", map[string]string{"genre": "RPG"})
	assert.Equal(t, http.StatusConflict, code)
	assert.Contains(t, resp["error"], "maximum")
}

func TestDeleteSavedSearch_NotFound(t *testing.T) {
	_, router, token := setupSavedSearchEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/user/saved-searches/99999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}
