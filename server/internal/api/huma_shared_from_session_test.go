package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// #885 — POST /api/shared-sessions with `sourceSessionId` set seeds
// the new shared session with a copy of that local session's most
// recent save state. Validation rules: caller must own the source
// session, the source must belong to the same game, and a missing
// source session is a 404.

func TestCreateSharedSession_FromSession_CopiesSaveState(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Find the user we just registered + a console + a game.
	var owner db.User
	require.NoError(t, database.Where("username = ?", "apitest").First(&owner).Error)
	var console db.Console
	require.NoError(t, database.First(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Source Game", FileName: "x.nes", FilePath: "/tmp/x.nes", FileSize: 100, IsPrimary: true}
	require.NoError(t, database.Create(&game).Error)

	// Create a source session + a save state on disk.
	src := db.GameSession{OwnerID: owner.ID, GameID: game.ID, Name: "My Run"}
	require.NoError(t, database.Create(&src).Error)

	saveBytes := []byte("source-save-bytes-payload")
	srcPath := cfg.Storage.SaveStatePath(owner.ID, game.ID, "src.state")
	require.NoError(t, os.MkdirAll(filepath.Dir(srcPath), 0700))
	require.NoError(t, os.WriteFile(srcPath, saveBytes, 0600))
	srcSave := db.SessionSaveState{
		SessionID: src.ID, UserID: owner.ID, Name: "checkpoint",
		FilePath: srcPath, FileSize: int64(len(saveBytes)),
		IsCurrent: true, CoreName: "nestopia",
	}
	require.NoError(t, database.Create(&srcSave).Error)

	// Create the shared session with sourceSessionId.
	body, _ := json.Marshal(map[string]interface{}{
		"name":            "From My Run",
		"gameId":          fmt.Sprintf("%d", game.ID),
		"sourceSessionId": src.ID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, w.Body.String())

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	ssID := resp["id"].(string)

	// The new shared session has its own GameSession; that GameSession
	// must own a SessionSaveState whose bytes equal the source's.
	var ss db.SharedSession
	require.NoError(t, database.Where("id = ?", ssID).First(&ss).Error)
	require.NotNil(t, ss.SessionID)

	var copied db.SessionSaveState
	require.NoError(t, database.Where("session_id = ?", *ss.SessionID).First(&copied).Error)
	copiedBytes, err := os.ReadFile(copied.FilePath)
	require.NoError(t, err)
	assert.Equal(t, saveBytes, copiedBytes, "copied bytes must equal source")
	assert.Equal(t, srcSave.CoreName, copied.CoreName)
	assert.True(t, copied.IsCurrent)
	assert.NotEqual(t, srcSave.FilePath, copied.FilePath, "must be a real copy on a separate path")
}

func TestCreateSharedSession_FromSession_RejectsCrossGame(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var owner db.User
	require.NoError(t, database.Where("username = ?", "apitest").First(&owner).Error)
	var console db.Console
	require.NoError(t, database.First(&console).Error)
	gameA := db.Game{ConsoleID: console.ID, Title: "Game A", FileName: "a.nes", FilePath: "/tmp/a.nes", FileSize: 1, IsPrimary: true}
	require.NoError(t, database.Create(&gameA).Error)
	gameB := db.Game{ConsoleID: console.ID, Title: "Game B", FileName: "b.nes", FilePath: "/tmp/b.nes", FileSize: 1, IsPrimary: true}
	require.NoError(t, database.Create(&gameB).Error)

	srcForA := db.GameSession{OwnerID: owner.ID, GameID: gameA.ID, Name: "A run"}
	require.NoError(t, database.Create(&srcForA).Error)

	// Try to create a shared session for game B seeded from a
	// session that belongs to game A — must be 400.
	body, _ := json.Marshal(map[string]interface{}{
		"name":            "Confused",
		"gameId":          fmt.Sprintf("%d", gameB.ID),
		"sourceSessionId": srcForA.ID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusBadRequest, w.Code, w.Body.String())

	// Roll-back: no shared session row should exist.
	var count int64
	database.Model(&db.SharedSession{}).Count(&count)
	assert.Equal(t, int64(0), count, "shared session must not be created when validation fails")
}

func TestCreateSharedSession_FromSession_RejectsCrossUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Two users.
	tokenA := registerAndGetToken(t, router)
	bodyB, _ := json.Marshal(map[string]string{
		"username": "otheruser", "email": "other@example.com", "password": "password123",
	})
	wB := httptest.NewRecorder()
	reqB := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(bodyB))
	reqB.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(wB, reqB)
	// Register returns 201 on a fresh server, 202 when the user
	// already exists from a prior test run sharing state. Either is
	// fine here — the test only needs the user row to exist.
	require.Contains(t, []int{http.StatusCreated, http.StatusAccepted}, wB.Code, wB.Body.String())

	var userA db.User
	require.NoError(t, database.Where("username = ?", "apitest").First(&userA).Error)
	var userB db.User
	require.NoError(t, database.Where("username = ?", "otheruser").First(&userB).Error)

	var console db.Console
	require.NoError(t, database.First(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Shared Game", FileName: "g.nes", FilePath: "/tmp/g.nes", FileSize: 1, IsPrimary: true}
	require.NoError(t, database.Create(&game).Error)

	// User B's session.
	srcB := db.GameSession{OwnerID: userB.ID, GameID: game.ID, Name: "B's run"}
	require.NoError(t, database.Create(&srcB).Error)

	// User A tries to share user B's session — must 403.
	body, _ := json.Marshal(map[string]interface{}{
		"name":            "Sneaky",
		"gameId":          fmt.Sprintf("%d", game.ID),
		"sourceSessionId": srcB.ID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+tokenA)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code, w.Body.String())
}

func TestCreateSharedSession_FromSession_MissingSourceIs404(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	require.NoError(t, database.First(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Lonely Game", FileName: "l.nes", FilePath: "/tmp/l.nes", FileSize: 1, IsPrimary: true}
	require.NoError(t, database.Create(&game).Error)

	body, _ := json.Marshal(map[string]interface{}{
		"name":            "Doomed",
		"gameId":          fmt.Sprintf("%d", game.ID),
		"sourceSessionId": uint(99999),
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code, w.Body.String())
}

func TestCreateSharedSession_FromSession_NoSavesIsOK(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var owner db.User
	require.NoError(t, database.Where("username = ?", "apitest").First(&owner).Error)
	var console db.Console
	require.NoError(t, database.First(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Empty Run Game", FileName: "e.nes", FilePath: "/tmp/e.nes", FileSize: 1, IsPrimary: true}
	require.NoError(t, database.Create(&game).Error)

	// Source session with NO save state.
	src := db.GameSession{OwnerID: owner.ID, GameID: game.ID, Name: "Empty run"}
	require.NoError(t, database.Create(&src).Error)

	body, _ := json.Marshal(map[string]interface{}{
		"name":            "Fresh Start",
		"gameId":          fmt.Sprintf("%d", game.ID),
		"sourceSessionId": src.ID,
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/shared-sessions", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, w.Body.String())

	// Shared session is created; the new GameSession just has no
	// save state copied — same end state as creating a shared
	// session without sourceSessionId at all.
	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	ssID := resp["id"].(string)
	var ss db.SharedSession
	require.NoError(t, database.Where("id = ?", ssID).First(&ss).Error)
	require.NotNil(t, ss.SessionID)

	var saveCount int64
	database.Model(&db.SessionSaveState{}).Where("session_id = ?", *ss.SessionID).Count(&saveCount)
	assert.Equal(t, int64(0), saveCount)
}
