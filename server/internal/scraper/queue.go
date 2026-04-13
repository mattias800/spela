package scraper

import (
	"fmt"
	"time"

	"github.com/spela/server/internal/db"
	"gorm.io/gorm"
)

// ScrapeQueue manages the persistent scrape queue backed by SQLite.
type ScrapeQueue struct {
	db *gorm.DB
}

// NewScrapeQueue creates a new ScrapeQueue.
func NewScrapeQueue(database *gorm.DB) *ScrapeQueue {
	return &ScrapeQueue{db: database}
}

// CreateJob creates a new scrape job in "running" state.
func (q *ScrapeQueue) CreateJob(mode, sourceFilter, statusFilter, consoleFilter string, totalItems int) (*db.ScrapeJob, error) {
	now := time.Now()
	job := &db.ScrapeJob{
		Status:        "running",
		Mode:          mode,
		SourceFilter:  sourceFilter,
		StatusFilter:  statusFilter,
		ConsoleFilter: consoleFilter,
		TotalItems:    totalItems,
		StartedAt:     &now,
	}
	if err := q.db.Create(job).Error; err != nil {
		return nil, fmt.Errorf("creating scrape job: %w", err)
	}
	return job, nil
}

// GetActiveJob returns the currently running job, or nil if none.
func (q *ScrapeQueue) GetActiveJob() (*db.ScrapeJob, error) {
	var job db.ScrapeJob
	err := q.db.Where("status = ?", "running").First(&job).Error
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("querying active job: %w", err)
	}
	return &job, nil
}

// EnqueueGames bulk-inserts queue items for the given game IDs.
func (q *ScrapeQueue) EnqueueGames(jobID uint, gameIDs []uint, priority int) error {
	if len(gameIDs) == 0 {
		return nil
	}
	items := make([]db.ScrapeQueueItem, len(gameIDs))
	for i, gid := range gameIDs {
		items[i] = db.ScrapeQueueItem{
			JobID:    &jobID,
			GameID:   gid,
			Priority: priority,
			Status:   "pending",
		}
	}
	if err := q.db.CreateInBatches(items, 500).Error; err != nil {
		return fmt.Errorf("enqueuing %d games: %w", len(gameIDs), err)
	}
	return nil
}

// EnqueueGame inserts a single queue item. jobID may be nil for standalone scrapes.
func (q *ScrapeQueue) EnqueueGame(gameID uint, jobID *uint, priority int) error {
	item := &db.ScrapeQueueItem{
		JobID:    jobID,
		GameID:   gameID,
		Priority: priority,
		Status:   "pending",
	}
	if err := q.db.Create(item).Error; err != nil {
		return fmt.Errorf("enqueuing game %d: %w", gameID, err)
	}
	return nil
}

// Dequeue returns the next pending item (highest priority first, then oldest)
// and marks it as in_progress. Returns nil if queue is empty.
func (q *ScrapeQueue) Dequeue() (*db.ScrapeQueueItem, error) {
	var item db.ScrapeQueueItem
	err := q.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("status = ?", "pending").
			Order("priority DESC, created_at ASC").
			First(&item).Error; err != nil {
			return err
		}
		return tx.Model(&item).Update("status", "in_progress").Error
	})
	if err == gorm.ErrRecordNotFound {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("dequeuing item: %w", err)
	}
	return &item, nil
}

// MarkCompleted marks an item as completed and updates job counters.
// Returns true if the parent job is now fully complete.
func (q *ScrapeQueue) MarkCompleted(item *db.ScrapeQueueItem) (bool, error) {
	now := time.Now()
	return q.finishItem(item, "completed", "", &now)
}

// MarkFailed marks an item as failed with an error message.
// Returns true if the parent job is now fully complete.
func (q *ScrapeQueue) MarkFailed(item *db.ScrapeQueueItem, errMsg string) (bool, error) {
	now := time.Now()
	return q.finishItem(item, "failed", errMsg, &now)
}

func (q *ScrapeQueue) finishItem(item *db.ScrapeQueueItem, status, errMsg string, completedAt *time.Time) (bool, error) {
	var jobDone bool
	err := q.db.Transaction(func(tx *gorm.DB) error {
		updates := map[string]interface{}{
			"status":       status,
			"completed_at": completedAt,
		}
		if errMsg != "" {
			updates["error_message"] = errMsg
		}
		if err := tx.Model(&db.ScrapeQueueItem{}).Where("id = ?", item.ID).Updates(updates).Error; err != nil {
			return err
		}

		if item.JobID == nil {
			return nil
		}

		col := "completed_items"
		if status == "failed" {
			col = "failed_items"
		}
		if err := tx.Model(&db.ScrapeJob{}).Where("id = ?", *item.JobID).
			Update(col, gorm.Expr(col+" + 1")).Error; err != nil {
			return err
		}

		var job db.ScrapeJob
		if err := tx.First(&job, *item.JobID).Error; err != nil {
			return err
		}
		if job.CompletedItems+job.FailedItems >= job.TotalItems {
			now := time.Now()
			if err := tx.Model(&job).Updates(map[string]interface{}{
				"status":       "completed",
				"completed_at": &now,
			}).Error; err != nil {
				return err
			}
			jobDone = true
		}
		return nil
	})
	return jobDone, err
}

// CancelJob marks the job and all its pending items as cancelled.
func (q *ScrapeQueue) CancelJob(jobID uint) error {
	return q.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Model(&db.ScrapeQueueItem{}).
			Where("job_id = ? AND status = ?", jobID, "pending").
			Update("status", "cancelled").Error; err != nil {
			return err
		}
		now := time.Now()
		return tx.Model(&db.ScrapeJob{}).Where("id = ?", jobID).
			Updates(map[string]interface{}{
				"status":       "cancelled",
				"completed_at": &now,
			}).Error
	})
}

// ResetInProgressItems resets any in_progress items back to pending.
// Called on startup to recover from interrupted scrapes.
func (q *ScrapeQueue) ResetInProgressItems() (int64, error) {
	result := q.db.Model(&db.ScrapeQueueItem{}).
		Where("status = ?", "in_progress").
		Update("status", "pending")
	return result.RowsAffected, result.Error
}

// MergeGames adds new game IDs to an existing job, deduplicating against
// pending items already in the queue. Returns the number of games added.
func (q *ScrapeQueue) MergeGames(jobID uint, gameIDs []uint) (int, error) {
	if len(gameIDs) == 0 {
		return 0, nil
	}

	var existingGameIDs []uint
	if err := q.db.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status = ? AND game_id IN ?", jobID, "pending", gameIDs).
		Pluck("game_id", &existingGameIDs).Error; err != nil {
		return 0, fmt.Errorf("querying existing items: %w", err)
	}

	existing := make(map[uint]bool, len(existingGameIDs))
	for _, id := range existingGameIDs {
		existing[id] = true
	}

	var newIDs []uint
	for _, id := range gameIDs {
		if !existing[id] {
			newIDs = append(newIDs, id)
		}
	}

	if len(newIDs) == 0 {
		return 0, nil
	}

	if err := q.EnqueueGames(jobID, newIDs, 0); err != nil {
		return 0, err
	}

	if err := q.db.Model(&db.ScrapeJob{}).Where("id = ?", jobID).
		Update("total_items", gorm.Expr("total_items + ?", len(newIDs))).Error; err != nil {
		return 0, fmt.Errorf("updating job total: %w", err)
	}

	return len(newIDs), nil
}
