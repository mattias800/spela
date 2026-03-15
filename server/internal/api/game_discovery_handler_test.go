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

func setupDiscoveryTestEnv(t *testing.T) (*gorm.DB, *gin.Engine) {
	t.Helper()

	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)

	err = database.AutoMigrate(
		&db.Console{}, &db.Game{}, &db.SimilarGame{}, &db.GameReleaseDate{},
		&db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{},
	)
	require.NoError(t, err)

	err = db.SeedConsoles(database)
	require.NoError(t, err)

	gin.SetMode(gin.TestMode)
	router := gin.New()

	handler := &GameDiscoveryHandler{DB: database}
	router.GET("/api/games/:id/similar", handler.GetSimilarGames)
	router.GET("/api/games/:id/developer-games", handler.GetDeveloperGames)

	return database, router
}

func TestGetSimilarGames_GameNotFound(t *testing.T) {
	_, router := setupDiscoveryTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999/similar", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "game not found", resp["error"])
}

func TestGetSimilarGames_NoScraperID(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Unscraped Game",
		FileName:  "game.nes",
		FilePath:  "/tmp/game.nes",
		FileSize:  1024,
		ScraperID: "", // no IGDB scraper ID
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/similar", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Empty(t, result)
}

func TestGetSimilarGames_ReturnsCachedData(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	// Pre-populate cache
	similar1 := db.SimilarGame{
		GameID:         game.ID,
		IGDBGameID:     5678,
		Name:           "Super Mario Bros. 2",
		CoverImageID:   "co9999",
		CoverLocalPath: "NES/5678/cover.jpg",
		Rating:         85.5,
	}
	similar2 := db.SimilarGame{
		GameID:         game.ID,
		IGDBGameID:     9012,
		Name:           "Kirby's Adventure",
		CoverImageID:   "co8888",
		CoverLocalPath: "NES/9012/cover.jpg",
		Rating:       82.0,
	}
	err = database.Create(&similar1).Error
	require.NoError(t, err)
	err = database.Create(&similar2).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/similar", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Len(t, result, 2)
	assert.Equal(t, "Super Mario Bros. 2", result[0].Name)
	assert.Equal(t, 85.5, result[0].Rating)
	assert.Contains(t, result[0].CoverUrl, "/api/images/NES/5678/cover.jpg")
	assert.Nil(t, result[0].LocalGameId, "should be nil when no local game matches")
}

func TestGetSimilarGames_CrossReferencesLocalLibrary(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Super Mario Bros.",
		FileName:  "smb.nes",
		FilePath:  "/tmp/smb.nes",
		FileSize:  1024,
		ScraperID: "igdb:1234",
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	// Create a local game that matches one of the similar games
	localMatch := db.Game{
		ConsoleID: console.ID,
		Title:     "Kirby's Adventure",
		FileName:  "kirby.nes",
		FilePath:  "/tmp/kirby.nes",
		FileSize:  2048,
	}
	err = database.Create(&localMatch).Error
	require.NoError(t, err)

	// Pre-populate cache
	similar := db.SimilarGame{
		GameID:       game.ID,
		IGDBGameID:   9012,
		Name:         "Kirby's Adventure",
		CoverImageID: "co8888",
		Rating:       82.0,
	}
	err = database.Create(&similar).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/similar", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []SimilarGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Len(t, result, 1)
	assert.NotNil(t, result[0].LocalGameId, "should have localGameId when local game matches")
	assert.Equal(t, "2", *result[0].LocalGameId)
}

func TestGetDeveloperGames_GameNotFound(t *testing.T) {
	_, router := setupDiscoveryTestEnv(t)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/999/developer-games", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)

	var resp map[string]interface{}
	err := json.Unmarshal(w.Body.Bytes(), &resp)
	require.NoError(t, err)
	assert.Equal(t, "game not found", resp["error"])
}

func TestGetDeveloperGames_NoDeveloper(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Unknown Game",
		FileName:  "unknown.nes",
		FilePath:  "/tmp/unknown.nes",
		FileSize:  1024,
		Developer: "", // no developer
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/developer-games", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Empty(t, result)
}

func TestGetDeveloperGames_ReturnsOtherGames(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

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

	err = database.Create(&game1).Error
	require.NoError(t, err)
	err = database.Create(&game2).Error
	require.NoError(t, err)
	err = database.Create(&game3).Error
	require.NoError(t, err)
	err = database.Create(&game4).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/developer-games", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Len(t, result, 2, "should return 2 other Nintendo games, excluding the queried game and Sega game")

	// Should be sorted alphabetically
	assert.Equal(t, "Legend of Zelda", result[0].Name)
	assert.Equal(t, "Super Mario Bros. 2", result[1].Name)

	// Should include local game IDs
	assert.Equal(t, "3", result[0].LocalGameId)
	assert.Equal(t, "2", result[1].LocalGameId)

	// Should resolve cover URLs
	assert.Equal(t, "/api/images/NES/3/boxart.jpg", result[0].CoverUrl)
	assert.Equal(t, "/api/images/NES/2/boxart.jpg", result[1].CoverUrl)
}

func TestGetDeveloperGames_ExcludesQueriedGame(t *testing.T) {
	database, router := setupDiscoveryTestEnv(t)

	var console db.Console
	err := database.Where("abbreviation = ?", "NES").First(&console).Error
	require.NoError(t, err)

	game := db.Game{
		ConsoleID: console.ID,
		Title:     "Only Game",
		FileName:  "only.nes",
		FilePath:  "/tmp/only.nes",
		FileSize:  1024,
		Developer: "SoloDev",
	}
	err = database.Create(&game).Error
	require.NoError(t, err)

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", "/api/games/1/developer-games", nil)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	var result []DeveloperGameResponse
	err = json.Unmarshal(w.Body.Bytes(), &result)
	require.NoError(t, err)
	assert.Empty(t, result, "should not return the queried game itself")
}

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
