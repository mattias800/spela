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

	job, err := q.GetActiveJob()
	require.NoError(t, err)
	assert.Nil(t, job)

	created, err := q.CreateJob("new", "", "", "", 50)
	require.NoError(t, err)

	job, err = q.GetActiveJob()
	require.NoError(t, err)
	require.NotNil(t, job)
	assert.Equal(t, created.ID, job.ID)
}

func TestGetActiveScrapeJobIgnoresMaintenanceJobs(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	maintenance, err := q.CreateJob(scrapeJobModeTitleRootBackfill, "igdb", "missing_title_root", "", 1)
	require.NoError(t, err)

	active, err := q.GetActiveScrapeJob()
	require.NoError(t, err)
	assert.Nil(t, active)

	scrape, err := q.CreateJob("new", "", "", "", 1)
	require.NoError(t, err)

	active, err = q.GetActiveScrapeJob()
	require.NoError(t, err)
	require.NotNil(t, active)
	assert.Equal(t, scrape.ID, active.ID)

	anyActive, err := q.GetActiveJob()
	require.NoError(t, err)
	require.NotNil(t, anyActive)
	assert.Equal(t, maintenance.ID, anyActive.ID)
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

	err := q.EnqueueGame(42, nil, 100)
	require.NoError(t, err)

	var item db.ScrapeQueueItem
	require.NoError(t, database.First(&item).Error)
	assert.Equal(t, uint(42), item.GameID)
	assert.Nil(t, item.JobID)
	assert.Equal(t, 100, item.Priority)
	assert.Equal(t, "pending", item.Status)
}

func TestDequeue(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	item, err := q.Dequeue()
	require.NoError(t, err)
	assert.Nil(t, item)

	job, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))
	require.NoError(t, q.EnqueueGame(3, &job.ID, 100))

	item, err = q.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)
	assert.Equal(t, uint(3), item.GameID)
	assert.Equal(t, "in_progress", item.Status)

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

	var updated db.ScrapeQueueItem
	database.First(&updated, item.ID)
	assert.Equal(t, "completed", updated.Status)
	assert.NotNil(t, updated.CompletedAt)

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
	assert.True(t, jobDone)

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

	item1, _ := q.Dequeue()
	jobDone, _ := q.MarkCompleted(item1)
	assert.False(t, jobDone)

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

	require.NoError(t, q.EnqueueGame(42, nil, 100))

	item, _ := q.Dequeue()
	jobDone, err := q.MarkCompleted(item)
	require.NoError(t, err)
	assert.False(t, jobDone)

	var updated db.ScrapeQueueItem
	database.First(&updated, item.ID)
	assert.Equal(t, "completed", updated.Status)
}

func TestCancelJob(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2, 3}, 0))

	item, _ := q.Dequeue()
	q.MarkCompleted(item)

	require.NoError(t, q.CancelJob(job.ID))

	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, "cancelled", updatedJob.Status)
	assert.NotNil(t, updatedJob.CompletedAt)

	var cancelledCount int64
	database.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status = ?", job.ID, "cancelled").Count(&cancelledCount)
	assert.Equal(t, int64(2), cancelledCount)

	var completedCount int64
	database.Model(&db.ScrapeQueueItem{}).
		Where("job_id = ? AND status = ?", job.ID, "completed").Count(&completedCount)
	assert.Equal(t, int64(1), completedCount)
}

func TestCancelledJobNotOverwrittenByCompletion(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	// Dequeue both items
	item1, _ := q.Dequeue()
	item2, _ := q.Dequeue()

	// Complete the first
	q.MarkCompleted(item1)

	// Cancel the job while item2 is in_progress
	require.NoError(t, q.CancelJob(job.ID))

	// Complete the second item (simulates worker finishing current game)
	jobDone, err := q.MarkCompleted(item2)
	require.NoError(t, err)
	assert.False(t, jobDone) // should NOT report done — job was cancelled

	// Job should still be cancelled, not completed
	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, "cancelled", updatedJob.Status)
}

func TestIsGameQueued(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Not queued
	queued, err := q.IsGameQueued(42)
	require.NoError(t, err)
	assert.False(t, queued)

	// Enqueue it
	require.NoError(t, q.EnqueueGame(42, nil, 0))
	queued, err = q.IsGameQueued(42)
	require.NoError(t, err)
	assert.True(t, queued)

	// Dequeue (in_progress) — still considered queued
	q.Dequeue()
	queued, err = q.IsGameQueued(42)
	require.NoError(t, err)
	assert.True(t, queued)

	// Complete — no longer queued
	var item db.ScrapeQueueItem
	database.First(&item)
	q.MarkCompleted(&item)
	queued, err = q.IsGameQueued(42)
	require.NoError(t, err)
	assert.False(t, queued)
}

func TestEnqueueGameWithType(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	err := q.EnqueueGameWithType(42, nil, 100, "ra_fetch")
	require.NoError(t, err)

	var item db.ScrapeQueueItem
	require.NoError(t, database.First(&item).Error)
	assert.Equal(t, uint(42), item.GameID)
	assert.Equal(t, 100, item.Priority)
	assert.Equal(t, "ra_fetch", item.Type)
	assert.Equal(t, "pending", item.Status)
	assert.Nil(t, item.JobID)
}

func TestIsGameQueuedForType(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Nothing queued yet
	queued, err := q.IsGameQueuedForType(42, "ra_fetch")
	require.NoError(t, err)
	assert.False(t, queued)

	// Enqueue a scrape item for game 42 (default type via EnqueueGame)
	err = q.EnqueueGame(42, nil, 0)
	require.NoError(t, err)

	// "scrape" type should be queued (default type), but "ra_fetch" should not
	queued, err = q.IsGameQueuedForType(42, "scrape")
	require.NoError(t, err)
	assert.True(t, queued)

	queued, err = q.IsGameQueuedForType(42, "ra_fetch")
	require.NoError(t, err)
	assert.False(t, queued)
}

func TestIsGameQueuedForType_IgnoresCompletedItems(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	err := q.EnqueueGameWithType(42, nil, 100, "ra_fetch")
	require.NoError(t, err)

	// Dequeue and complete the item
	item, err := q.Dequeue()
	require.NoError(t, err)
	require.NotNil(t, item)
	_, err = q.MarkCompleted(item)
	require.NoError(t, err)

	// Should no longer be queued
	queued, err := q.IsGameQueuedForType(42, "ra_fetch")
	require.NoError(t, err)
	assert.False(t, queued)
}

func TestWasGameRecentlyAttemptedForType(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Brand new — nothing attempted.
	recent, err := q.WasGameRecentlyAttemptedForType(42, "ra_fetch", 5*time.Minute)
	require.NoError(t, err)
	assert.False(t, recent, "no queue item — should not be recently attempted")

	// Pending/in-progress doesn't count as "attempted" (still ongoing).
	require.NoError(t, q.EnqueueGameWithType(42, nil, 100, "ra_fetch"))
	recent, _ = q.WasGameRecentlyAttemptedForType(42, "ra_fetch", 5*time.Minute)
	assert.False(t, recent, "pending item — not yet attempted")

	// Failed item within window → counts.
	item, _ := q.Dequeue()
	require.NotNil(t, item)
	_, err = q.MarkFailed(item, "transient error")
	require.NoError(t, err)
	recent, _ = q.WasGameRecentlyAttemptedForType(42, "ra_fetch", 5*time.Minute)
	assert.True(t, recent, "failed item within window — should count")

	// Different type — not counted.
	recent, _ = q.WasGameRecentlyAttemptedForType(42, "scrape", 5*time.Minute)
	assert.False(t, recent, "different item type — should not count")

	// Different game — not counted.
	recent, _ = q.WasGameRecentlyAttemptedForType(99, "ra_fetch", 5*time.Minute)
	assert.False(t, recent, "different game — should not count")

	// Beyond window — not counted. Backdate the completion timestamp.
	old := time.Now().Add(-10 * time.Minute)
	require.NoError(t, database.Model(&db.ScrapeQueueItem{}).
		Where("id = ?", item.ID).Update("completed_at", old).Error)
	recent, _ = q.WasGameRecentlyAttemptedForType(42, "ra_fetch", 5*time.Minute)
	assert.False(t, recent, "completed_at older than window — should not count")

	// Completed (not just failed) within window also counts — same backoff applies.
	require.NoError(t, q.EnqueueGameWithType(43, nil, 100, "ra_fetch"))
	item2, _ := q.Dequeue()
	require.NotNil(t, item2)
	_, err = q.MarkCompleted(item2)
	require.NoError(t, err)
	recent, _ = q.WasGameRecentlyAttemptedForType(43, "ra_fetch", 5*time.Minute)
	assert.True(t, recent, "completed item within window — should count")
}

func TestResetInProgressItems(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	job, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(job.ID, []uint{1, 2}, 0))

	q.Dequeue()

	count, err := q.ResetInProgressItems()
	require.NoError(t, err)
	assert.Equal(t, int64(1), count)

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

	added, err := q.MergeGames(job.ID, []uint{2, 3, 4})
	require.NoError(t, err)
	assert.Equal(t, 2, added)

	var updatedJob db.ScrapeJob
	database.First(&updatedJob, job.ID)
	assert.Equal(t, 4, updatedJob.TotalItems)

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

	oldJob, _ := q.CreateJob("all", "", "", "", 3)
	require.NoError(t, q.EnqueueGames(oldJob.ID, []uint{1, 2, 3}, 0))
	item, _ := q.Dequeue()
	q.MarkCompleted(item)

	require.NoError(t, q.CancelJob(oldJob.ID))

	newJob, _ := q.CreateJob("all", "", "", "", 2)
	require.NoError(t, q.EnqueueGames(newJob.ID, []uint{10, 20}, 0))

	var old db.ScrapeJob
	database.First(&old, oldJob.ID)
	assert.Equal(t, "cancelled", old.Status)

	active, _ := q.GetActiveJob()
	require.NotNil(t, active)
	assert.Equal(t, newJob.ID, active.ID)
}
