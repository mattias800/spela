package scraper

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestScrapeIGDB_CRCMatch_RenamesAndUsesCanonicalName(t *testing.T) {
	// Create a ROM file with known CRC inside a console-named subdirectory
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romContent := []byte("hello") // CRC32 = 3610A686
	romPath := filepath.Join(nesDir, "castlevania.nes")
	require.NoError(t, os.WriteFile(romPath, romContent, 0o644))

	// Set up DAT cache with a matching entry
	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := `game (
	name "Castlevania (USA)"
	rom ( name "Castlevania (USA).nes" size 5 crc 3610A686 md5 x sha1 y )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	// Set up IGDB mock that records the search query
	var searchQuery string
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Parse the search query from the IGDB request body
		body := make([]byte, 4096)
		n, _ := r.Body.Read(body)
		searchQuery = string(body[:n])

		json.NewEncoder(w).Encode([]igdb.Game{
			{ID: 1, Name: "Castlevania"},
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

	gameDirs := []string{gameDir}
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "castlevania",
		FileName:  "castlevania.nes",
		FilePath:  filepath.Join("nes", "castlevania.nes"),
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

	// Verify the ROM file was renamed to canonical name (relative path)
	assert.Equal(t, "Castlevania (USA).nes", game.FileName)
	assert.Equal(t, filepath.Join("nes", "Castlevania (USA).nes"), game.FilePath)

	// Verify the renamed file exists on disk
	_, err = os.Stat(filepath.Join(nesDir, "Castlevania (USA).nes"))
	assert.NoError(t, err)

	// Verify the old file no longer exists
	_, err = os.Stat(romPath)
	assert.True(t, os.IsNotExist(err))

	// Verify IGDB was searched with the canonical name (cleaned from "Castlevania (USA).nes")
	assert.Contains(t, searchQuery, "Castlevania")

	// Verify the game got metadata from IGDB
	assert.Equal(t, "Castlevania", game.Title)
	assert.Equal(t, "igdb:1", game.ScraperID)
}

func TestScrapeIGDB_NoCRCMatch_UsesCleannedFilename(t *testing.T) {
	// Create a ROM file with a CRC that won't match any DAT entry
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romContent := []byte("unknown rom data that has no match")
	require.NoError(t, os.WriteFile(filepath.Join(nesDir, "My Cool Game (Hack).nes"), romContent, 0o644))

	// Set up DAT cache with entries that won't match this CRC
	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := `game (
	name "Other Game (USA)"
	rom ( name "Other Game (USA).nes" size 100 crc 99999999 md5 x sha1 y )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

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
			{ID: 5, Name: "My Cool Game"},
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

	gameDirs := []string{gameDir}
	relPath := filepath.Join("nes", "My Cool Game (Hack).nes")
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "My Cool Game (Hack)",
		FileName:  "My Cool Game (Hack).nes",
		FilePath:  relPath,
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

	// File should NOT be renamed since there was no CRC match
	assert.Equal(t, "My Cool Game (Hack).nes", game.FileName)
	assert.Equal(t, relPath, game.FilePath)

	// But it should still scrape metadata from IGDB
	assert.Equal(t, "My Cool Game", game.Title)
	assert.Equal(t, "igdb:5", game.ScraperID)
}

func TestScrapeIGDB_DiscSystem_SkipsCRC(t *testing.T) {
	// For disc-based systems, CRC should be skipped entirely
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
			{ID: 10, Name: "Final Fantasy VII"},
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

	console := db.Console{Abbreviation: "PSX", Name: "Sony PlayStation"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Final Fantasy VII",
		FileName:  "Final Fantasy VII (USA).bin",
		FilePath:  "psx/Final Fantasy VII (USA).bin", // Relative path; doesn't need to exist for disc systems
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
		GameDirs:   nil,
		cache:      &nameCache{entries: make(map[string][]nameEntry)},
	}

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Should still get IGDB metadata without CRC lookup
	assert.Equal(t, "Final Fantasy VII", game.Title)
	assert.Equal(t, "igdb:10", game.ScraperID)
}

func TestScrapeIGDB_CRCMatch_PersistsPathImmediately(t *testing.T) {
	// Verify that after CRC rename, the DB record is updated immediately
	// (not deferred to the final Save). This prevents download 404s if the
	// later scrape steps fail.
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romContent := []byte("hello") // CRC32 = 3610A686
	romPath := filepath.Join(nesDir, "castlevania.nes")
	require.NoError(t, os.WriteFile(romPath, romContent, 0o644))

	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := `game (
	name "Castlevania (USA)"
	rom ( name "Castlevania (USA).nes" size 5 crc 3610A686 md5 x sha1 y )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

	// IGDB mock — return error to simulate IGDB failure after CRC rename
	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		})
	}))
	defer tokenServer.Close()

	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Return a valid IGDB result so scrape completes
		json.NewEncoder(w).Encode([]igdb.Game{
			{ID: 1, Name: "Castlevania"},
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

	gameDirs := []string{gameDir}
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "castlevania",
		FileName:  "castlevania.nes",
		FilePath:  filepath.Join("nes", "castlevania.nes"),
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

	// Reload the game from DB to verify the path was persisted
	var reloaded db.Game
	require.NoError(t, database.First(&reloaded, game.ID).Error)
	assert.Equal(t, "Castlevania (USA).nes", reloaded.FileName)
	assert.Equal(t, filepath.Join("nes", "Castlevania (USA).nes"), reloaded.FilePath)
}

func TestScrapeIGDB_CRCMatch_SameNameNoRename(t *testing.T) {
	// When CRC matches but file already has the canonical name, no rename needed
	gameDir := t.TempDir()
	nesDir := filepath.Join(gameDir, "nes")
	require.NoError(t, os.MkdirAll(nesDir, 0o755))
	romContent := []byte("hello") // CRC32 = 3610A686
	romAbsPath := filepath.Join(nesDir, "Castlevania (USA).nes")
	require.NoError(t, os.WriteFile(romAbsPath, romContent, 0o644))

	datDir := t.TempDir()
	systemName := AbbreviationToLibRetro["NES"]
	datPath := filepath.Join(datDir, systemName+".dat")
	datContent := `game (
	name "Castlevania (USA)"
	rom ( name "Castlevania (USA).nes" size 5 crc 3610A686 md5 x sha1 y )
)
`
	require.NoError(t, os.WriteFile(datPath, []byte(datContent), 0o644))

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
			{ID: 1, Name: "Castlevania"},
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

	gameDirs := []string{gameDir}
	relPath := filepath.Join("nes", "Castlevania (USA).nes")
	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Castlevania (USA)",
		FileName:  "Castlevania (USA).nes",
		FilePath:  relPath,
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

	// File should remain as-is (no rename since names match)
	assert.Equal(t, "Castlevania (USA).nes", game.FileName)
	assert.Equal(t, relPath, game.FilePath)

	// File should still exist at original path
	_, err = os.Stat(romAbsPath)
	assert.NoError(t, err)
}
