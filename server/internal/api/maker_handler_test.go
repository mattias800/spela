package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

// setupMakerTestEnv creates an in-memory DB with auto-migrated tables and seeded
// reference data + consoles, and a gin router with maker routes.
func setupMakerTestEnv(t *testing.T) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(&db.MediaTypeCategory{}, &db.MediaType{}, &db.HardwareMaker{}, &db.Console{}, &db.Game{})
	require.NoError(t, err)

	err = db.SeedMediaTypeCategories(database)
	require.NoError(t, err)

	err = db.SeedMediaTypes(database)
	require.NoError(t, err)

	err = db.SeedHardwareMakers(database)
	require.NoError(t, err)

	err = db.SeedConsoles(database)
	require.NoError(t, err)

	err = db.SeedConsoleMetadata(database)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &MakerHandler{DB: database}
	router.GET("/api/makers", handler.ListMakers)
	router.GET("/api/makers/:code", handler.GetMaker)

	return database, router
}

func TestListMakers_ReturnsOnlyMakersWithGames(t *testing.T) {
	_, router := setupMakerTestEnv(t)

	// With seeded consoles but no games, response should be empty
	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/makers", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var makers []MakerDetailResponse
	err := json.Unmarshal(w.Body.Bytes(), &makers)
	require.NoError(t, err)
	assert.Empty(t, makers, "should return no makers when no consoles have games")
}

func TestGetMaker_NotFound(t *testing.T) {
	_, router := setupMakerTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/makers/nonexistent", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "maker not found", resp["error"])
}

func TestGetMaker_Found(t *testing.T) {
	database, router := setupMakerTestEnv(t)

	// Add a game to a Nintendo console so the maker has consoles with games
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
	req := httptest.NewRequest("GET", "/api/makers/nintendo", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var maker MakerDetailResponse
	err = json.Unmarshal(w.Body.Bytes(), &maker)
	require.NoError(t, err)
	assert.Equal(t, "nintendo", maker.Code)
	assert.Equal(t, "Nintendo", maker.Name)
	assert.Equal(t, 1, maker.ConsoleCount)
	assert.Len(t, maker.Consoles, 1)
	assert.Equal(t, "NES", maker.Consoles[0].Abbreviation)
}
