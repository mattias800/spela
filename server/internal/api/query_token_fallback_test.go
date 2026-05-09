package api

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestQueryTokenFallback_DeniedOnJSONRoutes verifies that issue #1117 is
// fixed: the `?token=<jwt>` fallback no longer applies to JSON-API routes
// (only to file-download / WS / asset routes where browsers genuinely can
// not set Authorization headers). Authentication via query string on any
// JSON endpoint must return 401 even when the token is otherwise valid.
func TestQueryTokenFallback_DeniedOnJSONRoutes(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token := registerAndGetToken(t, router)

	// JSON endpoints that previously honored ?token= and must not now.
	jsonPaths := []string{
		"/api/user/profile",
		"/api/user/preferences",
		"/api/social/activity",
		"/api/admin/users",
	}

	for _, p := range jsonPaths {
		t.Run("denies "+p, func(t *testing.T) {
			w := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, p+"?token="+token, nil)
			router.ServeHTTP(w, req)
			assert.Equal(t, http.StatusUnauthorized, w.Code,
				"%s with ?token= should be 401 (query fallback restricted), got %d body=%s",
				p, w.Code, w.Body.String())
		})
	}

	// Header still works on those same endpoints.
	t.Run("header path still works", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/user/profile", nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code, "body=%s", w.Body.String())
	})
}

// TestQueryTokenFallback_AllowedOnDownloadRoutes verifies the fallback
// still works on the small allowlist of routes that legitimately need it
// (here: BIOS asset routes; they may 404/403 depending on test fixture
// state, but they must NOT be 401 — meaning auth via query did succeed).
func TestQueryTokenFallback_AllowedOnDownloadRoutes(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/bios/nonexistent.bin?token="+token, nil)
	router.ServeHTTP(w, req)

	assert.NotEqual(t, http.StatusUnauthorized, w.Code,
		"BIOS download via ?token= should still authenticate (got 401, body=%s)", w.Body.String())
}
