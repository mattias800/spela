package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// #1516: the generated placeholder domain (users.spela.invalid) is an INTERNAL
// namespace minted only by generatedRegistrationEmail for no-email accounts.
// User- and admin-supplied input must never be accepted in it, or an attacker
// could squat `<victim>@users.spela.invalid` to block victim's no-email signup,
// and publicUserEmail would render the squatter as having no email.

func TestNormalizeRegistrationEmail_RejectsReservedDomain(t *testing.T) {
	for _, email := range []string{
		"victim@users.spela.invalid",
		"victim@USERS.SPELA.INVALID", // case-insensitive
		"  victim@Users.Spela.Invalid  ",
	} {
		_, err := normalizeRegistrationEmail("attacker", email)
		assert.Error(t, err, "reserved-domain email %q must be rejected", email)
	}
}

func TestNormalizeRegistrationEmail_AcceptsAndGenerates(t *testing.T) {
	// Empty email -> we mint the placeholder; that is allowed.
	got, err := normalizeRegistrationEmail("bob", "")
	require.NoError(t, err)
	assert.Equal(t, "bob@users.spela.invalid", got)

	// A normal address passes through untouched.
	got, err = normalizeRegistrationEmail("bob", "bob@example.com")
	require.NoError(t, err)
	assert.Equal(t, "bob@example.com", got)
}

func TestPublicUserEmail_StripsReservedDomainCaseInsensitive(t *testing.T) {
	assert.Equal(t, "", publicUserEmail("bob@users.spela.invalid"))
	assert.Equal(t, "", publicUserEmail("bob@USERS.SPELA.INVALID"))
	assert.Equal(t, "real@example.com", publicUserEmail("real@example.com"))
}

func TestRegister_ReservedEmailDomainRejected(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	body, _ := json.Marshal(map[string]string{
		"username": "squatter",
		"email":    "victim@users.spela.invalid",
		"password": "SecureTestPass!2024",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnprocessableEntity, w.Code, w.Body.String())
}

func TestUpdateProfile_ReservedEmailDomainRejected(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router) // user "apitest", pw "SecureTestPass!2024"

	body, _ := json.Marshal(map[string]string{
		"email":           "victim@users.spela.invalid",
		"currentPassword": "SecureTestPass!2024",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/user/profile", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnprocessableEntity, w.Code, w.Body.String())
}

func TestAdminCreateUser_ReservedEmailDomainRejected(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	body, _ := json.Marshal(map[string]string{
		"username": "squatadmin",
		"email":    "victim@users.spela.invalid",
		"password": "SecureTestPass!2024",
		"role":     "user",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/users", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnprocessableEntity, w.Code, w.Body.String())
}

func TestAdminUpdateUser_ReservedEmailDomainRejected(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	createNonOwnerUser(t, router, ownerToken, "updatetarget", "target@example.com", "SecureTestPass!2024")
	var u db.User
	require.NoError(t, database.Where("username = ?", "updatetarget").First(&u).Error)

	body, _ := json.Marshal(map[string]string{"email": "victim@users.spela.invalid"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("PUT", "/api/admin/users/"+strconv.Itoa(int(u.ID)), bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusUnprocessableEntity, w.Code, w.Body.String())
}
