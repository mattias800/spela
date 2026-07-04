package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// Issue #1320: the public achievement-showcase read must honour the same
// block + profile-visibility gate as the public profile/heatmap reads.
func TestPublicShowcase_RespectsBlock(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var caller db.User
	database.Where("username = ?", "apitest").First(&caller)

	other := db.User{Username: "showcase_blocker", PasswordHash: "hash", Role: "user"}
	database.Create(&other)
	database.Create(&db.UserAchievementShowcase{UserID: other.ID, AchievementRAID: 1, RAGameID: 1, ShowcaseOrder: 0})
	database.Create(&db.Block{UserID: other.ID, BlockedUserID: caller.ID})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/users/%d/achievements/showcase", other.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code, "blocked user must not read the showcase")
}

func TestPublicShowcase_RespectsProfileVisibility(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	other := db.User{Username: "showcase_private", PasswordHash: "hash", Role: "user", ProfileVisibility: "private"}
	database.Create(&other)
	database.Create(&db.UserAchievementShowcase{UserID: other.ID, AchievementRAID: 1, RAGameID: 1, ShowcaseOrder: 0})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/users/%d/achievements/showcase", other.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var entries []ShowcaseEntryResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &entries))
	assert.Empty(t, entries, "private profile showcase must not leak entries")
}
