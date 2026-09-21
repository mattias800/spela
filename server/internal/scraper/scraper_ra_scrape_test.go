package scraper

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"sync/atomic"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// countingRAServer serves gameid lookups and counts them, so tests can assert
// that already-checked games produce no upstream traffic (#1674).
func countingRAServer(t *testing.T, gameID int, lookups *int64) *httptest.Server {
	t.Helper()

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dorequest.php":
			atomic.AddInt64(lookups, 1)
			json.NewEncoder(w).Encode(map[string]interface{}{
				"Success": true,
				"GameID":  float64(gameID),
			})
		case "/API/API_GetGameExtended.php":
			json.NewEncoder(w).Encode(map[string]interface{}{
				"ID":           gameID,
				"Title":        "Test RA Game",
				"Achievements": map[string]interface{}{},
			})
		}
	}))
}

// setupScrapeTest builds a scraper over an in-memory DB with one playable
// console and a ROM file on disk.
func setupScrapeTest(t *testing.T, mockRA *httptest.Server) (*Scraper, db.Console) {
	t.Helper()

	database := setupRAFetchTestDB(t)

	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	require.NoError(t, os.MkdirAll(romDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "game.nes"), testROMContent, 0o644))

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey: "test-api-key",
		GameDirs: []string{tmpDir},
	}
	return s, console
}

func reloadGame(t *testing.T, s *Scraper, id uint) db.Game {
	t.Helper()
	var g db.Game
	require.NoError(t, s.DB.First(&g, id).Error)
	return g
}

func TestScrapeRAAchievements_SkipsHashCheckedNoMatch(t *testing.T) {
	var lookups int64
	mockRA := countingRAServer(t, 0, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)

	// A game already known to have no RA match must never be re-queried.
	game := db.Game{
		ConsoleID: console.ID, Title: "Unmatched", FileName: "game.nes", FilePath: "roms/game.nes",
		RAHashChecked: true, RAGameID: 0,
	}
	require.NoError(t, s.DB.Create(&game).Error)

	_, total, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)

	assert.Equal(t, 0, total, "already-checked game should not be selected for scraping")
	assert.Equal(t, int64(0), atomic.LoadInt64(&lookups), "no dorequest.php traffic for already-checked games")
	assert.True(t, reloadGame(t, s, game.ID).RAHashChecked)
}

func TestScrapeRAAchievements_SetsHashCheckedOnNoMatch(t *testing.T) {
	var lookups int64
	mockRA := countingRAServer(t, 0, &lookups) // GameID 0 => no match
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)

	game := db.Game{
		ConsoleID: console.ID, Title: "Unmatched", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, s.DB.Create(&game).Error)

	_, _, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)

	updated := reloadGame(t, s, game.ID)
	assert.True(t, updated.RAHashChecked, "a completed no-match lookup must be persisted")
	assert.Equal(t, uint(0), updated.RAGameID)
	assert.Equal(t, int64(1), atomic.LoadInt64(&lookups))

	// The whole point: a second run (i.e. the next server boot) must be silent.
	_, _, err = s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)
	assert.Equal(t, int64(1), atomic.LoadInt64(&lookups), "second run must not re-query RA")
}

func TestScrapeRAAchievements_SetsHashCheckedOnMatch(t *testing.T) {
	var lookups int64
	mockRA := countingRAServer(t, 99, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)

	game := db.Game{
		ConsoleID: console.ID, Title: "Matched", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, s.DB.Create(&game).Error)

	successes, _, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)
	assert.Equal(t, 1, successes)

	updated := reloadGame(t, s, game.ID)
	assert.Equal(t, uint(99), updated.RAGameID)
	assert.True(t, updated.RAHashChecked, "a successful match must also record the hash as checked")
}
