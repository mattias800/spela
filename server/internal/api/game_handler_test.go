package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// TestDownloadGame_NoPlayHistory verifies that downloading a game does NOT
// create a PlayHistory record. Only actually playing a game (UpdatePlayTime)
// should add it to "Continue Playing".
func TestDownloadGame_NoPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a ROM file in the game directory
	romContent := []byte("fake ROM data")
	romPath := filepath.Join(cfg.GameDirs[0], "test.nes")
	require.NoError(t, os.WriteFile(romPath, romContent, 0644))

	// Create a game entry
	var console db.Console
	database.First(&console)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Download Test Game",
		FileName:  "test.nes",
		FilePath:  "test.nes",
		FileSize:  int64(len(romContent)),
	}
	require.NoError(t, database.Create(&game).Error)

	// Download the game
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/download", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify NO PlayHistory record was created
	var count int64
	database.Model(&db.PlayHistory{}).Where("game_id = ?", game.ID).Count(&count)
	assert.Equal(t, int64(0), count, "downloading a game should not create a PlayHistory record")
}

// TestUpdatePlayTime_CreatesPlayHistory verifies that UpdatePlayTime creates
// a PlayHistory record when the user starts playing a game for the first time.
// This is a regression guard: PlayHistory should only be created by play-time
// updates, not by downloads.
func TestUpdatePlayTime_CreatesPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router := NewRouter(*cfg)
	token := registerAndGetToken(t, router)

	// Create a test game
	var console db.Console
	database.First(&console)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Play History Test Game",
		FileName:  "test.nes",
		FilePath:  "/tmp/test.nes",
		FileSize:  100,
	}
	require.NoError(t, database.Create(&game).Error)
	gameID := fmt.Sprintf("%d", game.ID)

	// Verify no PlayHistory exists before playing
	var countBefore int64
	database.Model(&db.PlayHistory{}).Where("game_id = ?", game.ID).Count(&countBefore)
	assert.Equal(t, int64(0), countBefore, "no PlayHistory should exist before playing")

	// Update play time (simulates starting to play)
	body, _ := json.Marshal(map[string]interface{}{"seconds": 60})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/games/"+gameID+"/play-time", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify PlayHistory was created
	var ph db.PlayHistory
	err := database.Where("game_id = ?", game.ID).First(&ph).Error
	require.NoError(t, err, "PlayHistory should be created after UpdatePlayTime")
	assert.Equal(t, int64(60), ph.PlayTime)
	assert.False(t, ph.LastPlayed.IsZero(), "LastPlayed should be set")
}
