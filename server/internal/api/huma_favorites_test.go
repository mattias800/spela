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

// Re-favoriting a game that was previously favorited then unfavorited must
// succeed and reappear in the list. Regression for the soft-delete ghost row:
// remove soft-deleted the Favorite, and the unique index (user_id, game_id)
// then blocked the re-add with a 409 while the list/isFavorite excluded it (#1541).
func TestReFavoriteAfterUnfavorite(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Refav Game", FileName: "t.nes", FilePath: "/tmp/t.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	do := func(method string) int {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(method, "/api/user/favorites/"+gameID, nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		return w.Code
	}

	assert.Equal(t, http.StatusCreated, do("POST"), "initial favorite")
	assert.Equal(t, http.StatusOK, do("DELETE"), "unfavorite")
	assert.Equal(t, http.StatusCreated, do("POST"), "re-favoriting a previously-unfavorited game must succeed, not 409")

	// It must reappear in the favorites list.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/favorites", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	var games []map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &games)
	assert.Equal(t, 1, len(games), "re-favorited game must appear in the list")
}

// Favoriting a genuinely already-favorited game still conflicts (the add
// endpoint is not a toggle).
func TestFavoriteAlreadyActiveConflicts(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)
	game := db.Game{ConsoleID: console.ID, Title: "Active Fav", FileName: "t.nes", FilePath: "/tmp/t.nes", FileSize: 100}
	database.Create(&game)
	gameID := fmt.Sprintf("%d", game.ID)

	do := func(method string) int {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(method, "/api/user/favorites/"+gameID, nil)
		req.Header.Set("Authorization", "Bearer "+token)
		router.ServeHTTP(w, req)
		return w.Code
	}

	assert.Equal(t, http.StatusCreated, do("POST"), "initial favorite")
	assert.Equal(t, http.StatusConflict, do("POST"), "re-adding an active favorite is a 409")
}

func TestListFavoritesIncludesTitlePlatforms(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	snes := mustFindConsole(t, database, "SNES")
	gba := mustFindConsole(t, database, "GBA")
	rootID := uint(8642)
	selected := db.Game{ConsoleID: snes.ID, Title: "Platform Favorite", FileName: "fav.sfc", FilePath: "/tmp/fav.sfc", FileSize: 100, TitleRootIGDBID: &rootID}
	sibling := db.Game{ConsoleID: gba.ID, Title: "Platform Favorite Advance", FileName: "fav.gba", FilePath: "/tmp/fav.gba", FileSize: 100, TitleRootIGDBID: &rootID}
	require.NoError(t, database.Create(&selected).Error)
	require.NoError(t, database.Create(&sibling).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/user/favorites/"+fmt.Sprintf("%d", selected.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	w = httptest.NewRecorder()
	req = httptest.NewRequest("GET", "/api/user/favorites", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var games []GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &games))
	require.Len(t, games, 1)
	require.Len(t, games[0].Platforms, 2)
	assert.Equal(t, []string{"snes", "gba"}, platformConsoleIDs(games[0].Platforms))
	assert.Equal(t, []string{strconvID(selected.ID), strconvID(sibling.ID)}, platformGameIDs(games[0].Platforms))
	assert.True(t, games[0].Platforms[0].IsPreferred)
}
