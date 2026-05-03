package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetPlayStats_ReturnsPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game1 := db.Game{ConsoleID: console.ID, Title: "Game A", FileName: "a.nes", FilePath: "/tmp/a.nes", FileSize: 100}
	game2 := db.Game{ConsoleID: console.ID, Title: "Game B", FileName: "b.nes", FilePath: "/tmp/b.nes", FileSize: 100}
	database.Create(&game1)
	database.Create(&game2)

	var user db.User
	database.First(&user)

	now := time.Now().UTC()
	database.Create(&db.PlayHistory{
		UserID: user.ID, GameID: game1.ID,
		PlayTime: 3600, LastPlayed: now.Add(-1 * time.Hour),
	})
	database.Create(&db.PlayHistory{
		UserID: user.ID, GameID: game2.ID,
		PlayTime: 120, LastPlayed: now.Add(-24 * time.Hour),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []PlayStatsEntry
	err := json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Len(t, result, 2)

	statsMap := make(map[string]PlayStatsEntry)
	for _, s := range result {
		statsMap[s.GameID] = s
	}

	game1Key := strconv.FormatUint(uint64(game1.ID), 10)
	game2Key := strconv.FormatUint(uint64(game2.ID), 10)
	assert.Equal(t, int64(3600), statsMap[game1Key].PlayTime)
	assert.Equal(t, int64(120), statsMap[game2Key].PlayTime)
	assert.NotEmpty(t, statsMap[game1Key].LastPlayedAt)
	assert.NotEmpty(t, statsMap[game2Key].LastPlayedAt)
}

func TestGetPlayStats_EmptyWhenNoHistory(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []PlayStatsEntry
	err := json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Empty(t, result)
}

func TestGetPlayStats_ExcludesOtherUsers(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	ownerToken := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game := db.Game{ConsoleID: console.ID, Title: "Shared Game", FileName: "s.nes", FilePath: "/tmp/s.nes", FileSize: 100}
	database.Create(&game)

	// Create play history for the owner (user ID 1)
	var owner db.User
	database.First(&owner)
	database.Create(&db.PlayHistory{
		UserID: owner.ID, GameID: game.ID,
		PlayTime: 5000, LastPlayed: time.Now().UTC(),
	})

	// Create play history for a different user ID directly
	database.Create(&db.PlayHistory{
		UserID: 9999, GameID: game.ID,
		PlayTime: 999, LastPlayed: time.Now().UTC(),
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-stats", nil)
	req.Header.Set("Authorization", "Bearer "+ownerToken)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var result []PlayStatsEntry
	json.Unmarshal(w.Body.Bytes(), &result)
	require.Len(t, result, 1, "should only return current user's play history")
	assert.Equal(t, int64(5000), result[0].PlayTime)
}

func TestGetPlayStats_SkipsZeroPlayTime(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	var console db.Console
	database.First(&console)

	game := db.Game{ConsoleID: console.ID, Title: "Never Played", FileName: "np.nes", FilePath: "/tmp/np.nes", FileSize: 100}
	database.Create(&game)

	var user db.User
	database.First(&user)

	// Create a play history with zero play time and zero timestamp
	database.Create(&db.PlayHistory{
		UserID: user.ID, GameID: game.ID,
		PlayTime: 0, LastPlayed: time.Time{},
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/user/play-stats", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []PlayStatsEntry
	json.Unmarshal(w.Body.Bytes(), &result)
	assert.Empty(t, result, "should skip entries with zero play time and no last played")
}
