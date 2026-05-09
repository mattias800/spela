package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestBlockRoutes_BasicLifecycle covers issue #1121: a user can block
// another, the block list reflects the relationship, the blocked user no
// longer appears in search results, and the block can be removed.
func TestBlockRoutes_BasicLifecycle(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	tokenA := registerAndGetToken(t, router)

	// Create a second user directly via the DB (bypassing register, which
	// uses fixed credentials in registerAndGetToken).
	target := db.User{Username: "harasser", Email: "harasser@example.com", PasswordHash: "x"}
	require.NoError(t, database.Create(&target).Error)
	targetID := fmt.Sprintf("%d", target.ID)

	// Search returns the target before any block.
	t.Run("visible before block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=harasser", nil)
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code, w.Body.String())
		assert.Contains(t, w.Body.String(), "harasser")
	})

	// POST a block.
	t.Run("create block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/user/blocks/"+targetID, bytes.NewReader([]byte{}))
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusCreated, w.Code, w.Body.String())
	})

	// GET the block list.
	t.Run("list reflects block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/user/blocks", nil)
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code, w.Body.String())
		var resp ListBlocksResponse
		require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
		assert.Len(t, resp.Blocked, 1)
		assert.Equal(t, "harasser", resp.Blocked[0].Username)
	})

	// Search no longer returns the blocked user.
	t.Run("hidden after block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/search?q=harasser", nil)
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code, w.Body.String())
		assert.NotContains(t, w.Body.String(), "harasser")
	})

	// Profile lookup returns 404 instead of leaking existence.
	t.Run("profile 404 after block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("GET", "/api/users/"+targetID+"/profile", nil)
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		assert.Equal(t, http.StatusNotFound, w.Code, w.Body.String())
	})

	// DELETE removes the block.
	t.Run("delete block", func(t *testing.T) {
		w := httptest.NewRecorder()
		req := httptest.NewRequest("DELETE", "/api/user/blocks/"+targetID, nil)
		req.Header.Set("Authorization", "Bearer "+tokenA)
		router.ServeHTTP(w, req)
		require.Equal(t, http.StatusOK, w.Code, w.Body.String())
	})
}
