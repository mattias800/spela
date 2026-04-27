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
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
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

// TestUpdateMetadata_PartyInfo verifies that the PartyInfo field can be set
// via the admin game metadata endpoint and appears in the game response.
func TestUpdateMetadata_PartyInfo(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a test game on the ADEMO console
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "ADEMO").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "State of the Art",
		FileName:  "sota.adf",
		FilePath:  "/tmp/sota.adf",
		FileSize:  100,
	}
	require.NoError(t, database.Create(&game).Error)
	gameID := fmt.Sprintf("%d", game.ID)

	// Update PartyInfo via admin endpoint
	body, _ := json.Marshal(map[string]string{"partyInfo": "Assembly 1993, 1st place"})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/"+gameID+"/metadata", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	// Verify PartyInfo is returned in the response
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "Assembly 1993, 1st place", resp["partyInfo"])

	// Verify PartyInfo also appears in GET /api/games/:id
	w2 := httptest.NewRecorder()
	req2 := httptest.NewRequest("GET", "/api/games/"+gameID, nil)
	req2.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w2, req2)
	assert.Equal(t, http.StatusOK, w2.Code)

	var getResp map[string]interface{}
	json.Unmarshal(w2.Body.Bytes(), &getResp)
	assert.Equal(t, "Assembly 1993, 1st place", getResp["partyInfo"])
}

// TestUpdateMetadata_CanClearFields verifies that sending explicit empty /
// zero values clears fields via the admin game metadata endpoint. Prior to
// issue #450 the handler used non-empty semantics (`if req.Title != ""`) so
// admins couldn't clear a field once set — sending {"title": ""} was a no-op
// rather than a clear. Pointer-based request fields now distinguish "absent"
// (leave alone) from "present and zero" (clear).
func TestUpdateMetadata_CanClearFields(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Seed a game with non-empty metadata.
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "ADEMO").First(&console).Error)
	game := db.Game{
		ConsoleID:         console.ID,
		Title:             "Original Title",
		Description:       "Seed description",
		Developer:         "Seed Dev",
		Publisher:         "Seed Pub",
		Genre:             "Action",
		Players:           4,
		IGDBCriticsRating: 82.5,
		PartyInfo:         "Assembly 1993",
		FileName:          "seed.adf",
		FilePath:          "/tmp/seed.adf",
		FileSize:          100,
	}
	require.NoError(t, database.Create(&game).Error)
	gameID := fmt.Sprintf("%d", game.ID)

	// Clear a subset via explicit zero values.
	body, _ := json.Marshal(map[string]interface{}{
		"description":       "",
		"publisher":         "",
		"genre":             "",
		"players":           0,
		"igdbCriticsRating": 0.0,
		"partyInfo":         "",
		// title / developer / coverUrl intentionally omitted — those must
		// remain at their seeded values.
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/admin/games/"+gameID+"/metadata", bytes.NewReader(body))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)

	// Cleared fields — all present-but-zero in the request.
	assert.Empty(t, updated.Description, "description should be cleared by explicit empty")
	assert.Empty(t, updated.Publisher, "publisher should be cleared")
	assert.Empty(t, updated.Genre, "genre should be cleared")
	assert.Zero(t, updated.Players, "players should be cleared to 0")
	assert.Zero(t, updated.IGDBCriticsRating, "rating should be cleared to 0")
	assert.Empty(t, updated.PartyInfo, "partyInfo should be cleared")

	// Untouched fields — absent from the request.
	assert.Equal(t, "Original Title", updated.Title, "omitted title must not be cleared")
	assert.Equal(t, "Seed Dev", updated.Developer, "omitted developer must not be cleared")
}

// TestUpdatePlayTime_CreatesPlayHistory verifies that UpdatePlayTime creates
// a PlayHistory record when the user starts playing a game for the first time.
// This is a regression guard: PlayHistory should only be created by play-time
// updates, not by downloads.
func TestUpdatePlayTime_CreatesPlayHistory(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
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

// TestDownloadGame_NormalizesINESHeader verifies that .nes ROMs with a
// dirty iNES 1.0 header (e.g. tool-provenance markers like "NI2.1" in
// reserved bytes) are streamed with bytes 8-15 zeroed, but the on-disk
// file is left untouched. Strict cores like nestopia reject the dirty
// header at retro_load_game; #712.
func TestDownloadGame_NormalizesINESHeader(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Build an iNES 1.0 ROM whose reserved bytes 11-15 carry the
	// "NI2.1" ASCII marker. PRG/CHR sizes are nominal — the body
	// content past the header doesn't matter for this assertion.
	header := []byte{0x4e, 0x45, 0x53, 0x1a, 0x02, 0x01, 0x01, 0x00, 0, 0, 0, 'N', 'I', '2', '.', '1'}
	rom := append(header, bytes.Repeat([]byte{0xaa}, 64)...)
	romPath := filepath.Join(cfg.GameDirs[0], "smb.nes")
	require.NoError(t, os.WriteFile(romPath, rom, 0644))

	var console db.Console
	database.First(&console)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "iNES Header Test",
		FileName:  "smb.nes",
		FilePath:  "smb.nes",
		FileSize:  int64(len(rom)),
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/download", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusOK, w.Code)

	got := w.Body.Bytes()
	require.Equal(t, len(rom), len(got), "response length should match on-disk ROM length")
	// Header bytes 0-7 unchanged.
	assert.Equal(t, rom[:8], got[:8])
	// Reserved bytes 8-15 should now be zero in the response.
	for i := 8; i < 16; i++ {
		assert.Equal(t, byte(0), got[i], "byte %d should be normalized to zero", i)
	}
	// Body past the header is untouched.
	assert.Equal(t, rom[16:], got[16:])

	// On-disk file must NOT have been modified.
	onDisk, err := os.ReadFile(romPath)
	require.NoError(t, err)
	assert.Equal(t, rom, onDisk, "on-disk ROM must be untouched by the download path")
}
