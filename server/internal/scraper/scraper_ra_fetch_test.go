package scraper

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var testROMContent = []byte("test rom for ra fetch")

func testROMHash() string {
	h := md5.Sum(testROMContent)
	return hex.EncodeToString(h[:])
}

func setupRAFetchTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.Console{}, &db.Game{}, &db.GameAchievementCache{},
	))
	return database
}

func newTestRAServer(t *testing.T) *httptest.Server {
	t.Helper()
	expectedHash := testROMHash()

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dorequest.php":
			hash := r.URL.Query().Get("m")
			if hash == expectedHash {
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"GameID":  float64(99),
				})
			} else {
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"GameID":  float64(0),
				})
			}
		case "/API/API_GetGameExtended.php":
			json.NewEncoder(w).Encode(map[string]interface{}{
				"ID":    99,
				"Title": "Test RA Game",
				"Achievements": map[string]interface{}{
					"1001": map[string]interface{}{
						"ID":          1001,
						"Title":       "First Step",
						"Description": "Do the first thing",
						"Points":      5,
						"BadgeName":   "badge001",
						"type":        3,
					},
				},
			})
		}
	}))
}

func TestFetchRAAchievements_PopulatesCache(t *testing.T) {
	database := setupRAFetchTestDB(t)
	mockRA := newTestRAServer(t)
	defer mockRA.Close()

	// Create ROM file
	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	os.MkdirAll(romDir, 0o755)
	os.WriteFile(filepath.Join(romDir, "game.nes"), testROMContent, 0o644)

	// Create console + game
	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey: "test-api-key",
		GameDirs: []string{tmpDir},
	}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// Verify RAGameID was cached on the game record
	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, uint(99), updated.RAGameID)
	assert.True(t, updated.RAHashChecked)

	// Verify achievement cache was populated
	var cache db.GameAchievementCache
	require.NoError(t, database.Where("ra_game_id = ?", 99).First(&cache).Error)
	assert.Equal(t, "Test RA Game", cache.Title)
	assert.Equal(t, 1, cache.TotalCount)
	assert.Equal(t, game.ID, cache.GameID)
}

func TestFetchRAAchievements_SkipsWhenCacheFresh(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
		RAGameID: 99, RAHashChecked: true,
	}
	require.NoError(t, database.Create(&game).Error)

	// Pre-populate fresh cache (CachedAt must be recent for the freshness check)
	database.Create(&db.GameAchievementCache{
		RAGameID: 99, GameID: game.ID, Title: "Cached",
		AchievementJSON: "[]", TotalCount: 5, TotalPoints: 50,
		CachedAt: time.Now(),
	})

	// No RA client needed — should be a no-op
	s := &Scraper{DB: database}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// Cache should be untouched
	var cache db.GameAchievementCache
	database.Where("ra_game_id = ?", 99).First(&cache)
	assert.Equal(t, "Cached", cache.Title)
}

func TestFetchRAAchievements_SetsHashCheckedOnNoMatch(t *testing.T) {
	database := setupRAFetchTestDB(t)

	// Mock RA server that returns GameID=0 for all hashes
	mockRA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"GameID":  float64(0),
		})
	}))
	defer mockRA.Close()

	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	os.MkdirAll(romDir, 0o755)
	os.WriteFile(filepath.Join(romDir, "unknown.nes"), []byte("unknown rom"), 0o644)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Unknown Game", FileName: "unknown.nes", FilePath: "roms/unknown.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey: "test-api-key",
		GameDirs: []string{tmpDir},
	}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// RAHashChecked should be true, RAGameID should remain 0
	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, uint(0), updated.RAGameID)
	assert.True(t, updated.RAHashChecked)
}

func TestFetchRAAchievements_SkipsWhenHashCheckedAndNoMatch(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "No RA Game", FileName: "game.nes", FilePath: "roms/game.nes",
		RAGameID: 0, RAHashChecked: true, // Already checked, no match
	}
	require.NoError(t, database.Create(&game).Error)

	// No RA client needed — should skip immediately
	s := &Scraper{DB: database}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)
}

func TestFetchRAAchievements_NoRAConfig(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database} // No RAClient or RAAPIKey

	err := s.FetchRAAchievements(&game)
	assert.Error(t, err)
}
