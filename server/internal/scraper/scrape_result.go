package scraper

import (
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// RecordScrapeResult upserts a scrape result for the given game and source.
func RecordScrapeResult(database *gorm.DB, gameID uint, source, status, sourceID, errorMsg string) {
	now := time.Now()
	result := db.GameScrapeResult{
		GameID:        gameID,
		Source:        source,
		Status:        status,
		SourceID:      sourceID,
		LastAttemptAt: &now,
		ErrorMessage:  errorMsg,
	}
	database.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "game_id"}, {Name: "source"}},
		DoUpdates: clause.AssignmentColumns([]string{"status", "source_id", "last_attempt_at", "error_message", "updated_at"}),
	}).Create(&result)
}
