package api

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestPathParamRejectsSQLInjection verifies that user-reachable handlers which
// previously passed `path:"id"` strings directly to GORM's First() — and
// therefore inherited GORM's expression-fallback for non-numeric strings
// (CVE-class SQLi, issue #1115) — now reject any non-numeric ID at the API
// edge with 4xx, never reaching the DB layer.
func TestPathParamRejectsSQLInjection(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	token := registerAndGetToken(t, router)

	// Each of these paths used to allow GORM's string-WHERE expression branch.
	cases := []struct {
		name string
		path string
	}{
		{"public profile", "/api/users/" + url.PathEscape("1 OR 1=1") + "/profile"},
		{"add favorite", "/api/user/favorites/" + url.PathEscape("1 OR 1=1")},
		{"remove favorite", "/api/user/favorites/" + url.PathEscape("1 OR 1=1")},
		{"series detail", "/api/series/" + url.PathEscape("1 OR 1=1")},
		{"franchise detail", "/api/franchises/" + url.PathEscape("1 OR 1=1")},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			method := http.MethodGet
			if tc.name == "add favorite" {
				method = http.MethodPost
			} else if tc.name == "remove favorite" {
				method = http.MethodDelete
			}
			w := httptest.NewRecorder()
			req := httptest.NewRequest(method, tc.path, nil)
			req.Header.Set("Authorization", "Bearer "+token)
			router.ServeHTTP(w, req)
			// Pattern validation in huma rejects with 422 (Unprocessable
			// Entity); some routes may also surface 400 if the handler
			// itself parses. Either way, never 200.
			assert.True(t,
				w.Code == http.StatusBadRequest ||
					w.Code == http.StatusUnprocessableEntity,
				"%s: expected 400/422, got %d body=%s",
				tc.name, w.Code, w.Body.String())
		})
	}
}
