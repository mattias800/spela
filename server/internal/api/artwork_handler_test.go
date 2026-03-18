package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGetGameArtwork_NoArtwork(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a game
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Test Game",
		FileName:  "test.nes",
		FilePath:  "nes/test.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	// Request artwork for the game (none exists)
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(game.ID), 10)+"/artwork", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameArtworkResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.HeroURL)
	assert.Empty(t, resp.GridURL)
	assert.Empty(t, resp.LogoURL)
	assert.Empty(t, resp.IconURL)
}

func TestGetGameArtwork_WithArtwork(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a game
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario World",
		FileName:  "smw.sfc",
		FilePath:  "snes/smw.sfc",
	}
	require.NoError(t, database.Create(&game).Error)

	// Create artwork for the game
	artwork := db.GameArtwork{
		GameID:        game.ID,
		SteamGridDBID: 5432,
		HeroURL:       "https://cdn.steamgriddb.com/hero/smw.png",
		GridURL:       "https://cdn.steamgriddb.com/grid/smw.png",
		LogoURL:       "https://cdn.steamgriddb.com/logo/smw.png",
		IconURL:       "https://cdn.steamgriddb.com/icon/smw.png",
	}
	require.NoError(t, database.Create(&artwork).Error)

	// Request artwork
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(game.ID), 10)+"/artwork", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameArtworkResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/smw.png", resp.HeroURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/grid/smw.png", resp.GridURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/logo/smw.png", resp.LogoURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/icon/smw.png", resp.IconURL)
}

func TestGetGameArtwork_IncludedInGameDetail(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a game with console
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Metroid",
		FileName:  "metroid.nes",
		FilePath:  "nes/metroid.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	// Create artwork
	artwork := db.GameArtwork{
		GameID:  game.ID,
		HeroURL: "https://cdn.steamgriddb.com/hero/metroid.png",
		LogoURL: "https://cdn.steamgriddb.com/logo/metroid.png",
	}
	require.NoError(t, database.Create(&artwork).Error)

	// Get game detail — should include heroUrl and logoUrl
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(game.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "https://cdn.steamgriddb.com/hero/metroid.png", resp.HeroURL)
	assert.Equal(t, "https://cdn.steamgriddb.com/logo/metroid.png", resp.LogoURL)
}

func TestGetGameArtwork_GameDetailNoArtwork(t *testing.T) {
	database, cfg := setupTestEnv(t)
	cfg.NetplayHub = ws.NewNetplayHub(nil)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := registerAndGetToken(t, router)

	// Create a game without artwork
	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)
	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Zelda",
		FileName:  "zelda.nes",
		FilePath:  "nes/zelda.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	// Get game detail — heroUrl and logoUrl should be empty
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.FormatUint(uint64(game.ID), 10), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var resp GameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Empty(t, resp.HeroURL)
	assert.Empty(t, resp.LogoURL)
}

func TestSteamGridDBSettingsAllowed(t *testing.T) {
	// Verify that steamgriddb_api_key is in the allowed settings and secret settings
	assert.True(t, allowedSettingKeys["steamgriddb_api_key"], "steamgriddb_api_key should be in allowedSettingKeys")
	assert.True(t, secretSettingKeys["steamgriddb_api_key"], "steamgriddb_api_key should be in secretSettingKeys")
}
