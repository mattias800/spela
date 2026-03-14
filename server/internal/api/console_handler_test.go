package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/storage"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// setupConsoleTestEnv creates an in-memory DB with auto-migrated tables and seeded
// consoles, a temp-dir-backed Storage, and a gin router with just the
// preview-screenshot route. The route does not require auth middleware so the
// tests stay focused on the handler logic.
func setupConsoleTestEnv(t *testing.T) (*gorm.DB, *storage.Storage, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(&db.Console{}, &db.Game{}, &db.TopRatedGame{}, &db.GameReleaseDate{}, &db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{})
	require.NoError(t, err)

	err = db.SeedConsoles(database)
	require.NoError(t, err)

	tmpDir := t.TempDir()
	store, err := storage.NewStorage(
		filepath.Join(tmpDir, "saves"),
		filepath.Join(tmpDir, "cores"),
		filepath.Join(tmpDir, "images"),
		filepath.Join(tmpDir, "bios"),
	)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &ConsoleHandler{DB: database, Storage: store}
	router.GET("/api/consoles/:id/preview-screenshot", handler.GetPreviewScreenshot)

	return database, store, router
}

func TestListConsoles_OmitsConsolesWithNoGames(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	handler := &ConsoleHandler{DB: database, Storage: store}
	router.GET("/api/consoles", handler.ListConsoles)

	// With seeded consoles but no games, response should be empty
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	err := json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)
	assert.Empty(t, consoles, "should return no consoles when none have games")
}

func TestListConsoles_ReturnsConsolesWithGames(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	handler := &ConsoleHandler{DB: database, Storage: store}
	router.GET("/api/consoles", handler.ListConsoles)

	// Add a game to NES console
	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: nesConsole.ID,
		Title:     "Test Game",
		FileName:  "test.nes",
		FilePath:  "/tmp/test.nes",
		FileSize:  1024,
		IsPrimary: true,
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var consoles []ConsoleResponse
	err = json.Unmarshal(w.Body.Bytes(), &consoles)
	require.NoError(t, err)
	assert.Len(t, consoles, 1, "should return only the console with games")
	assert.Equal(t, "NES", consoles[0].Abbreviation)
	assert.Equal(t, 1, consoles[0].GameCount)
}

func TestGetPreviewScreenshot_ConsoleNotFound(t *testing.T) {
	_, _, router := setupConsoleTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/nonexistent/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "console not found", resp["error"])
}

func TestGetPreviewScreenshot_CachedPreview(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	// Create the cached preview file on disk.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	err = os.MkdirAll(previewDir, 0755)
	require.NoError(t, err)

	previewPath := filepath.Join(previewDir, "preview.png")
	err = os.WriteFile(previewPath, []byte("fake-png-data"), 0644)
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/" + strings.ToLower(console.Abbreviation) + "/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
	assert.Equal(t, "public, max-age=86400", w.Header().Get("Cache-Control"))
}

func TestGetPreviewScreenshot_NoPreviewAvailable(t *testing.T) {
	database, _, router := setupConsoleTestEnv(t)

	// Create a console with an abbreviation that is NOT in either
	// scraper.AbbreviationToLibRetro or previewFallbackGames.
	unknownConsole := db.Console{
		Name:         "Unknown Console",
		Abbreviation: "ZZZUNKNOWN",
		Extensions:   ".zzz",
	}
	err := database.Create(&unknownConsole).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/" + strings.ToLower(unknownConsole.Abbreviation) + "/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err = json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "no preview available for this console", resp["error"])
}

func TestGetPreviewScreenshot_IgnoresLocalGames(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	// Create a game with a scraped screenshot — should be ignored.
	game := db.Game{
		ConsoleID:     console.ID,
		Title:         "Game With Screenshot",
		FileName:      "game.nes",
		FilePath:      "/tmp/game.nes",
		FileSize:      2048,
		ScreenshotURL: "NES/42/screenshot.png",
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	// Create a cached CDN preview.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	err = os.MkdirAll(previewDir, 0755)
	require.NoError(t, err)
	err = os.WriteFile(filepath.Join(previewDir, "preview.png"), []byte("cdn-preview"), 0644)
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/" + strings.ToLower(console.Abbreviation) + "/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	// Should serve the CDN preview, NOT the local game screenshot.
	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
}

func TestGetPreviewScreenshot_WorksWithNoLocalGames(t *testing.T) {
	database, store, router := setupConsoleTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	// No games exist at all — verify we still serve the cached CDN preview.
	previewDir := filepath.Join(store.ImageDir, "previews", console.Abbreviation)
	err = os.MkdirAll(previewDir, 0755)
	require.NoError(t, err)
	err = os.WriteFile(filepath.Join(previewDir, "preview.png"), []byte("cached-data"), 0644)
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/consoles/" + strings.ToLower(console.Abbreviation) + "/preview-screenshot", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusFound, w.Code)
	expectedLocation := "/api/images/previews/" + console.Abbreviation + "/preview.png"
	assert.Equal(t, expectedLocation, w.Header().Get("Location"))
}

// setupTopListTestEnv creates an in-memory DB with seeded consoles, a temp Storage,
// and a gin router with the top-lists/top-rated route.
func setupTopListTestEnv(t *testing.T) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(&db.Console{}, &db.Game{}, &db.TopRatedGame{}, &db.GameReleaseDate{}, &db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{})
	require.NoError(t, err)

	err = db.SeedConsoles(database)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &ConsoleHandler{DB: database}
	router.GET("/api/top-lists/top-rated", handler.GetTopListAvailable)

	return database, router
}

func TestGetTopListAvailable_ReturnsOnlyLocalMatches(t *testing.T) {
	database, router := setupTopListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

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
		FileName: "smb.nes", FilePath: "/games/smb.nes", FileSize: 1024,
		CoverURL: "covers/nes/smb.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Len(t, result, 1, "should return only the game with a local match")
	assert.Equal(t, "Super Mario Bros.", result[0].Name)
	assert.Equal(t, "/api/images/covers/nes/smb.png", result[0].CoverUrl)
	assert.Equal(t, "nes", result[0].ConsoleId)
	assert.Equal(t, 92.5, result[0].Rating)
}

func TestGetTopListAvailable_SortedByRatingDesc(t *testing.T) {
	database, router := setupTopListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

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
			FileSize: 1024,
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	require.Len(t, result, 3)
	assert.Equal(t, "Game B", result[0].Name)
	assert.Equal(t, 95.0, result[0].Rating)
	assert.Equal(t, "Game C", result[1].Name)
	assert.Equal(t, 88.0, result[1].Rating)
	assert.Equal(t, "Game A", result[2].Name)
	assert.Equal(t, 80.0, result[2].Rating)
}

func TestGetTopListAvailable_SequentialRanks(t *testing.T) {
	database, router := setupTopListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

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
		FileName: "first.nes", FilePath: "/games/first.nes", FileSize: 1024,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Second",
		FileName: "second.nes", FilePath: "/games/second.nes", FileSize: 1024,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	require.Len(t, result, 2)
	assert.Equal(t, 1, result[0].Rank, "first result should have rank 1")
	assert.Equal(t, 2, result[1].Rank, "second result should have rank 2")
}

func TestGetTopListAvailable_EmptyWhenNoMatches(t *testing.T) {
	database, router := setupTopListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	// Create top-rated entries with no local game matches
	database.Create(&db.TopRatedGame{
		ConsoleID: nesConsole.ID, IGDBGameID: 100, Name: "No Match Game",
		TotalRating: 90.0, TotalRatingCount: 500, Rank: 1,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Empty(t, result, "should return empty array when no local matches exist")
}

func TestGetTopListAvailable_EmptyWithNoData(t *testing.T) {
	_, router := setupTopListTestEnv(t)

	// No top-rated entries and no games at all
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err := json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Empty(t, result, "should return empty array when no data exists")
}

func TestGetTopListAvailable_IncludesConsoleInfo(t *testing.T) {
	database, router := setupTopListTestEnv(t)

	var snesConsole db.Console
	err := database.Where("abbreviation = ?", "SNES").First(&snesConsole).Error
	require.NoError(t, err)

	database.Create(&db.TopRatedGame{
		ConsoleID: snesConsole.ID, IGDBGameID: 100, Name: "Super Mario World",
		TotalRating: 95.0, TotalRatingCount: 600, UserRating: 95.0, UserRatingCount: 500, CriticRating: 93.0, CriticRatingCount: 15, Rank: 1,
	})

	database.Create(&db.Game{
		ConsoleID: snesConsole.ID, Title: "Super Mario World",
		FileName: "smw.sfc", FilePath: "/games/smw.sfc", FileSize: 2048,
		CoverURL: "covers/snes/smw.png",
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/top-rated", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []TopListGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	require.Len(t, result, 1)
	assert.Equal(t, "Super Nintendo", result[0].ConsoleName)
	assert.Equal(t, "snes", result[0].ConsoleId)
	assert.Equal(t, "/api/images/covers/snes/smw.png", result[0].CoverUrl)
	assert.NotEmpty(t, result[0].GameId)
}

// --- GetTopListLongest tests ---

// setupLongestListTestEnv creates an in-memory DB with seeded consoles and a gin
// router with the top-lists/longest route.
func setupLongestListTestEnv(t *testing.T) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(&db.Console{}, &db.Game{}, &db.TopRatedGame{}, &db.GameReleaseDate{}, &db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{})
	require.NoError(t, err)

	err = db.SeedConsoles(database)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &ConsoleHandler{DB: database}
	router.GET("/api/top-lists/longest", handler.GetTopListLongest)

	return database, router
}

func TestGetTopListLongest_SortedByTimeToBeatDesc(t *testing.T) {
	database, router := setupLongestListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	// Create games with varying time-to-beat values
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Short Game",
		FileName: "short.nes", FilePath: "/games/short.nes", FileSize: 1024,
		TimeToBeatNormally: 10, TimeToBeatHastily: 5, TimeToBeatCompletely: 20,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Long Game",
		FileName: "long.nes", FilePath: "/games/long.nes", FileSize: 1024,
		TimeToBeatNormally: 100, TimeToBeatHastily: 60, TimeToBeatCompletely: 200,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Medium Game",
		FileName: "medium.nes", FilePath: "/games/medium.nes", FileSize: 1024,
		TimeToBeatNormally: 50, TimeToBeatHastily: 30, TimeToBeatCompletely: 80,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

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
	database, router := setupLongestListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	// Game with time-to-beat data
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Has Time Data",
		FileName: "has.nes", FilePath: "/games/has.nes", FileSize: 1024,
		TimeToBeatNormally: 40, TimeToBeatHastily: 20, TimeToBeatCompletely: 60,
	})
	// Game without time-to-beat data (zero value)
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "No Time Data",
		FileName: "no.nes", FilePath: "/games/no.nes", FileSize: 1024,
		TimeToBeatNormally: 0,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Len(t, result, 1, "should only include games with time_to_beat_normally > 0")
	assert.Equal(t, "Has Time Data", result[0].Name)
}

func TestGetTopListLongest_EmptyWhenNoGamesHaveTimeData(t *testing.T) {
	database, router := setupLongestListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	// Create games without time data
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Game A",
		FileName: "a.nes", FilePath: "/games/a.nes", FileSize: 1024,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Game B",
		FileName: "b.nes", FilePath: "/games/b.nes", FileSize: 1024,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Empty(t, result, "should return empty array when no games have time data")
}

func TestGetTopListLongest_SequentialRanks(t *testing.T) {
	database, router := setupLongestListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "First",
		FileName: "first.nes", FilePath: "/games/first.nes", FileSize: 1024,
		TimeToBeatNormally: 100,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Second",
		FileName: "second.nes", FilePath: "/games/second.nes", FileSize: 1024,
		TimeToBeatNormally: 50,
	})
	database.Create(&db.Game{
		ConsoleID: nesConsole.ID, Title: "Third",
		FileName: "third.nes", FilePath: "/games/third.nes", FileSize: 1024,
		TimeToBeatNormally: 25,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	require.Len(t, result, 3)
	assert.Equal(t, 1, result[0].Rank, "first result should have rank 1")
	assert.Equal(t, 2, result[1].Rank, "second result should have rank 2")
	assert.Equal(t, 3, result[2].Rank, "third result should have rank 3")
}

func TestGetTopListLongest_LimitOf50(t *testing.T) {
	database, router := setupLongestListTestEnv(t)

	var nesConsole db.Console
	err := database.Where("abbreviation = ?", "NES").First(&nesConsole).Error
	require.NoError(t, err)

	// Create 55 games with time-to-beat data
	for i := 1; i <= 55; i++ {
		database.Create(&db.Game{
			ConsoleID:          nesConsole.ID,
			Title:              fmt.Sprintf("Game %03d", i),
			FileName:           fmt.Sprintf("game%03d.nes", i),
			FilePath:           fmt.Sprintf("/games/game%03d.nes", i),
			FileSize:           1024,
			TimeToBeatNormally: i * 10,
		})
	}

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/top-lists/longest", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []LongestGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)

	assert.Len(t, result, 50, "should be limited to 50 results")
	// The first result should be the game with the highest time (55 * 10 = 550)
	assert.Equal(t, 550, result[0].TimeToBeatNormally)
	assert.Equal(t, 1, result[0].Rank)
	// The last result should be the 50th longest (game 6 → 60 hours)
	assert.Equal(t, 60, result[49].TimeToBeatNormally)
	assert.Equal(t, 50, result[49].Rank)
}
