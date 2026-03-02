package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

type saveDataTestEnv struct {
	database *gorm.DB
	router   http.Handler
	token    string
	token2   string
	gameID   string
}

func setupSaveDataTestEnv(t *testing.T) *saveDataTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Register first user (owner)
	token := registerAndGetToken(t, router)

	// Register second user
	token2 := createNonOwnerUser(t, router, token, "user2", "user2@example.com", "password123")

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	return &saveDataTestEnv{
		database: database,
		router:   router,
		token:    token,
		token2:   token2,
		gameID:   fmt.Sprintf("%d", game.ID),
	}
}

func uploadSaveData(t *testing.T, router http.Handler, token, gameID, name string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	part, _ := writer.CreateFormFile("file", "save.srm")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/save-data", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

func uploadActiveSaveData(t *testing.T, router http.Handler, token, gameID string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("file", "active_sram.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/save-data/active", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

func TestSaveData_ListEmpty(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 0)
}

func TestSaveData_UploadAndList(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	// Upload a save data
	w := uploadSaveData(t, env.router, env.token, env.gameID, "My Save", []byte("save data content"))
	assert.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	assert.Equal(t, "My Save", created["name"])
	assert.Equal(t, false, created["isActive"])

	// List should return 1 item
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 1)
	assert.Equal(t, "My Save", saves[0]["name"])
}

func TestSaveData_UploadActive_Create(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	w := uploadActiveSaveData(t, env.router, env.token, env.gameID, []byte("active save content"))
	assert.Equal(t, http.StatusOK, w.Code)

	var sd map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sd)
	assert.Equal(t, "Active Save Data", sd["name"])
	assert.Equal(t, true, sd["isActive"])
}

func TestSaveData_UploadActive_Upsert(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	// First upload
	w := uploadActiveSaveData(t, env.router, env.token, env.gameID, []byte("version 1"))
	assert.Equal(t, http.StatusOK, w.Code)

	var sd1 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sd1)
	id1 := sd1["id"]

	// Second upload (upsert) - should update same record
	w = uploadActiveSaveData(t, env.router, env.token, env.gameID, []byte("version 2 with more data"))
	assert.Equal(t, http.StatusOK, w.Code)

	var sd2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sd2)
	assert.Equal(t, id1, sd2["id"], "should update existing record, not create new")
	assert.Greater(t, sd2["fileSize"].(float64), sd1["fileSize"].(float64), "file size should reflect new content")

	// List should have only 1 active save data
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	activeCount := 0
	for _, s := range saves {
		if s["isActive"] == true {
			activeCount++
		}
	}
	assert.Equal(t, 1, activeCount)
}

func TestSaveData_DownloadActive(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	content := []byte("active sram content for download")
	w := uploadActiveSaveData(t, env.router, env.token, env.gameID, content)
	assert.Equal(t, http.StatusOK, w.Code)

	// Download active
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data/active", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, content, w.Body.Bytes())
}

func TestSaveData_DownloadActive_NotFound(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data/active", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestSaveData_DownloadSpecific(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	content := []byte("specific save data content")
	w := uploadSaveData(t, env.router, env.token, env.gameID, "Specific Save", content)
	assert.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	sdID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Download specific
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data/"+sdID+"/download", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, content, w.Body.Bytes())
}

func TestSaveData_Activate(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	// Upload two save data entries
	w := uploadSaveData(t, env.router, env.token, env.gameID, "Save A", []byte("data a"))
	require.Equal(t, http.StatusCreated, w.Code)
	var sdA map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sdA)
	idA := fmt.Sprintf("%.0f", sdA["id"].(float64))

	w = uploadSaveData(t, env.router, env.token, env.gameID, "Save B", []byte("data b"))
	require.Equal(t, http.StatusCreated, w.Code)
	var sdB map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sdB)
	idB := fmt.Sprintf("%.0f", sdB["id"].(float64))

	// Activate A
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/save-data/"+idA+"/activate", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var activated map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &activated)
	assert.Equal(t, true, activated["isActive"])

	// Activate B - should deactivate A
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/save-data/"+idB+"/activate", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// List and verify only B is active
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 2)

	activeCount := 0
	for _, s := range saves {
		if s["isActive"] == true {
			activeCount++
			assert.Equal(t, "Save B", s["name"])
		}
	}
	assert.Equal(t, 1, activeCount, "exactly one save should be active")
}

func TestSaveData_Rename(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	w := uploadSaveData(t, env.router, env.token, env.gameID, "Old Name", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	sdID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Rename
	body, _ := json.Marshal(map[string]string{"name": "New Name"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/save-data/"+sdID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var renamed map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &renamed)
	assert.Equal(t, "New Name", renamed["name"])
}

func TestSaveData_Delete(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	w := uploadSaveData(t, env.router, env.token, env.gameID, "To Delete", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	sdID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Delete
	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/save-data/"+sdID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// List should be empty
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 0)
}

func TestSaveData_AccessControl(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	// User 1 uploads save data
	w := uploadSaveData(t, env.router, env.token, env.gameID, "User1 Save", []byte("user1 data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	sdID := fmt.Sprintf("%.0f", created["id"].(float64))

	// User 2 tries to list user 1's save data - should see empty list (filtered by user)
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 0, "user 2 should not see user 1's save data")

	// User 2 tries to download user 1's save data by ID - should get 404
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data/"+sdID+"/download", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// User 2 tries to rename user 1's save data - should get 404
	body, _ := json.Marshal(map[string]string{"name": "Hacked Name"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/save-data/"+sdID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// User 2 tries to delete user 1's save data - should get 404
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/save-data/"+sdID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// User 2 tries to activate user 1's save data - should get 404
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/save-data/"+sdID+"/activate", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// User 1's data should still be intact
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 1, "user 1's save data should still exist")
	assert.Equal(t, "User1 Save", saves[0]["name"])
}

func TestSaveData_DownloadActive_AccessControl(t *testing.T) {
	env := setupSaveDataTestEnv(t)

	// User 1 uploads active save data
	w := uploadActiveSaveData(t, env.router, env.token, env.gameID, []byte("user1 active data"))
	assert.Equal(t, http.StatusOK, w.Code)

	// User 2 tries to download user 1's active save data - should get 404
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/save-data/active", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}
