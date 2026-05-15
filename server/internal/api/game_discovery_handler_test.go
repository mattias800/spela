package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// discoveryAuthToken registers a user and returns the access token for use
// with authenticated discovery routes.
func discoveryAuthToken(t *testing.T, router http.Handler) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": "discoverytest",
		"email":    "discoverytest@example.com",
		"password": "SecureTestPass!2024",
	})
	w := httptest.NewRecorder()
	req := httptest.NewRequest("POST", "/api/auth/register", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	router.ServeHTTP(w, req)
	require.Equal(t, http.StatusCreated, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	return resp["accessToken"].(string)
}

func TestGetSimilarGames_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "game not found", resp["error"])
}

func TestGetSimilarGames_NoScraperID(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Unscraped Game",
		FileName:  "game.nes",
		FilePath:  "/tmp/game.nes",
		FileSize:  1024,
		ScraperID: "", // no IGDB scraper ID
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result)
}

func TestGetSimilarGames_ReturnsCachedData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	require.NoError(t, database.Create(&game).Error)

	// Pre-populate cache. Platforms = "18" is the IGDB platform ID
	// for NES — needed for the generation filter in HumaGetSimilarGames
	// to recognise these similar games as same-generation candidates.
	similar1 := db.SimilarGame{
		GameID:            game.ID,
		IGDBGameID:        5678,
		Name:              "Super Mario Bros. 2",
		CoverImageID:      "co9999",
		CoverLocalPath:    "NES/5678/cover.jpg",
		IGDBCriticsRating: 85.5,
		Platforms:         "18",
	}
	similar2 := db.SimilarGame{
		GameID:            game.ID,
		IGDBGameID:        9012,
		Name:              "Kirby's Adventure",
		CoverImageID:      "co8888",
		CoverLocalPath:    "NES/9012/cover.jpg",
		IGDBCriticsRating: 82.0,
		Platforms:         "18",
	}
	require.NoError(t, database.Create(&similar1).Error)
	require.NoError(t, database.Create(&similar2).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Len(t, result, 2)
	assert.Equal(t, "Super Mario Bros. 2", result[0].Name)
	assert.Equal(t, 85.5, result[0].IGDBCriticsRating)
	assert.Contains(t, result[0].CoverUrl, "/api/images/NES/5678/cover.jpg")
	assert.Nil(t, result[0].LocalGameId, "should be nil when no local game matches")
}

func TestGetSimilarGames_CrossReferencesLocalLibrary(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	require.NoError(t, database.Create(&game).Error)

	// Create a local game that matches one of the similar games
	localMatch := db.Game{
		ConsoleID: console.ID,
		Title:     "Kirby's Adventure",
		FileName:  "kirby.nes",
		FilePath:  "/tmp/kirby.nes",
		FileSize:  2048,
	}
	require.NoError(t, database.Create(&localMatch).Error)

	// Pre-populate cache
	similar := db.SimilarGame{
		GameID:            game.ID,
		IGDBGameID:        9012,
		Name:              "Kirby's Adventure",
		CoverImageID:      "co8888",
		IGDBCriticsRating: 82.0,
	}
	require.NoError(t, database.Create(&similar).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Len(t, result, 1)
	require.NotNil(t, result[0].LocalGameId, "should have localGameId when local game matches")
	assert.Equal(t, strconv.Itoa(int(localMatch.ID)), *result[0].LocalGameId)
}

func TestGetSimilarGames_GenerationFilter(t *testing.T) {
	// IGDB's similar_games field ignores platform/era and routinely
	// surfaces modern games for retro titles. The handler filters
	// cached suggestions to platforms within ± 1 console generation
	// of the source game.
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	game := db.Game{
		ConsoleID: nes.ID,
		Title:     "Jurassic Park",
		FileName:  "jp.nes",
		FilePath:  "/tmp/jp.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	require.NoError(t, database.Create(&game).Error)

	// Three cached IGDB suggestions:
	// (a) Same gen (NES, platform 18, gen 3) — kept
	// (b) Adjacent gen (SNES, platform 19, gen 4) — kept (within ±1)
	// (c) Far gen (PS3, platform 9, gen 7) — dropped
	require.NoError(t, database.Create(&db.SimilarGame{
		GameID:     game.ID,
		IGDBGameID: 5001,
		Name:       "Same Gen NES Title",
		Platforms:  "18",
	}).Error)
	require.NoError(t, database.Create(&db.SimilarGame{
		GameID:     game.ID,
		IGDBGameID: 5002,
		Name:       "Adjacent Gen SNES Title",
		Platforms:  "19",
	}).Error)
	require.NoError(t, database.Create(&db.SimilarGame{
		GameID:     game.ID,
		IGDBGameID: 5003,
		Name:       "Far Gen PS3 Title",
		Platforms:  "9",
	}).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	names := make([]string, len(result))
	for i, r := range result {
		names[i] = r.Name
	}
	assert.ElementsMatch(t, []string{"Same Gen NES Title", "Adjacent Gen SNES Title"}, names,
		"expected same-gen and adjacent-gen suggestions kept, far-gen dropped")
}

func TestGetSimilarGames_GenerationFilter_DropsRowWithNoPlatformOrLocalMatch(t *testing.T) {
	// A cached row with no Platforms (legacy data) AND no matching
	// local game gives the handler no way to verify the generation.
	// It drops conservatively. Once the cache TTL expires and the
	// background refresh populates Platforms, the row re-appears
	// (if it actually belongs in the allowed generation range).
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var nes db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nes).Error)

	game := db.Game{
		ConsoleID: nes.ID,
		Title:     "Source Game",
		FileName:  "src.nes",
		FilePath:  "/tmp/src.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	require.NoError(t, database.Create(&game).Error)

	// No Platforms, no matching local game in the library.
	require.NoError(t, database.Create(&db.SimilarGame{
		GameID:     game.ID,
		IGDBGameID: 5001,
		Name:       "Mystery Suggestion",
	}).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/similar", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result, "legacy row with no platforms / local match is dropped conservatively")
}

func TestGetDeveloperGames_GameNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999/developer-games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "game not found", resp["error"])
}

func TestGetDeveloperGames_NoDeveloper(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Unknown Game",
		FileName:  "unknown.nes",
		FilePath:  "/tmp/unknown.nes",
		FileSize:  1024,
		Developer: "", // no developer
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/developer-games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result)
}

func TestGetDeveloperGames_ReturnsOtherGames(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game1 := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  1024,
		Developer: "Nintendo",
		CoverURL:  "NES/1/boxart.jpg",
		IsPrimary: true,
	}
	game2 := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros. 2",
		FileName:  "smb2.nes",
		FilePath:  "/tmp/smb2.nes",
		FileSize:  2048,
		Developer: "Nintendo",
		CoverURL:  "NES/2/boxart.jpg",
		IsPrimary: true,
	}
	game3 := db.Game{
		ConsoleID: console.ID,
		Title:     "Legend of Zelda",
		FileName:  "zelda.nes",
		FilePath:  "/tmp/zelda.nes",
		FileSize:  3072,
		IsPrimary: true,
		Developer: "Nintendo",
		CoverURL:  "NES/3/boxart.jpg",
	}
	// Different developer — should not be returned
	game4 := db.Game{
		ConsoleID: console.ID,
		Title:     "Sonic The Hedgehog",
		FileName:  "sonic.nes",
		FilePath:  "/tmp/sonic.nes",
		FileSize:  4096,
		Developer: "Sega",
	}

	require.NoError(t, database.Create(&game1).Error)
	require.NoError(t, database.Create(&game2).Error)
	require.NoError(t, database.Create(&game3).Error)
	require.NoError(t, database.Create(&game4).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game1.ID))+"/developer-games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Len(t, result, 2, "should return 2 other Nintendo games, excluding the queried game and Sega game")

	// Should be sorted alphabetically
	assert.Equal(t, "Legend of Zelda", result[0].Name)
	assert.Equal(t, "Super Mario Bros. 2", result[1].Name)

	// Should include local game IDs
	assert.Equal(t, strconv.Itoa(int(game3.ID)), result[0].LocalGameId)
	assert.Equal(t, strconv.Itoa(int(game2.ID)), result[1].LocalGameId)

	// Should resolve cover URLs
	assert.Equal(t, "/api/images/NES/3/boxart.jpg", result[0].CoverUrl)
	assert.Equal(t, "/api/images/NES/2/boxart.jpg", result[1].CoverUrl)
}

func TestGetDeveloperGames_ExcludesQueriedGame(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := discoveryAuthToken(t, router)

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Only Game",
		FileName:  "only.nes",
		FilePath:  "/tmp/only.nes",
		FileSize:  1024,
		Developer: "SoloDev",
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/"+strconv.Itoa(int(game.ID))+"/developer-games", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))
	assert.Empty(t, result, "should not return the queried game itself")
}

// TestParseIGDBGameID exercises the package-private parseIGDBGameID helper.
// This is a pure unit test — not wired to any HTTP route.
func TestParseIGDBGameID(t *testing.T) {
	tests := []struct {
		name      string
		scraperID string
		expected  int
	}{
		{"valid igdb id", "igdb:1234", 1234},
		{"valid igdb large id", "igdb:999999", 999999},
		{"libretro scraper id", "libretro", 0},
		{"empty", "", 0},
		{"igdb prefix no number", "igdb:", 0},
		{"igdb prefix bad number", "igdb:abc", 0},
		{"other format", "other:123", 0},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := parseIGDBGameID(tt.scraperID)
			assert.Equal(t, tt.expected, result)
		})
	}
}
