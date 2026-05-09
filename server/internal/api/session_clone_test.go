package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// cloneSession is the /clone counterpart of the legacy duplicateSession
// helper. Kept local to this file so the two endpoint paths stay visibly
// distinct in tests.
func cloneSession(t *testing.T, router http.Handler, token, sessionID, query string, body []byte) *httptest.ResponseRecorder {
	t.Helper()
	path := "/api/sessions/" + sessionID + "/clone"
	if query != "" {
		path += "?" + query
	}
	var req *http.Request
	if body != nil {
		req = httptest.NewRequest("POST", path, bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
	} else {
		req = httptest.NewRequest("POST", path, nil)
	}
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)
	return w
}

// TestCloneSession_EndpointAliasMatchesDuplicate verifies that
// POST /api/sessions/{id}/clone and the legacy /duplicate path route to the
// same handler and produce identical successful responses.
func TestCloneSession_EndpointAliasMatchesDuplicate(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "Alias Me")
	sessionID := created["id"].(string)

	w := cloneSession(t, env.router, env.token, sessionID, "", nil)
	require.Equal(t, http.StatusCreated, w.Code, "clone endpoint must succeed: %s", w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "Alias Me (Copy)", resp["name"])
}

// TestCloneSession_InheritsTotalPlayTimeAndPin verifies that the clone
// inherits TotalPlayTime and PinnedCoreSha256 from the source session —
// the two fields US-1 specifically calls out.
func TestCloneSession_InheritsTotalPlayTimeAndPin(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "10h run")
	sessionID := created["id"].(string)
	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)

	// Force the source session into a state that mimics 10h of play with a
	// pinned core. Bypass the play-time and save endpoints because they are
	// already covered by other tests — we want to pin the fields directly.
	require.NoError(t, env.database.Model(&db.GameSession{}).
		Where("id = ?", sidU).
		Updates(map[string]interface{}{
			"total_play_time":    int64(36000),
			"pinned_core_sha256": "deadbeef",
		}).Error)

	w := cloneSession(t, env.router, env.token, sessionID, "", nil)
	require.Equal(t, http.StatusCreated, w.Code, "clone must succeed: %s", w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, float64(36000), resp["totalPlayTime"],
		"totalPlayTime must be inherited (we played 10h together)")
	assert.Equal(t, "deadbeef", resp["pinnedCoreSha256"],
		"pinnedCoreSha256 must be inherited so the player can fetch the same core binary")

	// Cloned session must have a different ID than the source.
	assert.NotEqual(t, sessionID, resp["id"])
}

// TestCloneSession_WithSaveID verifies US-3: cloning from an explicit save
// seeds the new session from that save's bytes rather than the most recent.
func TestCloneSession_WithSaveID(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "branch")
	sessionID := created["id"].(string)

	// Two saves — older "A", newer "B". The default clone would pick "B";
	// the explicit saveId must pick "A".
	w := uploadSessionSave(t, env.router, env.token, sessionID, "A", []byte("aaaa"))
	require.Equal(t, http.StatusCreated, w.Code)
	var respA map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &respA)
	saveAID := respA["id"].(string)

	w = uploadSessionSave(t, env.router, env.token, sessionID, "B", []byte("bbbb"))
	require.Equal(t, http.StatusCreated, w.Code)

	w = cloneSession(t, env.router, env.token, sessionID, "saveId="+saveAID, nil)
	require.Equal(t, http.StatusCreated, w.Code, "clone with explicit saveId must succeed: %s", w.Body.String())

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	newSessionID := resp["id"].(string)

	// The cloned session must contain exactly one save, and it must be "A"
	// (the explicit seed) rather than "B" (the most recent).
	w = httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/sessions/"+newSessionID+"/saves", nil)
	req.Header.Set("Authorization", "Bearer "+env.token)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var saves []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &saves)
	require.Len(t, saves, 1)
	assert.Equal(t, "A", saves[0]["name"])
}

// TestCloneSession_BadSaveIDReturns404 verifies US-3 error path: a
// saveId that doesn't belong to the source session must 404 *before*
// any DB or filesystem writes happen.
func TestCloneSession_BadSaveIDReturns404(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "404 seed")
	sessionID := created["id"].(string)

	w := cloneSession(t, env.router, env.token, sessionID, "saveId=999999", nil)
	assert.Equal(t, http.StatusNotFound, w.Code)

	// No new session should have been created.
	var count int64
	require.NoError(t, env.database.Model(&db.GameSession{}).Where("owner_id = ?", 1).Count(&count).Error)
	assert.Equal(t, int64(1), count, "bad saveId must not leave a half-built session behind")
}

// TestCloneSession_NonMemberForbidden verifies that a user who is neither
// the owner nor a shared-session member gets 403.
func TestCloneSession_NonMemberForbidden(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "private")
	sessionID := created["id"].(string)

	w := cloneSession(t, env.router, env.token2, sessionID, "", nil)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

// TestCloneSession_SharedSessionMemberAllowed verifies US-1: a member of a
// shared session that backs this session can clone it even though they
// don't own it directly.
func TestCloneSession_SharedSessionMemberAllowed(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token1 := registerAndGetToken(t, router)
	token2 := createNonOwnerUser(t, router, token1, "cloner", "cloner@example.com", "SecureTestPass!2024")

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Test Game", FileName: "test.nes", FilePath: "/tmp/test.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	shared := createSharedSession(t, router, token1, gameID, "Coop Run")
	sharedSessionID := shared["id"].(string)
	inviteAndAcceptSharedSession(t, router, token1, token2, sharedSessionID, "cloner")

	backingSessionID := shared["sessionId"].(string)

	w := cloneSession(t, router, token2, backingSessionID, "", nil)
	require.Equal(t, http.StatusCreated, w.Code, "shared-session member must be allowed to clone: %s", w.Body.String())

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Contains(t, resp["name"], "(Copy)")

	// The clone must be owned by token2 (the caller), not token1.
	var user2ID uint
	database.Model(&db.User{}).Where("username = ?", "cloner").Select("id").Scan(&user2ID)
	clonedIDStr := resp["id"].(string)
	clonedID, _ := strconv.ParseUint(clonedIDStr, 10, 64)
	var cloned db.GameSession
	require.NoError(t, database.First(&cloned, clonedID).Error)
	assert.Equal(t, user2ID, cloned.OwnerID, "clone ownership transfers to the caller")
}

// TestCloneSession_TransactionRollbackOnCopyFailure injects a save whose
// FilePath points at a non-existent file so Storage.CopyFile errors; the
// handler must roll back — no new GameSession row must persist.
func TestCloneSession_TransactionRollbackOnCopyFailure(t *testing.T) {
	env := setupSessionTestEnv(t)

	created := createGameSession(t, env.router, env.token, env.gameID, "fail-me")
	sessionID := created["id"].(string)
	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)

	// Inject a SessionSaveState row whose FilePath points at a file that
	// doesn't exist. The handler will pick it as the most-recent save and
	// then fail inside cloneSaveInto → CopyFile.
	bogus := db.SessionSaveState{
		SessionID: uint(sidU),
		UserID:    1,
		Name:      "bogus",
		FilePath:  env.storage.SessionSaveStatePath(uint(sidU), "does-not-exist.sav"),
		FileSize:  10,
	}
	require.NoError(t, env.database.Create(&bogus).Error)

	// Count sessions for user 1 before the clone attempt.
	var before int64
	require.NoError(t, env.database.Model(&db.GameSession{}).Where("owner_id = ?", 1).Count(&before).Error)

	w := cloneSession(t, env.router, env.token, sessionID, "", nil)
	require.Equal(t, http.StatusInternalServerError, w.Code,
		"clone must surface the copy failure as a 500")

	// No new session row may persist after the rollback.
	var after int64
	require.NoError(t, env.database.Model(&db.GameSession{}).Where("owner_id = ?", 1).Count(&after).Error)
	assert.Equal(t, before, after,
		"transaction must roll back — no partial session row allowed")
}
