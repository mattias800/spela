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

// registerUserAndGetToken registers a user and returns the access token.
func registerUserAndGetToken(t *testing.T, router http.Handler, username, email string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": username,
		"email":    email,
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

// netplayTestCtx holds shared state for netplay tests.
type netplayTestCtx struct {
	router      http.Handler
	hostToken   string
	clientToken string
	gameID      string
}

func setupNetplayCtx(t *testing.T) netplayTestCtx {
	t.Helper()
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// NES is a supported netplay console
	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	game := db.Game{ConsoleID: nes.ID, Title: "Mega Man", FileName: "megaman.nes", FilePath: "/tmp/megaman.nes"}
	database.Create(&game)

	router := NewRouter(*cfg)
	hostToken := registerUserAndGetToken(t, router, "host", "host@test.com")
	clientToken := registerUserAndGetToken(t, router, "client", "client@test.com")

	return netplayTestCtx{
		router:      router,
		hostToken:   hostToken,
		clientToken: clientToken,
		gameID:      "1",
	}
}

func createSession(t *testing.T, ctx netplayTestCtx) NetplaySessionResponse {
	t.Helper()
	body, _ := json.Marshal(map[string]interface{}{
		"gameId": ctx.gameID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp
}

func TestNetplay_CreateSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	assert.NotEmpty(t, session.ID)
	assert.Equal(t, "waiting", session.Status)
	assert.Equal(t, 3, session.InputDelay) // default is 3 per AC
	assert.Len(t, session.InviteCode, 6)
	assert.Nil(t, session.ClientUserID)
	assert.NotEmpty(t, session.HostUsername)
	assert.Equal(t, "Mega Man", session.GameTitle)
}

func TestNetplay_CreateSession_CustomInputDelay(t *testing.T) {
	ctx := setupNetplayCtx(t)

	body, _ := json.Marshal(map[string]interface{}{
		"gameId":     ctx.gameID,
		"inputDelay": 5,
		"coreName":   "nestopia",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusCreated, w.Code)

	var resp NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, 5, resp.InputDelay)
	assert.Equal(t, "nestopia", resp.CoreName)
}

func TestNetplay_CreateSession_InvalidGame(t *testing.T) {
	ctx := setupNetplayCtx(t)

	body, _ := json.Marshal(map[string]interface{}{"gameId": "9999"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestNetplay_CreateSession_UnsupportedConsole(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	// PSX is NOT in the supported list
	var psx db.Console
	database.Where("abbreviation = ?", "PSX").First(&psx)
	if psx.ID == 0 {
		psx = db.Console{Name: "PlayStation", Abbreviation: "PSX", Extensions: ".bin"}
		database.Create(&psx)
	}
	game := db.Game{ConsoleID: psx.ID, Title: "FF7", FileName: "ff7.bin", FilePath: "/tmp/ff7.bin"}
	database.Create(&game)

	router := NewRouter(*cfg)
	token := registerUserAndGetToken(t, router, "psxhost", "psxhost@test.com")

	body, _ := json.Marshal(map[string]interface{}{"gameId": "1"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_CreateSession_MissingGameId(t *testing.T) {
	ctx := setupNetplayCtx(t)

	body, _ := json.Marshal(map[string]interface{}{})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_InviteCodeFormat(t *testing.T) {
	for i := 0; i < 20; i++ {
		code := generateInviteCode()
		assert.Len(t, code, 6)
		for _, ch := range code {
			assert.Contains(t, inviteCodeAlphabet, string(ch), "invite code should only use safe characters")
		}
	}
}

func TestNetplay_JoinByInviteCode(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{
		"inviteCode": session.InviteCode,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "in_progress", resp.Status)
	assert.NotNil(t, resp.ClientUserID)
	assert.NotEmpty(t, resp.ClientUsername)
	assert.NotNil(t, resp.StartedAt)
}

func TestNetplay_JoinByInviteCode_CaseInsensitive(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	lowered := string(bytes.ToLower([]byte(session.InviteCode)))
	body, _ := json.Marshal(map[string]interface{}{
		"inviteCode": lowered,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}

func TestNetplay_JoinByInviteCode_InvalidCode(t *testing.T) {
	ctx := setupNetplayCtx(t)

	body, _ := json.Marshal(map[string]interface{}{
		"inviteCode": "BADCOD",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestNetplay_JoinOwnSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{
		"inviteCode": session.InviteCode,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_JoinFullSession(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)

	var nes db.Console
	database.Where("abbreviation = ?", "NES").First(&nes)
	game := db.Game{ConsoleID: nes.ID, Title: "Game", FileName: "g.nes", FilePath: "/tmp/g.nes"}
	database.Create(&game)

	router := NewRouter(*cfg)
	hostToken := registerUserAndGetToken(t, router, "fullhost", "fh@t.com")
	p2Token := registerUserAndGetToken(t, router, "fullp2", "fp2@t.com")
	p3Token := registerUserAndGetToken(t, router, "fullp3", "fp3@t.com")

	// Create
	body, _ := json.Marshal(map[string]interface{}{"gameId": "1"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+hostToken)
	router.ServeHTTP(w, req)
	var session NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &session)

	// P2 joins
	body, _ = json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+p2Token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// P3 tries to join => no longer accepting (status is in_progress)
	body, _ = json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+p3Token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_GetSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, session.ID, resp.ID)
	assert.Equal(t, "waiting", resp.Status)
}

func TestNetplay_GetSession_NotFound(t *testing.T) {
	ctx := setupNetplayCtx(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions/99999", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestNetplay_ListSessions(t *testing.T) {
	ctx := setupNetplayCtx(t)
	createSession(t, ctx)

	// Host sees their session
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp PaginatedResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.True(t, resp.Total >= 1)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 20, resp.PageSize)

	// Client should also see waiting sessions
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp2 PaginatedResponse
	json.Unmarshal(w.Body.Bytes(), &resp2)
	assert.True(t, resp2.Total >= 1)
}

func TestNetplay_ListSessions_Pagination(t *testing.T) {
	ctx := setupNetplayCtx(t)

	// Create multiple sessions
	for i := 0; i < 3; i++ {
		createSession(t, ctx)
	}

	// Request page 1 with pageSize=2
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions?page=1&pageSize=2", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp PaginatedResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, int64(3), resp.Total)
	assert.Equal(t, 1, resp.Page)
	assert.Equal(t, 2, resp.PageSize)

	// The data array should have 2 items
	dataSlice, ok := resp.Data.([]interface{})
	require.True(t, ok)
	assert.Len(t, dataSlice, 2)
}

func TestNetplay_LeaveSession_Host(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Join first
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Host leaves
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "host_left", resp["endReason"])

	// Session should be ended
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	var detail NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &detail)
	assert.Equal(t, "ended", detail.Status)
	assert.Equal(t, "host_left", detail.EndReason)
	assert.NotNil(t, detail.EndedAt)
}

func TestNetplay_LeaveSession_Client(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "client_left", resp["endReason"])
}

func TestNetplay_LeaveSession_NotInSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_LeaveSession_AlreadyEnded(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_DeleteSession_HostOnly(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	var detail NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &detail)
	assert.Equal(t, "ended", detail.Status)
}

func TestNetplay_DeleteSession_NotWaiting(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("DELETE", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_UpdateSettings(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"inputDelay": 7})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var resp NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, 7, resp.InputDelay)
}

func TestNetplay_UpdateSettings_HostOnly(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"inputDelay": 5})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestNetplay_UpdateSettings_InvalidRange(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	body, _ := json.Marshal(map[string]interface{}{"inputDelay": 0})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)

	body, _ = json.Marshal(map[string]interface{}{"inputDelay": 11})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_UpdateSettings_EndedSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	body, _ := json.Marshal(map[string]interface{}{"inputDelay": 5})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_JoinEndedSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("DELETE", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code)
}

func TestNetplay_WebSocket_NotPlayer(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID+"/ws", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestNetplay_FullLifecycle(t *testing.T) {
	ctx := setupNetplayCtx(t)

	// 1. Create session
	session := createSession(t, ctx)
	assert.Equal(t, "waiting", session.Status)
	assert.Nil(t, session.ClientUserID)
	assert.Nil(t, session.StartedAt)

	// 2. Client joins => in_progress
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var joined NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &joined)
	assert.Equal(t, "in_progress", joined.Status)
	assert.NotNil(t, joined.ClientUserID)
	assert.NotNil(t, joined.StartedAt)

	// 3. Host adjusts input delay
	body, _ = json.Marshal(map[string]interface{}{"inputDelay": 4})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/netplay/sessions/"+session.ID+"/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var updated NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &updated)
	assert.Equal(t, 4, updated.InputDelay)

	// 4. Client leaves => ended
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// 5. Verify final state
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/netplay/sessions/"+session.ID, nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)

	var final NetplaySessionResponse
	json.Unmarshal(w.Body.Bytes(), &final)
	assert.Equal(t, "ended", final.Status)
	assert.Equal(t, "client_left", final.EndReason)
	assert.NotNil(t, final.EndedAt)
}
