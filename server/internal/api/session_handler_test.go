package api

import (
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
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

type sessionTestEnv struct {
	database *gorm.DB
	router   http.Handler
	token    string
	token2   string
	gameID   string
}

func setupSessionTestEnv(t *testing.T) *sessionTestEnv {
	t.Helper()
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Register first user (owner)
	token := registerAndGetToken(t, router)

	// Register second user
	token2 := createNonOwnerUser(t, router, token, "user2", "user2@example.com", "password123")

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)

	return &sessionTestEnv{
		database: database,
		router:   router,
		token:    token,
		token2:   token2,
		gameID:   fmt.Sprintf("%d", game.ID),
	}
}

func createGameSession(t *testing.T, router http.Handler, token, gameID, name string) map[string]interface{} {
	t.Helper()
	body, _ := json.Marshal(map[string]string{"name": name})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, "createGameSession failed: %s", w.Body.String())

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp
}

func uploadSessionSave(t *testing.T, router http.Handler, token, sessionID, name string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

func uploadSessionAutoSave(t *testing.T, router http.Handler, token, sessionID string, data []byte) *httptest.ResponseRecorder {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "autosave.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves/auto", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	return w
}

// --- Session CRUD ---

func TestCreateSession(t *testing.T) {
	env := setupSessionTestEnv(t)

	resp := createGameSession(t, env.router, env.token, env.gameID, "My Playthrough")
	assert.Equal(t, "My Playthrough", resp["name"])
	assert.Equal(t, env.gameID, resp["gameId"])
	assert.NotEmpty(t, resp["id"])
	assert.NotEmpty(t, resp["ownerId"])
	assert.Equal(t, float64(0), resp["totalPlayTime"])
	assert.Equal(t, float64(0), resp["saveCount"])
}

func TestCreateSession_EmptyName(t *testing.T) {
	env := setupSessionTestEnv(t)

	body, _ := json.Marshal(map[string]string{"name": ""})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateSession_WhitespaceOnlyName(t *testing.T) {
	env := setupSessionTestEnv(t)

	body, _ := json.Marshal(map[string]string{"name": "   "})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateSession_InvalidGame(t *testing.T) {
	env := setupSessionTestEnv(t)

	body, _ := json.Marshal(map[string]string{"name": "My Playthrough"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/9999/sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestListSessions(t *testing.T) {
	env := setupSessionTestEnv(t)

	// Create two sessions
	createGameSession(t, env.router, env.token, env.gameID, "Session 1")
	createGameSession(t, env.router, env.token, env.gameID, "Session 2")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/sessions", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var sessions []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sessions)
	assert.Len(t, sessions, 2)
}

func TestListSessions_EmptyForOtherUser(t *testing.T) {
	env := setupSessionTestEnv(t)

	// User 1 creates a session
	createGameSession(t, env.router, env.token, env.gameID, "User1 Session")

	// User 2 lists sessions for the same game
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+env.gameID+"/sessions", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var sessions []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &sessions)
	assert.Len(t, sessions, 0)
}

func TestGetSession(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "My Session", resp["name"])
}

func TestGetSession_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// User 2 tries to get user 1's session
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestGetSession_NotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/9999", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestUpdateSession(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "Old Name")
	sessionID := created["id"].(string)

	body, _ := json.Marshal(map[string]interface{}{
		"name":          "New Name",
		"cheatsEnabled": true,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "New Name", resp["name"])
	assert.Equal(t, true, resp["cheatsEnabled"])
}

func TestUpdateSession_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	body, _ := json.Marshal(map[string]string{"name": "Hacked"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteSession(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "To Delete")
	sessionID := created["id"].(string)

	// Upload a save to the session
	w := uploadSessionSave(t, env.router, env.token, sessionID, "Save 1", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Delete the session
	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteSession_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Session Save States ---

func TestUploadSessionSave(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token, sessionID, "My Save", []byte("save data"))
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "My Save", resp["name"])
	assert.Equal(t, sessionID, resp["sessionId"])
	assert.Equal(t, false, resp["isAuto"])
}

func TestUploadSessionSave_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token2, sessionID, "Hacked", []byte("evil"))
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestListSessionSaves(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	uploadSessionSave(t, env.router, env.token, sessionID, "Save 1", []byte("data1"))
	uploadSessionSave(t, env.router, env.token, sessionID, "Save 2", []byte("data2"))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 2)
}

func TestDownloadSessionSave(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token, sessionID, "My Save", []byte("download me"))
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("download me"), w.Body.Bytes())
}

func TestDeleteSessionSave(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token, sessionID, "Save 1", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	w = httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/sessions/"+sessionID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestUpdateSessionSave(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token, sessionID, "Old Name", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	body, _ := json.Marshal(map[string]string{"name": "New Name", "notes": "Before boss"})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/"+saveID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var updated map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &updated)
	assert.Equal(t, "New Name", updated["name"])
	assert.Equal(t, "Before boss", updated["notes"])
}

func TestUpdateSessionSave_NotesTooLong(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionSave(t, env.router, env.token, sessionID, "My Save", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	longNotes := string(make([]byte, 201))
	body, _ := json.Marshal(map[string]string{"notes": longNotes})
	w = httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/"+saveID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// --- Auto-Save ---

func TestSessionAutoSave(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := uploadSessionAutoSave(t, env.router, env.token, sessionID, []byte("auto save data"))
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["isAuto"])
	assert.Equal(t, "Auto Save", resp["name"])
}

func TestSessionAutoSave_GetLatest(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	uploadSessionAutoSave(t, env.router, env.token, sessionID, []byte("old auto"))
	uploadSessionAutoSave(t, env.router, env.token, sessionID, []byte("new auto"))

	// GET auto should return the latest file
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("new auto"), w.Body.Bytes())
}

func TestSessionAutoSave_PrunesOld(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload 7 auto-saves
	for i := 0; i < 7; i++ {
		w := uploadSessionAutoSave(t, env.router, env.token, sessionID, []byte(fmt.Sprintf("auto %d", i)))
		assert.Equal(t, http.StatusCreated, w.Code)
	}

	// DB should have max 5 auto-saves
	var count int64
	env.database.Model(&db.SessionSaveState{}).
		Where("session_id = ? AND is_auto = ?", 1, true).
		Count(&count)
	assert.LessOrEqual(t, count, int64(5))
}

func TestSessionAutoSave_NotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Slot Saves ---

func TestSessionSlotSave_Upsert(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Save to slot 1
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot 1 data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/slot/1", &buf)
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
	req = httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/slot/1", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var save2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &save2)
	assert.Equal(t, id1, save2["id"], "should update same record")
}

func TestSessionSlotSave_Download(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "slot.sav")
	part.Write([]byte("slot 3 content"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/slot/3", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Download
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/slot/3", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("slot 3 content"), w.Body.Bytes())
}

func TestSessionSlotSave_EmptySlot(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/slot/5", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestSessionSlotSave_InvalidSlot(t *testing.T) {
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
			env := setupSessionTestEnv(t)

			created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
			sessionID := created["id"].(string)

			var buf bytes.Buffer
			writer := multipart.NewWriter(&buf)
			part, _ := writer.CreateFormFile("save", "slot.sav")
			part.Write([]byte("data"))
			writer.Close()

			w := httptest.NewRecorder()
			req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/slot/"+tc.slot, &buf)
			req.Header.Set("Authorization", "Bearer "+env.token)
			req.Header.Set("Content-Type", writer.FormDataContentType())
			env.router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusBadRequest, w.Code)
		})
	}
}

func TestSessionSlotSave_ListSlots(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	for _, slot := range []string{"1", "5"} {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		part, _ := writer.CreateFormFile("save", "slot.sav")
		part.Write([]byte("slot " + slot + " data"))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/saves/slot/"+slot, &buf)
		req.Header.Set("Authorization", "Bearer "+env.token)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		env.router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves/slots", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var slots []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &slots)
	assert.Len(t, slots, 2)
	assert.Equal(t, float64(1), slots[0]["slot"])
	assert.Equal(t, float64(5), slots[1]["slot"])
}

// --- SRAM ---

func TestSessionSRAM_UploadAndDownload(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload SRAM
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("file", "save.srm")
	part.Write([]byte("sram data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/sram", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Download SRAM
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/sram", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("sram data"), w.Body.Bytes())
}

func TestSessionSRAM_UpsertOverwrites(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload first SRAM
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("file", "save.srm")
	part.Write([]byte("old sram"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/sram", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Upload second SRAM (should overwrite)
	buf.Reset()
	writer = multipart.NewWriter(&buf)
	part, _ = writer.CreateFormFile("file", "save.srm")
	part.Write([]byte("new sram"))
	writer.Close()

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/sram", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Download should return the new data
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/sram", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("new sram"), w.Body.Bytes())
}

func TestSessionSRAM_NotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/sram", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

// --- Play Time ---

func TestSessionPlayTime(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Add 60 seconds of play time
	body, _ := json.Marshal(map[string]int64{"seconds": 60})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(60), resp["totalPlayTime"])
	assert.NotNil(t, resp["lastPlayedAt"])

	// Add more play time
	body, _ = json.Marshal(map[string]int64{"seconds": 120})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(180), resp["totalPlayTime"])
}

func TestSessionPlayTime_NegativeSeconds(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	body, _ := json.Marshal(map[string]int64{"seconds": -10})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestSessionStopPlaying(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/sessions/"+sessionID+"/play-time", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
}

// --- Save Count ---

func TestSessionSaveCount(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Initially 0
	assert.Equal(t, float64(0), created["saveCount"])

	// Upload 2 saves
	uploadSessionSave(t, env.router, env.token, sessionID, "Save 1", []byte("data1"))
	uploadSessionSave(t, env.router, env.token, sessionID, "Save 2", []byte("data2"))

	// Get session - saveCount should be 2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(2), resp["saveCount"])
}

// --- Session Cheats ---

func TestGetSessionCheats_DefaultDisabled(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/cheats", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, false, resp["cheatsEnabled"])
	indices := resp["enabledIndices"].([]interface{})
	assert.Len(t, indices, 0)
}

func TestPutSessionCheats(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	body, _ := json.Marshal(map[string]interface{}{
		"cheatsEnabled":  true,
		"enabledIndices": []int{0, 2, 5},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/cheats", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["cheatsEnabled"])
	indices := resp["enabledIndices"].([]interface{})
	assert.Len(t, indices, 3)
	assert.Equal(t, float64(0), indices[0])
	assert.Equal(t, float64(2), indices[1])
	assert.Equal(t, float64(5), indices[2])
}

func TestSessionCheats_PutThenGet(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// PUT cheats
	body, _ := json.Marshal(map[string]interface{}{
		"cheatsEnabled":  true,
		"enabledIndices": []int{1, 3},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/cheats", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// GET cheats - should match
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/cheats", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["cheatsEnabled"])
	indices := resp["enabledIndices"].([]interface{})
	assert.Len(t, indices, 2)
	assert.Equal(t, float64(1), indices[0])
	assert.Equal(t, float64(3), indices[1])
}

func TestSessionCheats_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// User 2 tries to GET cheats
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/cheats", nil)
	req.Header.Set("Authorization", "Bearer "+env.token2)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)

	// User 2 tries to PUT cheats
	body, _ := json.Marshal(map[string]interface{}{
		"cheatsEnabled":  true,
		"enabledIndices": []int{0},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/cheats", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token2)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Upload with Screenshot ---

func TestUploadSessionSave_WithScreenshot(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Save with Screenshot")
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))
	ssPart, _ := writer.CreateFormFile("screenshot", "screenshot.png")
	ssPart.Write([]byte("PNG image data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NotEmpty(t, resp["screenshotUrl"])
}

// --- Create From Shared Save ---

func createSharedSaveOnDisk(t *testing.T, database *gorm.DB, storage string, gameID uint, userID uint, name string, data []byte) db.SharedSaveState {
	t.Helper()
	shared := db.SharedSaveState{
		UserID: userID,
		GameID: gameID,
		Name:   name,
	}
	require.NoError(t, database.Create(&shared).Error)

	// Write the file to the save dir so the handler can read it
	dir := filepath.Join(storage, "shared_saves", fmt.Sprintf("game_%d", gameID))
	require.NoError(t, os.MkdirAll(dir, 0700))
	filePath := filepath.Join(dir, fmt.Sprintf("save_%d.sav", shared.ID))
	require.NoError(t, os.WriteFile(filePath, data, 0600))

	shared.FilePath = filePath
	shared.FileSize = int64(len(data))
	require.NoError(t, database.Save(&shared).Error)

	return shared
}

func TestCreateFromSharedSave_Success(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var user db.User
	database.First(&user)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	shared := createSharedSaveOnDisk(t, database, cfg.Storage.SaveDir, game.ID, user.ID, "Boss Fight Save", []byte("shared save data"))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", fmt.Sprintf("/api/games/%s/sessions/from-shared-save/%d", gameID, shared.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "From: Boss Fight Save", resp["name"])
	assert.Equal(t, gameID, resp["gameId"])
	assert.Equal(t, float64(1), resp["saveCount"])
	assert.NotEmpty(t, resp["id"])

	// Verify download count was incremented
	var updatedShared db.SharedSaveState
	database.First(&updatedShared, shared.ID)
	assert.Equal(t, 1, updatedShared.DownloadCount)
}

func TestCreateFromSharedSave_InvalidGameID(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/abc/sessions/from-shared-save/1", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateFromSharedSave_InvalidSaveID(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/sessions/from-shared-save/abc", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestCreateFromSharedSave_GameNotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/9999/sessions/from-shared-save/1", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestCreateFromSharedSave_SaveNotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+env.gameID+"/sessions/from-shared-save/9999", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestCreateFromSharedSave_SaveBelongsToDifferentGame(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var user db.User
	database.First(&user)

	var console db.Console
	database.First(&console)
	game1 := db.Game{ConsoleID: console.ID, Title: "Game 1", FileName: "game1.nes", FilePath: "/tmp/game1.nes", FileSize: 100}
	database.Create(&game1)
	game2 := db.Game{ConsoleID: console.ID, Title: "Game 2", FileName: "game2.nes", FilePath: "/tmp/game2.nes", FileSize: 100}
	database.Create(&game2)

	// Create shared save for game2
	shared := createSharedSaveOnDisk(t, database, cfg.Storage.SaveDir, game2.ID, user.ID, "Wrong Game Save", []byte("data"))

	// Try to create session from shared save using game1's ID
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", fmt.Sprintf("/api/games/%d/sessions/from-shared-save/%d", game1.ID, shared.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "shared save does not belong to this game", resp["error"])
}

// --- Session Updates from Save Uploads ---

func TestSessionUpdatedOnSaveUpload(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload a save with coreName
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "My Save")
	writer.WriteField("coreName", "snes9x")
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get session - should have updated lastPlayedAt and coreName
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+sessionID, nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NotNil(t, resp["lastPlayedAt"])
	assert.Equal(t, "snes9x", resp["coreName"])
}

// --- Duplicate Session ---

func duplicateSession(t *testing.T, router http.Handler, token, sessionID string, body []byte) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	var req *http.Request
	if body != nil {
		req = httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/duplicate", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
	} else {
		req = httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/duplicate", nil)
	}
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

func TestDuplicateSession_Basic(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Playthrough")
	sessionID := created["id"].(string)

	w := duplicateSession(t, env.router, env.token, sessionID, nil)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "My Playthrough (Copy)", resp["name"])
	assert.Equal(t, env.gameID, resp["gameId"])
	assert.NotEqual(t, sessionID, resp["id"])
}

func TestDuplicateSession_CustomName(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "Original")
	sessionID := created["id"].(string)

	body, _ := json.Marshal(map[string]string{"name": "My Custom Copy"})
	w := duplicateSession(t, env.router, env.token, sessionID, body)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "My Custom Copy", resp["name"])
}

func TestDuplicateSession_CopiesSaves(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload saves to original session
	w := uploadSessionSave(t, env.router, env.token, sessionID, "Save 1", []byte("save1"))
	require.Equal(t, http.StatusCreated, w.Code)
	w = uploadSessionSave(t, env.router, env.token, sessionID, "Save 2", []byte("save2"))
	require.Equal(t, http.StatusCreated, w.Code)

	// Duplicate
	w = duplicateSession(t, env.router, env.token, sessionID, nil)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(2), resp["saveCount"])

	// List saves in the new session
	newSessionID := resp["id"].(string)
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+newSessionID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 2)
}

func TestDuplicateSession_CopiesSRAM(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload SRAM
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("file", "save.srm")
	part.Write([]byte("sram data content"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/sram", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Duplicate
	w = duplicateSession(t, env.router, env.token, sessionID, nil)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	newSessionID := resp["id"].(string)

	// Download SRAM from new session
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+newSessionID+"/sram", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, []byte("sram data content"), w.Body.Bytes())
}

func TestDuplicateSession_CopiesCheats(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Set cheats on original
	body, _ := json.Marshal(map[string]interface{}{
		"cheatsEnabled":  true,
		"enabledIndices": []int{0, 2},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/sessions/"+sessionID+"/cheats", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", "application/json")
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Duplicate
	w = duplicateSession(t, env.router, env.token, sessionID, nil)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["cheatsEnabled"])
	newSessionID := resp["id"].(string)

	// Get cheats from new session
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/sessions/"+newSessionID+"/cheats", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var cheats map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &cheats)
	assert.Equal(t, true, cheats["cheatsEnabled"])
	indices := cheats["enabledIndices"].([]interface{})
	assert.Len(t, indices, 2)
	assert.Equal(t, float64(0), indices[0])
	assert.Equal(t, float64(2), indices[1])
}

func TestDuplicateSession_NotOwner(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// User 2 tries to duplicate user 1's session
	w := duplicateSession(t, env.router, env.token2, sessionID, nil)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDuplicateSession_NotFound(t *testing.T) {
	env := setupSessionTestEnv(t)

	w := duplicateSession(t, env.router, env.token, "9999", nil)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDuplicateSession_SharedSessionMember(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token1 := registerAndGetToken(t, router)
	token2 := createNonOwnerUser(t, router, token1, "user2", "user2@example.com", "password123")

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// User 1 creates a shared session (this auto-creates a backing game session)
	shared := createSharedSession(t, router, token1, gameID, "Coop Run")
	sharedSessionID := shared["id"].(string)

	// Invite and accept user 2
	inviteAndAcceptSharedSession(t, router, token1, token2, sharedSessionID, "user2")

	// Find the backing session ID
	backingSessionID := shared["sessionId"].(string)

	// User 2 (member) duplicates the backing session
	w := duplicateSession(t, router, token2, backingSessionID, nil)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Contains(t, resp["name"], "(Copy)")
}

// --- Core Match Tests ---

func uploadSessionSaveWithCore(t *testing.T, router http.Handler, token, sessionID, name, coreName string, data []byte) map[string]interface{} {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", name)
	if coreName != "" {
		writer.WriteField("coreName", coreName)
	}
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write(data)
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, "upload save failed: %s", w.Body.String())

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp
}

func TestSaveResponse_CoreMatch_Matching(t *testing.T) {
	env := setupSessionTestEnv(t)

	// The default console from setupSessionTestEnv is NES with DefaultCore "nestopia"
	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload save with matching core name
	resp := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Save 1", "nestopia", []byte("data"))

	assert.Equal(t, "nestopia", resp["coreName"])
	assert.Equal(t, "nestopia", resp["currentCore"])
	assert.Equal(t, true, resp["coreMatch"])
}

func TestSaveResponse_CoreMatch_Mismatched(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload save with a different core name
	resp := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Save 1", "fceux", []byte("data"))

	assert.Equal(t, "fceux", resp["coreName"])
	assert.Equal(t, "nestopia", resp["currentCore"])
	assert.Equal(t, false, resp["coreMatch"])
}

func TestSaveResponse_CoreMatch_NoCoreName(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload save without core name
	resp := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Save 1", "", []byte("data"))

	assert.Empty(t, resp["coreName"])
	assert.Equal(t, "nestopia", resp["currentCore"])
	// coreMatch should be nil (omitted) when save has no core name
	assert.Nil(t, resp["coreMatch"])
}

func TestSaveResponse_CoreMatch_WithCoreOverride(t *testing.T) {
	env := setupSessionTestEnv(t)

	// Set a core override on the game
	env.database.Model(&db.Game{}).Where("id = ?", env.gameID).Update("core_override", "fceux")

	created := createGameSession(t, env.router, env.token, env.gameID, "Override Session")
	sessionID := created["id"].(string)

	// Upload save with the override core - should match
	resp := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Save 1", "fceux", []byte("data"))

	assert.Equal(t, "fceux", resp["coreName"])
	assert.Equal(t, "fceux", resp["currentCore"])
	assert.Equal(t, true, resp["coreMatch"])

	// Upload save with the default core - should NOT match because override takes priority
	resp2 := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Save 2", "nestopia", []byte("data2"))

	assert.Equal(t, "nestopia", resp2["coreName"])
	assert.Equal(t, "fceux", resp2["currentCore"])
	assert.Equal(t, false, resp2["coreMatch"])
}

func TestListSessionSaves_IncludesCoreMatch(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "My Session")
	sessionID := created["id"].(string)

	// Upload saves with different cores
	uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Matching", "nestopia", []byte("data1"))
	uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Mismatched", "fceux", []byte("data2"))

	// List saves
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+sessionID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	require.Len(t, saves, 2)

	for _, save := range saves {
		assert.Equal(t, "nestopia", save["currentCore"])
		if save["coreName"] == "nestopia" {
			assert.Equal(t, true, save["coreMatch"])
		} else {
			assert.Equal(t, false, save["coreMatch"])
		}
	}
}

// --- Session Screenshot Image Serving ---

func TestServeSessionScreenshot_OwnerCanAccess(t *testing.T) {
	env := setupSessionTestEnv(t)

	// Create a session and upload a save with a screenshot
	created := createGameSession(t, env.router, env.token, env.gameID, "Screenshot Session")
	sessionID := created["id"].(string)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Save with SS")
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))
	ssPart, _ := writer.CreateFormFile("screenshot", "screenshot.png")
	ssPart.Write([]byte("PNG image data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	screenshotURL := saveResp["screenshotUrl"].(string)
	require.NotEmpty(t, screenshotURL)

	// Fetch the screenshot image using query token auth
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", screenshotURL+"?token="+env.token, nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code, "owner should be able to access session screenshot")
}

func TestServeSessionScreenshot_OtherUserDenied(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "Private Session")
	sessionID := created["id"].(string)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Save")
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))
	ssPart, _ := writer.CreateFormFile("screenshot", "screenshot.png")
	ssPart.Write([]byte("PNG image data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	screenshotURL := saveResp["screenshotUrl"].(string)

	// Other user should be denied
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", screenshotURL+"?token="+env.token2, nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code, "non-owner should be denied access to session screenshot")
}

func TestServeSessionScreenshot_NoAuthDenied(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "Auth Session")
	sessionID := created["id"].(string)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	writer.WriteField("name", "Save")
	part, _ := writer.CreateFormFile("save", "state.sav")
	part.Write([]byte("save data"))
	ssPart, _ := writer.CreateFormFile("screenshot", "screenshot.png")
	ssPart.Write([]byte("PNG image data"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	screenshotURL := saveResp["screenshotUrl"].(string)

	// No token should return 401
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", screenshotURL, nil)
	env.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnauthorized, w.Code, "unauthenticated request should be denied")
}
