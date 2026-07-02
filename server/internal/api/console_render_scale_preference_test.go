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

func TestNormalizeRenderScale(t *testing.T) {
	cases := []struct {
		name      string
		in        string
		wantScale string
		wantClear bool
	}{
		{"2x", "2x", "2x", false},
		{"3x", "3x", "3x", false},
		{"4x", "4x", "4x", false},
		{"uppercase", "3X", "3x", false},
		{"whitespace", "  4x  ", "4x", false},
		{"empty clears", "", "", true},
		{"whitespace only clears", "   ", "", true},
		{"native clears", "native", "", true},
		{"1x clears", "1x", "", true},
		{"unknown is no-op", "5x", "", false},
		{"garbage is no-op", "asdf", "", false},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			scale, clear := normalizeRenderScale(tc.in)
			assert.Equal(t, tc.wantScale, scale)
			assert.Equal(t, tc.wantClear, clear)
		})
	}
}

func TestUpdatePreferences_SetConsoleRenderScale(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	prefs := putConsoleRenderScales(t, router, token, map[string]string{"nes": "2x"})
	scales, ok := prefs["consoleRenderScales"].(map[string]interface{})
	require.True(t, ok, "consoleRenderScales missing from PUT response")
	assert.Equal(t, "2x", scales["nes"])

	prefs = getPreferences(t, router, token)
	scales = prefs["consoleRenderScales"].(map[string]interface{})
	assert.Equal(t, "2x", scales["nes"])
}

func TestUpdatePreferences_UpdateConsoleRenderScale(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	putConsoleRenderScales(t, router, token, map[string]string{"nes": "2x"})
	prefs := putConsoleRenderScales(t, router, token, map[string]string{"nes": "4x"})

	scales := prefs["consoleRenderScales"].(map[string]interface{})
	assert.Equal(t, "4x", scales["nes"])
}

func TestUpdatePreferences_ClearConsoleRenderScale(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	putConsoleRenderScales(t, router, token, map[string]string{"nes": "3x"})
	prefs := putConsoleRenderScales(t, router, token, map[string]string{"nes": "native"})

	scales := consoleRenderScalesFromPrefs(prefs)
	_, exists := scales["nes"]
	assert.False(t, exists, "render scale should be cleared after sending native")
}

func TestUpdatePreferences_RenderScaleGarbageValuePreservesExistingOverride(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	putConsoleRenderScales(t, router, token, map[string]string{"nes": "3x"})
	putConsoleRenderScales(t, router, token, map[string]string{"nes": "5x"})

	prefs := getPreferences(t, router, token)
	scales := prefs["consoleRenderScales"].(map[string]interface{})
	assert.Equal(t, "3x", scales["nes"], "existing valid override must survive an invalid sync value")
}

func TestUpdatePreferences_RenderScaleUnknownAbbrSilentlySkipped(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	prefs := putConsoleRenderScales(t, router, token, map[string]string{
		"nes":     "2x",
		"bogosys": "4x",
	})

	scales := prefs["consoleRenderScales"].(map[string]interface{})
	assert.Equal(t, "2x", scales["nes"])
	_, exists := scales["bogosys"]
	assert.False(t, exists, "bogus console abbreviation must not write a row")
}

func putConsoleRenderScales(t *testing.T, router http.Handler, token string, scales map[string]string) map[string]interface{} {
	t.Helper()

	body, _ := json.Marshal(map[string]interface{}{
		"consoleRenderScales": scales,
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

func consoleRenderScalesFromPrefs(prefs map[string]interface{}) map[string]interface{} {
	scales, _ := prefs["consoleRenderScales"].(map[string]interface{})
	if scales == nil {
		return map[string]interface{}{}
	}
	return scales
}

func getPreferences(t *testing.T, router http.Handler, token string) map[string]interface{} {
	t.Helper()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	return prefs
}
