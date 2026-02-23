package scraper

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestScrapeGame_PrefersLibRetroBoxArt(t *testing.T) {
	// Mock IGDB with a cover image
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
				Name:    "Test Game",
				Summary: "A test game",
				Cover:   &igdb.Image{ID: 1, ImageID: "co1234"},
			},
		})
	}))
	defer igdbServer.Close()

	// Image server that serves both IGDB and LibRetro images
	imageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
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

	// Also override the libretro thumbnail base to point to our test server
	origLibRetroBase := libRetroThumbnailBase
	defer func() { libRetroThumbnailBase = origLibRetroBase }()
	libRetroThumbnailBase = imageServer.URL

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

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// Both covers should be stored
	assert.NotEmpty(t, game.IGDBCoverURL, "IGDB cover should be stored")
	assert.NotEmpty(t, game.LibRetroCoverURL, "LibRetro cover should be stored")

	// Active cover should be the LibRetro one (preferred)
	assert.Equal(t, game.LibRetroCoverURL, game.CoverURL, "active cover should be LibRetro")

	// File paths should be distinct
	assert.Contains(t, game.IGDBCoverURL, "boxart-igdb")
	assert.Contains(t, game.LibRetroCoverURL, "boxart-libretro")
}

func TestScrapeGame_FallsBackToIGDBCover_WhenLibRetroMissing(t *testing.T) {
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
				ID:    42,
				Name:  "Rare Game",
				Cover: &igdb.Image{ID: 1, ImageID: "co1234"},
			},
		})
	}))
	defer igdbServer.Close()

	// Image server that serves IGDB images but returns 404 for LibRetro
	imageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Serve IGDB images, reject LibRetro-style requests
		if r.URL.Path == "/t_cover_big/co1234.jpg" {
			w.Header().Set("Content-Type", "image/jpeg")
			w.Write([]byte("fake-igdb-image"))
			return
		}
		// LibRetro requests fail
		w.WriteHeader(http.StatusNotFound)
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

	// Point LibRetro thumbnail base to the image server (which will 404)
	origLibRetroBase := libRetroThumbnailBase
	defer func() { libRetroThumbnailBase = origLibRetroBase }()
	libRetroThumbnailBase = imageServer.URL

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID: console.ID,
		Console:   console,
		Title:     "Rare Game",
		FileName:  "Rare Game.nes",
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

	// IGDB cover should be stored
	assert.NotEmpty(t, game.IGDBCoverURL, "IGDB cover should be stored")
	// LibRetro cover should be empty
	assert.Empty(t, game.LibRetroCoverURL, "LibRetro cover should be empty")

	// Active cover should fall back to IGDB
	assert.Equal(t, game.IGDBCoverURL, game.CoverURL, "active cover should fall back to IGDB")
}

func TestScrapeGame_ReScrape_ClearsBothCovers(t *testing.T) {
	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:        console.ID,
		Console:          console,
		Title:            "Test Game",
		FileName:         "Test Game.nes",
		ScrapeAttempts:   1,
		CoverURL:         "old-cover.png",
		LibRetroCoverURL: "old-libretro.png",
		IGDBCoverURL:     "old-igdb.jpg",
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

	err := s.ScrapeGame(&game)
	require.NoError(t, err)

	// All cover URLs should be cleared on re-scrape (since no images could be fetched)
	assert.Empty(t, game.LibRetroCoverURL, "LibRetro cover should be cleared on re-scrape")
	assert.Empty(t, game.IGDBCoverURL, "IGDB cover should be cleared on re-scrape")
	assert.Empty(t, game.CoverURL, "active cover should be cleared when no source available")
}

func TestScrapeGame_ReScrape_PreservesManualOverride(t *testing.T) {
	// Image server that serves both IGDB and LibRetro images
	imageServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Write([]byte("fake-image-data"))
	}))
	defer imageServer.Close()

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
				ID:    42,
				Name:  "Test Game",
				Cover: &igdb.Image{ID: 1, ImageID: "co1234"},
			},
		})
	}))
	defer igdbServer.Close()

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

	origLibRetroBase := libRetroThumbnailBase
	defer func() { libRetroThumbnailBase = origLibRetroBase }()
	libRetroThumbnailBase = imageServer.URL

	database := setupTestDB(t)
	store := setupTestStorage(t)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	// Simulate a game where admin manually selected IGDB cover
	game := db.Game{
		ConsoleID:        console.ID,
		Console:          console,
		Title:            "Test Game",
		FileName:         "Test Game.nes",
		ScrapeAttempts:   1,
		CoverURL:         "NES/1/boxart-igdb.jpg",
		LibRetroCoverURL: "NES/1/boxart-libretro.png",
		IGDBCoverURL:     "NES/1/boxart-igdb.jpg",
		CoverManuallySet: true,
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

	// After re-scrape, IGDB should still be the active cover (admin's choice preserved)
	assert.NotEmpty(t, game.IGDBCoverURL, "IGDB cover should be re-downloaded")
	assert.NotEmpty(t, game.LibRetroCoverURL, "LibRetro cover should be re-downloaded")
	assert.Equal(t, game.IGDBCoverURL, game.CoverURL, "admin's IGDB choice should be preserved after re-scrape")
	assert.True(t, game.CoverManuallySet, "CoverManuallySet should remain true")
}
