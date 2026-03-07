package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNetplayInvite_SendInvite(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code)

	var resp NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.NotEmpty(t, resp.ID)
	assert.Equal(t, session.ID, resp.NetplaySessionID)
	assert.Equal(t, "pending", resp.Status)
	assert.Equal(t, "client", resp.InviteeUsername)
	assert.Equal(t, "Mega Man", resp.GameTitle)
	assert.NotEmpty(t, resp.HostUsername)
}

func TestNetplayInvite_SendInvite_NotHost(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "host"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestNetplayInvite_SendInvite_CannotInviteSelf(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "host"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplayInvite_SendInvite_DuplicatePending(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})

	// First invite
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Duplicate invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusConflict, w.Code)
}

func TestNetplayInvite_SendInvite_UserNotFound(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "nonexistent"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestNetplayInvite_SendInvite_NotWaiting(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// End the session first
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplayInvite_ListMyInvites(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Client lists their invites
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var listResp struct {
		Data  []NetplayInviteResponse `json:"data"`
		Total int                     `json:"total"`
	}
	json.Unmarshal(w.Body.Bytes(), &listResp)
	assert.Equal(t, 1, listResp.Total)
	assert.Len(t, listResp.Data, 1)
	assert.Equal(t, "pending", listResp.Data[0].Status)
	assert.Equal(t, "Mega Man", listResp.Data[0].GameTitle)
}

func TestNetplayInvite_PendingCount(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// No invites yet
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)
	var countResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(0), countResp["count"])

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Now count should be 1
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(1), countResp["count"])
}

func TestNetplayInvite_AcceptInvite(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)

	// Client accepts
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "accepted", resp.Status)
}

func TestNetplayInvite_AcceptInvite_NotInvitee(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)

	// Host tries to accept (wrong user)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestNetplayInvite_DeclineInvite(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)

	// Client declines
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/decline", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Invite should no longer appear in pending list
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	var listResp struct {
		Data  []NetplayInviteResponse `json:"data"`
		Total int                     `json:"total"`
	}
	json.Unmarshal(w.Body.Bytes(), &listResp)
	assert.Equal(t, 0, listResp.Total)
	assert.Len(t, listResp.Data, 0)
}

func TestNetplayInvite_DeclineAndReinvite(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"username": "client"})

	// Send first invite
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)

	// Client declines
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/decline", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Host can send a new invite (old declined one is cleaned up)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusCreated, w.Code)
}

func TestNetplayInvite_ListSessionInvites(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Host lists session invites (AC-3.1)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID+"/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var invites []NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invites)
	assert.Len(t, invites, 1)
}

func TestNetplayInvite_ListSessionInvites_NotHost(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID+"/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestNetplayInvite_ExpireOnJoin(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite to client
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Someone joins via invite code (the invite should expire)
	body, _ = json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Client's pending invite count should be 0
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	var countResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(0), countResp["count"])
}

func TestNetplayInvite_ExpireOnDelete(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Host cancels session
	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Client's pending invite count should be 0 (AC-5.2, AC-5.3)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	var countResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(0), countResp["count"])
}

func TestNetplayInvite_AcceptExpireOthers(t *testing.T) {
	ctx := setupNetplayCtx(t)

	// Need a third user to have two pending invites
	session := createSession(t, ctx)

	// We need the owner token from setupNetplayCtx - let's use a fresh setup
	// Create a third user
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	game := db.Game{ConsoleID: nes.ID, Title: "Mega Man", FileName: "megaman.nes", FilePath: "/tmp/megaman.nes"}
	database.Create(&game)

	router := NewRouter(*cfg)
	ownerToken := registerAndGetToken(t, router)
	hostToken := registerUserAndGetToken(t, router, ownerToken, "host2", "host2@test.com")
	client1Token := registerUserAndGetToken(t, router, ownerToken, "player1", "p1@test.com")
	client2Token := registerUserAndGetToken(t, router, ownerToken, "player2", "p2@test.com")

	// Create session
	body, _ := json.Marshal(map[string]interface{}{"gameId": "1"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+hostToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	json.Unmarshal(w.Body.Bytes(), &session)

	// Invite player1
	body, _ = json.Marshal(map[string]interface{}{"username": "player1"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+hostToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite1 NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite1)

	// Invite player2
	body, _ = json.Marshal(map[string]interface{}{"username": "player2"})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+hostToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Player1 accepts
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite1.ID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+client1Token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Player2's invite should be expired now (AC-5.1 via accept)
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+client2Token)
	router.ServeHTTP(w, req)
	var countResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(0), countResp["count"])
}

func TestNetplayInvite_AcceptEndedSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Send invite
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)
	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)

	// Host ends the session
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Client tries to accept the invite — should fail because session ended
	// Note: the invite was already expired by the leave handler, so status != pending
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplayInvite_MultipleInvitesDifferentSessions(t *testing.T) {
	ctx := setupNetplayCtx(t)

	// Create two sessions
	session1 := createSession(t, ctx)
	session2 := createSession(t, ctx)

	// Send invite from both sessions
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session1.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session2.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	// Client should see 2 pending invites
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	var countResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp)
	assert.Equal(t, float64(2), countResp["count"])
}

// TestNetplayInvite_FullFlow exercises the complete invite lifecycle:
// create session → send invite → list invites → accept → verify session state.
func TestNetplayInvite_FullFlow(t *testing.T) {
	ctx := setupNetplayCtx(t)

	// 1. Host creates a session
	session := createSession(t, ctx)
	assert.Equal(t, "waiting", session.Status)
	assert.Nil(t, session.ClientUserID)

	// 2. Host sends invite to client
	body, _ := json.Marshal(map[string]interface{}{"username": "client"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/invites", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var invite NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &invite)
	assert.Equal(t, "pending", invite.Status)
	assert.Equal(t, session.ID, invite.NetplaySessionID)

	// 3. Client sees 1 pending invite in their invite count
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var countResp2 map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countResp2)
	assert.Equal(t, float64(1), countResp2["count"])

	// 4. Client lists their invites and sees the invite with game info
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var listResp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &listResp)
	invites := listResp["data"].([]interface{})
	assert.Len(t, invites, 1)
	firstInvite := invites[0].(map[string]interface{})
	assert.Equal(t, "Mega Man", firstInvite["gameTitle"])
	assert.Equal(t, "pending", firstInvite["status"])

	// 5. Client accepts the invite
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/invites/"+invite.ID+"/accept", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// 6. Client's pending count should now be 0
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/invites/count", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	var countAfter map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &countAfter)
	assert.Equal(t, float64(0), countAfter["count"])

	// 7. Host views session invites — invite should be "accepted"
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID+"/invites", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var sessionInvites []NetplayInviteResponse
	json.Unmarshal(w.Body.Bytes(), &sessionInvites)
	require.Len(t, sessionInvites, 1)
	assert.Equal(t, "accepted", sessionInvites[0].Status)
}
