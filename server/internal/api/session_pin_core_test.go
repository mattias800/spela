package api

import (
	"bytes"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// autoSaveUploadBody bundles a multipart body and its content-type header for
// reuse across the pin-core tests.
type autoSaveUploadBody struct {
	reader      io.Reader
	contentType string
}

func newAutoSaveBodyWithCore(t *testing.T, coreName string, data []byte) autoSaveUploadBody {
	t.Helper()
	var buf bytes.Buffer
	writer := multipart.NewWriter(&buf)
	if coreName != "" {
		require.NoError(t, writer.WriteField("coreName", coreName))
	}
	part, err := writer.CreateFormFile("save", "autosave.sav")
	require.NoError(t, err)
	_, err = part.Write(data)
	require.NoError(t, err)
	require.NoError(t, writer.Close())
	return autoSaveUploadBody{reader: &buf, contentType: writer.FormDataContentType()}
}

// TestSessionPinCoreSha256_FirstSavePins verifies that the first save upload
// against a session pins PinnedCoreSha256 from the matching Core row and
// that subsequent saves don't overwrite it. Also checks that pinning is a
// no-op when the client doesn't supply a coreName or when the resolved Core
// row has no SHA yet.
func TestSessionPinCoreSha256_FirstSavePins(t *testing.T) {
	env := setupSessionTestEnv(t)

	// Seed the Core row with a known SHA so the pin can resolve.
	const nestopiaSha = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
	require.NoError(t, env.database.Model(&db.Core{}).
		Where("name = ?", "nestopia").
		Update("sha256", nestopiaSha).Error)

	created := createGameSession(t, env.router, env.token, env.gameID, "Pin Me")
	sessionID := created["id"].(string)

	// Before any save, the session's pin must be empty.
	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)
	var session db.GameSession
	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Empty(t, session.PinnedCoreSha256)

	// First save with a coreName that resolves — pin should land.
	w := uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "First", "nestopia", []byte("s1"))
	_ = w

	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Equal(t, nestopiaSha, session.PinnedCoreSha256,
		"first save should pin the session to the Core row's current sha256")

	// Subsequent save with a different, also-resolvable core — the pin must NOT change.
	const fceuxSha = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
	// fceux may or may not be in the seed set; we'll ensure it exists with a known SHA.
	var fceux db.Core
	if err := env.database.Where("name = ?", "fceux").First(&fceux).Error; err != nil {
		fceux = db.Core{Name: "fceux", Sha256: fceuxSha}
		require.NoError(t, env.database.Create(&fceux).Error)
	} else {
		require.NoError(t, env.database.Model(&fceux).Update("sha256", fceuxSha).Error)
	}

	_ = uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "Second", "fceux", []byte("s2"))

	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Equal(t, nestopiaSha, session.PinnedCoreSha256,
		"subsequent saves must not overwrite an existing pin")
}

// TestSessionPinCoreSha256_NoCoreNameLeavesEmpty verifies that uploading a
// save without a coreName leaves the pin empty (the pin is opportunistic —
// we wait for a save that carries enough info to resolve it).
func TestSessionPinCoreSha256_NoCoreNameLeavesEmpty(t *testing.T) {
	env := setupSessionTestEnv(t)

	const nestopiaSha = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
	require.NoError(t, env.database.Model(&db.Core{}).
		Where("name = ?", "nestopia").
		Update("sha256", nestopiaSha).Error)

	created := createGameSession(t, env.router, env.token, env.gameID, "No Core")
	sessionID := created["id"].(string)

	// Upload without coreName.
	w := uploadSessionSave(t, env.router, env.token, sessionID, "bare", []byte("data"))
	require.Equal(t, http.StatusCreated, w.Code)

	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)
	var session db.GameSession
	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Empty(t, session.PinnedCoreSha256,
		"save without coreName must not pin anything")
}

// TestSessionPinCoreSha256_EmptyCoreShaNoPin verifies that if the Core row
// exists but its Sha256 is empty (binary not yet backfilled), the session
// stays unpinned so the next save can retry after the backfill.
func TestSessionPinCoreSha256_EmptyCoreShaNoPin(t *testing.T) {
	env := setupSessionTestEnv(t)

	// Clear nestopia's sha to simulate "core not yet downloaded".
	require.NoError(t, env.database.Model(&db.Core{}).
		Where("name = ?", "nestopia").
		Update("sha256", "").Error)

	created := createGameSession(t, env.router, env.token, env.gameID, "Empty SHA")
	sessionID := created["id"].(string)

	_ = uploadSessionSaveWithCore(t, env.router, env.token, sessionID, "one", "nestopia", []byte("d"))

	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)
	var session db.GameSession
	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Empty(t, session.PinnedCoreSha256,
		"pin must stay empty until a Core row with a populated sha256 exists")
}

// TestSessionPinCoreSha256_AutoSavePins verifies that auto-saves pin the SHA
// when the manual save path hasn't run yet.
func TestSessionPinCoreSha256_AutoSavePins(t *testing.T) {
	env := setupSessionTestEnv(t)

	const nestopiaSha = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
	require.NoError(t, env.database.Model(&db.Core{}).
		Where("name = ?", "nestopia").
		Update("sha256", nestopiaSha).Error)

	created := createGameSession(t, env.router, env.token, env.gameID, "Auto Pin")
	sessionID := created["id"].(string)

	// Auto-save upload mirrors manual save's coreName field, so use a helper
	// that wires it up via the multipart form.
	body := newAutoSaveBodyWithCore(t, "nestopia", []byte("auto"))
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/sessions/"+sessionID+"/saves/auto", body.reader)
	req.Header.Set("Authorization", "Bearer "+env.token)
	req.Header.Set("Content-Type", body.contentType)
	env.router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, "auto save upload failed: %s", w.Body.String())

	sidU, err := strconv.ParseUint(sessionID, 10, 64)
	require.NoError(t, err)
	var session db.GameSession
	require.NoError(t, env.database.First(&session, sidU).Error)
	assert.Equal(t, nestopiaSha, session.PinnedCoreSha256,
		"auto-save should pin the SHA just like a manual save")
}
