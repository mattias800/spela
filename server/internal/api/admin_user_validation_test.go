package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
)

// TestAdminCreateUser_ValidationBounds verifies the huma-level validation
// tags on AdminCreateUserRequest — username/email/password length checks
// run before the handler body, returning 422 with huma's error shape.
//
// The admin endpoint does NOT enforce the public self-signup pattern
// (`^[a-zA-Z0-9]+$`) on username — admins need to be able to create
// migrated / reserved / legacy usernames that don't match the strict
// public regex. Length and password strength still apply.
func TestAdminCreateUser_ValidationBounds(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	create := func(body map[string]string) int {
		buf, _ := json.Marshal(body)
		w := httptest.NewRecorder()
		req := httptest.NewRequest("POST", "/api/admin/users", bytes.NewReader(buf))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		return w.Code
	}

	cases := []struct {
		name   string
		body   map[string]string
		expect int
	}{
		{
			name: "valid request succeeds",
			body: map[string]string{
				"username": "valid_user_01",
				"email":    "valid@example.com",
				"password": "password123",
				"role":     "user",
			},
			expect: http.StatusCreated,
		},
		{
			name: "username too short (<3)",
			body: map[string]string{
				"username": "ab",
				"email":    "x@example.com",
				"password": "password123",
				"role":     "user",
			},
			expect: http.StatusUnprocessableEntity,
		},
		{
			name: "username too long (>64)",
			body: map[string]string{
				"username": "a234567890123456789012345678901234567890123456789012345678901234X",
				"email":    "x@example.com",
				"password": "password123",
				"role":     "user",
			},
			expect: http.StatusUnprocessableEntity,
		},
		{
			name: "password too short (<8)",
			body: map[string]string{
				"username": "new_user_pw",
				"email":    "x@example.com",
				"password": "short",
				"role":     "user",
			},
			expect: http.StatusUnprocessableEntity,
		},
		{
			name: "empty email",
			body: map[string]string{
				"username": "new_user_em",
				"email":    "",
				"password": "password123",
				"role":     "user",
			},
			expect: http.StatusUnprocessableEntity,
		},
		{
			name: "admin can create username with underscores (not allowed on public signup)",
			body: map[string]string{
				"username": "migrated_user_123",
				"email":    "migrated@example.com",
				"password": "password123",
				"role":     "user",
			},
			expect: http.StatusCreated,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := create(tc.body)
			assert.Equal(t, tc.expect, got, "body=%+v", tc.body)
		})
	}
}
