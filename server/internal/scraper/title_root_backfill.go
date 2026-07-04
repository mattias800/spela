package scraper

import (
	"errors"
	"fmt"
	"log/slog"
	"strconv"
	"strings"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// BackfillTitleRoots queues a one-shot metadata repair for games scraped before
// title-root IGDB fields were persisted. The queued work is intentionally
// metadata-only: it fills IGDB relationship columns without changing titles,
// artwork, ratings, scrape attempts, or other user-visible metadata.
func (s *Scraper) BackfillTitleRoots() error {
	if s.Queue == nil {
		return fmt.Errorf("title-root backfill queue is not configured")
	}
	if !s.IsIGDBConfigured() {
		slog.Info("title-root backfill skipped: IGDB is not configured")
		return nil
	}

	done, err := s.isTitleRootBackfillDone()
	if err != nil {
		return err
	}
	if done {
		return nil
	}

	active, err := s.hasActiveTitleRootBackfill()
	if err != nil {
		return err
	}
	if active {
		slog.Info("title-root backfill already queued")
		return nil
	}

	gameIDs, err := s.titleRootBackfillCandidateIDs()
	if err != nil {
		return err
	}
	if len(gameIDs) == 0 {
		return recordTitleRootBackfillDone(s.DB)
	}

	job, err := s.Queue.CreateJob(scrapeJobModeTitleRootBackfill, "igdb", "missing_title_root", "", len(gameIDs))
	if err != nil {
		return fmt.Errorf("creating title-root backfill job: %w", err)
	}
	if err := s.Queue.EnqueueGamesWithType(job.ID, gameIDs, scrapeQueuePriorityMaintenance, scrapeQueueTypeTitleRootBackfill); err != nil {
		if cancelErr := s.Queue.CancelJob(job.ID); cancelErr != nil {
			slog.Warn("failed to cancel title-root backfill job after enqueue failure", "jobId", job.ID, "error", cancelErr)
		}
		return fmt.Errorf("enqueuing title-root backfill games: %w", err)
	}

	slog.Info("queued title-root IGDB backfill", "jobId", job.ID, "count", len(gameIDs))
	return nil
}

// BackfillTitleRootForGame fills title-root IGDB fields for one already
// IGDB-scraped game. It treats a missing IGDB game as a completed no-op so the
// one-shot job can finish; transient API errors are returned and retried by the
// queue on the next startup.
func (s *Scraper) BackfillTitleRootForGame(game *db.Game) error {
	igdbID, ok := igdbIDFromScraperID(game.ScraperID)
	if !ok {
		slog.Info("title-root backfill skipped non-IGDB game", "gameId", game.ID, "scraperId", game.ScraperID)
		return nil
	}
	if !s.IsIGDBConfigured() {
		return fmt.Errorf("IGDB client is not configured")
	}

	igdbGame, err := s.IGDBClient.GetGameByID(igdbID)
	if err != nil {
		return fmt.Errorf("fetching IGDB game %d for title-root backfill: %w", igdbID, err)
	}
	if igdbGame == nil {
		slog.Warn("title-root backfill IGDB game not found", "gameId", game.ID, "igdbId", igdbID)
		return nil
	}

	parentID := uintPtrFromOptionalInt(igdbGame.ParentGameID)
	versionParentID := uintPtrFromOptionalInt(igdbGame.VersionParentID)
	category := intPtrCopy(igdbGame.Category)
	titleRootID, err := resolveTitleRootIGDBIDStrict(*igdbGame, s.IGDBClient.GetGameByID)
	if err != nil {
		return fmt.Errorf("resolving IGDB title root for game %d: %w", game.ID, err)
	}

	if err := s.DB.Model(&db.Game{}).
		Where("id = ?", game.ID).
		Updates(map[string]interface{}{
			"igdb_parent_game_id":    parentID,
			"igdb_version_parent_id": versionParentID,
			"igdb_category":          category,
			"title_root_igdb_id":     titleRootID,
		}).Error; err != nil {
		return fmt.Errorf("updating title-root fields for game %d: %w", game.ID, err)
	}

	game.IGDBParentGameID = parentID
	game.IGDBVersionParentID = versionParentID
	game.IGDBCategory = category
	game.TitleRootIGDBID = titleRootID
	return nil
}

func (s *Scraper) isTitleRootBackfillDone() (bool, error) {
	var setting db.ServerSetting
	err := s.DB.Where("key = ?", backfillTitleRootIGDBFlag).First(&setting).Error
	if err == nil {
		return true, nil
	}
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return false, nil
	}
	return false, fmt.Errorf("checking title-root backfill flag: %w", err)
}

func (s *Scraper) hasActiveTitleRootBackfill() (bool, error) {
	var jobs []db.ScrapeJob
	if err := s.DB.Model(&db.ScrapeJob{}).
		Where("mode = ? AND status = ?", scrapeJobModeTitleRootBackfill, "running").
		Find(&jobs).Error; err != nil {
		return false, fmt.Errorf("checking active title-root backfill job: %w", err)
	}
	for _, job := range jobs {
		var activeItems int64
		if err := s.DB.Model(&db.ScrapeQueueItem{}).
			Where("job_id = ? AND status IN ?", job.ID, []string{"pending", "in_progress"}).
			Count(&activeItems).Error; err != nil {
			return false, fmt.Errorf("checking title-root backfill queue items for job %d: %w", job.ID, err)
		}
		if activeItems > 0 {
			return true, nil
		}
		slog.Warn("cancelling stale title-root backfill job with no active queue items", "jobId", job.ID)
		if s.Queue == nil {
			return false, fmt.Errorf("title-root backfill queue is not configured")
		}
		if err := s.Queue.CancelJob(job.ID); err != nil {
			return false, fmt.Errorf("cancelling stale title-root backfill job %d: %w", job.ID, err)
		}
	}
	return false, nil
}

func (s *Scraper) titleRootBackfillCandidateIDs() ([]uint, error) {
	var gameIDs []uint
	activeBackfillItem := s.DB.Model(&db.ScrapeQueueItem{}).
		Select("1").
		Where("scrape_queue_items.game_id = games.id").
		Where("scrape_queue_items.type = ?", scrapeQueueTypeTitleRootBackfill).
		Where("scrape_queue_items.status IN ?", []string{"pending", "in_progress"})

	if err := s.DB.Model(&db.Game{}).
		Where("scraper_id LIKE ?", "igdb:%").
		Where("title_root_igdb_id IS NULL").
		Where("NOT EXISTS (?)", activeBackfillItem).
		Order("games.id ASC").
		Pluck("games.id", &gameIDs).Error; err != nil {
		return nil, fmt.Errorf("querying title-root backfill candidates: %w", err)
	}
	return gameIDs, nil
}

func recordTitleRootBackfillDone(database *gorm.DB) error {
	return database.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "key"}},
		DoUpdates: clause.Assignments(map[string]interface{}{"value": "done"}),
	}).Create(&db.ServerSetting{Key: backfillTitleRootIGDBFlag, Value: "done"}).Error
}

func igdbIDFromScraperID(scraperID string) (int, bool) {
	idStr, ok := strings.CutPrefix(scraperID, "igdb:")
	if !ok {
		return 0, false
	}
	id, err := strconv.Atoi(idStr)
	return id, err == nil && id > 0
}
