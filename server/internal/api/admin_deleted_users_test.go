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

func softDeleteUser(t *testing.T, database *gorm.DB, username string) db.User {
	t.Helper()
	user := db.User{
		Username:     username,
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	require.NoError(t, database.Delete(&user).Error)
	return user
}

func TestListDeletedUsers_ReturnsOnlySoftDeleted(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	// Create an active user
	activeUser := db.User{
		Username:     "active",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&activeUser).Error)

	// Create and soft-delete a user
	softDeleteUser(t, database, "deleted-user")

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp []DeletedUserResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))

	assert.Len(t, resp, 1)
	assert.Equal(t, "deleted-user", resp[0].Username)
	assert.False(t, resp[0].DeletedAt.IsZero())
}

func TestListDeletedUsers_EmptyWhenNoneDeleted(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	user := db.User{
		Username:     "regular",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	req := httptest.NewRequest("GET", "/api/admin/users/deleted", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestHardDeleteUser_PermanentlyRemovesUser(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	deleted := softDeleteUser(t, database, "to-purge")

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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	activeUser := db.User{
		Username:     "still-active",
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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Create the owner
	owner := db.User{
		Username:     "owner",
		PasswordHash: "unused",
		Role:         "owner",
	}
	require.NoError(t, database.Create(&owner).Error)

	// Create a separate admin to make the request
	admin := db.User{
		Username:     "admin2",
		PasswordHash: "unused",
		Role:         "admin",
	}
	require.NoError(t, database.Create(&admin).Error)
	adminToken, err := auth.GenerateAccessToken(admin.ID, admin.Username, string(admin.Role), testJWTSecret)
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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	req := httptest.NewRequest("DELETE", "/api/admin/users/99999/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+adminToken)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

// TestUserFKCascadeOnRawDelete verifies that #971's CASCADE FK constraints
// fire at the SQL level on a fresh install, independent of the explicit
// HumaHardDeleteUser cleanup. The test sidesteps the handler entirely:
// it inserts child rows, calls a raw DELETE on the user, and checks the
// children are gone. This proves new installs are safe even via paths
// that don't route through the handler (e.g. an admin running a manual
// SQL delete, or a future endpoint that forgets the explicit cleanup).
func TestUserFKCascadeOnRawDelete(t *testing.T) {
	_, cfg := setupTestEnv(t)
	database := cfg.DB

	user := db.User{
		Username:     "cascade-target",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)

	// Need a Console + Game to satisfy GameID FKs on Favorite / PlayHistory.
	console := db.Console{Name: "TestConsole", Abbreviation: "TC", FolderName: "tc"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{Title: "TestGame", ConsoleID: console.ID, FilePath: "tc/test.rom"}
	require.NoError(t, database.Create(&game).Error)

	// Insert a sample of child rows from CASCADE-tagged tables.
	require.NoError(t, database.Create(&db.Favorite{UserID: user.ID, GameID: game.ID}).Error)
	require.NoError(t, database.Create(&db.PlayHistory{UserID: user.ID, GameID: game.ID, LastPlayed: time.Now()}).Error)
	require.NoError(t, database.Create(&db.SavedSearch{UserID: user.ID, Name: "x", Filters: "{}"}).Error)
	require.NoError(t, database.Create(&db.DailyPlayActivity{UserID: user.ID, Date: time.Now(), PlayTime: 60}).Error)

	// Hard DELETE bypassing soft-delete (Unscoped) — exercises the FK
	// constraint, not the handler's explicit child cleanup.
	require.NoError(t, database.Unscoped().Delete(&user).Error)

	var count int64
	database.Unscoped().Model(&db.Favorite{}).Where("user_id = ?", user.ID).Count(&count)
	assert.Equal(t, int64(0), count, "Favorite should cascade-delete on user delete")
	database.Unscoped().Model(&db.PlayHistory{}).Where("user_id = ?", user.ID).Count(&count)
	assert.Equal(t, int64(0), count, "PlayHistory should cascade-delete on user delete")
	database.Unscoped().Model(&db.SavedSearch{}).Where("user_id = ?", user.ID).Count(&count)
	assert.Equal(t, int64(0), count, "SavedSearch should cascade-delete on user delete")
	database.Unscoped().Model(&db.DailyPlayActivity{}).Where("user_id = ?", user.ID).Count(&count)
	assert.Equal(t, int64(0), count, "DailyPlayActivity should cascade-delete on user delete")
}

func TestHardDeleteUser_NonAdmin_Returns403(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	user := db.User{
		Username:     "regular",
		PasswordHash: "unused",
		Role:         "user",
	}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	deleted := softDeleteUser(t, database, "deleted-user")

	req := httptest.NewRequest("DELETE", "/api/admin/users/"+strconv.Itoa(int(deleted.ID))+"/permanent", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestListDeletedUsers_IncludesDeletedAtTimestamp(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	_, adminToken := createAdminUser(t, database)

	beforeDelete := time.Now().Add(-time.Second)
	softDeleteUser(t, database, "timed-delete")

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
