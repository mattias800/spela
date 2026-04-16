package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestOpenAPISpecGeneration verifies that the huma-generated OpenAPI spec
// is reachable and contains the registered operations. This is the
// foundational guarantee of the huma migration: the spec is always present,
// always derived from code, and always covers everything we register.
func TestOpenAPISpecGeneration(t *testing.T) {
	_, cfg := setupTestEnv(t)
	cfg.Version = "v0.0.1-test"
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/openapi.json", nil)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, "OpenAPI spec endpoint should return 200")

	var spec map[string]any
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &spec))

	// Spec metadata
	assert.Equal(t, "3.1.0", spec["openapi"], "expected OpenAPI 3.1 spec")
	info, ok := spec["info"].(map[string]any)
	require.True(t, ok, "spec must include info block")
	assert.Equal(t, "Spela API", info["title"])
	assert.Equal(t, "v0.0.1-test", info["version"])

	// The health endpoint should be registered
	paths, ok := spec["paths"].(map[string]any)
	require.True(t, ok, "spec must include paths block")
	healthOp, ok := paths["/api/health"].(map[string]any)
	require.True(t, ok, "/api/health must be present in spec")
	getOp, ok := healthOp["get"].(map[string]any)
	require.True(t, ok, "GET /api/health must be present")
	assert.Equal(t, "getHealth", getOp["operationId"])
}
