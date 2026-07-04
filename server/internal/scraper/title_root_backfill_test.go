package scraper

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strconv"
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/igdb"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

func setupTitleRootBackfillDB(t *testing.T) *gorm.DB {
	t.Helper()
	database := setupTestDB(t)
	require.NoError(t, database.AutoMigrate(&db.ScrapeJob{}, &db.ScrapeQueueItem{}, &db.ServerSetting{}))
	return database
}

func newTitleRootBackfillIGDBClient(t *testing.T, games map[int]igdb.Game, requests *[]int) *igdb.Client {
	t.Helper()

	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.NoError(t, json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		}))
	}))
	t.Cleanup(tokenServer.Close)

	idPattern := regexp.MustCompile(`where id = ([0-9]+)`)
	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.Equal(t, http.MethodPost, r.Method)
		require.Equal(t, "/v4/games", r.URL.Path)
		body, err := io.ReadAll(r.Body)
		require.NoError(t, err)

		matches := idPattern.FindStringSubmatch(string(body))
		require.Len(t, matches, 2, "request should query one IGDB id: %s", string(body))
		id, err := strconv.Atoi(matches[1])
		require.NoError(t, err)
		*requests = append(*requests, id)

		game, ok := games[id]
		if !ok {
			require.NoError(t, json.NewEncoder(w).Encode([]igdb.Game{}))
			return
		}
		require.NoError(t, json.NewEncoder(w).Encode([]igdb.Game{game}))
	}))
	t.Cleanup(igdbServer.Close)

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	t.Cleanup(func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	})

	client := igdb.NewClient("test-id", "test-secret")
	client.HTTPClient = &http.Client{Timeout: 5 * time.Second}
	t.Cleanup(client.Close)
	return client
}

func TestBackfillTitleRootsQueuesMissingIGDBGames(t *testing.T) {
	database := setupTitleRootBackfillDB(t)
	queue := NewScrapeQueue(database)
	s := &Scraper{DB: database, Queue: queue, IGDBClient: igdb.NewClient("test-id", "test-secret")}
	t.Cleanup(s.IGDBClient.Close)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)

	existingRoot := uint(100)
	missingRoot := db.Game{ConsoleID: console.ID, Title: "Missing Root", FileName: "missing.nes", FilePath: "/roms/missing.nes", ScraperID: "igdb:300"}
	hasRoot := db.Game{ConsoleID: console.ID, Title: "Has Root", FileName: "has.nes", FilePath: "/roms/has.nes", ScraperID: "igdb:301", TitleRootIGDBID: &existingRoot}
	nonIGDB := db.Game{ConsoleID: console.ID, Title: "Pouet", FileName: "pouet.adf", FilePath: "/roms/pouet.adf", ScraperID: "pouet:abc"}
	require.NoError(t, database.Create(&missingRoot).Error)
	require.NoError(t, database.Create(&hasRoot).Error)
	require.NoError(t, database.Create(&nonIGDB).Error)

	require.NoError(t, s.BackfillTitleRoots())

	var job db.ScrapeJob
	require.NoError(t, database.First(&job).Error)
	assert.Equal(t, scrapeJobModeTitleRootBackfill, job.Mode)
	assert.Equal(t, "running", job.Status)
	assert.Equal(t, 1, job.TotalItems)

	var item db.ScrapeQueueItem
	require.NoError(t, database.First(&item).Error)
	assert.Equal(t, missingRoot.ID, item.GameID)
	assert.Equal(t, scrapeQueueTypeTitleRootBackfill, item.Type)
	assert.Equal(t, scrapeQueuePriorityMaintenance, item.Priority)
	require.NotNil(t, item.JobID)
	assert.Equal(t, job.ID, *item.JobID)

	require.NoError(t, s.BackfillTitleRoots(), "active title-root job should make the startup path idempotent")

	var jobCount int64
	require.NoError(t, database.Model(&db.ScrapeJob{}).Count(&jobCount).Error)
	assert.Equal(t, int64(1), jobCount)

	var itemCount int64
	require.NoError(t, database.Model(&db.ScrapeQueueItem{}).Count(&itemCount).Error)
	assert.Equal(t, int64(1), itemCount)
}

func TestBackfillTitleRootsCancelsStaleRunningJobWithNoItems(t *testing.T) {
	database := setupTitleRootBackfillDB(t)
	queue := NewScrapeQueue(database)
	s := &Scraper{DB: database, Queue: queue, IGDBClient: igdb.NewClient("test-id", "test-secret")}
	t.Cleanup(s.IGDBClient.Close)

	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Missing Root", FileName: "missing.nes", FilePath: "/roms/missing.nes", ScraperID: "igdb:300"}
	require.NoError(t, database.Create(&game).Error)

	staleJob, err := queue.CreateJob(scrapeJobModeTitleRootBackfill, "igdb", "missing_title_root", "", 1)
	require.NoError(t, err)

	require.NoError(t, s.BackfillTitleRoots())

	var stale db.ScrapeJob
	require.NoError(t, database.First(&stale, staleJob.ID).Error)
	assert.Equal(t, "cancelled", stale.Status)

	var active db.ScrapeJob
	require.NoError(t, database.
		Where("mode = ? AND status = ?", scrapeJobModeTitleRootBackfill, "running").
		First(&active).Error)
	assert.NotEqual(t, staleJob.ID, active.ID)
	assert.Equal(t, 1, active.TotalItems)

	var item db.ScrapeQueueItem
	require.NoError(t, database.Where("job_id = ?", active.ID).First(&item).Error)
	assert.Equal(t, game.ID, item.GameID)
	assert.Equal(t, scrapeQueueTypeTitleRootBackfill, item.Type)
}

func TestBackfillTitleRootsSetsFlagWhenNoCandidates(t *testing.T) {
	database := setupTitleRootBackfillDB(t)
	s := &Scraper{DB: database, Queue: NewScrapeQueue(database), IGDBClient: igdb.NewClient("test-id", "test-secret")}
	t.Cleanup(s.IGDBClient.Close)

	require.NoError(t, s.BackfillTitleRoots())

	var setting db.ServerSetting
	require.NoError(t, database.First(&setting, "key = ?", backfillTitleRootIGDBFlag).Error)
	assert.Equal(t, "done", setting.Value)
}

func TestBackfillTitleRootForGameUpdatesOnlyRelationshipFields(t *testing.T) {
	parentID := 100
	category := igdb.IGDBCategoryPort
	var requests []int
	igdbClient := newTitleRootBackfillIGDBClient(t, map[int]igdb.Game{
		300: {ID: 300, Name: "Port", ParentGameID: &parentID, Category: &category},
		100: {ID: 100, Name: "Original"},
	}, &requests)

	database := setupTitleRootBackfillDB(t)
	console := db.Console{Abbreviation: "PSX", Name: "PlayStation"}
	require.NoError(t, database.Create(&console).Error)

	game := db.Game{
		ConsoleID:         console.ID,
		Title:             "Original Local Title",
		FileName:          "port.chd",
		FilePath:          "/roms/port.chd",
		CoverURL:          "old-cover.png",
		ScrapeAttempts:    7,
		ScraperID:         "igdb:300",
		IGDBCriticsRating: 82.5,
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database, IGDBClient: igdbClient}
	require.NoError(t, s.BackfillTitleRootForGame(&game))

	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	require.NotNil(t, updated.IGDBParentGameID)
	assert.Equal(t, uint(100), *updated.IGDBParentGameID)
	assert.Nil(t, updated.IGDBVersionParentID)
	require.NotNil(t, updated.IGDBCategory)
	assert.Equal(t, igdb.IGDBCategoryPort, *updated.IGDBCategory)
	require.NotNil(t, updated.TitleRootIGDBID)
	assert.Equal(t, uint(100), *updated.TitleRootIGDBID)

	assert.Equal(t, "Original Local Title", updated.Title)
	assert.Equal(t, "old-cover.png", updated.CoverURL)
	assert.Equal(t, 7, updated.ScrapeAttempts)
	assert.InDelta(t, 82.5, updated.IGDBCriticsRating, 0.01)
	assert.Equal(t, []int{300, 100}, requests)
}

func TestBackfillTitleRootForGameReturnsAncestorFetchErrors(t *testing.T) {
	parentID := 100
	var requests []int

	tokenServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		require.NoError(t, json.NewEncoder(w).Encode(map[string]interface{}{
			"access_token": "test-token",
			"expires_in":   3600,
			"token_type":   "bearer",
		}))
	}))
	t.Cleanup(tokenServer.Close)

	idPattern := regexp.MustCompile(`where id = ([0-9]+)`)
	igdbServer := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		body, err := io.ReadAll(r.Body)
		require.NoError(t, err)
		matches := idPattern.FindStringSubmatch(string(body))
		require.Len(t, matches, 2)
		id, err := strconv.Atoi(matches[1])
		require.NoError(t, err)
		requests = append(requests, id)

		if id == parentID {
			http.Error(w, "rate limit", http.StatusTooManyRequests)
			return
		}
		require.NoError(t, json.NewEncoder(w).Encode([]igdb.Game{
			{ID: 300, Name: "Port", ParentGameID: &parentID},
		}))
	}))
	t.Cleanup(igdbServer.Close)

	origTokenURL := igdb.TwitchTokenURLForTest()
	origAPIBase := igdb.IGDBAPIBaseForTest()
	igdb.SetTwitchTokenURLForTest(tokenServer.URL)
	igdb.SetIGDBAPIBaseForTest(igdbServer.URL + "/v4")
	t.Cleanup(func() {
		igdb.SetTwitchTokenURLForTest(origTokenURL)
		igdb.SetIGDBAPIBaseForTest(origAPIBase)
	})

	igdbClient := igdb.NewClient("test-id", "test-secret")
	igdbClient.HTTPClient = &http.Client{Timeout: 5 * time.Second}
	t.Cleanup(igdbClient.Close)

	database := setupTitleRootBackfillDB(t)
	console := db.Console{Abbreviation: "PSX", Name: "PlayStation"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{ConsoleID: console.ID, Title: "Local Title", FileName: "port.chd", FilePath: "/roms/port.chd", ScraperID: "igdb:300"}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database, IGDBClient: igdbClient}
	err := s.BackfillTitleRootForGame(&game)
	require.Error(t, err)
	assert.ErrorIs(t, err, igdb.ErrRateLimit)
	assert.Equal(t, []int{300, parentID}, requests)

	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Nil(t, updated.TitleRootIGDBID)
}

func TestScrapeWorkerProcessesTitleRootBackfillWithoutFullScrape(t *testing.T) {
	parentID := 100
	category := igdb.IGDBCategoryRemaster
	var requests []int
	igdbClient := newTitleRootBackfillIGDBClient(t, map[int]igdb.Game{
		300: {ID: 300, Name: "Remaster", ParentGameID: &parentID, Category: &category},
		100: {ID: 100, Name: "Original"},
	}, &requests)

	database := setupTitleRootBackfillDB(t)
	queue := NewScrapeQueue(database)
	console := db.Console{Abbreviation: "GC", Name: "GameCube"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID:      console.ID,
		Title:          "Local Title",
		FileName:       "game.iso",
		FilePath:       "/roms/game.iso",
		ScrapeAttempts: 3,
		ScraperID:      "igdb:300",
	}
	require.NoError(t, database.Create(&game).Error)

	job, err := queue.CreateJob(scrapeJobModeTitleRootBackfill, "igdb", "missing_title_root", "", 1)
	require.NoError(t, err)
	require.NoError(t, queue.EnqueueGamesWithType(job.ID, []uint{game.ID}, 5, scrapeQueueTypeTitleRootBackfill))
	item, err := queue.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)

	s := &Scraper{DB: database, Queue: queue, IGDBClient: igdbClient}
	worker := NewScrapeWorker(database, queue, s, nil, nil)
	worker.processItem(context.Background(), item)

	var updatedItem db.ScrapeQueueItem
	require.NoError(t, database.First(&updatedItem, item.ID).Error)
	assert.Equal(t, "completed", updatedItem.Status)

	var updatedJob db.ScrapeJob
	require.NoError(t, database.First(&updatedJob, job.ID).Error)
	assert.Equal(t, "completed", updatedJob.Status)
	assert.Equal(t, 1, updatedJob.CompletedItems)
	assert.Equal(t, 0, updatedJob.FailedItems)

	var setting db.ServerSetting
	require.NoError(t, database.First(&setting, "key = ?", backfillTitleRootIGDBFlag).Error)
	assert.Equal(t, "done", setting.Value)

	var updatedGame db.Game
	require.NoError(t, database.First(&updatedGame, game.ID).Error)
	assert.Equal(t, "Local Title", updatedGame.Title)
	assert.Equal(t, 3, updatedGame.ScrapeAttempts)
	require.NotNil(t, updatedGame.TitleRootIGDBID)
	assert.Equal(t, uint(100), *updatedGame.TitleRootIGDBID)
	assert.Equal(t, []int{300, 100}, requests)
}

func TestScrapeWorkerUnknownQueueTypeFailsWithoutScrape(t *testing.T) {
	database := setupTitleRootBackfillDB(t)
	queue := NewScrapeQueue(database)
	console := db.Console{Abbreviation: "NES", Name: "Nintendo Entertainment System"}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID:      console.ID,
		Title:          "Unknown Type",
		FileName:       "unknown.nes",
		FilePath:       "/roms/unknown.nes",
		ScrapeAttempts: 4,
	}
	require.NoError(t, database.Create(&game).Error)

	job, err := queue.CreateJob("maintenance", "", "", "", 1)
	require.NoError(t, err)
	require.NoError(t, queue.EnqueueGamesWithType(job.ID, []uint{game.ID}, 0, "unexpected"))
	item, err := queue.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)

	worker := NewScrapeWorker(database, queue, nil, nil, nil)
	worker.processItem(context.Background(), item)

	var updatedItem db.ScrapeQueueItem
	require.NoError(t, database.First(&updatedItem, item.ID).Error)
	assert.Equal(t, "failed", updatedItem.Status)
	assert.Contains(t, updatedItem.ErrorMessage, "unknown scrape queue item type")

	var updatedGame db.Game
	require.NoError(t, database.First(&updatedGame, game.ID).Error)
	assert.Equal(t, 4, updatedGame.ScrapeAttempts)
}
