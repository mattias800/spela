package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/require"
)

// createNonOwnerUser creates a user via the admin endpoint (which bypasses pending approval)
// and returns their access token by logging in. ownerToken must be a valid admin/owner token.
func createNonOwnerUser(t *testing.T, router http.Handler, ownerToken, username, password string) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": username,
		"password": password,
		"role":     "user",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/users", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code, "createNonOwnerUser: admin create failed: %s", w.Body.String())

	loginBody, _ := json.Marshal(map[string]string{
		"username": username,
		"password": password,
	})
	w = httptest.NewRecorder()
	req = httptest.NewRequest("POST", "/api/auth/login", bytes.NewReader(loginBody))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code, "createNonOwnerUser: login failed: %s", w.Body.String())

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	return resp["accessToken"].(string)
}
