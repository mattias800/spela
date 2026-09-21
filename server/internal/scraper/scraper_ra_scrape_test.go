package scraper

import (
	"context"
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

// unmatchedROMContent hashes to something newTestRAServerCounting does not
// know, so lookups for it come back Success:true GameID:0 — RA's "no match".
var unmatchedROMContent = []byte("rom content that RA has never seen")

// failingRAServer always fails gameid lookups with a 500 — a transient
// upstream failure, as opposed to a definitive "no match".
func failingRAServer(t *testing.T, lookups *int64) *httptest.Server {
	t.Helper()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		atomic.AddInt64(lookups, 1)
		http.Error(w, "upstream exploded", http.StatusInternalServerError)
	}))
	t.Cleanup(srv.Close)
	return srv
}

// setupScrapeTest builds a scraper over an in-memory DB with one playable
// console, a ROM the mock recognises and one it doesn't.
func setupScrapeTest(t *testing.T, mockRA *httptest.Server) (*Scraper, db.Console) {
	t.Helper()

	database := setupRAFetchTestDB(t)

	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	require.NoError(t, os.MkdirAll(romDir, 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "game.nes"), testROMContent, 0o644))
	require.NoError(t, os.WriteFile(filepath.Join(romDir, "unmatched.nes"), unmatchedROMContent, 0o644))

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

// createScrapeGame creates a game pointing at the given ROM filename.
func createScrapeGame(t *testing.T, s *Scraper, console db.Console, title, file string) db.Game {
	t.Helper()
	game := db.Game{
		ConsoleID: console.ID, Title: title,
		FileName: file, FilePath: filepath.Join("roms", file),
	}
	require.NoError(t, s.DB.Create(&game).Error)
	return game
}

func reloadGame(t *testing.T, s *Scraper, id uint) db.Game {
	t.Helper()
	var g db.Game
	require.NoError(t, s.DB.First(&g, id).Error)
	return g
}

func TestScrapeRAAchievements_SkipsHashCheckedNoMatch(t *testing.T) {
	var lookups int64
	mockRA := newTestRAServerCounting(t, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)

	// A game already known to have no RA match must never be re-queried.
	game := createScrapeGame(t, s, console, "Unmatched", "unmatched.nes")
	require.NoError(t, s.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true}).Error)

	_, total, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)

	assert.Equal(t, 0, total, "already-checked game should not be selected for scraping")
	assert.Equal(t, int64(0), atomic.LoadInt64(&lookups), "no dorequest.php traffic for already-checked games")
	assert.True(t, reloadGame(t, s, game.ID).RAHashChecked)
}

func TestScrapeRAAchievements_SetsHashCheckedOnNoMatch(t *testing.T) {
	var lookups int64
	mockRA := newTestRAServerCounting(t, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)
	game := createScrapeGame(t, s, console, "Unmatched", "unmatched.nes")

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
	mockRA := newTestRAServerCounting(t, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)
	game := createScrapeGame(t, s, console, "Matched", "game.nes")

	successes, _, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)
	assert.Equal(t, 1, successes)

	updated := reloadGame(t, s, game.ID)
	assert.Equal(t, uint(99), updated.RAGameID)
	assert.True(t, updated.RAHashChecked, "a successful match must also record the hash as checked")
}

// Nothing ever clears RAHashChecked, so the transient branch is the dangerous
// one: marking a game during an RA outage would hide it permanently.
func TestScrapeRAAchievements_TransientFailureStaysRetryable(t *testing.T) {
	var lookups int64
	mockRA := failingRAServer(t, &lookups)

	s, console := setupScrapeTest(t, mockRA)
	game := createScrapeGame(t, s, console, "Blipped", "unmatched.nes")

	_, _, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)

	assert.False(t, reloadGame(t, s, game.ID).RAHashChecked,
		"a transient RA failure must NOT be negative-cached")

	// Still selected on the next run, so it recovers once RA is healthy.
	_, total, err := s.ScrapeRAAchievements(context.Background(), nil)
	require.NoError(t, err)
	assert.Equal(t, 1, total, "a transiently-failed game must stay in scope")
}

// A ROM that isn't readable right now is not an RA answer. Marking it checked
// would permanently hide the game once the share is mounted again — and costs
// nothing upstream, since this path never reaches RA.
func TestFetchRAAchievements_MissingROMStaysRetryable(t *testing.T) {
	var lookups int64
	mockRA := newTestRAServerCounting(t, &lookups)
	defer mockRA.Close()

	s, console := setupScrapeTest(t, mockRA)
	game := createScrapeGame(t, s, console, "On A Dead Mount", "not-on-disk.nes")

	require.NoError(t, s.FetchRAAchievements(&game))

	assert.False(t, reloadGame(t, s, game.ID).RAHashChecked,
		"an unreadable ROM must NOT be negative-cached")
	assert.Equal(t, int64(0), atomic.LoadInt64(&lookups), "no RA traffic for an unreadable ROM")
}
