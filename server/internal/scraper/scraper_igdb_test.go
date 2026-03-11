package scraper

import (
	"encoding/json"
	"fmt"
	"hash/crc32"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/spela/server/internal/storage"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	dbPath := filepath.Join(t.TempDir(), "test.db")
	database, err := gorm.Open(sqlite.Open(dbPath+"?_journal_mode=WAL&_busy_timeout=5000"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.Console{}, &db.Game{}, &db.GameScreenshot{},
		&db.GameTheme{}, &db.GameKeyword{}, &db.GamePlayerPerspective{},
		&db.GameFranchise{}, &db.GameSeries{}, &db.GameSeriesEntry{},
		&db.GameArtworkImage{}, &db.GameReleaseDate{},
		&db.GameVideo{}, &db.GameLanguageSupport{}, &db.GameAgeRating{},
	))
	return database
}

func setupTestStorage(t *testing.T) *storage.Storage {
	t.Helper()
	dir := t.TempDir()
	store, err := storage.NewStorage(dir+"/saves", dir+"/cores", dir+"/images", dir+"/bios")
	require.NoError(t, err)
	return store
}

func TestScrapeGame_IGDBMetadata(t *testing.T) {
	// Set up IGDB mock
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{
				ID:      42,
				Name:    "Super Mario Bros.",
				Summary: "A classic Nintendo platformer",
				Cover:   &igdb.Image{ID: 1, ImageID: "co1234"},
				Screenshots: []igdb.Image{
					{ID: 2, ImageID: "sc5678"},
					{ID: 3, ImageID: "sc9012"},
					{ID: 4, ImageID: "sc3456"},
				},
				Genres: []igdb.Genre{
					{ID: 8, Name: "Platform"},
				},
				InvolvedCompanies: []igdb.InvolvedCompany{
					{Company: igdb.Company{ID: 1, Name: "Nintendo R&D1"}, Developer: true},
					{Company: igdb.Company{ID: 2, Name: "Nintendo"}, Publisher: true},
				},
				FirstReleaseDate: 496800000,
				AggregatedRating: 85.5,
				GameModes: []igdb.GameMode{
					{ID: 1, Name: "Single player"},
					{ID: 2, Name: "Multiplayer"},
				},
			},
		})
	}))
	defer igdbServer.Close()

	// Set up image server for IGDB cover/screenshot
	imageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/jpeg")
		w.Write([]byte("fake-image-data"))
	}))
	defer imageServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Super Mario Bros. (USA)",
		FileName:  "Super Mario Bros. (USA).nes",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	// Override image base to point to our test server
	origImageBase := igdb.ImageBaseForTest()
	igdb.SetImageBaseForTest(imageServer.URL)
	defer igdb.SetImageBaseForTest(origImageBase)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Verify IGDB metadata
	assert.Equal(t, "Super Mario Bros.", game.Title)
	assert.Equal(t, "A classic Nintendo platformer", game.Description)
	assert.Equal(t, "Nintendo R&D1", game.Developer)
	assert.Equal(t, "Nintendo", game.Publisher)
	assert.Equal(t, "Platform", game.Genre)
	assert.Equal(t, "1985-09-29", game.ReleaseDate)
	assert.InDelta(t, 85.5, game.Rating, 0.01)
	assert.Equal(t, 2, game.Players)
	assert.Equal(t, "igdb:42", game.ScraperID)

	// Verify cover was downloaded
	assert.NotEmpty(t, game.CoverURL)

	// Verify all 3 screenshots were saved as GameScreenshot rows
	var screenshots []db.GameScreenshot
	database.Where("game_id = ?", game.ID).Order("position ASC").Find(&screenshots)
	assert.Len(t, screenshots, 3)
	for i, ss := range screenshots {
		assert.Equal(t, i, ss.Position)
		assert.NotEmpty(t, ss.URL)
	}
}

func TestScrapeGame_IGDBNoResults_FallsBackToLibRetro(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Return empty results
		json.NewEncoder(w).Encode([]igdb.Game{})
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Unknown Game",
		FileName:  "Unknown Game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Without LibRetro server, images won't be found, but it should fall back gracefully
	assert.Equal(t, "libretro", game.ScraperID)
	assert.Equal(t, 1, game.ScrapeAttempts)
}

func TestScrapeGame_IGDBError_FallsBackGracefully(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
		w.Write([]byte("internal error"))
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Test Game",
		FileName:  "Test Game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	// Should not return an error — falls back gracefully
	err := s.ScrapeGame(&game)
	require.NoError(t, err)
	assert.Equal(t, "libretro", game.ScraperID)
}

func TestScrapeGame_NoIGDB_LibRetroOnly(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Test Game",
		FileName:  "Test Game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: nil, // no IGDB configured
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)
	assert.Equal(t, "libretro", game.ScraperID)
	assert.Equal(t, 1, game.ScrapeAttempts)
}

func TestScrapeGame_DoesNotOverwriteWithEmptyValues(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Return a result with minimal metadata
		json.NewEncoder(w).Encode([]igdb.Game{
			{
				ID:   99,
				Name: "New Title",
				// No summary, no genres, no companies, etc.
			},
		})
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:   console.ID,
		Console:     console,
		Title:       "Original Title",
		FileName:    "Test.nes",
		Developer:   "Original Dev",
		Publisher:   "Original Pub",
		Genre:       "Original Genre",
		Description: "Original Description",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Title should be updated (IGDB returned a name)
	assert.Equal(t, "New Title", game.Title)
	// These should remain unchanged (IGDB returned empty values)
	assert.Equal(t, "Original Dev", game.Developer)
	assert.Equal(t, "Original Pub", game.Publisher)
	assert.Equal(t, "Original Genre", game.Genre)
	// Description should NOT be overwritten since IGDB returned empty
	// (but Summary was empty, so Description stays)
	assert.Equal(t, "Original Description", game.Description)
	assert.Equal(t, "igdb:99", game.ScraperID)
}

func TestScrapeGame_UnknownPlatform_SkipsIGDB(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		t.Fatal("should not call IGDB for unknown platform")
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "UNKNOWNPLATFORM", Name: "Unknown"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Test Game",
		FileName:  "Test Game.rom",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)
	// Should fall back to libretro (even though LibRetro also won't know this platform)
	assert.Equal(t, "libretro", game.ScraperID)
}

// writeROMFile writes content to a file and returns the CRC32 as uppercase hex.
func writeROMFile(t *testing.T, path string, content []byte) string {
	t.Helper()
	require.NoError(t, os.MkdirAll(filepath.Dir(path), 0o755))
	require.NoError(t, os.WriteFile(path, content, 0o644))
	h := crc32.NewIEEE()
	h.Write(content)
	return strings.ToUpper(fmt.Sprintf("%08x", h.Sum32()))
}

func TestScrapeGame_CRCMatch_SetsVerified(t *testing.T) {
	// Set up IGDB mock
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{ID: 10, Name: "Verified Game"},
		})
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	// Create a ROM file with known content and compute its CRC
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romPath := filepath.Join(nesDir, "TestGame (USA).nes")
	romContent := []byte("test rom content for verification")
	crc := writeROMFile(t, romPath, romContent)

	// Create a DAT file that contains this CRC
	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := fmt.Sprintf(`game (
	name "Canonical Game Name (USA)"
	rom ( name "Canonical Game Name (USA).nes" size %d crc %s md5 abc sha1 def )
)
`, len(romContent), crc)
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	gameDirs := []string{gameDir}
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "TestGame (USA)",
		FileName:  "TestGame (USA).nes",
		FilePath:  filepath.Join("nes", "TestGame (USA).nes"),
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(datDir, &http.Client{Timeout: 5 * time.Second}),
		GameDirs:   gameDirs,
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	assert.Equal(t, "verified", game.VerificationStatus)
	assert.Equal(t, crc, game.CRC32)
}

func TestScrapeGame_CRCNoMatch_SetsUnverified(t *testing.T) {
	// Set up IGDB mock
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{ID: 11, Name: "Unverified Game"},
		})
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	// Create a ROM file
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romPath := filepath.Join(nesDir, "Unknown (USA).nes")
	romContent := []byte("unmatched rom content")
	crc := writeROMFile(t, romPath, romContent)

	// Create a DAT file that does NOT contain this CRC
	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := `game (
	name "Other Game (USA)"
	rom ( name "Other Game (USA).nes" size 100 crc DEADBEEF md5 abc sha1 def )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	gameDirs := []string{gameDir}
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Unknown (USA)",
		FileName:  "Unknown (USA).nes",
		FilePath:  filepath.Join("nes", "Unknown (USA).nes"),
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(datDir, &http.Client{Timeout: 5 * time.Second}),
		GameDirs:   gameDirs,
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	assert.Equal(t, "unverified", game.VerificationStatus)
	assert.Equal(t, crc, game.CRC32)
}

func TestScrapeAll_DefaultOnlyUnscraped(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	// Use an unknown platform so no real HTTP requests are made
	console := db.Console{Abbreviation: "TESTPLAT", Name: "Test Platform"}
	require.NoError(t, database.Create(&console).Error)

	romDir := t.TempDir()

	// One scraped game, one unscraped
	scraped := db.Game{ConsoleID: console.ID, Console: console, Title: "Scraped", FileName: "scraped.rom", FilePath: filepath.Join(romDir, "scraped.rom"), ScraperID: "igdb:1"}
	unscraped := db.Game{ConsoleID: console.ID, Console: console, Title: "Unscraped", FileName: "unscraped.rom", FilePath: filepath.Join(romDir, "unscraped.rom")}
	require.NoError(t, database.Create(&scraped).Error)
	require.NoError(t, database.Create(&unscraped).Error)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	successes, total, err := s.ScrapeAll("", nil)
	require.NoError(t, err)
	assert.Equal(t, 1, total, "should only attempt the unscraped game")
	assert.Equal(t, 1, successes)
}

func TestScrapeAll_ForceScrapesAll(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	// Use an unknown platform so no real HTTP requests are made
	console := db.Console{Abbreviation: "TESTPLAT", Name: "Test Platform"}
	require.NoError(t, database.Create(&console).Error)

	romDir := t.TempDir()

	// Both games already scraped
	g1 := db.Game{ConsoleID: console.ID, Console: console, Title: "Game 1", FileName: "g1.rom", FilePath: filepath.Join(romDir, "g1.rom"), ScraperID: "igdb:1"}
	g2 := db.Game{ConsoleID: console.ID, Console: console, Title: "Game 2", FileName: "g2.rom", FilePath: filepath.Join(romDir, "g2.rom"), ScraperID: "igdb:2"}
	require.NoError(t, database.Create(&g1).Error)
	require.NoError(t, database.Create(&g2).Error)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	successes, total, err := s.ScrapeAll("all", nil)
	require.NoError(t, err)
	assert.Equal(t, 2, total, "force should attempt all games")
	assert.Equal(t, 2, successes)
}

func TestScrapeGameWithIGDBMatch_Success(t *testing.T) {
	// Set up IGDB mock that responds to GetGameByID
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{
			{
				ID:      999,
				Name:    "Disney's Aladdin",
				Summary: "The correct Aladdin game",
				Cover:   &igdb.Image{ID: 1, ImageID: "co7777"},
				Screenshots: []igdb.Image{
					{ID: 2, ImageID: "sc8888"},
				},
				Genres: []igdb.Genre{
					{ID: 8, Name: "Platform"},
				},
				InvolvedCompanies: []igdb.InvolvedCompany{
					{Company: igdb.Company{ID: 1, Name: "Capcom"}, Developer: true},
					{Company: igdb.Company{ID: 2, Name: "Nintendo"}, Publisher: true},
				},
				FirstReleaseDate: 753926400,
				AggregatedRating: 82.0,
				GameModes: []igdb.GameMode{
					{ID: 1, Name: "Single player"},
				},
			},
		})
	}))
	defer igdbServer.Close()

	imageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/jpeg")
		w.Write([]byte("fake-image-data"))
	}))
	defer imageServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	origImageBase := igdb.ImageBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	igdb.SetImageBaseForTest(imageServer.URL)
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
		igdb.SetImageBaseForTest(origImageBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)

	// Create a game that was previously scraped with the wrong IGDB match
	game := db.Game{
		ConsoleID:      console.ID,
		Console:        console,
		Title:          "Aladdin 2000",
		FileName:       "Aladdin (USA).sfc",
		Description:    "Wrong description from bootleg",
		Developer:      "Bootleg Corp",
		Publisher:      "Bootleg Corp",
		Genre:          "Action",
		Rating:         10.0,
		Players:        1,
		ReleaseDate:    "2000-01-01",
		ScraperID:      "igdb:555",
		ScrapeAttempts: 1,
	}
	require.NoError(t, database.Create(&game).Error)

	// Add an old screenshot that should be cleared
	database.Create(&db.GameScreenshot{GameID: game.ID, URL: "/old/screenshot.jpg", Position: 0})

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGameWithIGDBMatch(&game, 999)
	require.NoError(t, err)

	// Verify all metadata was replaced with the correct IGDB match
	assert.Equal(t, "Disney's Aladdin", game.Title)
	assert.Equal(t, "The correct Aladdin game", game.Description)
	assert.Equal(t, "Capcom", game.Developer)
	assert.Equal(t, "Nintendo", game.Publisher)
	assert.Equal(t, "Platform", game.Genre)
	assert.Equal(t, "1993-11-22", game.ReleaseDate)
	assert.InDelta(t, 82.0, game.Rating, 0.01)
	assert.Equal(t, 1, game.Players)
	assert.Equal(t, "igdb:999", game.ScraperID)
	assert.Equal(t, 2, game.ScrapeAttempts)

	// Verify cover was downloaded
	assert.NotEmpty(t, game.IGDBCoverURL)

	// Verify new screenshots replaced old ones
	var screenshots []db.GameScreenshot
	database.Where("game_id = ?", game.ID).Find(&screenshots)
	assert.Len(t, screenshots, 1)
	assert.NotEqual(t, "/old/screenshot.jpg", screenshots[0].URL)
}

func TestScrapeGameWithIGDBMatch_NotFound(t *testing.T) {
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode([]igdb.Game{})
	}))
	defer igdbServer.Close()

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	defer func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	}()

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Test Game",
		FileName:  "Test.sfc",
		ScraperID: "igdb:555",
	}
	require.NoError(t, database.Create(&game).Error)

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: igdbClient,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGameWithIGDBMatch(&game, 99999)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not found")
}

func TestScrapeGameWithIGDBMatch_NoIGDBClient(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "SNES", Name: "Super Nintendo"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Test Game",
		FileName:  "Test.sfc",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		IGDBClient: nil,
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGameWithIGDBMatch(&game, 999)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not configured")
}

func TestScrapeGame_RescrapesClearsStaleImages(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	// Use an unknown platform so LibRetro image fallback doesn't fire
	console := db.Console{Abbreviation: "TESTPLAT", Name: "Test Platform"}
	require.NoError(t, database.Create(&console).Error)

	romDir := t.TempDir()

	game := db.Game{
		ConsoleID:      console.ID,
		Console:        console,
		Title:          "Test Game",
		FileName:       "test.rom",
		FilePath:       filepath.Join(romDir, "test.rom"),
		CoverURL:       "/old/cover.png",
		ScreenshotURL:  "/old/screenshot.png",
		ScrapeAttempts: 1, // already scraped once
		ScraperID:      "libretro",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:         database,
		Storage:    store,
		HTTPClient: &http.Client{Timeout: 5 * time.Second},
		DATCache:   NewDATCache(t.TempDir(), &http.Client{Timeout: 5 * time.Second}),
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Images should be cleared since no image server is available
	// (stale images from previous scrape are removed before attempting download)
	assert.Empty(t, game.CoverURL, "stale cover should be cleared on re-scrape")
	assert.Empty(t, game.ScreenshotURL, "stale screenshot should be cleared on re-scrape")
	assert.Equal(t, 2, game.ScrapeAttempts)

	// Verify normalized screenshots table is also empty after re-scrape
	var screenshots []db.GameScreenshot
	database.Where("game_id = ?", game.ID).Find(&screenshots)
	assert.Empty(t, screenshots, "normalized screenshots should be cleared on re-scrape")
}
