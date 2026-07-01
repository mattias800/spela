package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
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
