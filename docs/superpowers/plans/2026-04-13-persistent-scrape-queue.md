# Persistent Scrape Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-memory scraping loop with a SQLite-backed persistent queue that survives server restarts, supports pause/resume, priority insertion for manual scrapes, and conflict resolution (reject/replace/merge).

**Architecture:** Two new tables (`scrape_jobs`, `scrape_queue_items`) persist scrape state. A `ScrapeQueue` struct provides all queue operations. A single `ScrapeWorker` goroutine runs for the server lifetime, dequeuing and processing items one at a time. All scraping (bulk, manual, startup auto-scrape) flows through this single pipeline.

**Tech Stack:** Go, GORM, SQLite (existing stack — no new dependencies)

**Spec:** `docs/superpowers/specs/2026-04-13-persistent-scrape-queue-design.md`

---

### Task 1: Database Models + Migration

**Files:**
- Create: `server/internal/db/scrape_job.go`
- Modify: `server/internal/db/database.go:207`

- [ ] **Step 1: Create the GORM models**

Create `server/internal/db/scrape_job.go`:

```go
package db

import (
	"time"
)

// ScrapeJob represents a bulk scraping operation that can be paused and resumed.
type ScrapeJob struct {
	ID             uint       `gorm:"primarykey" json:"id"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
	Status         string     `gorm:"size:32;not null;default:'pending';index" json:"status"` // pending, running, completed, cancelled
	Mode           string     `gorm:"size:32;not null" json:"mode"`                           // new, all, fallback
	SourceFilter   string     `gorm:"size:64" json:"sourceFilter,omitempty"`
	StatusFilter   string     `gorm:"size:64" json:"statusFilter,omitempty"`
	ConsoleFilter  string     `gorm:"size:64" json:"consoleFilter,omitempty"`
	TotalItems     int        `json:"totalItems"`
	CompletedItems int        `json:"completedItems"`
	FailedItems    int        `json:"failedItems"`
	VerifiedItems  int        `json:"verifiedItems"`
	StartedAt      *time.Time `json:"startedAt,omitempty"`
	CompletedAt    *time.Time `json:"completedAt,omitempty"`
}

// ScrapeQueueItem represents a single game queued for scraping.
type ScrapeQueueItem struct {
	ID           uint       `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time  `gorm:"index:idx_queue_dequeue,priority:3" json:"createdAt"`
	JobID        *uint      `gorm:"index" json:"jobId,omitempty"`
	GameID       uint       `gorm:"not null" json:"gameId"`
	Priority     int        `gorm:"not null;default:0;index:idx_queue_dequeue,priority:2,sort:desc" json:"priority"` // 0 = bulk, 100 = manual
	Status       string     `gorm:"size:32;not null;default:'pending';index:idx_queue_dequeue,priority:1" json:"status"` // pending, in_progress, completed, failed, cancelled
	ErrorMessage string     `gorm:"size:512" json:"errorMessage,omitempty"`
	CompletedAt  *time.Time `json:"completedAt,omitempty"`
}
```

- [ ] **Step 2: Register models in AutoMigrate**

In `server/internal/db/database.go`, add the two new models after `&GameScrapeResult{}` (line 207):

```go
		// Scrape results per source
		&GameScrapeResult{},
		// Persistent scrape queue
		&ScrapeJob{},
		&ScrapeQueueItem{},
	)
```

- [ ] **Step 3: Verify migration compiles**

Run: `cd server && go build ./...`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
cd server && git add internal/db/scrape_job.go internal/db/database.go
git commit -m "feat: add ScrapeJob and ScrapeQueueItem models"
```

---

### Task 2: ScrapeQueue — Job + Enqueue Operations

**Files:**
- Create: `server/internal/scraper/queue.go`
- Create: `server/internal/scraper/queue_test.go`

- [ ] **Step 1: Write failing tests for CreateJob, GetActiveJob, EnqueueGames, EnqueueGame**

Create `server/internal/scraper/queue_test.go`:

```go
package scraper

import (
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

func setupQueueTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(&db.ScrapeJob{}, &db.ScrapeQueueItem{}))
	return database
}

func TestCreateJob(t *testing.T) {
	q := NewScrapeQueue(setupQueueTestDB(t))

	job, err := q.CreateJob("all", "", "", "", 100)
	require.NoError(t, err)
	assert.Equal(t, "running", job.Status)
	assert.Equal(t, "all", job.Mode)
	assert.Equal(t, 100, job.TotalItems)
	assert.NotNil(t, job.StartedAt)
	assert.NotZero(t, job.ID)
}

func TestGetActiveJob(t *testing.T) {
	q := NewScrapeQueue(setupQueueTestDB(t))

	// No active job
	job, err := q.GetActiveJob()
	require.NoError(t, err)
	assert.Nil(t, job)

	// Create one
	created, err := q.CreateJob("new", "", "", "", 50)
	require.NoError(t, err)

	// Now it's active
	job, err = q.GetActiveJob()
	require.NoError(t, err)
	require.NotNil(t, job)
	assert.Equal(t, created.ID, job.ID)
}

func TestEnqueueGames(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, err := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, err)

	err = q.EnqueueGames(job.ID, []uint{10, 20, 30}, 0)
	require.NoError(t, err)

	var count int64
	database.Model(&db.ScrapeQueueItem{}).Where("job_id = ?", job.ID).Count(&count)
	assert.Equal(t, int64(3), count)
}

func TestEnqueueGamesEmpty(t *testing.T) {
	q := NewScrapeQueue(setupQueueTestDB(t))
	err := q.EnqueueGames(1, []uint{}, 0)
	require.NoError(t, err)
}

func TestEnqueueGame(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Standalone (no job)
	err := q.EnqueueGame(42, nil, 100)
	require.NoError(t, err)

	var item db.ScrapeQueueItem
	require.NoError(t, database.First(&item).Error)
	assert.Equal(t, uint(42), item.GameID)
	assert.Nil(t, item.JobID)
	assert.Equal(t, 100, item.Priority)
	assert.Equal(t, "pending", item.Status)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scraper/ -run "TestCreateJob|TestGetActiveJob|TestEnqueueGames|TestEnqueueGame" -v`
Expected: Compilation error — `NewScrapeQueue` undefined.

- [ ] **Step 3: Implement ScrapeQueue with job + enqueue operations**

Create `server/internal/scraper/queue.go`:

```go
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scraper/ -run "TestCreateJob|TestGetActiveJob|TestEnqueueGames|TestEnqueueGame" -v`
Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
cd server && git add internal/scraper/queue.go internal/scraper/queue_test.go
git commit -m "feat: add ScrapeQueue with job and enqueue operations"
```

---

### Task 3: ScrapeQueue — Dequeue + Completion Operations

**Files:**
- Modify: `server/internal/scraper/queue.go`
- Modify: `server/internal/scraper/queue_test.go`

- [ ] **Step 1: Write failing tests for Dequeue, MarkCompleted, MarkFailed**

Append to `server/internal/scraper/queue_test.go`:

```go
func TestDequeue(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Empty queue
	item, err := q.Dequeue()
	require.NoError(t, err)
	assert.Nil(t, item)

	// Add items with different priorities
	job, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))
	require.NoError(t, q.EnqueueGame(3, &job.ID, 100)) // high priority

	// Should dequeue high-priority item first
	item, err = q.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)
	assert.Equal(t, uint(3), item.GameID)
	assert.Equal(t, "in_progress", item.Status)

	// Next should be the first bulk item
	item, err = q.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)
	assert.Equal(t, uint(1), item.GameID)
}

func TestMarkCompleted(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	item, _ := q.Dequeue()
	jobDone, err := q.MarkCompleted(item)
	require.NoError(t, err)
	assert.False(t, jobDone)

	// Check item status
	var updated db.ScrapeQueueItem
	database.First(&updated, item.ID)
	assert.Equal(t, "completed", updated.Status)
	assert.NotNil(t, updated.CompletedAt)

	// Check job counters
	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, 1, updatedJob.CompletedItems)
	assert.Equal(t, "running", updatedJob.Status)
}

func TestMarkFailed(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 1)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1}, 0))

	item, _ := q.Dequeue()
	jobDone, err := q.MarkFailed(item, "IGDB timeout")
	require.NoError(t, err)
	assert.True(t, jobDone) // only item in job

	var updated db.ScrapeQueueItem
	database.First(&updated, item.ID)
	assert.Equal(t, "failed", updated.Status)
	assert.Equal(t, "IGDB timeout", updated.ErrorMessage)

	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, 1, updatedJob.FailedItems)
	assert.Equal(t, "completed", updatedJob.Status)
	assert.NotNil(t, updatedJob.CompletedAt)
}

func TestJobAutoCompletes(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("new", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	// Complete first item
	item1, _ := q.Dequeue()
	jobDone, _ := q.MarkCompleted(item1)
	assert.False(t, jobDone)

	// Complete second item
	item2, _ := q.Dequeue()
	jobDone, _ = q.MarkCompleted(item2)
	assert.True(t, jobDone)

	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, "completed", updatedJob.Status)
	assert.Equal(t, 2, updatedJob.CompletedItems)
}

func TestMarkCompletedStandaloneItem(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Item with no job
	require.NoError(t, q.EnqueueGame(42, nil, 100))

	item, _ := q.Dequeue()
	jobDone, err := q.MarkCompleted(item)
	require.NoError(t, err)
	assert.False(t, jobDone)

	var updated db.ScrapeQueueItem
	database.First(&updated, item.ID)
	assert.Equal(t, "completed", updated.Status)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scraper/ -run "TestDequeue|TestMark|TestJobAutoCompletes" -v`
Expected: Compilation error — `Dequeue`, `MarkCompleted`, `MarkFailed` undefined.

- [ ] **Step 3: Implement Dequeue, MarkCompleted, MarkFailed**

Append to `server/internal/scraper/queue.go`:

```go
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
		// Update item
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

		// Increment job counter
		col := "completed_items"
		if status == "failed" {
			col = "failed_items"
		}
		if err := tx.Model(&db.ScrapeJob{}).Where("id = ?", *item.JobID).
			Update(col, gorm.Expr(col+" + 1")).Error; err != nil {
			return err
		}

		// Check if job is done
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scraper/ -run "TestDequeue|TestMark|TestJobAutoCompletes" -v`
Expected: All PASS.

- [ ] **Step 5: Commit**

```bash
cd server && git add internal/scraper/queue.go internal/scraper/queue_test.go
git commit -m "feat: add ScrapeQueue dequeue and completion operations"
```

---

### Task 4: ScrapeQueue — Cancel, Reset, Merge

**Files:**
- Modify: `server/internal/scraper/queue.go`
- Modify: `server/internal/scraper/queue_test.go`

- [ ] **Step 1: Write failing tests for CancelJob, ResetInProgressItems, MergeGames**

Append to `server/internal/scraper/queue_test.go`:

```go
func TestCancelJob(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2, 3}, 0))

	// Process one item
	item, _ := q.Dequeue()
	q.MarkCompleted(item)

	// Cancel the job
	require.NoError(t, q.CancelJob(job.ID))

	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, "cancelled", updatedJob.Status)
	assert.NotNil(t, updatedJob.CompletedAt)

	// Remaining pending items should be cancelled
	var cancelledCount int64
	database.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status = ?", job.ID, "cancelled").Count(&cancelledCount)
	assert.Equal(t, int64(2), cancelledCount)

	// Completed item should stay completed
	var completedCount int64
	database.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status = ?", job.ID, "completed").Count(&completedCount)
	assert.Equal(t, int64(1), completedCount)
}

func TestResetInProgressItems(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	// Dequeue one (marks it in_progress)
	q.Dequeue()

	// Simulate crash recovery
	count, err := q.ResetInProgressItems()
	require.NoError(t, err)
	assert.Equal(t, int64(1), count)

	// Item should be pending again
	var pendingCount int64
	database.Model(&db.ScrapeQueueItem{}).
		Where("status = ?", "pending").Count(&pendingCount)
	assert.Equal(t, int64(2), pendingCount)
}

func TestMergeGames(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	// Merge with overlap and new games
	added, err := q.MergeGames(job.ID, []uint{2, 3, 4})
	require.NoError(t, err)
	assert.Equal(t, 2, added) // 3 and 4 are new, 2 is a duplicate

	// Total items updated
	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, 4, updatedJob.TotalItems)

	// Total queue items
	var totalCount int64
	database.Model(&db.ScrapeQueueItem{}).Where("job_id = ?", job.ID).Count(&totalCount)
	assert.Equal(t, int64(4), totalCount)
}

func TestMergeGamesEmpty(t *testing.T) {
	q := NewScrapeQueue(setupQueueTestDB(t))
	job, _ := q.CreateJob("all", "", "", "", 0)
	added, err := q.MergeGames(job.ID, []uint{})
	require.NoError(t, err)
	assert.Equal(t, 0, added)
}

func TestReplaceJob(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Create and partially process a job
	oldJob, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(oldJob.ID, []uint{1, 2, 3}, 0))
	item, _ := q.Dequeue()
	q.MarkCompleted(item)

	// Cancel the old job (replace step 1)
	require.NoError(t, q.CancelJob(oldJob.ID))

	// Create a new job (replace step 2)
	newJob, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(newJob.ID, []uint{10, 20}, 0))

	// Old job is cancelled, new job is running
	var old db.ScrapeJob
	database.First(&old, oldJob.ID)
	assert.Equal(t, "cancelled", old.Status)

	active, _ := q.GetActiveJob()
	require.NotNil(t, active)
	assert.Equal(t, newJob.ID, active.ID)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scraper/ -run "TestCancelJob|TestResetInProgress|TestMergeGames|TestReplaceJob" -v`
Expected: Compilation error — `CancelJob`, `ResetInProgressItems`, `MergeGames` undefined.

- [ ] **Step 3: Implement CancelJob, ResetInProgressItems, MergeGames**

Append to `server/internal/scraper/queue.go`:

```go
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

	// Find game IDs already pending in this job
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scraper/ -run "TestCancelJob|TestResetInProgress|TestMergeGames|TestReplaceJob" -v`
Expected: All PASS.

- [ ] **Step 5: Run all queue tests together**

Run: `cd server && go test ./internal/scraper/ -run "TestCreateJob|TestGetActiveJob|TestEnqueue|TestDequeue|TestMark|TestJobAutoCompletes|TestCancelJob|TestResetInProgress|TestMergeGames|TestReplaceJob" -v`
Expected: All PASS.

- [ ] **Step 6: Commit**

```bash
cd server && git add internal/scraper/queue.go internal/scraper/queue_test.go
git commit -m "feat: add ScrapeQueue cancel, reset, and merge operations"
```

---

### Task 5: ScrapeWorker

**Files:**
- Create: `server/internal/scraper/worker.go`

- [ ] **Step 1: Implement ScrapeWorker**

Create `server/internal/scraper/worker.go`:

```go
package scraper

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
	"gorm.io/gorm"
)

// ScrapeWorker processes items from the scrape queue in the background.
type ScrapeWorker struct {
	db            *gorm.DB
	queue         *ScrapeQueue
	scraper       *Scraper
	hub           *ws.Hub
	onJobComplete func()
}

// NewScrapeWorker creates a new worker.
func NewScrapeWorker(database *gorm.DB, queue *ScrapeQueue, s *Scraper, hub *ws.Hub, onJobComplete func()) *ScrapeWorker {
	return &ScrapeWorker{
		db:            database,
		queue:         queue,
		scraper:       s,
		hub:           hub,
		onJobComplete: onJobComplete,
	}
}

// Run starts the worker loop. It blocks until ctx is cancelled.
func (w *ScrapeWorker) Run(ctx context.Context) {
	slog.Info("scrape worker started")

	// Recover from interrupted state (server crash / hard kill)
	if count, err := w.queue.ResetInProgressItems(); err != nil {
		slog.Error("failed to reset in-progress scrape items", "error", err)
	} else if count > 0 {
		slog.Info("reset interrupted scrape items to pending", "count", count)
	}

	for {
		select {
		case <-ctx.Done():
			slog.Info("scrape worker shutting down")
			return
		default:
		}

		item, err := w.queue.Dequeue()
		if err != nil {
			slog.Error("failed to dequeue scrape item", "error", err)
			if !w.sleepOrShutdown(ctx, 5*time.Second) {
				return
			}
			continue
		}

		if item == nil {
			if !w.sleepOrShutdown(ctx, 2*time.Second) {
				return
			}
			continue
		}

		w.processItem(ctx, item)
	}
}

// sleepOrShutdown sleeps for d or returns false if ctx is cancelled.
func (w *ScrapeWorker) sleepOrShutdown(ctx context.Context, d time.Duration) bool {
	select {
	case <-ctx.Done():
		return false
	case <-time.After(d):
		return true
	}
}

func (w *ScrapeWorker) processItem(ctx context.Context, item *db.ScrapeQueueItem) {
	var game db.Game
	if err := w.db.Preload("Console").First(&game, item.GameID).Error; err != nil {
		slog.Warn("scrape worker: game not found", "gameId", item.GameID, "error", err)
		w.queue.MarkFailed(item, fmt.Sprintf("game not found: %v", err))
		return
	}

	// Variant group propagation for 'new' mode jobs
	propagated := false
	if item.JobID != nil {
		var job db.ScrapeJob
		if err := w.db.First(&job, *item.JobID).Error; err == nil {
			if job.Mode == "new" && game.GroupKey != "" {
				if w.scraper.propagateGroupMetadata(&game) {
					propagated = true
				}
			}
		}
	}

	if !propagated {
		if err := w.scraper.ScrapeGame(&game); err != nil {
			slog.Warn("scrape worker: scrape failed", "game", game.Title, "error", err)
			jobDone, _ := w.queue.MarkFailed(item, err.Error())
			w.broadcastProgress(item, &game, false, jobDone)
			return
		}

		// Propagate metadata to unscraped siblings in the same variant group
		if game.GroupKey != "" {
			w.scraper.propagateToGroup(&game)
		}
	}

	verified := game.VerificationStatus == "verified"
	if verified && item.JobID != nil {
		w.db.Model(&db.ScrapeJob{}).Where("id = ?", *item.JobID).
			Update("verified_items", gorm.Expr("verified_items + 1"))
	}

	jobDone, _ := w.queue.MarkCompleted(item)
	w.broadcastProgress(item, &game, verified, jobDone)
}

func (w *ScrapeWorker) broadcastProgress(item *db.ScrapeQueueItem, game *db.Game, verified bool, jobDone bool) {
	if w.hub == nil {
		return
	}

	if item.JobID != nil {
		var job db.ScrapeJob
		if err := w.db.First(&job, *item.JobID).Error; err != nil {
			return
		}

		w.hub.Broadcast(ws.Event{
			Type: "scrape_progress",
			Payload: ScrapeProgress{
				Current:     job.CompletedItems + job.FailedItems,
				Total:       job.TotalItems,
				GameID:      game.ID,
				GameName:    game.Title,
				ConsoleName: game.Console.Name,
				ConsoleAbbr: game.Console.Abbreviation,
				Successes:   job.CompletedItems,
				Failures:    job.FailedItems,
				Verified:    job.VerifiedItems,
			},
		})

		if jobDone {
			w.hub.Broadcast(ws.Event{
				Type: "scrape_complete",
				Payload: map[string]interface{}{
					"scraped": job.CompletedItems,
					"total":   job.TotalItems,
				},
			})
			if w.onJobComplete != nil {
				w.onJobComplete()
			}
		}
	}

	// For high-priority items (manual scrapes), broadcast game_scraped
	// so the frontend can update the game detail page.
	if item.Priority >= 100 {
		w.db.Preload("Console").Preload("Screenshots").First(game, game.ID)
		w.hub.Broadcast(ws.Event{
			Type:    "game_scraped",
			Payload: game,
		})
	}
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd server && go build ./...`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
cd server && git add internal/scraper/worker.go
git commit -m "feat: add ScrapeWorker for background queue processing"
```

---

### Task 6: Add Queue to Scraper + Remove Scrape Lock

**Files:**
- Modify: `server/internal/scraper/scraper.go`

- [ ] **Step 1: Read the current Scraper struct**

Read `server/internal/scraper/scraper.go` to confirm exact field names and line numbers before editing.

- [ ] **Step 2: Add Queue field and remove scrape-specific lock fields**

In the `Scraper` struct, remove the in-memory scrape state fields and add `Queue`:

Replace:
```go
	// Scrape state tracking (shared across handlers)
	scrapeMu       sync.Mutex
	scraping       bool
	scrapeProgress *ScrapeProgress
	scrapeCancel   context.CancelFunc
```

With:
```go
	// Persistent scrape queue (replaces in-memory lock)
	Queue *ScrapeQueue
```

- [ ] **Step 3: Remove scrape lock methods**

Delete the following methods (keep enrichment lock methods unchanged):
- `TryStartScrape()`
- `CancelScrape()` (the method on Scraper — the queue's CancelJob replaces it)
- `FinishScrape()`
- `SetScrapeProgress()`
- `GetScrapeStatus()`

Keep these enrichment methods untouched:
- `TryStartEnrich()`
- `FinishEnrich()`
- `SetEnrichProgress()`
- `GetEnrichStatus()`

- [ ] **Step 4: Update NewScraper to initialize Queue**

In `NewScraper`, add Queue initialization:

```go
func NewScraper(database *gorm.DB, store *storage.Storage, datDir string, gameDirs []string) *Scraper {
	s := &Scraper{
		DB:         database,
		Storage:    store,
		DATCache:   NewDATCache(datDir),
		GameDirs:   gameDirs,
		HTTPClient: &http.Client{Timeout: 30 * time.Second},
		cache:      &nameCache{},
		Queue:      NewScrapeQueue(database),
	}
	return s
}
```

- [ ] **Step 5: Remove sync import if no longer needed**

Check if `sync.Mutex` is still used (for enrichment). If `enrichMu` still uses it, keep the `sync` import. If not, remove it.

The enrichment lock (`enrichMu sync.Mutex`) is still in the struct, so `sync` stays.

- [ ] **Step 6: Verify it compiles (expect errors from callers)**

Run: `cd server && go build ./... 2>&1 | head -30`
Expected: Compilation errors in `admin_handler_scraper.go` and `main.go` where removed methods are called. This is expected — we fix those in Tasks 7-9.

- [ ] **Step 7: Commit (with build errors noted)**

```bash
cd server && git add internal/scraper/scraper.go
git commit -m "refactor: replace in-memory scrape lock with persistent Queue

Build errors expected in handlers and main.go — fixed in next tasks."
```

---

### Task 7: Modify TriggerScrape Handler

**Files:**
- Modify: `server/internal/api/admin_handler_scraper.go`

This is the biggest handler change. The handler currently starts a goroutine that calls `ScrapeAll()`. Now it collects game IDs, creates a job, and enqueues them.

- [ ] **Step 1: Read the current TriggerScrape handler**

Read `server/internal/api/admin_handler_scraper.go` to confirm exact code and line numbers.

- [ ] **Step 2: Rewrite TriggerScrape handler**

Replace the `TriggerScrape` method with:

```go
// TriggerScrape creates a scrape job and enqueues matching games.
func (h *AdminHandler) TriggerScrape(c *gin.Context) {
	mode := c.DefaultQuery("mode", "new")
	if c.Query("force") == "true" {
		mode = "all"
	}

	// Resolve console filter
	var consoleID uint
	if abbr := c.Query("console"); abbr != "" {
		var console db.Console
		if err := h.DB.Where("abbreviation = ?", abbr).First(&console).Error; err != nil {
			c.JSON(http.StatusBadRequest, gin.H{"error": "unknown console"})
			return
		}
		consoleID = console.ID
	}

	source := c.Query("source")
	status := c.Query("status")
	conflict := c.DefaultQuery("conflict", "reject")

	// Check for active job
	activeJob, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}

	if activeJob != nil {
		switch conflict {
		case "replace":
			if err := h.Scraper.Queue.CancelJob(activeJob.ID); err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "cancelling active job"})
				return
			}
			h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{}})
		case "merge":
			gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "collecting games"})
				return
			}
			added, err := h.Scraper.Queue.MergeGames(activeJob.ID, gameIDs)
			if err != nil {
				c.JSON(http.StatusInternalServerError, gin.H{"error": "merging games"})
				return
			}
			c.JSON(http.StatusOK, gin.H{
				"jobId":      activeJob.ID,
				"added":      added,
				"totalItems": activeJob.TotalItems + added,
			})
			return
		default: // reject
			c.JSON(http.StatusConflict, gin.H{
				"error":      "scrape already in progress",
				"jobId":      activeJob.ID,
				"totalItems": activeJob.TotalItems,
				"completed":  activeJob.CompletedItems,
				"failed":     activeJob.FailedItems,
			})
			return
		}
	}

	// Collect game IDs matching the query
	gameIDs, err := h.collectGameIDs(mode, consoleID, source, status)
	if err != nil {
		slog.Error("failed to collect game IDs for scrape", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "collecting games"})
		return
	}

	if len(gameIDs) == 0 {
		c.JSON(http.StatusOK, gin.H{"total": 0, "message": "no games to scrape"})
		return
	}

	consoleFilter := c.Query("console")

	// Create job and enqueue
	job, err := h.Scraper.Queue.CreateJob(mode, source, status, consoleFilter, len(gameIDs))
	if err != nil {
		slog.Error("failed to create scrape job", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "creating job"})
		return
	}

	if err := h.Scraper.Queue.EnqueueGames(job.ID, gameIDs, 0); err != nil {
		slog.Error("failed to enqueue games", "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "enqueuing games"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_started", Payload: gin.H{
		"jobId": job.ID,
		"total": len(gameIDs),
		"mode":  mode,
	}})

	slog.Info("scrape job created", "jobId", job.ID, "mode", mode, "total", len(gameIDs))
	c.JSON(http.StatusOK, gin.H{"jobId": job.ID, "total": len(gameIDs)})
}

// collectGameIDs builds the game query based on filters and returns matching IDs.
func (h *AdminHandler) collectGameIDs(mode string, consoleID uint, source, status string) ([]uint, error) {
	q := h.DB.Model(&db.Game{})
	if consoleID > 0 {
		q = q.Where("console_id = ?", consoleID)
	}

	if source != "" && status != "" {
		cooldownCutoff := time.Now().AddDate(0, 0, -7)
		if status == "not_attempted" {
			q = q.Where("id NOT IN (SELECT game_id FROM game_scrape_results WHERE source = ?)", source)
		} else {
			subQ := "id IN (SELECT game_id FROM game_scrape_results WHERE source = ? AND status = ?"
			args := []interface{}{source, status}
			if status == "not_found" || status == "error" {
				subQ += " AND (last_attempt_at IS NULL OR last_attempt_at < ?)"
				args = append(args, cooldownCutoff)
			}
			subQ += ")"
			q = q.Where(subQ, args...)
		}
	} else {
		switch mode {
		case "all":
			// no filter
		case "fallback":
			q = q.Where("scraper_id = 'libretro'")
		default:
			q = q.Where("scraper_id = '' OR scraper_id IS NULL")
		}
	}

	var gameIDs []uint
	if err := q.Pluck("id", &gameIDs).Error; err != nil {
		return nil, fmt.Errorf("collecting game IDs: %w", err)
	}
	return gameIDs, nil
}
```

- [ ] **Step 3: Update imports**

Ensure the file imports include `time`:
```go
import (
	"log/slog"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/spela/server/internal/db"
	ws "github.com/spela/server/internal/websocket"
)
```

Remove unused imports (`"os"`, `"github.com/spela/server/internal/igdb"`, `"github.com/spela/server/internal/scraper"`) — check which are still needed by other methods in the file before removing.

- [ ] **Step 4: Verify it compiles (handler-only)**

Run: `cd server && go vet ./internal/api/...`
Expected: May still have errors in other methods — that's expected.

- [ ] **Step 5: Commit**

```bash
cd server && git add internal/api/admin_handler_scraper.go
git commit -m "feat: rewrite TriggerScrape to use persistent queue"
```

---

### Task 8: Modify CancelScrape, ScrapeStatus, and ScrapeGame Handlers

**Files:**
- Modify: `server/internal/api/admin_handler_scraper.go`

- [ ] **Step 1: Rewrite CancelScrape handler**

Replace the `CancelScrape` method with:

```go
func (h *AdminHandler) CancelScrape(c *gin.Context) {
	job, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}
	if job == nil {
		c.JSON(http.StatusConflict, gin.H{"error": "no scrape operation is running"})
		return
	}

	if err := h.Scraper.Queue.CancelJob(job.ID); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "cancelling job"})
		return
	}

	h.Hub.Broadcast(ws.Event{Type: "scrape_cancelled", Payload: gin.H{
		"jobId": job.ID,
	}})

	adminID, _ := c.Get("userId")
	slog.Info("audit: admin cancelled scrape", "admin_id", adminID, "jobId", job.ID)
	c.JSON(http.StatusOK, gin.H{"message": "scrape cancellation requested"})
}
```

- [ ] **Step 2: Rewrite ScrapeStatus handler**

Replace the `ScrapeStatus` method with:

```go
func (h *AdminHandler) ScrapeStatus(c *gin.Context) {
	job, err := h.Scraper.Queue.GetActiveJob()
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "checking active job"})
		return
	}

	if job == nil {
		c.JSON(http.StatusOK, gin.H{"active": false})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"active":    true,
		"jobId":     job.ID,
		"current":   job.CompletedItems + job.FailedItems,
		"total":     job.TotalItems,
		"successes": job.CompletedItems,
		"failures":  job.FailedItems,
		"verified":  job.VerifiedItems,
		"mode":      job.Mode,
		"startedAt": job.StartedAt,
	})
}
```

Note: This response shape keeps the `active`, `current`, `total`, `successes`, `failures`, `verified` fields that the frontend expects, and adds `jobId`, `mode`, `startedAt`. The `gameName`, `consoleName`, `consoleAbbr`, `gameId` fields are no longer in the poll response (they come via WebSocket `scrape_progress` events instead).

- [ ] **Step 3: Rewrite ScrapeGame handler (single game, admin)**

Replace the `ScrapeGame` method with:

```go
// ScrapeGame enqueues a single game for scraping with high priority.
func (h *AdminHandler) ScrapeGame(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	h.tryConfigureIGDB()
	h.tryConfigureSteamGridDB()

	// Attach to active job if one exists
	activeJob, _ := h.Scraper.Queue.GetActiveJob()
	var jobID *uint
	if activeJob != nil {
		jobID = &activeJob.ID
		// Update job total since we're adding an item
		h.DB.Model(&db.ScrapeJob{}).Where("id = ?", activeJob.ID).
			Update("total_items", gorm.Expr("total_items + 1"))
	}

	if err := h.Scraper.Queue.EnqueueGame(game.ID, jobID, 100); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to enqueue game"})
		return
	}

	c.JSON(http.StatusAccepted, gin.H{
		"status": "queued",
		"gameId": game.ID,
	})
}
```

- [ ] **Step 4: Add gorm import if needed**

Ensure the import list includes `"gorm.io/gorm"` for the `gorm.Expr` call in `ScrapeGame`.

- [ ] **Step 5: Verify compilation**

Run: `cd server && go vet ./internal/api/...`
Expected: Fewer errors. Remaining errors may be in `game_handler.go` (ScrapeIfNeeded) and `main.go`.

- [ ] **Step 6: Commit**

```bash
cd server && git add internal/api/admin_handler_scraper.go
git commit -m "feat: rewrite CancelScrape, ScrapeStatus, ScrapeGame handlers for queue"
```

---

### Task 9: Modify ScrapeIfNeeded Handler

**Files:**
- Modify: `server/internal/api/game_handler.go`

- [ ] **Step 1: Read the current ScrapeIfNeeded handler**

Read `server/internal/api/game_handler.go` — find `ScrapeIfNeeded` method (around line 685).

- [ ] **Step 2: Rewrite ScrapeIfNeeded to enqueue**

Replace the `ScrapeIfNeeded` method with:

```go
// ScrapeIfNeeded enqueues an unscraped game for scraping with high priority.
func (h *GameHandler) ScrapeIfNeeded(c *gin.Context) {
	id := c.Param("id")
	var game db.Game
	if err := h.DB.First(&game, id).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "game not found"})
		return
	}

	if game.ScrapeAttempts > 0 {
		c.JSON(http.StatusOK, gin.H{"status": "already_scraped"})
		return
	}

	// Attach to active job if one exists
	activeJob, _ := h.Scraper.Queue.GetActiveJob()
	var jobID *uint
	if activeJob != nil {
		jobID = &activeJob.ID
		h.DB.Model(&db.ScrapeJob{}).Where("id = ?", activeJob.ID).
			Update("total_items", gorm.Expr("total_items + 1"))
	}

	// Configure scrapers if not already done
	if apiKey := steamGridDBAPIKey(h.DB); apiKey != "" {
		h.Scraper.ConfigureSteamGridDB(apiKey)
	}

	if err := h.Scraper.Queue.EnqueueGame(game.ID, jobID, 100); err != nil {
		slog.Warn("auto-scrape: failed to enqueue", "game", game.Title, "error", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "failed to enqueue"})
		return
	}

	c.JSON(http.StatusAccepted, gin.H{"status": "queued"})
}
```

- [ ] **Step 3: Clean up imports**

Remove unused imports from the method (the `go func()` goroutine and WebSocket broadcast are gone).

- [ ] **Step 4: Add needed imports**

Ensure `"gorm.io/gorm"` and `"log/slog"` are imported. Add `"github.com/spela/server/internal/db"` if not already present.

- [ ] **Step 5: Verify compilation**

Run: `cd server && go vet ./internal/api/...`
Expected: API package compiles. Remaining errors in `main.go` only.

- [ ] **Step 6: Commit**

```bash
cd server && git add internal/api/game_handler.go
git commit -m "feat: rewrite ScrapeIfNeeded to use queue"
```

---

### Task 10: Wire Everything in main.go

**Files:**
- Modify: `server/cmd/server/main.go`

- [ ] **Step 1: Read current main.go startup and shutdown sections**

Read `server/cmd/server/main.go` — focus on lines 182-346 (scraper setup, auto-scrape) and lines 357-376 (shutdown).

- [ ] **Step 2: Add server context for worker lifecycle**

Near the top of `main()`, before the HTTP server setup, create a context for the worker:

```go
// Context for background workers — cancelled on shutdown
workerCtx, workerCancel := context.WithCancel(context.Background())
defer workerCancel()
```

- [ ] **Step 3: Start the ScrapeWorker**

After `metaScraper` is created (line ~183) and IGDB/RA clients are configured, start the worker:

```go
// Start background scrape worker
scrapeWorker := scraper.NewScrapeWorker(
	database, metaScraper.Queue, metaScraper, hub,
	func() {
		// Called when a job completes — refresh top-rated cache
		if metaScraper.IGDBClient != nil && metaScraper.IGDBClient.IsConfigured() {
			// refreshTopRatedForAllConsoles is on AdminHandler — call IGDB directly
			slog.Info("scrape job complete, top-rated cache will refresh on next request")
		}
	},
)
go scrapeWorker.Run(workerCtx)
```

Note: The `refreshTopRatedForAllConsoles` method is on `AdminHandler`. For the worker callback, we have two options: (a) skip the refresh from the worker (it's a nice-to-have), or (b) extract the refresh logic to a shared function. For now, option (a) is simpler — the top-rated cache refreshes naturally when games are loaded. If needed, extract later.

- [ ] **Step 4: Modify the startup auto-scrape to use the queue**

Replace the auto-scrape goroutine section (where it calls `TryStartScrape` + `ScrapeAll`) with queue-based enqueuing. The existing library scan code stays unchanged — only the post-scan scraping changes.

Find the section that does:
```go
if newGames > 0 && metaScraper.IsIGDBConfigured() {
	// ... configure SteamGridDB ...
	scrapeCtx, ok := metaScraper.TryStartScrape()
	if ok {
		// ... broadcast, ScrapeAll, FinishScrape ...
	}
}
```

Replace with:
```go
if newGames > 0 && metaScraper.IsIGDBConfigured() {
	// Configure SteamGridDB if available
	steamKey := os.Getenv("STEAMGRIDDB_API_KEY")
	if steamKey == "" {
		var setting db.ServerSetting
		if err := database.Where("key = ?", "steamgriddb_api_key").First(&setting).Error; err == nil {
			steamKey = setting.Value
		}
	}
	if steamKey != "" {
		metaScraper.ConfigureSteamGridDB(steamKey)
	}

	// Collect new game IDs
	var gameIDs []uint
	database.Model(&db.Game{}).
		Where("scraper_id = '' OR scraper_id IS NULL").
		Pluck("id", &gameIDs)

	if len(gameIDs) > 0 {
		// Use merge to add to existing job (if one was interrupted and is resuming)
		// or create a new job if none exists
		activeJob, _ := metaScraper.Queue.GetActiveJob()
		if activeJob != nil {
			added, err := metaScraper.Queue.MergeGames(activeJob.ID, gameIDs)
			if err != nil {
				slog.Error("failed to merge new games into active scrape job", "error", err)
			} else if added > 0 {
				slog.Info("merged new games into active scrape job", "added", added, "jobId", activeJob.ID)
			}
		} else {
			job, err := metaScraper.Queue.CreateJob("new", "", "", "", len(gameIDs))
			if err != nil {
				slog.Error("failed to create startup scrape job", "error", err)
			} else {
				if err := metaScraper.Queue.EnqueueGames(job.ID, gameIDs, 0); err != nil {
					slog.Error("failed to enqueue startup scrape games", "error", err)
				} else {
					slog.Info("startup scrape job created", "jobId", job.ID, "total", len(gameIDs))
					hub.Broadcast(ws.Event{Type: "scrape_started", Payload: gin.H{
						"jobId": job.ID,
						"total": len(gameIDs),
						"mode":  "new",
					}})
				}
			}
		}
	}
}
```

- [ ] **Step 5: Cancel worker context on shutdown**

In the shutdown signal handler, cancel the worker context before shutting down HTTP:

```go
go func() {
	sig := <-shutdownCh
	slog.Info("shutdown signal received, draining connections", "signal", sig)
	// Stop the scrape worker first (finishes current game)
	workerCancel()
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		slog.Error("graceful shutdown failed", "error", err)
	}
}()
```

- [ ] **Step 6: Add imports**

Ensure `main.go` imports `"context"` and `"github.com/spela/server/internal/scraper"` (if not already present).

- [ ] **Step 7: Verify full build**

Run: `cd server && go build ./...`
Expected: Build succeeds (all caller sites now updated).

- [ ] **Step 8: Commit**

```bash
cd server && git add cmd/server/main.go
git commit -m "feat: wire ScrapeWorker into server startup and shutdown"
```

---

### Task 11: Remove Old ScrapeAll Code

**Files:**
- Modify: `server/internal/scraper/scraper_batch.go`

- [ ] **Step 1: Read scraper_batch.go to identify what to remove**

Read `server/internal/scraper/scraper_batch.go` — identify `ScrapeAll()` and `ScrapeProgress` struct.

- [ ] **Step 2: Remove ScrapeAll function**

Delete the entire `ScrapeAll` function (approximately lines 323-469). The `ScrapeProgress` struct is still used by the worker for WebSocket broadcasts — keep it.

- [ ] **Step 3: Check for other references to removed code**

Run: `cd server && grep -rn "ScrapeAll" internal/`
Expected: No remaining references. If any exist, they need to be updated.

Run: `cd server && grep -rn "TryStartScrape\|FinishScrape\|SetScrapeProgress\|GetScrapeStatus" internal/`
Expected: No remaining references to removed Scraper methods.

- [ ] **Step 4: Clean up unused imports in scraper_batch.go**

Remove any imports that were only used by `ScrapeAll` (e.g., `"context"`, `"fmt"` if no longer needed).

- [ ] **Step 5: Full build verification**

Run: `cd server && go build ./...`
Expected: Build succeeds.

- [ ] **Step 6: Run all tests**

Run: `cd server && go test ./... -v`
Expected: All tests pass. If any existing tests reference removed methods, update them.

- [ ] **Step 7: Commit**

```bash
cd server && git add internal/scraper/scraper_batch.go internal/scraper/scraper.go
git commit -m "refactor: remove ScrapeAll and in-memory scrape lock"
```

---

### Task 12: Final Verification

**Files:** None (testing only)

- [ ] **Step 1: Full build**

Run: `cd server && go build ./...`
Expected: Clean build.

- [ ] **Step 2: Run all tests**

Run: `cd server && go test ./... -v -count=1`
Expected: All tests pass.

- [ ] **Step 3: Run queue-specific tests**

Run: `cd server && go test ./internal/scraper/ -run "TestCreateJob|TestGetActiveJob|TestEnqueue|TestDequeue|TestMark|TestJobAutoCompletes|TestCancelJob|TestResetInProgress|TestMergeGames|TestReplaceJob" -v`
Expected: All 11 queue tests pass.

- [ ] **Step 4: Verify no references to old scrape lock API**

Run: `cd server && grep -rn "TryStartScrape\|FinishScrape\|SetScrapeProgress\|scrapeProgress\|scrapeMu\|scrapeCancel" internal/ cmd/`
Expected: No matches (all old lock code is gone).

- [ ] **Step 5: Commit final state if any cleanup was needed**

```bash
git add -A && git status
# Only commit if there are changes from cleanup
```
