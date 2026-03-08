package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetCoreCompatibility_AdminOnly(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	ownerToken := registerAndGetToken(t, router)

	// Non-admin user should be rejected
	userToken := createNonOwnerUser(t, router, ownerToken, "regular", "regular@test.com", "password123")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/core-compatibility", nil)
	req.Header.Set("Authorization", "Bearer "+userToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestGetCoreCompatibility_ReturnsConsoles(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/core-compatibility", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var wrapper struct {
		Consoles []CoreCompatibilityEntry `json:"consoles"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &wrapper))
	result := wrapper.Consoles
	assert.Greater(t, len(result), 0, "should return at least one console")

	// Verify all entries have required fields
	for _, entry := range result {
		assert.NotEmpty(t, entry.ConsoleID, "consoleId should not be empty")
		assert.NotEmpty(t, entry.ConsoleName, "consoleName should not be empty")
	}
}

func TestGetCoreCompatibility_MatchedField(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/core-compatibility", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var wrapper struct {
		Consoles []CoreCompatibilityEntry `json:"consoles"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &wrapper))
	result := wrapper.Consoles

	// Find NES (should match: nestopia/nestopia)
	var nes *CoreCompatibilityEntry
	// Find NDS (should NOT match: desmume/melonds)
	var nds *CoreCompatibilityEntry
	for i := range result {
		if result[i].ConsoleID == "nes" {
			nes = &result[i]
		}
		if result[i].ConsoleID == "nds" {
			nds = &result[i]
		}
	}

	require.NotNil(t, nes, "NES should be present")
	assert.Equal(t, "nestopia", nes.NativeCore)
	assert.Equal(t, "nestopia", nes.WebCore)
	assert.True(t, nes.Matched, "NES cores should match")

	require.NotNil(t, nds, "NDS should be present")
	assert.Equal(t, "desmume", nds.NativeCore)
	assert.Equal(t, "melonds", nds.WebCore)
	assert.False(t, nds.Matched, "NDS cores should not match")
}

func TestGetCoreCompatibility_NonPlayableConsolesIncluded(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/admin/core-compatibility", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var wrapper struct {
		Consoles []CoreCompatibilityEntry `json:"consoles"`
	}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &wrapper))
	result := wrapper.Consoles

	// Non-playable consoles (e.g., PS3) should be included and show empty cores with matched=true
	var ps3 *CoreCompatibilityEntry
	for i := range result {
		if result[i].ConsoleID == "ps3" {
			ps3 = &result[i]
		}
	}
	require.NotNil(t, ps3, "PS3 should be present")
	assert.Empty(t, ps3.NativeCore)
	assert.Empty(t, ps3.WebCore)
	assert.True(t, ps3.Matched, "empty cores should match (both empty)")
}
