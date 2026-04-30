package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestNormalizeSaveStateChoice locks the closed-set sanitiser. Three
// distinct outcomes:
//
//	valid input   → (choice, false)   upsert
//	empty/blank   → ("", true)        clear the row
//	unknown value → ("", false)       no-op (preserve existing override)
//
// The empty-vs-unknown split prevents a client typo from silently
// destroying a valid override. See #804 phase 4 review feedback.
func TestNormalizeSaveStateChoice(t *testing.T) {
	cases := []struct {
		name      string
		in        string
		wantChoice db.ConsoleSaveStateChoice
		wantClear bool
	}{
		{"enabled", "enabled", db.ConsoleSaveStateChoiceEnabled, false},
		{"disabled", "disabled", db.ConsoleSaveStateChoiceDisabled, false},
		{"ask-once", "ask-once", db.ConsoleSaveStateChoiceAskOnce, false},
		{"uppercase enabled", "ENABLED", db.ConsoleSaveStateChoiceEnabled, false},
		{"mixed case", "Ask-Once", db.ConsoleSaveStateChoiceAskOnce, false},
		{"whitespace", "  disabled  ", db.ConsoleSaveStateChoiceDisabled, false},
		{"empty clears", "", "", true},
		{"whitespace only clears", "   ", "", true},
		{"unknown is no-op", "off", "", false},
		{"garbage is no-op", "asdf", "", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			choice, clear := normalizeSaveStateChoice(tc.in)
			assert.Equal(t, tc.wantChoice, choice)
			assert.Equal(t, tc.wantClear, clear)
		})
	}
}

// TestUpdatePreferences_SetConsoleSaveStatePolicy upserts a per-console
// save-state choice and verifies it round-trips via PUT and GET. This
// is the data shape the player will read in phase 4b.
func TestUpdatePreferences_SetConsoleSaveStatePolicy(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": "disabled"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies, ok := prefs["consoleSaveStatePolicies"].(map[string]interface{})
	require.True(t, ok, "consoleSaveStatePolicies missing from PUT response")
	assert.Equal(t, "disabled", policies["gc"])

	// GET round-trip — the stored row survives a fresh fetch.
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies = prefs["consoleSaveStatePolicies"].(map[string]interface{})
	assert.Equal(t, "disabled", policies["gc"])
}

// TestUpdatePreferences_ClearConsoleSaveStatePolicy verifies that
// sending an empty string removes the row so the console reverts to
// its tier-driven default. The player relies on absence-from-map to
// resolve "use the default" — without this, an opted-out user would
// be stuck on "disabled" forever once they tried to revert.
func TestUpdatePreferences_ClearConsoleSaveStatePolicy(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// First set disabled.
	body, _ := json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": "disabled"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// Clear by sending "".
	body, _ = json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": ""},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["consoleSaveStatePolicies"].(map[string]interface{})
	_, exists := policies["gc"]
	assert.False(t, exists, "policy should be cleared after sending empty string")
}

// TestUpdatePreferences_SaveStatePolicyUnknownAbbrSilentlySkipped
// mirrors the ConsoleShaders behaviour: an unknown console abbreviation
// must not 500 the request, just no-op the row. Lets the player send
// a bulk sync without enumerating the full server console list.
func TestUpdatePreferences_SaveStatePolicyUnknownAbbrSilentlySkipped(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{
			"gc":      "disabled",
			"bogosys": "enabled",
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
	policies := prefs["consoleSaveStatePolicies"].(map[string]interface{})
	assert.Equal(t, "disabled", policies["gc"])
	_, exists := policies["bogosys"]
	assert.False(t, exists, "bogus console abbreviation must not write a row")
}

// TestUpdatePreferences_SaveStatePolicyGarbageValuePreservesExistingOverride
// is the regression test for PR #817 review feedback. A typo in a
// bulk sync ("absolutely-not-a-state") must NOT destroy the user's
// existing valid override. Garbage = no-op, only an explicit empty
// string clears.
func TestUpdatePreferences_SaveStatePolicyGarbageValuePreservesExistingOverride(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// 1. User opts GameCube out.
	body, _ := json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": "disabled"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// 2. A subsequent sync sends a typo. Pre-fix this would silently
	//    delete the row.
	body, _ = json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": "absolutely-not-a-state"},
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	// 3. The original "disabled" override must still be there.
	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/preferences", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)
	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["consoleSaveStatePolicies"].(map[string]interface{})
	assert.Equal(t, "disabled", policies["gc"],
		"existing valid override must survive a typo'd sync")
}

// TestUpdatePreferences_SaveStatePolicyGarbageValueIsRejected verifies
// the sanitiser drops anything that isn't a known choice — without it
// the row would carry a value the player would have to defensively
// switch on.
func TestUpdatePreferences_SaveStatePolicyGarbageValueIsRejected(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]interface{}{
		"consoleSaveStatePolicies": map[string]string{"gc": "absolutely-not-a-state"},
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/preferences", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var prefs map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &prefs))
	policies := prefs["consoleSaveStatePolicies"].(map[string]interface{})
	_, exists := policies["gc"]
	assert.False(t, exists, "garbage value should not be persisted")
}
