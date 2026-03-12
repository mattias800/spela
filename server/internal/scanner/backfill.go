package scanner

import (
	"fmt"
	"log/slog"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// BackfillGameMetadataWithProgress populates Region, Revision, Tags, IsPreRelease, and GroupKey
// for existing games that don't have a GroupKey set. This handles games created before the
// large library support feature was added.
// The onProgress callback is called after each batch with (processed, total) counts.
// It does NOT run GroupAndElectPrimaries — the caller (Scan) handles that separately.
func BackfillGameMetadataWithProgress(database *gorm.DB, onProgress func(processed, total int64)) error {
	// Count games needing backfill
	var totalNeedingBackfill int64
	if err := database.Model(&db.Game{}).Where("group_key = '' OR group_key IS NULL").Count(&totalNeedingBackfill).Error; err != nil {
		return fmt.Errorf("counting games needing backfill: %w", err)
	}

	if totalNeedingBackfill == 0 {
		slog.Info("no games need metadata backfill")
		return nil
	}

	slog.Info("backfilling game metadata", "total", totalNeedingBackfill)

	// Process in batches of 100
	const batchSize = 100
	var processed int64

	for {
		var games []db.Game
		if err := database.Where("group_key = '' OR group_key IS NULL").
			Limit(batchSize).
			Find(&games).Error; err != nil {
			return fmt.Errorf("loading games for backfill: %w", err)
		}

		if len(games) == 0 {
			break
		}

		// Accumulate updates in memory, then batch-save in a transaction
		for i := range games {
			meta := ParseFilenameMetadata(games[i].FileName)
			games[i].Revision = meta.Revision
			games[i].Tags = meta.Tags
			games[i].IsPreRelease = meta.IsPreRelease
			games[i].GroupKey = meta.GroupKey

			// Only backfill Region if it's currently empty
			if games[i].Region == "" && meta.Region != "" {
				games[i].Region = meta.Region
			}
		}

		if err := database.Transaction(func(tx *gorm.DB) error {
			for i := range games {
				if err := tx.Save(&games[i]).Error; err != nil {
					slog.Warn("failed to backfill game metadata",
						"gameId", games[i].ID, "title", games[i].Title, "error", err)
				}
			}
			return nil
		}); err != nil {
			return fmt.Errorf("batch-saving backfilled games: %w", err)
		}

		processed += int64(len(games))
		slog.Info("backfilling game metadata progress",
			"processed", processed, "total", totalNeedingBackfill)
		if onProgress != nil {
			onProgress(processed, totalNeedingBackfill)
		}
	}

	slog.Info("game metadata backfill complete", "processed", processed)
	return nil
}
