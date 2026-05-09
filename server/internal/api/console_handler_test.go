package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// seedConsoleMetadata migrates the metadata lookup tables and seeds them.
// setupTestEnv seeds consoles but not the HardwareMaker/MediaType* lookup
// tables; call this for tests that assert on console metadata fields
// (maker, mediaType, releaseYear, summary, ...).
func seedConsoleMetadata(t *testing.T, database *gorm.DB) {
	t.Helper()
	require.NoError(t, database.AutoMigrate(
		&db.MediaTypeCategory{}, &db.MediaType{}, &db.HardwareMaker{},
	))
	require.NoError(t, db.SeedMediaTypeCategories(database))
	require.NoError(t, db.SeedMediaTypes(database))
	require.NoError(t, db.SeedHardwareMakers(database))
	require.NoError(t, db.SeedConsoleMetadata(database))
}

// consoleAuthToken returns an auth token for a regular user so tests can call
// the authenticated public console endpoints.
func consoleAuthToken(t *testing.T, router http.Handler) string {
	t.Helper()
	body, _ := json.Marshal(map[string]string{
		"username": "consoletest",
		"email":    "consoletest@example.com",
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

func TestListConsoles_OmitsConsolesWithNoGames(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	// With seeded consoles but no games, response should be empty
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &consoles))
	assert.Empty(t, consoles, "should return no consoles when none have games")
}

func TestListConsoles_ReturnsConsolesWithGames(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	// Add a game to NES console
	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	game := db.Game{
		ConsoleID: nesConsole.ID,
		Title:     "Test Game",
		FileName:  "test.nes",
		FilePath:  "/tmp/test.nes",
		FileSize:  1024,
		IsPrimary: true,
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &consoles))
	assert.Len(t, consoles, 1, "should return only the console with games")
	assert.Equal(t, "NES", consoles[0].Abbreviation)
	assert.Equal(t, 1, consoles[0].GameCount)
}

func TestListConsoles_IncludesMetadata(t *testing.T) {
	database, cfg := setupTestEnv(t)
	seedConsoleMetadata(t, database)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	// Add a game to NES so it appears in the list
	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	game := db.Game{
		ConsoleID: nesConsole.ID,
		Title:     "Test Game",
		FileName:  "test.nes",
		FilePath:  "/tmp/test.nes",
		FileSize:  1024,
		IsPrimary: true,
	}
	require.NoError(t, database.Create(&game).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &consoles))
	require.Len(t, consoles, 1)

	nes := consoles[0]
	assert.Equal(t, "nes", nes.Code, "code should come from seeded metadata")
	assert.Equal(t, "NES", nes.Abbreviation)

	// Maker
	require.NotNil(t, nes.Maker, "maker should be populated from seeded metadata")
	assert.Equal(t, "nintendo", nes.Maker.Code)
	assert.Equal(t, "Nintendo", nes.Maker.Name)

	// Media type
	require.NotNil(t, nes.MediaType, "mediaType should be populated from seeded metadata")
	assert.Equal(t, "cartridge", nes.MediaType.Code)
	assert.Equal(t, "cartridge", nes.MediaType.Category.Code)

	// Numeric metadata
	require.NotNil(t, nes.ReleaseYear, "releaseYear should be set")
	assert.Equal(t, 1983, *nes.ReleaseYear)

	require.NotNil(t, nes.UnitsSold, "unitsSold should be set")
	assert.True(t, *nes.UnitsSold > 0, "unitsSold should be positive")

	require.NotNil(t, nes.Summary, "summary should be set")
	assert.NotEmpty(t, *nes.Summary, "summary should not be empty")
}

func TestGetPreviewScreenshot_ConsoleNotFound(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/nonexistent/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "console not found", resp["error"])
}

func TestGetPreviewScreenshot_CachedPreview(t *testing.T) {
	database, cfg := setupTestEnv(t)
	store := cfg.Storage
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	// Create the cached preview file on disk.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	require.NoError(t, os.MkdirAll(previewDir, 0755))

	previewPath := filepath.Join(previewDir, "preview.png")
	require.NoError(t, os.WriteFile(previewPath, []byte("fake-png-data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/"+strings.ToLower(console.Abbreviation)+"/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
	assert.Equal(t, "public, max-age=86400", w.Header().Get("Cache-Control"))
}

func TestGetPreviewScreenshot_NoPreviewAvailable(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// Create a console with an abbreviation that is NOT in either
	// scraper.AbbreviationToLibRetro or previewFallbackGames.
	unknownConsole := db.Console{
		Name:         "Unknown Console",
		Abbreviation: "ZZZUNKNOWN",
		Extensions:   ".zzz",
	}
	require.NoError(t, database.Create(&unknownConsole).Error)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/"+strings.ToLower(unknownConsole.Abbreviation)+"/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &resp))
	assert.Equal(t, "no preview available for this console", resp["error"])
}

func TestGetPreviewScreenshot_IgnoresLocalGames(t *testing.T) {
	database, cfg := setupTestEnv(t)
	store := cfg.Storage
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	// Create a game with a scraped screenshot — should be ignored.
	game := db.Game{
		ConsoleID:     console.ID,
		Title:         "Game With Screenshot",
		FileName:      "game.nes",
		FilePath:      "/tmp/game.nes",
		FileSize:      2048,
		ScreenshotURL: "NES/42/screenshot.png",
	}
	require.NoError(t, database.Create(&game).Error)

	// Create a cached CDN preview.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	require.NoError(t, os.MkdirAll(previewDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(previewDir, "preview.png"), []byte("cdn-preview"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/"+strings.ToLower(console.Abbreviation)+"/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	// Should serve the CDN preview, NOT the local game screenshot.
	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
}

func TestGetPreviewScreenshot_WorksWithNoLocalGames(t *testing.T) {
	database, cfg := setupTestEnv(t)
	store := cfg.Storage
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	var console db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&console).Error)

	// No games exist at all — verify we still serve the cached CDN preview.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	require.NoError(t, os.MkdirAll(previewDir, 0755))
	require.NoError(t, os.WriteFile(filepath.Join(previewDir, "preview.png"), []byte("cached-data"), 0644))

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/"+strings.ToLower(console.Abbreviation)+"/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
}

// --- Top lists (available) ---

func TestGetTopListAvailable_ReturnsOnlyLocalMatches(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create two top-rated IGDB entries
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "Super Mario Bros.",
		TotalRating: 92.5, TotalRatingCount: 500, UserRating: 92.5, UserRatingCount: 400, CriticRating: 90.0, CriticRatingCount: 10, Rank: 1,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 200, Name: "The Legend of Zelda",
		TotalRating: 90.0, TotalRatingCount: 400, UserRating: 90.0, UserRatingCount: 350, CriticRating: 88.0, CriticRatingCount: 8, Rank: 2,
	})

	// Create a local game matching only one of them (case-insensitive)
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "super mario bros.",
		FileName: "smb.nes", FilePath: "/games/smb.nes", FileSize: 1024, IsPrimary: true,
		CoverURL: "covers/nes/smb.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should return only the game with a local match")
	assert.Equal(t, "Super Mario Bros.", result[0].Name)
	assert.Equal(t, "/api/images/covers/nes/smb.png", result[0].CoverUrl)
	assert.Equal(t, "nes", result[0].ConsoleId)
	assert.Equal(t, 92.5, result[0].IGDBCriticsRating)
}

func TestGetTopListAvailable_SortedByRatingDesc(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create top-rated entries with varying ratings
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "Game A",
		TotalRating: 80.0, TotalRatingCount: 300, UserRating: 80.0, UserRatingCount: 250, CriticRating: 75.0, CriticRatingCount: 5, Rank: 3,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 200, Name: "Game B",
		TotalRating: 95.0, TotalRatingCount: 500, UserRating: 95.0, UserRatingCount: 450, CriticRating: 92.0, CriticRatingCount: 10, Rank: 1,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 300, Name: "Game C",
		TotalRating: 88.0, TotalRatingCount: 400, UserRating: 88.0, UserRatingCount: 350, CriticRating: 85.0, CriticRatingCount: 8, Rank: 2,
	})

	// Create matching local games for all three
	for _, title := range []string{"Game A", "Game B", "Game C"} {
		database.Create(&db.Game{
			ConsoleID: nesConsole.ID, Title: title,
			FileName: strings.ToLower(title) + ".nes",
			FilePath: "/games/" + strings.ToLower(title) + ".nes",
			FileSize: 1024, IsPrimary: true,
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 3)
	assert.Equal(t, "Game B", result[0].Name)
	assert.Equal(t, 95.0, result[0].IGDBCriticsRating)
	assert.Equal(t, "Game C", result[1].Name)
	assert.Equal(t, 88.0, result[1].IGDBCriticsRating)
	assert.Equal(t, "Game A", result[2].Name)
	assert.Equal(t, 80.0, result[2].IGDBCriticsRating)
}

func TestGetTopListAvailable_SequentialRanks(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create two top-rated entries
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "First",
		TotalRating: 90.0, TotalRatingCount: 500, UserRating: 90.0, UserRatingCount: 400, CriticRating: 88.0, CriticRatingCount: 10, Rank: 1,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 200, Name: "Second",
		TotalRating: 85.0, TotalRatingCount: 400, UserRating: 85.0, UserRatingCount: 350, CriticRating: 82.0, CriticRatingCount: 8, Rank: 2,
	})

	// Create matching local games
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "First",
		FileName: "first.nes", FilePath: "/games/first.nes", FileSize: 1024, IsPrimary: true,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Second",
		FileName: "second.nes", FilePath: "/games/second.nes", FileSize: 1024, IsPrimary: true,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 2)
	assert.Equal(t, 1, result[0].Rank, "first result should have rank 1")
	assert.Equal(t, 2, result[1].Rank, "second result should have rank 2")
}

func TestGetTopListAvailable_EmptyWhenNoMatches(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create top-rated entries with no local game matches
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "No Match Game",
		TotalRating: 90.0, TotalRatingCount: 500, Rank: 1,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Empty(t, result, "should return empty array when no local matches exist")
}

func TestGetTopListAvailable_EmptyWithNoData(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	// No top-rated entries and no games at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Empty(t, result, "should return empty array when no data exists")
}

func TestGetTopListAvailable_IncludesConsoleInfo(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var snesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snesConsole).Error)

	database.Create(&db.TopRatedGame{
		ConsoleID: snesConsole.ID, IGDBGameID: 100, Name: "Super Mario World",
		TotalRating: 95.0, TotalRatingCount: 600, UserRating: 95.0, UserRatingCount: 500, CriticRating: 93.0, CriticRatingCount: 15, Rank: 1,
	})

	database.Create(&db.Game{
		ConsoleID: snesConsole.ID, Title: "Super Mario World",
		FileName: "smw.sfc", FilePath: "/games/smw.sfc", FileSize: 2048, IsPrimary: true,
		CoverURL: "covers/snes/smw.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 1)
	assert.Equal(t, "Super Nintendo", result[0].ConsoleName)
	assert.Equal(t, "snes", result[0].ConsoleId)
	assert.Equal(t, "/api/images/covers/snes/smw.png", result[0].CoverUrl)
	assert.NotEmpty(t, result[0].GameId)
}

// --- GetTopListLongest tests ---

func TestGetTopListLongest_SortedByTimeToBeatDesc(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create games with varying time-to-beat values
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Short Game",
		FileName: "short.nes", FilePath: "/games/short.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 10, TimeToBeatHastily: 5, TimeToBeatCompletely: 20,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Long Game",
		FileName: "long.nes", FilePath: "/games/long.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 100, TimeToBeatHastily: 60, TimeToBeatCompletely: 200,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Medium Game",
		FileName: "medium.nes", FilePath: "/games/medium.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 50, TimeToBeatHastily: 30, TimeToBeatCompletely: 80,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 3)
	assert.Equal(t, "Long Game", result[0].Name)
	assert.Equal(t, 100, result[0].TimeToBeatNormally)
	assert.Equal(t, 60, result[0].TimeToBeatHastily)
	assert.Equal(t, 200, result[0].TimeToBeatCompletely)
	assert.Equal(t, "Medium Game", result[1].Name)
	assert.Equal(t, 50, result[1].TimeToBeatNormally)
	assert.Equal(t, "Short Game", result[2].Name)
	assert.Equal(t, 10, result[2].TimeToBeatNormally)
}

func TestGetTopListLongest_OnlyIncludesGamesWithTimeToBeat(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Game with time-to-beat data
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Has Time Data",
		FileName: "has.nes", FilePath: "/games/has.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 40, TimeToBeatHastily: 20, TimeToBeatCompletely: 60,
	})
	// Game without time-to-beat data (zero value)
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "No Time Data",
		FileName: "no.nes", FilePath: "/games/no.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 0,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should only include games with time_to_beat_normally > 0")
	assert.Equal(t, "Has Time Data", result[0].Name)
}

func TestGetTopListLongest_EmptyWhenNoGamesHaveTimeData(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create games without time data
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Game A",
		FileName: "a.nes", FilePath: "/games/a.nes", FileSize: 1024, IsPrimary: true,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Game B",
		FileName: "b.nes", FilePath: "/games/b.nes", FileSize: 1024, IsPrimary: true,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Empty(t, result, "should return empty array when no games have time data")
}

func TestGetTopListLongest_SequentialRanks(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "First",
		FileName: "first.nes", FilePath: "/games/first.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 100,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Second",
		FileName: "second.nes", FilePath: "/games/second.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 50,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Third",
		FileName: "third.nes", FilePath: "/games/third.nes", FileSize: 1024, IsPrimary: true,
		TimeToBeatNormally: 25,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 3)
	assert.Equal(t, 1, result[0].Rank, "first result should have rank 1")
	assert.Equal(t, 2, result[1].Rank, "second result should have rank 2")
	assert.Equal(t, 3, result[2].Rank, "third result should have rank 3")
}

func TestGetTopListLongest_LimitOf50(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Create 55 games with time-to-beat data
	for i := 1; i <= 55; i++ {
		database.Create(&db.Game{
			ConsoleID:          nesConsole.ID,
			Title:              fmt.Sprintf("Game %03d", i),
			FileName:           fmt.Sprintf("game%03d.nes", i),
			FilePath:           fmt.Sprintf("/games/game%03d.nes", i),
			FileSize:           1024,
			IsPrimary:          true,
			TimeToBeatNormally: i * 10,
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 50, "should be limited to 50 results")
	// The first result should be the game with the highest time (55 * 10 = 550)
	assert.Equal(t, 550, result[0].TimeToBeatNormally)
	assert.Equal(t, 1, result[0].Rank)
	// The last result should be the 50th longest (game 6 → 60 hours)
	assert.Equal(t, 60, result[49].TimeToBeatNormally)
	assert.Equal(t, 50, result[49].Rank)
}

// --- Bug fix tests ---

func TestGetTopListAvailable_UsesTotalRatingNotUserRating(t *testing.T) {
	// Bug: Audience list was empty because it used user_rating which IGDB
	// often returns as null (stored as 0). Fix uses total_rating instead.
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Game with total_rating but user_rating=0 (typical IGDB response)
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "Mega Man 2",
		TotalRating: 88.0, TotalRatingCount: 200,
		UserRating: 0, UserRatingCount: 0,
		CriticRating: 85.0, CriticRatingCount: 5, Rank: 1,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Mega Man 2",
		FileName: "mm2.nes", FilePath: "/games/mm2.nes", IsPrimary: true,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should include game with total_rating even if user_rating is 0")
	assert.Equal(t, "Mega Man 2", result[0].Name)
	assert.Equal(t, 88.0, result[0].IGDBCriticsRating)
}

func TestGetTopListCritics_ReturnsGamesWithCriticRating(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "Zelda",
		TotalRating: 92.0, TotalRatingCount: 500,
		CriticRating: 95.0, CriticRatingCount: 10, Rank: 1,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Zelda",
		FileName: "zelda.nes", FilePath: "/games/zelda.nes", IsPrimary: true,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated-critics", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)
	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1)
	assert.Equal(t, 95.0, result[0].IGDBCriticsRating)
}

func TestGetTopListAvailable_ExcludesDemoConsoles(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole, ademoConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)
	require.NoError(t, database.Where("abbreviation = ?", "ADEMO").First(&ademoConsole).Error)

	// NES game (should appear)
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "Super Mario",
		TotalRating: 90.0, TotalRatingCount: 400, Rank: 1,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Super Mario",
		FileName: "sm.nes", FilePath: "/games/sm.nes", IsPrimary: true,
	})

	// Demo "game" (should be excluded)
	database.Create(&db.TopRatedGame{
		ConsoleID: ademoConsole.ID, IGDBGameID: 200, Name: "Some Demo",
		TotalRating: 95.0, TotalRatingCount: 50, Rank: 1,
	})
	database.Create(&db.Game{
		ConsoleID: ademoConsole.ID, Title: "Some Demo",
		FileName: "demo.adf", FilePath: "/games/demo.adf", IsPrimary: true,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var result []TopListGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should exclude demo console entries")
	assert.Equal(t, "Super Mario", result[0].Name)
}

func TestGetTopListLongest_CapsUnreasonableTimeToBeat(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)

	// Reasonable game
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Long RPG",
		FileName: "rpg.nes", FilePath: "/games/rpg.nes", IsPrimary: true,
		TimeToBeatNormally: 100 * 3600, // 100 hours
	})

	// Unreasonable game (567890 hours like World Cup 98)
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Broken Data",
		FileName: "broken.nes", FilePath: "/games/broken.nes", IsPrimary: true,
		TimeToBeatNormally: 567890 * 3600,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should exclude games with unreasonable time-to-beat")
	assert.Equal(t, "Long RPG", result[0].Name)
}

func TestGetTopListLongest_ExcludesDemoConsoles(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var nesConsole, ddemoConsole db.Console
	require.NoError(t, database.Where("abbreviation = ?", "NES").First(&nesConsole).Error)
	require.NoError(t, database.Where("abbreviation = ?", "DDEMO").First(&ddemoConsole).Error)

	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Real Game",
		FileName: "game.nes", FilePath: "/games/game.nes", IsPrimary: true,
		TimeToBeatNormally: 50 * 3600,
	})
	database.Create(&db.Game{
		ConsoleID: ddemoConsole.ID, Title: "DOS Demo",
		FileName: "demo.zip", FilePath: "/games/demo.zip", IsPrimary: true,
		TimeToBeatNormally: 1 * 3600,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	var result []LongestGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	assert.Len(t, result, 1, "should exclude demo console entries")
	assert.Equal(t, "Real Game", result[0].Name)
}

func TestGetTopRatedGlobal_DeduplicatesSameGameAcrossConsoles(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	// Look up two different consoles
	var snes, gba db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "GBA").First(&gba).Error)

	// Create library games so the console filter includes both SNES and GBA.
	database.Create(&db.Game{
		ConsoleID: snes.ID, Title: "Some SNES Game",
		FileName: "some.sfc", FilePath: "SNES/some.sfc",
	})
	database.Create(&db.Game{
		ConsoleID: gba.ID, Title: "Some GBA Game",
		FileName: "some.gba", FilePath: "GBA/some.gba",
	})

	// Insert the same game (same IGDB game ID) as top-rated on two consoles.
	// This happens because IGDB lists "Super Metroid" on both SNES and GBA
	// (or virtual console re-releases), and upsertTopRatedGames runs per-console.
	database.Create(&db.TopRatedGame{
		ConsoleID: snes.ID, IGDBGameID: 1103, Name: "Super Metroid",
		TotalRating: 96.0, TotalRatingCount: 500, Rank: 1,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: gba.ID, IGDBGameID: 1103, Name: "Super Metroid",
		TotalRating: 95.0, TotalRatingCount: 200, Rank: 1,
	})
	// A different game for variety
	database.Create(&db.TopRatedGame{
		ConsoleID: snes.ID, IGDBGameID: 2000, Name: "Chrono Trigger",
		TotalRating: 92.0, TotalRatingCount: 400, Rank: 2,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopRatedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	// Super Metroid should appear exactly once, not twice
	names := make([]string, len(result))
	for i, g := range result {
		names[i] = g.Name
	}
	assert.Len(t, result, 2, "should deduplicate: got %v", names)
	assert.Equal(t, "Super Metroid", result[0].Name)
	assert.Equal(t, "Chrono Trigger", result[1].Name)
}

func TestGetTopRatedGlobal_DeduplicatePrefersLocalLibraryMatch(t *testing.T) {
	database, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()
	token := consoleAuthToken(t, router)

	var snes, gba db.Console
	require.NoError(t, database.Where("abbreviation = ?", "SNES").First(&snes).Error)
	require.NoError(t, database.Where("abbreviation = ?", "GBA").First(&gba).Error)

	// Add a local SNES game matching "Super Metroid"
	database.Create(&db.Game{
		ConsoleID: snes.ID, Title: "Super Metroid",
		FileName: "super_metroid.sfc", FilePath: "SNES/super_metroid.sfc",
		CoverURL: "SNES/1/boxart.png",
	})

	// Insert same game on two consoles — GBA version has higher rating
	// but SNES has the local library match
	database.Create(&db.TopRatedGame{
		ConsoleID: gba.ID, IGDBGameID: 1103, Name: "Super Metroid",
		TotalRating: 97.0, TotalRatingCount: 200, Rank: 1,
	})
	database.Create(&db.TopRatedGame{
		ConsoleID: snes.ID, IGDBGameID: 1103, Name: "Super Metroid",
		TotalRating: 96.0, TotalRatingCount: 500, Rank: 1,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-rated", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopRatedGameResponse
	require.NoError(t, json.Unmarshal(w.Body.Bytes(), &result))

	require.Len(t, result, 1)
	assert.Equal(t, "Super Metroid", result[0].Name)
	// Should have a localGameId because the SNES version matches a library game
	assert.NotNil(t, result[0].LocalGameId, "should prefer the console entry that has a local library match")
}

// #888 — child consoles without their own logo asset fall back to
// the parent platform's logo. Avoids the placeholder/blank state in
// the player app when the child asset hasn't been shipped yet.
func TestGetConsoleLogo_ParentFallback(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	tests := []struct {
		name        string
		childAbbr   string
		parentAbbr  string
		contentType string
	}{
		{"Amiga Demos falls back to Amiga (svg)", "ademo", "amiga", "image/svg+xml"},
		{"DOS Demos falls back to DOS (svg)", "ddemo", "dos", "image/svg+xml"},
		{"Amiga Demos falls back to Amiga (png)", "ademo", "amiga", "image/png"},
		{"DOS Demos falls back to DOS (png)", "ddemo", "dos", "image/png"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			suffix := "/logo"
			if tt.contentType == "image/png" {
				suffix = "/logo.png"
			}

			// Fetch the parent's bytes for byte-equality.
			pw := httptest.NewRecorder()
			preq := httptest.NewRequest("GET", "/api/consoles/"+tt.parentAbbr+suffix, nil)
			router.ServeHTTP(pw, preq)
			require.Equal(t, http.StatusOK, pw.Code, "parent logo must be available")
			parentBody := pw.Body.Bytes()
			require.NotEmpty(t, parentBody)

			// Fetch the child's bytes — must succeed and equal parent.
			cw := httptest.NewRecorder()
			creq := httptest.NewRequest("GET", "/api/consoles/"+tt.childAbbr+suffix, nil)
			router.ServeHTTP(cw, creq)
			assert.Equal(t, http.StatusOK, cw.Code, "child logo should fall back, not 404")
			assert.Equal(t, tt.contentType, cw.Header().Get("Content-Type"))
			assert.Equal(t, parentBody, cw.Body.Bytes(), "child should serve identical bytes to parent")
		})
	}
}

// Consoles with no fallback configured AND no asset still 404 — the
// fallback map is opt-in, not a generic catch-all.
func TestGetConsoleLogo_NoFallbackStill404s(t *testing.T) {
	_, cfg := setupTestEnv(t)
	router, cleanup := NewRouter(*cfg)
	defer cleanup()

	// ARCADE has no parent platform; the issue notes it needs a fresh
	// asset and remains 404 until one is shipped.
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/arcade/logo", nil)
	router.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
}
