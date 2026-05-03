package scraper

import (
	"fmt"
	"time"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// ScrapeQueue manages the persistent scrape queue backed by SQLite.
type ScrapeQueue struct {
	db  *gorm.DB
	hub *ws.Hub
}

// NewScrapeQueue creates a new ScrapeQueue.
func NewScrapeQueue(database *gorm.DB) *ScrapeQueue {
	return &ScrapeQueue{db: database}
}

// SetHub wires the WebSocket hub so the queue can broadcast scrape
// status changes (queued / scraping / idle). Safe to leave unset —
// the broadcast helpers are nil-safe — but production wiring should
// call this once at startup.
func (q *ScrapeQueue) SetHub(hub *ws.Hub) {
	q.hub = hub
}

// broadcastQueued emits a "queued" scrape-status event for a single
// game. Skipped when no hub is wired or when itemType isn't "scrape" —
// only user-visible scrape activity belongs in the scrape-status feed
// (mirrors the broadcastStatus gate in worker.go::processItem).
func (q *ScrapeQueue) broadcastQueued(gameID uint, itemType string) {
	if q.hub == nil || itemType != "scrape" {
		return
	}
	q.hub.Broadcast(ws.Event{
		Type: ws.EventGameScrapeStatus,
		Payload: ws.GameScrapeStatusPayload{
			GameID: gameID,
			Status: "queued",
		},
	})
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
// Items default to Type="scrape", so each enqueued game also broadcasts a
// "queued" scrape-status event so the UI can show "Scrape queued" before
// the worker actually picks the item up.
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
	for _, gid := range gameIDs {
		q.broadcastQueued(gid, "scrape")
	}
	return nil
}

// EnqueueGamesWithType inserts multiple queue items with a specific type.
func (q *ScrapeQueue) EnqueueGamesWithType(jobID uint, gameIDs []uint, priority int, itemType string) error {
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
			Type:     itemType,
		}
	}
	if err := q.db.CreateInBatches(items, 500).Error; err != nil {
		return fmt.Errorf("enqueuing %d games (type=%s): %w", len(gameIDs), itemType, err)
	}
	for _, gid := range gameIDs {
		q.broadcastQueued(gid, itemType)
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
	q.broadcastQueued(gameID, "scrape")
	return nil
}

// IsGameQueued returns true if the game already has a pending or in_progress queue item.
func (q *ScrapeQueue) IsGameQueued(gameID uint) (bool, error) {
	var count int64
	err := q.db.Model(&db.ScrapeQueueItem{}).
		Where("game_id = ? AND status IN ?", gameID, []string{"pending", "in_progress"}).
		Count(&count).Error
	return count > 0, err
}

// EnqueueGameWithType adds a single game to the queue with a specific type.
// Type determines what the worker does: "scrape" for full metadata, "ra_fetch" for RA achievements only.
func (q *ScrapeQueue) EnqueueGameWithType(gameID uint, jobID *uint, priority int, itemType string) error {
	item := &db.ScrapeQueueItem{
		JobID:    jobID,
		GameID:   gameID,
		Priority: priority,
		Status:   "pending",
		Type:     itemType,
	}
	if err := q.db.Create(item).Error; err != nil {
		return fmt.Errorf("enqueuing game %d (type=%s): %w", gameID, itemType, err)
	}
	q.broadcastQueued(gameID, itemType)
	return nil
}

// IsGameQueuedForType checks whether a game already has a pending or in-progress
// queue item of the given type. Use this to prevent duplicate enqueuing.
func (q *ScrapeQueue) IsGameQueuedForType(gameID uint, itemType string) (bool, error) {
	var count int64
	err := q.db.Model(&db.ScrapeQueueItem{}).
		Where("game_id = ? AND type = ? AND status IN ?", gameID, itemType, []string{"pending", "in_progress"}).
		Count(&count).Error
	return count > 0, err
}

// WasGameRecentlyAttemptedForType reports whether a finished (completed or
// failed) queue item of the given type exists for this game within `since`.
// Use as a backoff so a polling client can't drive an infinite re-enqueue
// loop when fetches keep failing — once a recent attempt is on record, the
// caller should stop re-enqueuing and let the cooldown expire.
func (q *ScrapeQueue) WasGameRecentlyAttemptedForType(gameID uint, itemType string, since time.Duration) (bool, error) {
	cutoff := time.Now().Add(-since)
	var count int64
	err := q.db.Model(&db.ScrapeQueueItem{}).
		Where("game_id = ? AND type = ? AND status IN ? AND completed_at >= ?",
			gameID, itemType, []string{"completed", "failed"}, cutoff).
		Count(&count).Error
	return count > 0, err
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
		if job.Status == "running" && job.CompletedItems+job.FailedItems >= job.TotalItems {
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

	// Include in_progress as well as pending — an item the worker has
	// already dequeued but not yet finished must not be re-enqueued, or
	// the game gets scraped twice and TotalItems over-counts so the job
	// never reaches completion (CompletedItems + FailedItems >= TotalItems).
	var existingGameIDs []uint
	if err := q.db.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status IN ? AND game_id IN ?", jobID, []string{"pending", "in_progress"}, gameIDs).
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
