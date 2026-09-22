package api

import (
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/auth"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// setupRARecheckEnv builds an admin-authenticated router with one game already
// negative-cached against RA and one with a resolved RA game ID.
func setupRARecheckEnv(t *testing.T) (http.Handler, *gorm.DB, string, db.Game, db.Game) {
	t.Helper()
	database, cfg := setupTestEnv(t)

	user := db.User{Username: "raadmin", PasswordHash: "unused", Role: "owner"}
	require.NoError(t, database.Create(&user).Error)
	token, err := auth.GenerateAccessToken(user.ID, user.Username, string(user.Role), testJWTSecret)
	require.NoError(t, err)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	cached := db.Game{ConsoleID: console.ID, Title: "No Match", FileName: "a.nes", FilePath: "roms/a.nes",
		RAHashChecked: true, RAGameID: 0}
	resolved := db.Game{ConsoleID: console.ID, Title: "Matched", FileName: "b.nes", FilePath: "roms/b.nes",
		RAHashChecked: true, RAGameID: 42}
	require.NoError(t, database.Create(&cached).Error)
	require.NoError(t, database.Create(&resolved).Error)

	router, cleanup := NewRouter(*cfg)
	t.Cleanup(cleanup)
	return router, database, token, cached, resolved
}

func triggerScrape(t *testing.T, router http.Handler, token, mode string) *httptest.ResponseRecorder {
	t.Helper()
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", fmt.Sprintf("/api/admin/scrape?mode=%s", mode), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	return w
}

// Nothing else in the codebase ever clears RAHashChecked, so this endpoint is
// the only way back from a game wrongly recorded as having no RA match.
func TestTriggerScrape_RARecheckReopensNegativeCachedGames(t *testing.T) {
	router, database, token, cached, resolved := setupRARecheckEnv(t)

	w := triggerScrape(t, router, token, "ra_recheck")
	require.Contains(t, []int{http.StatusOK, http.StatusAccepted}, w.Code, w.Body.String())

	var got db.Game
	require.NoError(t, database.First(&got, cached.ID).Error)
	assert.False(t, got.RAHashChecked, "ra_recheck must re-open a negative-cached game")

	var gotResolved db.Game
	require.NoError(t, database.First(&gotResolved, resolved.ID).Error)
	assert.True(t, gotResolved.RAHashChecked, "a resolved game must not be re-opened")
	assert.Equal(t, uint(42), gotResolved.RAGameID)
}

// Plain 'ra' must keep its existing meaning: fetch what's missing, without
// undoing the negative cache. Otherwise the existing admin button would
// silently become a full re-query of every unmatched ROM.
func TestTriggerScrape_PlainRADoesNotClearNegativeCache(t *testing.T) {
	router, database, token, cached, _ := setupRARecheckEnv(t)

	w := triggerScrape(t, router, token, "ra")
	require.Contains(t, []int{http.StatusOK, http.StatusAccepted}, w.Code, w.Body.String())

	var got db.Game
	require.NoError(t, database.First(&got, cached.ID).Error)
	assert.True(t, got.RAHashChecked, "plain 'ra' must leave the negative cache intact")
}

// The 'ra' mode joins consoles, so the collector's trailing Pluck("id") was
// ambiguous and SQLite rejected the whole query — the admin "Fetch
// Achievements" button returned 500 every time, with no test covering it.
func TestTriggerScrape_RAModeQueryIsNotAmbiguous(t *testing.T) {
	router, _, token, _, _ := setupRARecheckEnv(t)

	for _, mode := range []string{"ra", "ra_recheck", "new", "all", "fallback"} {
		t.Run(mode, func(t *testing.T) {
			w := triggerScrape(t, router, token, mode)
			assert.NotEqual(t, http.StatusInternalServerError, w.Code,
				"mode %q must not fail collecting games: %s", mode, w.Body.String())
		})
	}
}
