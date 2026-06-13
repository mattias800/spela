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

// Issue #1319: with no registration_enabled setting configured, self-service
// registration is closed by default. The first user (owner) is still created,
// but a second registration is rejected until the owner opts in.
func TestRegistration_ClosedByDefault(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Remove the harness's convenience seed so we exercise the true default.
	require.NoError(t, database.Where("key = ?", "registration_enabled").Delete(&db.ServerSetting{}).Error)

	register := func(username, email string) *httptest.ResponseRecorder {
		body, _ := json.Marshal(map[string]string{
			"username": username,
			"email":    email,
			"password": "SecureTestPass!2024",
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		return w
	}

	// First user becomes owner regardless of the flag.
	require.Equal(t, http.StatusCreated, register("owner", "owner@example.com").Code)

	// Second registration is denied because registration is closed by default.
	w := register("intruder", "intruder@example.com")
	assert.Equal(t, http.StatusForbidden, w.Code, w.Body.String())
}

// When the owner enables registration, subsequent registrations are accepted
// (held pending approval).
func TestRegistration_EnabledAllowsSecondUser(t *testing.T) {
	database, cfg := setupTestEnv(t) // seeds registration_enabled=true
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	register := func(username, email string) *httptest.ResponseRecorder {
		body, _ := json.Marshal(map[string]string{
			"username": username,
			"email":    email,
			"password": "SecureTestPass!2024",
		})
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		router.ServeHTTP(w, req)
		return w
	}

	require.Equal(t, http.StatusCreated, register("owner", "owner@example.com").Code)
	// Non-owner registration is accepted (202, pending approval) when enabled.
	w := register("member", "member@example.com")
	assert.Equal(t, http.StatusAccepted, w.Code, w.Body.String())
	_ = database
}
