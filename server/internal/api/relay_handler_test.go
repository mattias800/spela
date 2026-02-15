package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Helper functions ---

// registerSecondUser registers a second user and returns their access token.
func registerSecondUser(t *testing.T, router http.Handler) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": "player2",
		"email":    "player2@example.com",
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

// registerNamedUser registers a user with the given username and returns their access token.
func registerNamedUser(t *testing.T, router http.Handler, username string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": username,
		"email":    username + "@example.com",
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

// createRelay creates a relay via API and returns the response map.
func createRelay(t *testing.T, router http.Handler, token, gameID, name string) map[string]interface{} {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"gameId": gameID,
		"name":   name,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp
}

// inviteAndAccept invites a user to a relay and has them accept it.
func inviteAndAccept(t *testing.T, router http.Handler, ownerToken, inviteeToken, relayID, inviteeUsername string) {
	t.Helper()

	// Invite
	body, _ := json.Marshal(map[string]string{"username": inviteeUsername})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get the invite ID from the invitee's list
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/relay-invites", nil)
	req.Header.Set("Authorization", "Bearer "+inviteeToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var invites []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &invites)
	require.True(t, len(invites) > 0, "should have at least one invite")

	inviteID := invites[0]["id"].(string)

	// Accept
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/relay-invites/"+inviteID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+inviteeToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
}

// takeTurn acquires the turn and returns the turn token.
func takeTurn(t *testing.T, router http.Handler, token, relayID string) string {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp["turnToken"].(string)
}

// --- CRUD tests ---

func TestRelay_Create(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a game
	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Relay Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	resp := createRelay(t, router, token, gameID, "My Relay")

	assert.NotEmpty(t, resp["id"])
	assert.Equal(t, "My Relay", resp["name"])
	assert.Equal(t, gameID, resp["gameId"])
	assert.Equal(t, "active", resp["status"])
	assert.Equal(t, "Relay Game", resp["gameTitle"])
	assert.Equal(t, "apitest", resp["ownerUsername"])
	assert.Equal(t, float64(1), resp["memberCount"])

	// Verify members include the owner
	members := resp["members"].([]interface{})
	assert.Len(t, members, 1)
	member := members[0].(map[string]interface{})
	assert.Equal(t, "owner", member["role"])
	assert.Equal(t, "apitest", member["username"])
}

func TestRelay_Create_MissingFields(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	tests := []struct {
		name string
		body map[string]string
	}{
		{"missing gameId", map[string]string{"name": "Test"}},
		{"missing name", map[string]string{"gameId": "1"}},
		{"both missing", map[string]string{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			body, _ := json.Marshal(tt.body)
			w := httptest.NewRecorder()
			req := httptest.NewRequest("POST", "/api/relays", bytes.NewReader(body))
			req.Header.Set("Authorization", "Bearer "+token)
			req.Header.Set("Content-Type", "application/json")
			router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusBadRequest, w.Code)
		})
	}
}

func TestRelay_Create_InvalidGame(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"gameId": "99999",
		"name":   "Relay for Missing Game",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRelay_List_OnlyMyRelays(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "List Test", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	// User1 creates a relay
	createRelay(t, router, token1, gameID, "User1 Relay")

	// User2 creates a relay
	createRelay(t, router, token2, gameID, "User2 Relay")

	// User1 should only see their relay
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays", nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var relays []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relays)
	assert.Len(t, relays, 1)
	assert.Equal(t, "User1 Relay", relays[0]["name"])

	// User2 should only see their relay
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &relays)
	assert.Len(t, relays, 1)
	assert.Equal(t, "User2 Relay", relays[0]["name"])
}

func TestRelay_GetDetail_MemberCanView(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Detail Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Detail Relay")
	relayID := relay["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Detail Relay", resp["name"])
	members := resp["members"].([]interface{})
	assert.Len(t, members, 1)
}

func TestRelay_GetDetail_NonMemberForbidden(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Forbidden Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Forbidden Relay")
	relayID := relay["id"].(string)

	// User2 (non-member) tries to view
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRelay_Update_OwnerCanUpdate(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Update Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Original Name")
	relayID := relay["id"].(string)

	body, _ := json.Marshal(map[string]string{
		"name":   "Updated Name",
		"status": "completed",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/relays/"+relayID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Updated Name", resp["name"])
	assert.Equal(t, "completed", resp["status"])
}

func TestRelay_Update_MemberCannotUpdate(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Update Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Owner Relay")
	relayID := relay["id"].(string)

	// Invite and accept user2
	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// User2 tries to update
	body, _ := json.Marshal(map[string]string{"name": "Hacked"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/relays/"+relayID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token2)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRelay_Update_InvalidStatus(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Status Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Status Relay")
	relayID := relay["id"].(string)

	body, _ := json.Marshal(map[string]string{"status": "invalid_status"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/relays/"+relayID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRelay_Delete_OwnerCanDelete(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Delete Me")
	relayID := relay["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify it's gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRelay_Delete_MemberCannotDelete(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Protected Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Membership tests ---

func TestRelay_InviteUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	_ = registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Invite Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Invite Relay")
	relayID := relay["id"].(string)

	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "pending", resp["status"])
	assert.Equal(t, "player2", resp["inviteeUsername"])
	assert.Equal(t, "apitest", resp["inviterUsername"])
}

func TestRelay_InviteUser_AlreadyMember(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Already Member Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Already Member Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Try to invite again
	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestRelay_InviteUser_AlreadyInvited(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	_ = registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Already Invited Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Already Invited Relay")
	relayID := relay["id"].(string)

	// Invite once
	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Invite again (duplicate)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestRelay_InviteUser_SelfInviteFails(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Self Invite Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Self Invite Relay")
	relayID := relay["id"].(string)

	body, _ := json.Marshal(map[string]string{"username": "apitest"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRelay_AcceptInvite(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Accept Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Accept Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Verify user2 is now a member by listing relays
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var relays []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relays)
	assert.Len(t, relays, 1)
	assert.Equal(t, float64(2), relays[0]["memberCount"])
}

func TestRelay_AcceptInvite_WrongUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	_ = registerSecondUser(t, router)
	token3 := registerNamedUser(t, router, "player3")

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Wrong User Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Wrong User Relay")
	relayID := relay["id"].(string)

	// Invite player2
	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var inviteResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &inviteResp)
	inviteID := inviteResp["id"].(string)

	// Player3 tries to accept player2's invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/relay-invites/"+inviteID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+token3)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRelay_DeclineInvite(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Decline Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Decline Relay")
	relayID := relay["id"].(string)

	// Invite
	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Get invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/relay-invites", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var invites []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &invites)
	inviteID := invites[0]["id"].(string)

	// Decline
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/relay-invites/"+inviteID+"/decline", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify invite list is now empty
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/relay-invites", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &invites)
	assert.Len(t, invites, 0)
}

func TestRelay_LeaveRelay_MemberCanLeave(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Leave Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Leave Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Player2 leaves
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Player2 can no longer see the relay
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var relays []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &relays)
	assert.Len(t, relays, 0)
}

func TestRelay_LeaveRelay_OwnerCannotLeave(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Owner Leave Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Owner Leave Relay")
	relayID := relay["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRelay_RemoveMember_OwnerCanRemove(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Remove Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Remove Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Get player2's user ID
	var player2 db.User
	database.Where("username = ?", "player2").First(&player2)
	player2ID := fmt.Sprintf("%d", player2.ID)

	// Owner removes player2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/relays/"+relayID+"/members/"+player2ID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify member count
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(1), resp["memberCount"])
}

func TestRelay_RemoveMember_MemberCannotRemoveOthers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Remove Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Remove Auth Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Get owner's user ID
	var owner db.User
	database.Where("username = ?", "apitest").First(&owner)
	ownerID := fmt.Sprintf("%d", owner.ID)

	// Player2 tries to remove owner
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/relays/"+relayID+"/members/"+ownerID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Turn control tests ---

func TestRelay_TakeTurn_Success(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Turn Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Turn Relay")
	relayID := relay["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NotEmpty(t, resp["turnToken"])
	assert.NotNil(t, resp["turnTakenAt"])
}

func TestRelay_TakeTurn_Conflict(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Conflict Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Conflict Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// User1 takes turn
	takeTurn(t, router, token1, relayID)

	// User2 tries to take turn - should fail
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestRelay_TakeTurn_StaleTurnExpired(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Stale Turn Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Stale Turn Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// User1 takes turn
	takeTurn(t, router, token1, relayID)

	// Manually make the turn stale by updating the timestamp in the DB
	var user1 db.User
	database.Where("username = ?", "apitest").First(&user1)
	staleTime := time.Now().Add(-RelayTurnTimeout - time.Minute)
	database.Model(&db.Relay{}).Where("id = ?", relayID).Update("turn_taken_at", staleTime)

	// User2 should now be able to take the turn
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NotEmpty(t, resp["turnToken"])
}

func TestRelay_ReleaseTurn(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Release Turn Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Release Turn Relay")
	relayID := relay["id"].(string)

	takeTurn(t, router, token, relayID)

	// Release
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/release-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify relay no longer has active user
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Nil(t, resp["activeUserId"])
}

func TestRelay_ReleaseTurn_NonHolderFails(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Release Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Release Auth Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")
	takeTurn(t, router, token1, relayID)

	// User2 tries to release user1's turn
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/release-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRelay_Heartbeat(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Heartbeat Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Heartbeat Relay")
	relayID := relay["id"].(string)

	takeTurn(t, router, token, relayID)

	// Small delay to ensure timestamp changes
	time.Sleep(10 * time.Millisecond)

	// Heartbeat
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/heartbeat", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, relayID, resp["relayId"])
	assert.NotNil(t, resp["turnTakenAt"])
}

func TestRelay_Heartbeat_NonHolderFails(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Heartbeat Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Heartbeat Auth Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")
	takeTurn(t, router, token1, relayID)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/heartbeat", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestRelay_TakeTurn_InactiveRelayFails(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Inactive Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Inactive Relay")
	relayID := relay["id"].(string)

	// Set relay to completed
	body, _ := json.Marshal(map[string]string{"status": "completed"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/relays/"+relayID, bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Try to take turn
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/take-turn", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

// --- Save tests ---

func TestRelay_UploadSave_RequiresActiveTurn(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Save Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Save Auth Relay")
	relayID := relay["id"].(string)

	// Try to upload without holding the turn
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "test.sav")
	part.Write([]byte("save data"))
	writer.WriteField("name", "My Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRelay_UploadSave_InvalidTurnToken(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Token Auth Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Token Auth Relay")
	relayID := relay["id"].(string)

	// Take the turn
	takeTurn(t, router, token, relayID)

	// Upload with wrong turn token
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "test.sav")
	part.Write([]byte("save data"))
	writer.WriteField("name", "My Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", "wrong-token")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestRelay_UploadAndListSaves(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Upload Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Upload Save Relay")
	relayID := relay["id"].(string)

	turnToken := takeTurn(t, router, token, relayID)

	// Upload save
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "mysave.sav")
	part.Write([]byte("relay save data"))
	writer.WriteField("name", "Boss Fight Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	assert.Equal(t, "Boss Fight Save", saveResp["name"])
	assert.Equal(t, float64(15), saveResp["fileSize"])
	assert.Equal(t, false, saveResp["isAuto"])
	assert.Equal(t, "apitest", saveResp["username"])

	// List saves
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 1)
	assert.Equal(t, "Boss Fight Save", saves[0]["name"])
}

func TestRelay_DownloadSave_MemberCanDownload(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Download Relay Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Download Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// User1 takes turn and uploads save
	turnToken := takeTurn(t, router, token1, relayID)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "shared.sav")
	part.Write([]byte("shared save data"))
	writer.WriteField("name", "Shared Save")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	// User2 (member) downloads
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "shared save data", w.Body.String())
}

func TestRelay_AutoSave_Upsert(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Auto Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "Auto Save Relay")
	relayID := relay["id"].(string)

	turnToken := takeTurn(t, router, token, relayID)

	// First auto-save (creates)
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "auto.sav")
	part.Write([]byte("auto save v1"))
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves/auto", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, true, resp["isAuto"])
	assert.Equal(t, "Auto Save", resp["name"])
	firstSaveID := resp["id"].(string)

	// Second auto-save (upserts)
	buf.Reset()
	writer = multipart.NewWriter(&buf)
	part, _ = writer.CreateFormFile("save", "auto.sav")
	part.Write([]byte("auto save v2 - longer"))
	writer.Close()

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves/auto", &buf)
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, firstSaveID, resp["id"], "should update same record, not create new")
	assert.Equal(t, float64(len("auto save v2 - longer")), resp["fileSize"])

	// Get auto-save
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	assert.Equal(t, "auto save v2 - longer", w.Body.String())
}

func TestRelay_GetAutoSave_NotFound(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "No Auto Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token, gameID, "No Auto Save Relay")
	relayID := relay["id"].(string)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestRelay_DeleteSave_OwnerOnly(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Delete Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Delete Save Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Upload save
	turnToken := takeTurn(t, router, token1, relayID)

	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	part, _ := writer.CreateFormFile("save", "deleteme.sav")
	part.Write([]byte("delete me"))
	writer.WriteField("name", "Delete Me")
	writer.Close()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/saves", &buf)
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", writer.FormDataContentType())
	req.Header.Set("X-Turn-Token", turnToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var saveResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saveResp)
	saveID := saveResp["id"].(string)

	// Player2 (member, not owner) tries to delete - should fail
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/relays/"+relayID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)

	// Owner deletes - should succeed
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/relays/"+relayID+"/saves/"+saveID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify save is gone
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	assert.Len(t, saves, 0)
}

func TestRelay_Saves_NonMemberCannotAccess(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "NonMember Save Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "NonMember Relay")
	relayID := relay["id"].(string)

	// Non-member tries to list saves
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)

	// Non-member tries to get auto-save
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID+"/saves/auto", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// --- Activity event tests ---

func TestRelay_Create_CreatesActivityEvent(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Activity Relay Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	createRelay(t, router, token, gameID, "Activity Relay")

	// Check activity feed
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/social/activity", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var feedResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &feedResp)
	data := feedResp["data"].([]interface{})

	found := false
	for _, item := range data {
		event := item.(map[string]interface{})
		if event["eventType"] == "created_relay" {
			found = true
			break
		}
	}
	assert.True(t, found, "should have a created_relay activity event")
}

// --- Tests for code review bug fixes ---

func TestRelay_LeaveRelay_ReleasesTurn(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Leave Turn Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Leave Turn Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Player2 takes the turn
	takeTurn(t, router, token2, relayID)

	// Verify turn is held
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &detail)
	assert.NotNil(t, detail["activeUserId"], "turn should be held before leaving")

	// Player2 leaves while holding the turn
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the turn was released
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &detail)
	assert.Nil(t, detail["activeUserId"], "turn should be released after member leaves")

	// Owner can now take the turn (not deadlocked)
	takeTurn(t, router, token1, relayID)
}

func TestRelay_ReInviteAfterDecline(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Re-invite Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Re-invite Relay")
	relayID := relay["id"].(string)

	// Invite player2
	body, _ := json.Marshal(map[string]string{"username": "player2"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Player2 gets and declines the invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/relay-invites", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var invites []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &invites)
	require.Len(t, invites, 1)
	inviteID := invites[0]["id"].(string)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/user/relay-invites/"+inviteID+"/decline", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Re-invite player2 — should succeed (not blocked by declined invite)
	body, _ = json.Marshal(map[string]string{"username": "player2"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/relays/"+relayID+"/invites", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token1)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)

	// Player2 can now accept the new invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/relay-invites", nil)
	req.Header.Set("Authorization", "Bearer "+token2)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	json.Unmarshal(w.Body.Bytes(), &invites)
	assert.Len(t, invites, 1)
	assert.Equal(t, "pending", invites[0]["status"])
}

func TestRelay_RemoveMember_ReleasesTurn(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token1 := registerAndGetToken(t, router)
	token2 := registerSecondUser(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Remove Turn Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	relay := createRelay(t, router, token1, gameID, "Remove Turn Relay")
	relayID := relay["id"].(string)

	inviteAndAccept(t, router, token1, token2, relayID, "player2")

	// Player2 takes the turn
	takeTurn(t, router, token2, relayID)

	// Get player2's user ID
	var player2 db.User
	database.Where("username = ?", "player2").First(&player2)
	player2ID := fmt.Sprintf("%d", player2.ID)

	// Owner removes player2 who holds the turn
	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/relays/"+relayID+"/members/"+player2ID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the turn was released
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/relays/"+relayID, nil)
	req.Header.Set("Authorization", "Bearer "+token1)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var detail map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &detail)
	assert.Nil(t, detail["activeUserId"], "turn should be released after member is removed")

	// Owner can take the turn (not deadlocked)
	takeTurn(t, router, token1, relayID)
}
