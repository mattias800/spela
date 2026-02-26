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

type saveTestEnv struct {
	database *gorm.DB
	router   http.Handler
	token    string
	token2   string
	gameID   string
}

func setupSaveTestEnv(t *testing.T) *saveTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Register first user (owner)
	token := registerAndGetToken(t, router)

	// Register second user
	body, _ := json.Marshal(map[string]string{
		"username": "user2",
		"email":    "user2@example.com",
		"password": "password123",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var reg2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &reg2)
	token2 := reg2["accessToken"].(string)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	return &saveTestEnv{
		database: database,
		router:   router,
		token:    token,
		token2:   token2,
		gameID:   fmt.Sprintf("%d", game.ID),
	}
}

func uploadSave(t *testing.T, router http.Handler, token, gameID, name string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

func uploadAutoSave(t *testing.T, router http.Handler, token, gameID string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "autosave.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/saves/auto", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

// --- Feature 2: Rename Save States ---

func TestRenameSave(t *testing.T) {
	tests := []struct {
		name           string
		body           map[string]string
		expectedStatus int
		expectedName   string
	}{
		{
			name:           "success",
			body:           map[string]string{"name": "New Name"},
			expectedStatus: http.StatusOK,
			expectedName:   "New Name",
		},
		{
			name:           "empty name",
			body:           map[string]string{"name": ""},
			expectedStatus: http.StatusBadRequest,
		},
		{
			name:           "whitespace only name",
			body:           map[string]string{"name": "   "},
			expectedStatus: http.StatusBadRequest,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			env := setupSaveTestEnv(t)

			// Upload a save
			w := uploadSave(t, env.router, env.token, env.gameID, "Old Name", []byte("data"))
			require.Equal(t, http.StatusCreated, w.Code)

			var created map[string]interface{}
			json.Unmarshal(w.Body.Bytes(), &created)
			saveID := fmt.Sprintf("%.0f", created["id"].(float64))

			// Rename
			body, _ := json.Marshal(tc.body)
			w = httptest.NewRecorder()
			req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID, bytes.NewReader(body))
			req.Header.Set("Authorization", "Bearer "+env.token)
			req.Header.Set("Content-Type", "application/json")
			env.router.ServeHTTP(w, req)
			assert.Equal(t, tc.expectedStatus, w.Code)

			if tc.expectedStatus == http.StatusOK {
				var renamed map[string]interface{}
				json.Unmarshal(w.Body.Bytes(), &renamed)
				assert.Equal(t, tc.expectedName, renamed["name"])
			}
		})
	}
}

func TestRenameSave_NotFound(t *testing.T) {
	env := setupSaveTestEnv(t)

	body, _ := json.Marshal(map[string]string{"name": "New Name"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/9999", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRenameSave_Unauthorized(t *testing.T) {
	env := setupSaveTestEnv(t)

	// User 1 creates save
	w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	// User 2 tries to rename
	body, _ := json.Marshal(map[string]string{"name": "Hacked"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRenameSave_MissingBody(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	// No body
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// --- Feature 2: Rename Relay Save ---

func TestRenameRelaySave(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Create a relay
	body, _ := json.Marshal(map[string]string{"gameId": env.gameID, "name": "Test Relay"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var relay map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relay)
	relayID := relay["id"].(string)

	// Take turn
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var turnResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &turnResp)
	turnToken := turnResp["turnToken"].(string)

	// Upload a relay save
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Old Relay Save")
	part, _ := writer.CreateFormFile("save", "relay.sav")
	part.Write([]byte("relay data"))
	writer.Close()

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var relaySave map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relaySave)
	saveID := relaySave["id"].(string)

	// Rename the relay save
	body, _ = json.Marshal(map[string]string{"name": "New Relay Save Name"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/relays/"+relayID+"/saves/"+saveID+"/rename", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var renamed map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &renamed)
	assert.Equal(t, "New Relay Save Name", renamed["name"])
}

func TestRenameRelaySave_NotMember(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Create a relay
	body, _ := json.Marshal(map[string]string{"gameId": env.gameID, "name": "Test Relay"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var relay map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relay)
	relayID := relay["id"].(string)

	// User 2 (not a member) tries to rename
	body, _ = json.Marshal(map[string]string{"name": "Hacked"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/relays/"+relayID+"/saves/1/rename", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Feature 5 (notes): Save State Notes ---

func TestUpdateNotes(t *testing.T) {
	tests := []struct {
		name           string
		notes          string
		expectedStatus int
	}{
		{
			name:           "set notes",
			notes:          "Right before boss fight",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "clear notes",
			notes:          "",
			expectedStatus: http.StatusOK,
		},
		{
			name:           "max length notes",
			notes:          string(make([]byte, 200)),
			expectedStatus: http.StatusOK,
		},
		{
			name:           "too long notes",
			notes:          string(make([]byte, 201)),
			expectedStatus: http.StatusBadRequest,
		},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			env := setupSaveTestEnv(t)

			w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("data"))
			require.Equal(t, http.StatusCreated, w.Code)

			var created map[string]interface{}
			json.Unmarshal(w.Body.Bytes(), &created)
			saveID := fmt.Sprintf("%.0f", created["id"].(float64))

			body, _ := json.Marshal(map[string]string{"notes": tc.notes})
			w = httptest.NewRecorder()
			req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID+"/notes", bytes.NewReader(body))
			req.Header.Set("Authorization", "Bearer "+env.token)
			req.Header.Set("Content-Type", "application/json")
			env.router.ServeHTTP(w, req)
			assert.Equal(t, tc.expectedStatus, w.Code)

			if tc.expectedStatus == http.StatusOK {
				var updated map[string]interface{}
				json.Unmarshal(w.Body.Bytes(), &updated)
				if tc.notes == "" {
					// Notes should be empty/absent
					notes, ok := updated["notes"]
					assert.True(t, !ok || notes == "" || notes == nil)
				} else {
					assert.Equal(t, tc.notes, updated["notes"])
				}
			}
		})
	}
}

func TestUpdateNotes_NotFound(t *testing.T) {
	env := setupSaveTestEnv(t)

	body, _ := json.Marshal(map[string]string{"notes": "test"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/9999/notes", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestUpdateNotes_Unauthorized(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	body, _ := json.Marshal(map[string]string{"notes": "hacked"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID+"/notes", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestNotesInListResponse(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload a save
	w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Set notes
	body, _ := json.Marshal(map[string]string{"notes": "Before boss"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/"+saveID+"/notes", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// List saves and verify notes are included
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	require.Len(t, saves, 1)
	assert.Equal(t, "Before boss", saves[0]["notes"])
}

// --- Feature 6: Multiple Auto-Save History ---

func TestAutoSaveHistory_CreatesMultiple(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload 3 auto-saves
	for i := 0; i < 3; i++ {
		w := uploadAutoSave(t, env.router, env.token, env.gameID, []byte(fmt.Sprintf("auto data %d", i)))
		assert.Equal(t, http.StatusOK, w.Code)
	}

	// Check auto-save history
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/auto/history", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 3)

	// Verify order (newest first)
	for i := 0; i < len(saves)-1; i++ {
		assert.True(t, saves[i]["createdAt"].(string) >= saves[i+1]["createdAt"].(string))
	}
}

func TestAutoSaveHistory_KeepsMax5(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload 7 auto-saves
	for i := 0; i < 7; i++ {
		w := uploadAutoSave(t, env.router, env.token, env.gameID, []byte(fmt.Sprintf("auto data %d", i)))
		assert.Equal(t, http.StatusOK, w.Code)
	}

	// Check auto-save history - should have max 5
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/auto/history", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 5)
}

func TestAutoSave_GetAutoSaveReturnsLatest(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload two auto-saves
	uploadAutoSave(t, env.router, env.token, env.gameID, []byte("old auto"))
	w := uploadAutoSave(t, env.router, env.token, env.gameID, []byte("new auto"))
	assert.Equal(t, http.StatusOK, w.Code)

	// GET /saves/auto should return the latest
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("new auto"), w.Body.Bytes())
}

func TestAutoSaveHistory_Empty(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/auto/history", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 0)
}

// --- Feature 7: Bulk Delete Saves ---

func TestBulkDeleteSaves(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload 3 saves
	var ids []float64
	for i := 0; i < 3; i++ {
		w := uploadSave(t, env.router, env.token, env.gameID, fmt.Sprintf("Save %d", i), []byte(fmt.Sprintf("data %d", i)))
		require.Equal(t, http.StatusCreated, w.Code)
		var created map[string]interface{}
		json.Unmarshal(w.Body.Bytes(), &created)
		ids = append(ids, created["id"].(float64))
	}

	// Bulk delete first two
	body, _ := json.Marshal(map[string]interface{}{"ids": []uint{uint(ids[0]), uint(ids[1])}})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/saves/bulk", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &result)
	assert.Equal(t, float64(2), result["deleted"])

	// List should have 1 save remaining
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 1)
}

func TestBulkDeleteSaves_CannotDeleteAutoSaves(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload an auto-save
	w := uploadAutoSave(t, env.router, env.token, env.gameID, []byte("auto data"))
	require.Equal(t, http.StatusOK, w.Code)

	var autoSave map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &autoSave)
	autoID := uint(autoSave["id"].(float64))

	// Try bulk deleting the auto-save
	body, _ := json.Marshal(map[string]interface{}{"ids": []uint{autoID}})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/saves/bulk", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestBulkDeleteSaves_EmptyIds(t *testing.T) {
	env := setupSaveTestEnv(t)

	body, _ := json.Marshal(map[string]interface{}{"ids": []uint{}})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/saves/bulk", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestBulkDeleteSaves_OnlyDeletesOwned(t *testing.T) {
	env := setupSaveTestEnv(t)

	// User 1 creates a save
	w := uploadSave(t, env.router, env.token, env.gameID, "User1 Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := uint(created["id"].(float64))

	// User 2 tries to bulk delete user 1's save
	body, _ := json.Marshal(map[string]interface{}{"ids": []uint{saveID}})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/games/"+env.gameID+"/saves/bulk", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &result)
	assert.Equal(t, float64(0), result["deleted"])

	// User 1's save should still exist
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 1)
}

// --- Feature 8: Save State Storage Usage ---

func TestStorageUsage_Empty(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/storage-usage", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &result)
	assert.Equal(t, float64(0), result["totalBytes"])
	games := result["games"].([]interface{})
	assert.Len(t, games, 0)
}

func TestStorageUsage_WithSaves(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload a save state (17 bytes)
	w := uploadSave(t, env.router, env.token, env.gameID, "My Save", []byte("save state data!!"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Upload SRAM save data (11 bytes)
	w = uploadSaveData(t, env.router, env.token, env.gameID, "SRAM Save", []byte("sram data!!"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Check storage usage
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/storage-usage", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &result)
	assert.Greater(t, result["totalBytes"].(float64), float64(0))

	games := result["games"].([]interface{})
	require.Len(t, games, 1)

	gameUsage := games[0].(map[string]interface{})
	assert.Equal(t, "Test Game", gameUsage["gameTitle"])
	assert.Greater(t, gameUsage["saveStateBytes"].(float64), float64(0))
	assert.Greater(t, gameUsage["sramBytes"].(float64), float64(0))
	assert.Equal(t, gameUsage["saveStateBytes"].(float64)+gameUsage["sramBytes"].(float64), gameUsage["totalBytes"].(float64))
}

func TestStorageUsage_SortedBySize(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Create a second game
	var console db.Console
	env.database.First(&console)
	game2 := db.Game{ConsoleID: console.ID, Title: "Big Game", FileName: "big.nes", FilePath: "/tmp/big.nes", FileSize: 200}
	env.database.Create(&game2)
	game2ID := fmt.Sprintf("%d", game2.ID)

	// Upload small save to game 1
	w := uploadSave(t, env.router, env.token, env.gameID, "Small", []byte("sm"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Upload big save to game 2
	w = uploadSave(t, env.router, env.token, game2ID, "Big", []byte("this is a much bigger save"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Check storage usage - should be sorted by totalBytes desc
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/storage-usage", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &result)

	games := result["games"].([]interface{})
	require.Len(t, games, 2)

	first := games[0].(map[string]interface{})
	second := games[1].(map[string]interface{})
	assert.GreaterOrEqual(t, first["totalBytes"].(float64), second["totalBytes"].(float64))
}

// --- Feature 9: Save State Export/Import ---

func TestImportSave(t *testing.T) {
	env := setupSaveTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Imported Save")
	part, _ := writer.CreateFormFile("save", "imported.sav")
	part.Write([]byte("imported save data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/saves/import", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	assert.Equal(t, "Imported Save", created["name"])
	assert.Equal(t, false, created["isAuto"])
}

func TestImportSave_EmptyFile(t *testing.T) {
	env := setupSaveTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Empty Save")
	part, _ := writer.CreateFormFile("save", "empty.sav")
	part.Write([]byte{})
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/saves/import", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestImportSave_NoFile(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/saves/import", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestImportSave_InvalidGame(t *testing.T) {
	env := setupSaveTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "save.sav")
	part.Write([]byte("data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/9999/saves/import", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDownloadSave_HasCorrectHeaders(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := uploadSave(t, env.router, env.token, env.gameID, "Export Test", []byte("exportable data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Download
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Contains(t, w.Header().Get("Content-Disposition"), "attachment")
	assert.Equal(t, []byte("exportable data"), w.Body.Bytes())
}

// --- Feature 5 (slots): Quick-Save Slots ---

func TestSlotSave_Upsert(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Save to slot 1
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot 1 data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/1", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var save1 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &save1)
	id1 := save1["id"]
	assert.Equal(t, float64(1), save1["slot"])

	// Overwrite slot 1
	buf.Reset()
	writer = multipart.NewWriter(&buf)
	part, _ = writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot 1 data v2"))
	writer.Close()

	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/1", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var save2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &save2)
	assert.Equal(t, id1, save2["id"], "should update same record")
}

func TestSlotSave_DownloadSlot(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Save to slot 3
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot 3 content"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/3", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Download from slot 3
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/slot/3", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("slot 3 content"), w.Body.Bytes())
}

func TestSlotSave_EmptySlot(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/slot/5", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestSlotSave_InvalidSlotNumber(t *testing.T) {
	tests := []struct {
		name string
		slot string
	}{
		{"slot 0", "0"},
		{"slot 11", "11"},
		{"slot -1", "-1"},
		{"slot abc", "abc"},
	}

	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			env := setupSaveTestEnv(t)

			var buf bytes.Buffer
			writer := multipart.NewWriter(&buf)
			part, _ := writer.CreateFormFile("save", "slot.sav")
			part.Write([]byte("data"))
			writer.Close()

			w := httptest.NewRecorder()
			req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/"+tc.slot, &buf)
			req.Header.Set("Authorization", "Bearer "+env.token)
			req.Header.Set("Content-Type", writer.FormDataContentType())
			env.router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusBadRequest, w.Code)
		})
	}
}

func TestSlotSave_ListSlots(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Save to slots 1 and 5
	for _, slot := range []string{"1", "5"} {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		part, _ := writer.CreateFormFile("save", "slot.sav")
		part.Write([]byte("slot " + slot + " data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/"+slot, &buf)
		req.Header.Set("Authorization", "Bearer "+env.token)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)
	}

	// List all slot saves
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/slots", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var slots []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &slots)
	assert.Len(t, slots, 2)

	// Should be ordered by slot number ASC
	assert.Equal(t, float64(1), slots[0]["slot"])
	assert.Equal(t, float64(5), slots[1]["slot"])
}

func TestSlotSave_SeparateFromRegularSaves(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload regular save
	w := uploadSave(t, env.router, env.token, env.gameID, "Regular", []byte("regular"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Upload slot save
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot data"))
	writer.Close()

	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/games/"+env.gameID+"/saves/slot/1", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// List regular saves - should include both (since ListSaves returns all)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var allSaves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &allSaves)
	assert.Len(t, allSaves, 2) // regular + slot

	// List slot saves - should only have slot saves
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/slots", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var slotSaves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &slotSaves)
	assert.Len(t, slotSaves, 1)
	assert.Equal(t, float64(1), slotSaves[0]["slot"])
}

// --- Feature 1: Save State Screenshots ---

func TestUploadSave_WithScreenshotFile(t *testing.T) {
	env := setupSaveTestEnv(t)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Save with Screenshot")

	// Add save file
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))

	// Add screenshot file
	ssPart, _ := writer.CreateFormFile("screenshot", "screenshot.png")
	ssPart.Write([]byte("PNG image data"))

	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	assert.NotEmpty(t, created["screenshotUrl"], "screenshot URL should be set when screenshot file is uploaded")
}

func TestGetSaveScreenshot_NotFound(t *testing.T) {
	env := setupSaveTestEnv(t)

	// Upload save without screenshot
	w := uploadSave(t, env.router, env.token, env.gameID, "No Screenshot", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var created map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &created)
	saveID := fmt.Sprintf("%.0f", created["id"].(float64))

	// Try to get screenshot
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/"+saveID+"/screenshot", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetSaveScreenshot_SaveNotFound(t *testing.T) {
	env := setupSaveTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/saves/9999/screenshot", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}
