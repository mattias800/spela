package scraper

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"fmt"
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

func TestTryFetchRAAchievements_SkipsWhenNotConfigured(t *testing.T) {
	database := setupRAFetchTestDB(t)
	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "t.nes", FilePath: "t.nes"}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database} // No RA config
	s.tryFetchRAAchievements(&game)
	// Should not panic or error — just a no-op
	assert.False(t, s.raCircuitOpen)
}

func TestTryFetchRAAchievements_SkipsWhenCircuitOpen(t *testing.T) {
	database := setupRAFetchTestDB(t)
	mockRA := newTestRAServer(t)
	defer mockRA.Close()

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Test", FileName: "t.nes", FilePath: "t.nes"}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:            database,
		RAClient:      &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey:      "test-key",
		GameDirs:      []string{t.TempDir()},
		raCircuitOpen: true, // Already tripped
	}

	s.tryFetchRAAchievements(&game)
	// Should skip — no RA calls made, no cache populated
	var count int64
	database.Model(&db.GameAchievementCache{}).Count(&count)
	assert.Equal(t, int64(0), count)
}

func TestTryFetchRAAchievements_CircuitTripsAfterConsecutiveFailures(t *testing.T) {
	database := setupRAFetchTestDB(t)

	// Mock RA server that always returns 500
	failingRA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer failingRA.Close()

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)

	tmpDir := t.TempDir()

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: failingRA.URL, HTTPClient: failingRA.Client()},
		RAAPIKey: "test-key",
		GameDirs: []string{tmpDir},
	}

	// Create 6 games with ROM files (circuit trips at 5)
	for i := 0; i < 6; i++ {
		fname := fmt.Sprintf("game%d.nes", i)
		os.WriteFile(filepath.Join(tmpDir, fname), []byte(fmt.Sprintf("rom%d", i)), 0o644)
		game := db.Game{ConsoleID: console.ID, Title: fname, FileName: fname, FilePath: fname}
		require.NoError(t, database.Create(&game).Error)
		s.tryFetchRAAchievements(&game)
	}

	assert.True(t, s.raCircuitOpen)
	assert.Equal(t, 5, s.raConsecutiveFailures) // Stops incrementing after trip
}

func TestTryFetchRAAchievements_SuccessResetsCounter(t *testing.T) {
	database := setupRAFetchTestDB(t)
	mockRA := newTestRAServer(t)
	defer mockRA.Close()

	tmpDir := t.TempDir()
	os.MkdirAll(filepath.Join(tmpDir, "roms"), 0o755)
	os.WriteFile(filepath.Join(tmpDir, "roms/game.nes"), testROMContent, 0o644)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:                    database,
		RAClient:              &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey:              "test-key",
		GameDirs:              []string{tmpDir},
		raConsecutiveFailures: 3, // Some prior failures
	}

	s.tryFetchRAAchievements(&game)

	assert.False(t, s.raCircuitOpen)
	assert.Equal(t, 0, s.raConsecutiveFailures)
}
