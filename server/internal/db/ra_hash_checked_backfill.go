package db

import (
	"fmt"
	"log/slog"

	"gorm.io/gorm"
)

// raHashCheckedBackfilledKey gates MigrateRAHashChecked to a single run.
const raHashCheckedBackfilledKey = "ra_hash_checked_backfilled"

// MigrateRAHashChecked re-opens games that were negative-cached against
// RetroAchievements before #1674.
//
// Until #1674, FetchRAAchievements set RAHashChecked when the ROM file merely
// could not be read — an unmounted share, or a copy still in flight — which is
// not an RA answer at all. That was survivable because the startup scrape
// re-queried every RAGameID=0 game on every boot and healed such rows. #1674
// removes that re-query (it was the traffic RA complained about), so those
// rows would now be excluded from the scrape, both handlers and the admin
// rescrape, permanently, with no operator recovery.
//
// The poisoned rows are indistinguishable from genuine no-matches after the
// fact, so this clears the flag for every RAGameID=0 row exactly once. Games
// with a resolved RAGameID are untouched. The affected rows get one more
// lookup each, after which the (now correct) negative cache keeps them quiet
// for good.
//
// Strictly one-time: re-running on every boot would clear the negative cache
// on every boot, which is precisely the hammering #1674 exists to stop. The
// ServerSetting sentinel is written only after the update succeeds, so a crash
// mid-migration just retries on the next start.
func MigrateRAHashChecked(database *gorm.DB) error {
	var sentinel ServerSetting
	if err := database.Where("key = ?", raHashCheckedBackfilledKey).First(&sentinel).Error; err == nil {
		return nil // Already completed.
	}

	n, err := ClearRANegativeCache(database, 0)
	if err != nil {
		return fmt.Errorf("re-opening RA-negative-cached games: %w", err)
	}
	if n > 0 {
		slog.Info("re-opened RA-negative-cached games for one re-check", "games", n)
	}

	if err := database.Save(&ServerSetting{Key: raHashCheckedBackfilledKey, Value: "true"}).Error; err != nil {
		slog.Warn("failed to write ra-hash-checked backfill sentinel — migration will retry on next start", "error", err)
	}
	return nil
}

// ClearRANegativeCache clears RAHashChecked on games recorded as having no
// RetroAchievements match, so they are looked up once more. Pass consoleID 0
// for the whole library.
//
// This is the only way back: nothing else ever clears the flag, so without it
// a game wrongly recorded as "RA has no such game" — a mass RA incident, or a
// fix to our ROM hashing that would now match — stays hidden forever. Games
// with a resolved RAGameID are left alone; they are not negative-cached.
//
// Returns the number of rows re-opened.
func ClearRANegativeCache(database *gorm.DB, consoleID uint) (int64, error) {
	q := database.Model(&Game{}).
		Where("ra_hash_checked = ? AND (ra_game_id = 0 OR ra_game_id IS NULL)", true)
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}
	res := q.Update("ra_hash_checked", false)
	if res.Error != nil {
		return 0, fmt.Errorf("clearing RA negative cache: %w", res.Error)
	}
	return res.RowsAffected, nil
}
