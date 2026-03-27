package scraper

import (
	"testing"
	"time"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
	"path/filepath"
)

func setupScrapeResultDB(t *testing.T) *gorm.DB {
	t.Helper()
	dbPath := filepath.Join(t.TempDir(), "test.db")
	database, err := gorm.Open(sqlite.Open(dbPath+"?_journal_mode=WAL&_busy_timeout=5000"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.GameScrapeResult{}))
	return database
}

func TestRecordScrapeResult_CreatesNewRow(t *testing.T) {
	database := setupScrapeResultDB(t)

	before := time.Now()
	RecordScrapeResult(database, 42, "igdb", "matched", "12345", "")
	after := time.Now()

	var result db.GameScrapeResult
	err := database.Where("game_id = ? AND source = ?", 42, "igdb").First(&result).Error
	require.NoError(t, err)

	assert.Equal(t, uint(42), result.GameID)
	assert.Equal(t, "igdb", result.Source)
	assert.Equal(t, "matched", result.Status)
	assert.Equal(t, "12345", result.SourceID)
	assert.Empty(t, result.ErrorMessage)
	require.NotNil(t, result.LastAttemptAt)
	assert.True(t, result.LastAttemptAt.After(before) || result.LastAttemptAt.Equal(before))
	assert.True(t, result.LastAttemptAt.Before(after) || result.LastAttemptAt.Equal(after))
}

func TestRecordScrapeResult_UpsertsExistingRow(t *testing.T) {
	database := setupScrapeResultDB(t)

	// First call: create the row with "not_found"
	RecordScrapeResult(database, 10, "igdb", "not_found", "", "")

	var first db.GameScrapeResult
	require.NoError(t, database.Where("game_id = ? AND source = ?", 10, "igdb").First(&first).Error)
	assert.Equal(t, "not_found", first.Status)
	firstID := first.ID

	// Second call: upsert should update status to "matched"
	RecordScrapeResult(database, 10, "igdb", "matched", "99", "")

	var updated db.GameScrapeResult
	require.NoError(t, database.Where("game_id = ? AND source = ?", 10, "igdb").First(&updated).Error)
	assert.Equal(t, firstID, updated.ID, "should update the same row, not insert a new one")
	assert.Equal(t, "matched", updated.Status)
	assert.Equal(t, "99", updated.SourceID)

	// Confirm only one row exists
	var count int64
	database.Model(&db.GameScrapeResult{}).Where("game_id = ? AND source = ?", 10, "igdb").Count(&count)
	assert.Equal(t, int64(1), count)
}

func TestRecordScrapeResult_MultipleSourcesForSameGame(t *testing.T) {
	database := setupScrapeResultDB(t)

	RecordScrapeResult(database, 7, "igdb", "matched", "555", "")
	RecordScrapeResult(database, 7, "libretro", "matched", "", "")
	RecordScrapeResult(database, 7, "steamgriddb", "not_found", "", "")

	var results []db.GameScrapeResult
	require.NoError(t, database.Where("game_id = ?", 7).Find(&results).Error)
	assert.Len(t, results, 3)

	sourceStatus := make(map[string]string, 3)
	for _, r := range results {
		sourceStatus[r.Source] = r.Status
	}
	assert.Equal(t, "matched", sourceStatus["igdb"])
	assert.Equal(t, "matched", sourceStatus["libretro"])
	assert.Equal(t, "not_found", sourceStatus["steamgriddb"])
}

func TestRecordScrapeResult_RecordsErrorMessage(t *testing.T) {
	database := setupScrapeResultDB(t)

	errMsg := "connection timeout: dial tcp 1.2.3.4:443"
	RecordScrapeResult(database, 99, "igdb", "error", "", errMsg)

	var result db.GameScrapeResult
	require.NoError(t, database.Where("game_id = ? AND source = ?", 99, "igdb").First(&result).Error)
	assert.Equal(t, "error", result.Status)
	assert.Equal(t, errMsg, result.ErrorMessage)
	assert.Empty(t, result.SourceID)
}
