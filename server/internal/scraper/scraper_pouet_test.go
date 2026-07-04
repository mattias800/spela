package scraper

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSanitizePouetDate(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"1993-08-01", "1993-08-01"}, // valid date unchanged
		{"1989-00-15", "1989-01-15"}, // zero month → 01
		{"1992-05-00", "1992-05-01"}, // zero day → 01
		{"1990-00-00", "1990-01-01"}, // both zero → 01
		{"1993", "1993"},             // year only unchanged
		{"", ""},                     // empty unchanged
		{"2020-12-31", "2020-12-31"}, // valid date unchanged
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, sanitizePouetDate(tt.input))
		})
	}
}

func TestBackfillDemoConsoleMisscrapesEnqueuesStandaloneScrapes(t *testing.T) {
	database := setupTestDB(t)
	require.NoError(t, database.AutoMigrate(&db.ScrapeJob{}, &db.ScrapeQueueItem{}, &db.ServerSetting{}))
	queue := NewScrapeQueue(database)

	console := db.Console{Abbreviation: "ADEMO", Name: "Amiga Demos"}
	require.NoError(t, database.Create(&console).Error)

	rootID := uint(100)
	game := db.Game{
		ConsoleID:       console.ID,
		Title:           "Mis-scraped Demo",
		FileName:        "demo.adf",
		FilePath:        "/roms/demo.adf",
		ScraperID:       "igdb:123",
		CoverURL:        "old-cover.png",
		TitleRootIGDBID: &rootID,
		ScrapeAttempts:  3,
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database, Queue: queue}
	require.NoError(t, s.BackfillDemoConsoleMisscrapes())

	item, err := queue.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)
	assert.Equal(t, game.ID, item.GameID)
	assert.Nil(t, item.JobID)
	assert.Equal(t, scrapeQueueTypeScrape, item.Type)
	assert.Equal(t, scrapeQueuePriorityMaintenance, item.Priority)

	jobDone, err := queue.MarkCompleted(item)
	require.NoError(t, err)
	assert.False(t, jobDone)

	var updatedItem db.ScrapeQueueItem
	require.NoError(t, database.First(&updatedItem, item.ID).Error)
	assert.Equal(t, "completed", updatedItem.Status)

	var updatedGame db.Game
	require.NoError(t, database.First(&updatedGame, game.ID).Error)
	assert.Empty(t, updatedGame.ScraperID)
	assert.Empty(t, updatedGame.CoverURL)
	assert.Nil(t, updatedGame.TitleRootIGDBID)
	assert.Equal(t, 0, updatedGame.ScrapeAttempts)
}

func TestStripParenContent(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"State of the Art (Phenomena)", "State of the Art"},
		{"Demo (Group) (Extra)", "Demo"},
		{"No parens here", "No parens here"},
		{"(All parens)", ""},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			assert.Equal(t, tt.want, stripParenContent(tt.input))
		})
	}
}
