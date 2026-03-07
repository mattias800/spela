package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// dialNetplayWS upgrades a connection to the netplay WebSocket endpoint.
// Returns the websocket connection or fails the test.
func dialNetplayWS(t *testing.T, server *httptest.Server, sessionID, token string) *websocket.Conn {
	t.Helper()
	wsURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/api/netplay/sessions/" + sessionID + "/ws"
	header := http.Header{}
	header.Set("Authorization", "Bearer "+token)
	conn, resp, err := websocket.DefaultDialer.Dial(wsURL, header)
	if err != nil {
		if resp != nil {
			t.Fatalf("WebSocket dial failed with status %d: %v", resp.StatusCode, err)
		}
		t.Fatalf("WebSocket dial failed: %v", err)
	}
	return conn
}

// readJSONMessage reads a JSON message from the WS connection with a timeout.
func readJSONMessage(t *testing.T, conn *websocket.Conn, timeout time.Duration) map[string]interface{} {
	t.Helper()
	conn.SetReadDeadline(time.Now().Add(timeout))
	_, data, err := conn.ReadMessage()
	require.NoError(t, err, "expected to read a JSON message")
	var msg map[string]interface{}
	require.NoError(t, json.Unmarshal(data, &msg))
	return msg
}

func TestNetplay_WebSocket_PeerJoined(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Client joins the session via REST
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Start a real HTTP server for WebSocket upgrade
	server := httptest.NewServer(ctx.router)
	defer server.Close()

	// Host connects first
	hostConn := dialNetplayWS(t, server, session.ID, ctx.hostToken)
	defer hostConn.Close()

	// Client connects — host should receive peer_joined
	clientConn := dialNetplayWS(t, server, session.ID, ctx.clientToken)
	defer clientConn.Close()

	msg := readJSONMessage(t, hostConn, 3*time.Second)
	assert.Equal(t, "peer_joined", msg["type"])
	assert.NotZero(t, msg["senderId"])
}

func TestNetplay_WebSocket_BinaryInputRelay(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Client joins via REST
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	server := httptest.NewServer(ctx.router)
	defer server.Close()

	hostConn := dialNetplayWS(t, server, session.ID, ctx.hostToken)
	defer hostConn.Close()

	clientConn := dialNetplayWS(t, server, session.ID, ctx.clientToken)
	defer clientConn.Close()

	// Drain the peer_joined message that the host receives
	readJSONMessage(t, hostConn, 3*time.Second)

	// Host sends a binary input frame
	inputFrame := []byte{0x01, 0x02, 0x03, 0x04, 0xFF}
	err := hostConn.WriteMessage(websocket.BinaryMessage, inputFrame)
	require.NoError(t, err)

	// Client should receive the binary frame
	clientConn.SetReadDeadline(time.Now().Add(3 * time.Second))
	msgType, data, err := clientConn.ReadMessage()
	require.NoError(t, err)
	assert.Equal(t, websocket.BinaryMessage, msgType)
	assert.Equal(t, inputFrame, data)

	// Client sends binary back to host
	replyFrame := []byte{0xAA, 0xBB}
	err = clientConn.WriteMessage(websocket.BinaryMessage, replyFrame)
	require.NoError(t, err)

	hostConn.SetReadDeadline(time.Now().Add(3 * time.Second))
	msgType, data, err = hostConn.ReadMessage()
	require.NoError(t, err)
	assert.Equal(t, websocket.BinaryMessage, msgType)
	assert.Equal(t, replyFrame, data)
}

func TestNetplay_WebSocket_PeerLeft(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Client joins via REST
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	server := httptest.NewServer(ctx.router)
	defer server.Close()

	hostConn := dialNetplayWS(t, server, session.ID, ctx.hostToken)
	defer hostConn.Close()

	clientConn := dialNetplayWS(t, server, session.ID, ctx.clientToken)

	// Drain peer_joined
	readJSONMessage(t, hostConn, 3*time.Second)

	// Client disconnects
	clientConn.Close()

	// Host should receive peer_left
	msg := readJSONMessage(t, hostConn, 3*time.Second)
	assert.Equal(t, "peer_left", msg["type"])
	assert.NotZero(t, msg["senderId"])
}

func TestNetplay_WebSocket_NonParticipant403(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)
	// Note: clientToken user has NOT joined the session via REST

	server := httptest.NewServer(ctx.router)
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/api/netplay/sessions/" + session.ID + "/ws"
	header := http.Header{}
	header.Set("Authorization", "Bearer "+ctx.clientToken)
	_, resp, err := websocket.DefaultDialer.Dial(wsURL, header)
	require.Error(t, err, "expected WS dial to fail for non-participant")
	require.NotNil(t, resp)
	assert.Equal(t, http.StatusForbidden, resp.StatusCode)
}

func TestNetplay_WebSocket_JSONTextRelay(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Client joins via REST
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	server := httptest.NewServer(ctx.router)
	defer server.Close()

	hostConn := dialNetplayWS(t, server, session.ID, ctx.hostToken)
	defer hostConn.Close()

	clientConn := dialNetplayWS(t, server, session.ID, ctx.clientToken)
	defer clientConn.Close()

	// Drain peer_joined
	readJSONMessage(t, hostConn, 3*time.Second)

	// Host sends a chat-type JSON text message (broadcast to all except sender)
	chatMsg := map[string]interface{}{
		"type": "chat",
		"data": map[string]string{"text": "hello"},
	}
	chatBytes, _ := json.Marshal(chatMsg)
	err := hostConn.WriteMessage(websocket.TextMessage, chatBytes)
	require.NoError(t, err)

	// Client should receive the relayed message with senderId
	msg := readJSONMessage(t, clientConn, 3*time.Second)
	assert.Equal(t, "chat", msg["type"])
	assert.NotZero(t, msg["senderId"])
}

func TestNetplay_WebSocket_EndedSession(t *testing.T) {
	ctx := setupNetplayCtx(t)
	session := createSession(t, ctx)

	// Client joins then host ends the session
	body, _ := json.Marshal(map[string]interface{}{"inviteCode": session.InviteCode})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/netplay/sessions/join", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ctx.clientToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Host leaves to end the session
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/netplay/sessions/"+session.ID+"/leave", nil)
	req.Header.Set("Authorization", "Bearer "+ctx.hostToken)
	ctx.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Trying to connect WS to an ended session should fail
	server := httptest.NewServer(ctx.router)
	defer server.Close()

	wsURL := "ws" + strings.TrimPrefix(server.URL, "http") + "/api/netplay/sessions/" + session.ID + "/ws"
	header := http.Header{}
	header.Set("Authorization", "Bearer "+ctx.hostToken)
	_, resp, err := websocket.DefaultDialer.Dial(wsURL, header)
	require.Error(t, err)
	require.NotNil(t, resp)
	assert.Equal(t, http.StatusBadRequest, resp.StatusCode)
}
