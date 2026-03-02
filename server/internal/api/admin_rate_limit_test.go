package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetUserRateLimit_NoAttempts(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	req := httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp RateLimitResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.FailedCount)
	assert.Nil(t, resp.LockedUntil)
	assert.False(t, resp.IsLockedOut)
}

func TestGetUserRateLimit_WithFailedAttempts(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Create a login attempt record with 3 failed attempts (below lockout threshold)
	hashed := hashUsername(user.Username)
	attempt := db.LoginAttempt{
		Username:    hashed,
		FailedCount: 3,
	}
	require.NoError(t, database.Create(&attempt).Error)

	req := httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp RateLimitResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 3, resp.FailedCount)
	assert.False(t, resp.IsLockedOut)
}

func TestGetUserRateLimit_LockedOut(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Create a locked-out login attempt record
	hashed := hashUsername(user.Username)
	lockedUntil := time.Now().Add(15 * time.Minute)
	attempt := db.LoginAttempt{
		Username:    hashed,
		FailedCount: 5,
		LockedUntil: lockedUntil,
	}
	require.NoError(t, database.Create(&attempt).Error)

	req := httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp RateLimitResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 5, resp.FailedCount)
	assert.NotNil(t, resp.LockedUntil)
	assert.True(t, resp.IsLockedOut)
}

func TestGetUserRateLimit_ExpiredLockout(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Create a login attempt with expired lockout
	hashed := hashUsername(user.Username)
	attempt := db.LoginAttempt{
		Username:    hashed,
		FailedCount: 5,
		LockedUntil: time.Now().Add(-1 * time.Minute), // expired
	}
	require.NoError(t, database.Create(&attempt).Error)

	req := httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp RateLimitResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 5, resp.FailedCount)
	assert.False(t, resp.IsLockedOut)
}

func TestGetUserRateLimit_UserNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	database := cfg.DB
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/users/99999/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestGetUserRateLimit_NonAdmin_Returns403(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	user := db.User{
		Username:     "regular",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	req := httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestResetUserRateLimit_ClearsAttempts(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Create a locked-out login attempt record
	hashed := hashUsername(user.Username)
	attempt := db.LoginAttempt{
		Username:    hashed,
		FailedCount: 5,
		LockedUntil: time.Now().Add(15 * time.Minute),
	}
	require.NoError(t, database.Create(&attempt).Error)

	// Reset the rate limit
	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify the rate limit is cleared
	req = httptest.NewRequest("GET", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w = httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp RateLimitResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, 0, resp.FailedCount)
	assert.False(t, resp.IsLockedOut)
}

func TestResetUserRateLimit_UserNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	database := cfg.DB
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("DELETE", "/api/admin/users/99999/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestResetUserRateLimit_NonAdmin_Returns403(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	user := db.User{
		Username:     "regular",
		Email:        "regular@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, user.Role, testJWTSecret)
	require.NoError(t, err)

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestResetUserRateLimit_NoExistingAttempts(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	user := db.User{
		Username:     "testuser",
		Email:        "test@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Reset even when no attempts exist should succeed
	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(user.ID))+"/rate-limit", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
}
