package api

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestServeFrontend_RejectsTraversal verifies issue #1118: the SPA
// fallback file server containment-checks resolved paths against
// frontendDir and refuses anything that escapes, even if the request
// path normalised through filepath.Clean wouldn't strip the traversal
// (relative `..` segments are preserved by Clean).
func TestServeFrontend_RejectsTraversal(t *testing.T) {
	tmp := t.TempDir()
	frontend := filepath.Join(tmp, "dist")
	require.NoError(t, os.MkdirAll(frontend, 0o755))

	require.NoError(t, os.WriteFile(filepath.Join(frontend, "index.html"), []byte("<html/>"), 0o644))

	// Plant a "secret" outside the dist root.
	secret := filepath.Join(tmp, "secret.txt")
	require.NoError(t, os.WriteFile(secret, []byte("PWNED"), 0o644))

	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.NoRoute(serveFrontend(frontend))

	cases := []string{
		"/../secret.txt",
		"/../../secret.txt",
		"/foo/../../secret.txt",
	}
	for _, p := range cases {
		t.Run(p, func(t *testing.T) {
			w := httptest.NewRecorder()
			req := httptest.NewRequest(http.MethodGet, p, nil)
			r.ServeHTTP(w, req)

			body := w.Body.String()
			assert.NotContains(t, body, "PWNED",
				"path %s leaked secret content; status=%d body=%s", p, w.Code, body)
		})
	}

	t.Run("legitimate file still served", func(t *testing.T) {
		require.NoError(t, os.WriteFile(filepath.Join(frontend, "robots.txt"), []byte("User-agent: *\n"), 0o644))
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/robots.txt", nil)
		r.ServeHTTP(w, req)
		assert.Equal(t, http.StatusOK, w.Code)
		assert.Contains(t, w.Body.String(), "User-agent")
	})
}
