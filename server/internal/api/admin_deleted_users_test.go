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
	"gorm.io/gorm"
)

func softDeleteUser(t *testing.T, database *gorm.DB, username, email string) db.User {
	t.Helper()
	user := db.User{
		Username:     username,
		Email:        email,
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	require.NoError(t, database.Delete(&user).Error)
	return user
}

func TestListDeletedUsers_ReturnsOnlySoftDeleted(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	// Create an active user
	activeUser := db.User{
		Username:     "active",
		Email:        "active@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&activeUser).Error)

	// Create and soft-delete a user
	softDeleteUser(t, database, "deleted-user", "deleted@test.com")

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []DeletedUserResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Len(t, resp, 1)
	assert.Equal(t, "deleted-user", resp[0].Username)
	assert.Equal(t, "deleted@test.com", resp[0].Email)
	assert.False(t, resp[0].DeletedAt.IsZero())
}

func TestListDeletedUsers_EmptyWhenNoneDeleted(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []DeletedUserResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp)
}

func TestListDeletedUsers_NonAdmin_Returns403(t *testing.T) {
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

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestHardDeleteUser_PermanentlyRemovesUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	deleted := softDeleteUser(t, database, "to-purge", "purge@test.com")

	// Create some child records for this user
	fav := db.Favorite{UserID: deleted.ID, GameID: 1}
	database.Create(&fav)
	ph := db.PlayHistory{UserID: deleted.ID, GameID: 1, LastPlayed: time.Now()}
	database.Create(&ph)

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(deleted.ID))+"/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	// Verify user is gone even with Unscoped
	var count int64
	database.Unscoped().Model(&db.User{}).Where("id = ?", deleted.ID).Count(&count)
	assert.Equal(t, int64(0), count)

	// Verify child records are gone
	database.Unscoped().Model(&db.Favorite{}).Where("user_id = ?", deleted.ID).Count(&count)
	assert.Equal(t, int64(0), count)
	database.Unscoped().Model(&db.PlayHistory{}).Where("user_id = ?", deleted.ID).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestHardDeleteUser_RejectsNonSoftDeletedUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	activeUser := db.User{
		Username:     "still-active",
		Email:        "active@test.com",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&activeUser).Error)

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(activeUser.ID))+"/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusBadRequest, w.Code)

	var body map[string]string
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &body))
	assert.Contains(t, body["error"], "not soft-deleted")
}

func TestHardDeleteUser_RejectsOwner(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)

	// Create the owner
	owner := db.User{
		Username:     "owner",
		Email:        "owner@test.com",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&owner).Error)

	// Create a separate admin to make the request
	admin := db.User{
		Username:     "admin2",
		Email:        "admin2@test.com",
		PasswordHash: "unused",
		Role:         "admin",
	}
	require.NoError(t, database.Create(&admin).Error)
	adminToken, err := auth.GenerateAccessToken(admin.ID, admin.Username, admin.Role, testJWTSecret)
	require.NoError(t, err)

	// Soft-delete the owner (shouldn't happen normally, but test the guard)
	database.Delete(&owner)

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(owner.ID))+"/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestHardDeleteUser_NotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	database := cfg.DB
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("DELETE", "/api/admin/users/99999/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestHardDeleteUser_NonAdmin_Returns403(t *testing.T) {
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

	deleted := softDeleteUser(t, database, "deleted-user", "deleted@test.com")

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(deleted.ID))+"/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestListDeletedUsers_IncludesDeletedAtTimestamp(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	_, adminToken := createAdminUser(t, database)

	beforeDelete := time.Now().Add(-time.Second)
	softDeleteUser(t, database, "timed-delete", "timed@test.com")

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []DeletedUserResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	require.Len(t, resp, 1)
	assert.True(t, resp[0].DeletedAt.After(beforeDelete))
}
