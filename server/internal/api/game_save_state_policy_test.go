package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestUpdatePreferences_SetGameSaveStatePolicy upserts a per-game
// override and verifies it round-trips via PUT and GET.
func TestUpdatePreferences_SetGameSaveStatePolicy(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Seed a GameCube game so we have a valid game ID to reference.
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Title: "Metroid Prime",
		FileName: "mp.iso", FilePath: "/tmp/mp.iso", FileSize: 1024, IsPrimary: true,
	}
	require.NoError(t, database.Create(&game).Error)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	body, _ := json.Marshal(map[string]interface{}{
		"gameSaveStatePolicies": map[string]string{gameIDStr: "disabled"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies, ok := prefs["gameSaveStatePolicies"].(map[string]interface{})
	require.True(t, ok, "gameSaveStatePolicies missing from PUT response")
	assert.Equal(t, "disabled", policies[gameIDStr])

	// GET round-trip.
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies = prefs["gameSaveStatePolicies"].(map[string]interface{})
	assert.Equal(t, "disabled", policies[gameIDStr])
}

// TestUpdatePreferences_ClearGameSaveStatePolicy verifies that an
// empty-string value clears the row so the game falls back to the
// per-console policy (and ultimately to the tier default).
func TestUpdatePreferences_ClearGameSaveStatePolicy(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Title: "Mario Sunshine",
		FileName: "ms.iso", FilePath: "/tmp/ms.iso", FileSize: 1024, IsPrimary: true,
	}
	require.NoError(t, database.Create(&game).Error)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// Set then clear.
	for _, raw := range []string{"enabled", ""} {
		body, _ := json.Marshal(map[string]interface{}{
			"gameSaveStatePolicies": map[string]string{gameIDStr: raw},
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)
	}

	// After clearing, the key must be absent.
	var prefs map[string]interface{}
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["gameSaveStatePolicies"].(map[string]interface{})
	_, exists := policies[gameIDStr]
	assert.False(t, exists, "policy should be cleared after sending empty string")
}

// TestUpdatePreferences_GameSaveStatePolicyUnknownGameSilentlySkipped
// matches the per-console behaviour: unknown game IDs don't crash
// the request, the row just isn't written.
func TestUpdatePreferences_GameSaveStatePolicyUnknownGameSilentlySkipped(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"gameSaveStatePolicies": map[string]string{
			"99999": "disabled", // No such game.
			"abc":   "enabled",  // Not even numeric.
		},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["gameSaveStatePolicies"].(map[string]interface{})
	assert.Empty(t, policies, "unknown game IDs must not write rows")
}

// TestUpdatePreferences_GameSaveStatePolicyGarbageValuePreservesOverride
// is the regression test mirroring the per-console one — a typo
// must not destroy an existing valid override.
func TestUpdatePreferences_GameSaveStatePolicyGarbageValuePreservesOverride(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "GC").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Title: "Pikmin",
		FileName: "pk.iso", FilePath: "/tmp/pk.iso", FileSize: 1024, IsPrimary: true,
	}
	require.NoError(t, database.Create(&game).Error)
	gameIDStr := strconv.FormatUint(uint64(game.ID), 10)

	// 1. User sets "enabled".
	body, _ := json.Marshal(map[string]interface{}{
		"gameSaveStatePolicies": map[string]string{gameIDStr: "enabled"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// 2. Subsequent sync sends a typo. Must not destroy the row.
	body, _ = json.Marshal(map[string]interface{}{
		"gameSaveStatePolicies": map[string]string{gameIDStr: "absolutely-not-a-state"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// 3. Original "enabled" override must still be there.
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["gameSaveStatePolicies"].(map[string]interface{})
	assert.Equal(t, "enabled", policies[gameIDStr])
}
