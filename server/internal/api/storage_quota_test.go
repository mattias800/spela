package api

import (
	"bytes"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// checkStorageQuota must account for every on-disk artifact a user can create,
// not just session save states. Issue #1314: shared saves, shared-session
// saves, and challenge starting-saves bypassed the quota entirely because the
// SUM only covered SessionSaveState — letting any authenticated user fill the
// host disk. This test seeds one row in each user-attributable table and
// asserts they all count toward the quota.
func TestCheckStorageQuota_CountsAllArtifactTypes(t *testing.T) {
	database, _ := setupTestEnv(t)
	// 1 MB quota.
	t.Setenv("SPELA_MAX_SAVE_STORAGE_MB", "1")
	const quota = int64(1) << 20

	user := db.User{Username: "quota-user", PasswordHash: "x"}
	require.NoError(t, database.Create(&user).Error)

	game := db.Game{Title: "Game", ConsoleID: 1, FilePath: "g.rom"}
	require.NoError(t, database.Create(&game).Error)
	session := db.GameSession{OwnerID: user.ID, GameID: game.ID, Name: "sess"}
	require.NoError(t, database.Create(&session).Error)
	sharedSession := db.SharedSession{OwnerID: user.ID, GameID: game.ID, Name: "shared", Status: "active"}
	require.NoError(t, database.Create(&sharedSession).Error)

	const each = 200 * 1024 // 200 KB per artifact

	// One row in each table that attributes on-disk bytes to the user.
	require.NoError(t, database.Create(&db.SessionSaveState{UserID: user.ID, SessionID: session.ID, Name: "s", FilePath: "a", FileSize: each}).Error)
	require.NoError(t, database.Create(&db.SharedSaveState{UserID: user.ID, GameID: game.ID, Name: "s", FilePath: "b", FileSize: each}).Error)
	require.NoError(t, database.Create(&db.SharedSessionSave{UserID: user.ID, SharedSessionID: sharedSession.ID, Name: "s", FilePath: "c", FileSize: each}).Error)
	require.NoError(t, database.Create(&db.Challenge{CreatorID: user.ID, GameID: game.ID, Name: "c", SaveFilePath: "d", SaveFileSize: each}).Error)

	// Total stored is 4 * 200 KB = 800 KB. Adding 300 KB would reach
	// 1100 KB > 1 MB and must be rejected. With the pre-fix code (which
	// only summed SessionSaveState = 200 KB) this would wrongly pass.
	err := checkStorageQuota(database, user.ID, 300*1024)
	assert.Error(t, err, "quota must count shared saves, shared-session saves, and challenge saves")

	// A small addition that still fits under the 1 MB ceiling is allowed.
	require.NoError(t, checkStorageQuota(database, user.ID, 100*1024),
		"800 KB stored + 100 KB = 900 KB is under the 1 MB quota")

	_ = quota
}

// A user's quota usage must not be inflated by another user's artifacts.
func TestCheckStorageQuota_ScopedPerUser(t *testing.T) {
	database, _ := setupTestEnv(t)
	t.Setenv("SPELA_MAX_SAVE_STORAGE_MB", "1")

	u1 := db.User{Username: "u1", PasswordHash: "x"}
	u2 := db.User{Username: "u2", PasswordHash: "x"}
	require.NoError(t, database.Create(&u1).Error)
	require.NoError(t, database.Create(&u2).Error)

	game := db.Game{Title: "Game", ConsoleID: 1, FilePath: "g.rom"}
	require.NoError(t, database.Create(&game).Error)

	// u2 has nearly filled their quota; u1 has nothing.
	require.NoError(t, database.Create(&db.SharedSaveState{UserID: u2.ID, GameID: game.ID, Name: "s", FilePath: "b", FileSize: 900 * 1024}).Error)

	require.NoError(t, checkStorageQuota(database, u1.ID, 500*1024),
		"u1 must not be charged for u2's stored bytes")
	assert.Error(t, checkStorageQuota(database, u2.ID, 500*1024),
		"u2 is over quota")
}

// End-to-end enforcement: the challenge-create endpoint (which previously
// bypassed the quota entirely, #1314) must return 413 once the creator's
// stored bytes would exceed the quota.
func TestCreateChallenge_StorageQuotaEnforced(t *testing.T) {
	t.Setenv("SPELA_MAX_SAVE_STORAGE_MB", "1") // 1 MB quota
	env := setupChallengeTest(t)

	post := func(name string, saveBytes int) *httptest.ResponseRecorder {
		var buf bytes.Buffer
		writer := multipart.NewWriter(&buf)
		writer.WriteField("name", name)
		writer.WriteField("gameId", fmt.Sprintf("%d", env.gameID))
		part, _ := writer.CreateFormFile("save", "save.state")
		part.Write(make([]byte, saveBytes))
		writer.Close()

		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/challenges", &buf)
		req.Header.Set("Content-Type", writer.FormDataContentType())
		req.Header.Set("Authorization", "Bearer "+env.token)
		env.router.ServeHTTP(w, req)
		return w
	}

	// First ~700 KB save fits under the 1 MB quota.
	w1 := post("c1", 700*1024)
	require.Equal(t, http.StatusCreated, w1.Code, w1.Body.String())

	// Second ~700 KB save would push the total to ~1.4 MB → rejected.
	w2 := post("c2", 700*1024)
	require.Equal(t, http.StatusRequestEntityTooLarge, w2.Code, w2.Body.String())
}
