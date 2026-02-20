package api

import (
	"encoding/json"
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

	err = database.AutoMigrate(&db.Console{}, &db.Game{})
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
