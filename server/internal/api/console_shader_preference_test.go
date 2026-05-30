package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestUpdatePreferences_ConsoleShaderResetAfterClear reproduces #1219:
// selecting a per-console shader, clearing it to "none" (a soft-delete),
// then selecting a shader again must persist. Before the fix the
// re-select hit the (user_id, console_id) unique index against the
// soft-deleted row, the Create failed silently, and the shader "reset"
// when the user left and returned to the console settings screen.
func TestUpdatePreferences_ConsoleShaderResetAfterClear(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	putShader := func(shader string) map[string]interface{} {
		body, _ := json.Marshal(map[string]interface{}{
			"consoleShaders": map[string]string{"nes": shader},
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
		req.Header.Set("Authorization", "Bearer "+token)
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code)
		var prefs map[string]interface{}
		require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
		return prefs
	}

	putShader("crt-simple")
	putShader("none") // clears (soft-delete)
	prefs := putShader("lcd-grid")

	shaders, ok := prefs["consoleShaders"].(map[string]interface{})
	require.True(t, ok, "consoleShaders missing from PUT response")
	assert.Equal(t, "lcd-grid", shaders["nes"], "re-selected shader must persist after a clear")

	// And it survives a fresh GET (the "leave and return" the user saw reset).
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var prefs2 map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs2))
	shaders = prefs2["consoleShaders"].(map[string]interface{})
	assert.Equal(t, "lcd-grid", shaders["nes"], "re-selected shader must survive a GET after a clear")
}
