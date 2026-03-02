package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestCreateCollection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"name":        "My Favorites",
		"description": "The best games ever",
		"isPublic":    true,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "My Favorites", resp["name"])
	assert.Equal(t, "The best games ever", resp["description"])
	assert.Equal(t, true, resp["isPublic"])
	assert.Equal(t, "apitest", resp["username"])
	assert.Equal(t, float64(0), resp["gameCount"])
	assert.NotEmpty(t, resp["id"])
}

func TestCreateCollection_MissingName(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"description": "No name",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateCollection_CreatesActivityEvent(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"name": "Activity Collection",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
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
		if event["eventType"] == "created_collection" {
			found = true
			break
		}
	}
	assert.True(t, found, "should have a created_collection activity event")
}

func TestListMyCollections(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create two collections
	for _, name := range []string{"Collection A", "Collection B"} {
		body, _ := json.Marshal(map[string]interface{}{"name": name})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	// List
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/collections", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 2)
	assert.Equal(t, float64(2), resp["total"])
	assert.Equal(t, float64(1), resp["page"])
	assert.Equal(t, float64(20), resp["pageSize"])
}

func TestListMyCollections_Empty(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/collections", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 0)
	assert.Equal(t, float64(0), resp["total"])
}

func TestListPublicCollections(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create one public and one private collection
	for _, tc := range []struct {
		name     string
		isPublic bool
	}{
		{"Public Collection", true},
		{"Private Collection", false},
	} {
		body, _ := json.Marshal(map[string]interface{}{"name": tc.name, "isPublic": tc.isPublic})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code)
	}

	// List public only
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/collections/public", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	data := resp["data"].([]interface{})
	assert.Len(t, data, 1)
	assert.Equal(t, float64(1), resp["total"])

	col := data[0].(map[string]interface{})
	assert.Equal(t, "Public Collection", col["name"])
	assert.Equal(t, true, col["isPublic"])
}

func TestGetCollection(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Collection Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	// Create collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Detail Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get collection detail
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Detail Collection", resp["name"])
	assert.Equal(t, float64(1), resp["gameCount"])
	games := resp["games"].([]interface{})
	assert.Len(t, games, 1)
	assert.Equal(t, "Collection Game", games[0].(map[string]interface{})["title"])
}

func TestGetCollection_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/collections/99999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetCollection_PrivateNotAccessibleByOthers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a private collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Private", "isPublic": false})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Register a second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Other user tries to access private collection
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+otherToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetCollection_PublicAccessibleByOthers(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a public collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Public Visible", "isPublic": true})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Register a second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Other user can access public collection
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+otherToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Public Visible", resp["name"])
}

func TestUpdateCollection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Original Name"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Update
	body, _ = json.Marshal(map[string]interface{}{
		"name":        "Updated Name",
		"description": "New description",
		"isPublic":    true,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/collections/"+colID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Updated Name", resp["name"])
	assert.Equal(t, "New description", resp["description"])
	assert.Equal(t, true, resp["isPublic"])
}

func TestUpdateCollection_NotOwner(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Owner Only"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Register a second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Other user tries to update
	body, _ = json.Marshal(map[string]interface{}{"name": "Hacked Name"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/collections/"+colID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+otherToken)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteCollection(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a game and collection with it
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Col Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]interface{}{"name": "To Delete"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add a game to the collection
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Delete collection
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// Verify items were deleted too
	var itemCount int64
	database.Model(&db.CollectionItem{}).Where("collection_id = ?", colID).Count(&itemCount)
	assert.Equal(t, int64(0), itemCount)
}

func TestDeleteCollection_NotOwner(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"name": "Protected"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Register second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Other user tries to delete
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+otherToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestAddGame(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Add Game Test", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	// Create collection
	body, _ := json.Marshal(map[string]interface{}{"name": "Game Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(1), resp["gameCount"])
}

func TestAddGame_Duplicate(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Dupe Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]interface{}{"name": "Dupe Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add game first time
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Add same game again - should conflict
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestAddGame_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"name": "Empty Col"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	body, _ = json.Marshal(map[string]interface{}{"gameId": 99999})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestAddGame_NotOwner(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]interface{}{"name": "Auth Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Register second user
	otherToken := createNonOwnerUser(t, router, token, "otheruser", "other@example.com", "password123")

	// Other user tries to add game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+otherToken)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRemoveGame(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Remove Game Test", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	body, _ := json.Marshal(map[string]interface{}{"name": "Remove Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Remove game
	gameIDStr := fmt.Sprintf("%d", game.ID)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/collections/"+colID+"/games/"+gameIDStr, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify collection has 0 games
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(0), resp["gameCount"])
	games := resp["games"].([]interface{})
	assert.Len(t, games, 0)
}

func TestRemoveGame_NotInCollection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{"name": "Empty For Remove"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/collections/"+colID+"/games/99999", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestAddMultipleGames_PositionOrder(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "First Game", FileName: "first.nes", FilePath: "/tmp/first.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Second Game", FileName: "second.nes", FilePath: "/tmp/second.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	body, _ := json.Marshal(map[string]interface{}{"name": "Ordered Collection"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/collections", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var createResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &createResp)
	colID := createResp["id"].(string)

	// Add first game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game1.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Add second game
	body, _ = json.Marshal(map[string]interface{}{"gameId": game2.ID})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/collections/"+colID+"/games", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get collection - games should be in order
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/collections/"+colID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	games := resp["games"].([]interface{})
	assert.Len(t, games, 2)
	assert.Equal(t, "First Game", games[0].(map[string]interface{})["title"])
	assert.Equal(t, "Second Game", games[1].(map[string]interface{})["title"])
}
