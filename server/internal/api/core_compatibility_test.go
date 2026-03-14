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
	// Find NDS (should now match: desmume/desmume)
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
	assert.Equal(t, "desmume", nds.WebCore)
	assert.True(t, nds.Matched, "NDS cores should match")

	// Find PSX (should match via beetle/mednafen equivalence: beetle_psx_hw ≈ mednafen_psx_hw)
	var psx *CoreCompatibilityEntry
	// Find SAT (should NOT match: beetle_saturn vs yabause are different engines)
	var sat *CoreCompatibilityEntry
	for i := range result {
		if result[i].ConsoleID == "psx" {
			psx = &result[i]
		}
		if result[i].ConsoleID == "sat" {
			sat = &result[i]
		}
	}

	require.NotNil(t, psx, "PSX should be present")
	assert.Equal(t, "beetle_psx_hw", psx.NativeCore)
	assert.Equal(t, "mednafen_psx_hw", psx.WebCore)
	assert.True(t, psx.Matched, "PSX cores should match via beetle/mednafen equivalence")

	require.NotNil(t, sat, "SAT should be present")
	assert.Equal(t, "beetle_saturn", sat.NativeCore)
	assert.Equal(t, "yabause", sat.WebCore)
	assert.False(t, sat.Matched, "Saturn cores should not match (different engines)")
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
