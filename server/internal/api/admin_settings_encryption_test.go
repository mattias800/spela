package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// Issue #1318: secret server settings must be encrypted at rest, returned
// masked from the GET endpoint, and transparently decrypted when read back
// for use.
func TestAdminSettings_SecretsEncryptedAtRest(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	const secret = "sgdb-super-secret-key-123"
	body, _ := json.Marshal(map[string]string{"steamgriddb_api_key": secret})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/admin/settings", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, w.Body.String())

	// Stored ciphertext must not contain the plaintext and must carry the
	// "enc:" marker.
	var row db.ServerSetting
	require.NoError(t, database.Where("key = ?", "steamgriddb_api_key").First(&row).Error)
	assert.NotEqual(t, secret, row.Value, "secret must not be stored in plaintext")
	assert.True(t, strings.HasPrefix(row.Value, "enc:"), "stored value should be encrypted, got %q", row.Value)
	assert.NotContains(t, row.Value, secret)

	// Reading it back for use must return the original plaintext.
	assert.Equal(t, secret, steamGridDBAPIKey(database), "decrypted read must round-trip")

	// The GET endpoint must mask it, never returning the real or encrypted value.
	gw := httptest.NewRecorder()
	greq := httptest.NewRequest("GET", "/api/admin/settings", nil)
	greq.Header.Set("Authorization", "Bearer "+adminToken)
	router.ServeHTTP(gw, greq)
	require.Equal(t, http.StatusOK, gw.Code)
	var settings map[string]string
	require.NoError(t, json.Unmarshal(gw.Body.Bytes(), &settings))
	assert.Equal(t, secretMaskPlaceholder, settings["steamgriddb_api_key"])
	assert.NotContains(t, gw.Body.String(), secret)
}

// Legacy plaintext values (written before #1318) must still be readable.
func TestAdminSettings_LegacyPlaintextStillReadable(t *testing.T) {
	database, cfg := setupTestEnv(t)
	_, cleanup := NewRouter(*cfg)
	defer cleanup()

	require.NoError(t, database.Create(&db.ServerSetting{Key: "steamgriddb_api_key", Value: "legacy-plain"}).Error)
	assert.Equal(t, "legacy-plain", steamGridDBAPIKey(database),
		"non-enc-prefixed legacy values must pass through unchanged")
}
