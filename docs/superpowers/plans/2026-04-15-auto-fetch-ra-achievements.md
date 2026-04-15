# Auto-Fetch RetroAchievements on Game Detail Page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Automatically fetch RetroAchievements data when a user views a game detail page, using the server's RA API key so achievements are visible to all users.

**Architecture:** Extend the scrape queue with a new `ra_fetch` item type. The `GET /games/{id}/achievements` handler enqueues an `ra_fetch` job when no cache exists, returning `{ status: "pending" }`. The queue worker processes it (hash computation + RA API call), populates the cache, and the frontend polls until data arrives.

**Tech Stack:** Go (Gin, GORM, SQLite), React (TanStack Query), TypeScript

**Spec:** `docs/superpowers/specs/2026-04-15-auto-fetch-ra-achievements-design.md`

---

## File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `server/internal/db/scrape_job.go` | Add `Type` field to `ScrapeQueueItem` |
| Modify | `server/internal/db/models.go` | Add `RAHashChecked` field to `Game` |
| Modify | `server/internal/scraper/queue.go` | Add `IsGameQueuedForType` method |
| Modify | `server/internal/scraper/worker.go` | Dispatch on item type (`scrape` vs `ra_fetch`) |
| Modify | `server/internal/scraper/scraper_ra.go` | Add `FetchRAAchievements` (single-game) |
| Modify | `server/internal/api/ra_handler.go` | Add queue/API-key fields, auto-enqueue logic |
| Modify | `server/internal/api/router.go` | Pass queue + API key to `RAHandler` |
| Modify | `server/internal/scraper/queue_test.go` | Tests for type-aware queueing |
| Create | `server/internal/scraper/scraper_ra_fetch_test.go` | Tests for single-game RA fetch |
| Modify | `server/internal/api/ra_handler_test.go` | Tests for auto-enqueue handler logic |
| Modify | `web/src/types/api.ts` | Add `status` field to `GameAchievements` |
| Modify | `web/src/hooks/use-retroachievements.ts` | Poll when status is `"pending"` |
| Modify | `web/src/hooks/__tests__/use-retroachievements.test.ts` | Tests for pending→data transition |

---

### Task 1: Add `Type` field to `ScrapeQueueItem`

**Files:**
- Modify: `server/internal/db/scrape_job.go:25-35`

- [ ] **Step 1: Add the Type field to the struct**

In `server/internal/db/scrape_job.go`, add a `Type` field to `ScrapeQueueItem`:

```go
type ScrapeQueueItem struct {
	ID           uint       `gorm:"primarykey" json:"id"`
	CreatedAt    time.Time  `gorm:"index:idx_queue_dequeue,priority:3" json:"createdAt"`
	JobID        *uint      `gorm:"index" json:"jobId,omitempty"`
	GameID       uint       `gorm:"not null" json:"gameId"`
	Type         string     `gorm:"size:32;not null;default:'scrape'" json:"type"` // "scrape" = full metadata scrape, "ra_fetch" = RetroAchievements only
	Priority     int        `gorm:"not null;default:0;index:idx_queue_dequeue,priority:2,sort:desc" json:"priority"`
	Status       string     `gorm:"size:32;not null;default:'pending';index:idx_queue_dequeue,priority:1" json:"status"`
	ErrorMessage string     `gorm:"size:512" json:"errorMessage,omitempty"`
	CompletedAt  *time.Time `json:"completedAt,omitempty"`
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd server && go build ./...`
Expected: success (GORM auto-migrates on startup; the new column gets a default value so existing rows are fine)

- [ ] **Step 3: Commit**

```bash
git add server/internal/db/scrape_job.go
git commit -m "feat(db): add Type field to ScrapeQueueItem for ra_fetch support (#410)"
```

---

### Task 2: Add `RAHashChecked` sentinel to Game model

**Files:**
- Modify: `server/internal/db/models.go:161`

- [ ] **Step 1: Add RAHashChecked field next to RAGameID**

In `server/internal/db/models.go`, find the `RAGameID` field (line 161) and add `RAHashChecked` directly after it:

```go
	RAGameID            uint           `gorm:"index" json:"-"` // RetroAchievements game ID (cached from hash lookup)
	// RAHashChecked + RAGameID sentinel logic:
	//   RAHashChecked=false, RAGameID=0  → Not yet looked up. Compute ROM MD5 and query RA.
	//   RAHashChecked=true,  RAGameID=0  → Looked up, but RA doesn't have this game. Do NOT retry.
	//   RAHashChecked=true,  RAGameID>0  → Valid RA game ID cached.
	// RAHashChecked is ONLY set to true after a successful API response (even if RA returned no match).
	// Transient errors (network, 403) leave RAHashChecked=false so the next visit retries.
	RAHashChecked       bool           `gorm:"default:false" json:"-"`
```

- [ ] **Step 2: Verify it compiles**

Run: `cd server && go build ./...`
Expected: success

- [ ] **Step 3: Commit**

```bash
git add server/internal/db/models.go
git commit -m "feat(db): add RAHashChecked sentinel to Game model (#410)"
```

---

### Task 3: Add type-aware queue methods and tests

**Files:**
- Modify: `server/internal/scraper/queue.go:86-93`
- Test: `server/internal/scraper/queue_test.go`

- [ ] **Step 1: Write failing tests for type-aware queueing**

Add these tests to `server/internal/scraper/queue_test.go`:

```go
func TestIsGameQueuedForType(t *testing.T) {
	database := setupQueueTestDB(t)
	q := NewScrapeQueue(database)

	// Nothing queued yet
	queued, err := q.IsGameQueuedForType(42, "ra_fetch")
	require.NoError(t, err)
	assert.False(t, queued)

	// Enqueue a scrape item for game 42
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scraper/ -run "TestIsGameQueuedForType|TestEnqueueGameWithType" -v`
Expected: FAIL — `IsGameQueuedForType` and `EnqueueGameWithType` not defined

- [ ] **Step 3: Implement `EnqueueGameWithType` and `IsGameQueuedForType`**

Add to `server/internal/scraper/queue.go`:

```go
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scraper/ -run "TestIsGameQueuedForType|TestEnqueueGameWithType" -v`
Expected: PASS

- [ ] **Step 5: Run full queue test suite for regressions**

Run: `cd server && go test ./internal/scraper/ -run "Test.*Queue\|Test.*Enqueue\|Test.*Dequeue" -v`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add server/internal/scraper/queue.go server/internal/scraper/queue_test.go
git commit -m "feat(scraper): add type-aware queue methods for ra_fetch (#410)"
```

---

### Task 4: Add `FetchRAAchievements` (single-game) to scraper

**Files:**
- Modify: `server/internal/scraper/scraper_ra.go`
- Create: `server/internal/scraper/scraper_ra_fetch_test.go`

- [ ] **Step 1: Write failing tests**

Create `server/internal/scraper/scraper_ra_fetch_test.go`:

```go
package scraper

import (
	"crypto/md5"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/spela/server/internal/db"
	"github.com/spela/server/internal/retroachievements"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var testROMContent = []byte("test rom for ra fetch")

func testROMHash() string {
	h := md5.Sum(testROMContent)
	return hex.EncodeToString(h[:])
}

func setupRAFetchTestDB(t *testing.T) *gorm.DB {
	t.Helper()
	database, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
	})
	require.NoError(t, err)
	require.NoError(t, database.AutoMigrate(
		&db.Console{}, &db.Game{}, &db.GameAchievementCache{},
	))
	return database
}

func newTestRAServer(t *testing.T) *httptest.Server {
	t.Helper()
	expectedHash := testROMHash()

	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/dorequest.php":
			hash := r.URL.Query().Get("m")
			if hash == expectedHash {
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"GameID":  float64(99),
				})
			} else {
				json.NewEncoder(w).Encode(map[string]interface{}{
					"Success": true,
					"GameID":  float64(0),
				})
			}
		case "/API/API_GetGameExtended.php":
			json.NewEncoder(w).Encode(map[string]interface{}{
				"ID":    99,
				"Title": "Test RA Game",
				"Achievements": map[string]interface{}{
					"1001": map[string]interface{}{
						"ID":          1001,
						"Title":       "First Step",
						"Description": "Do the first thing",
						"Points":      5,
						"BadgeName":   "badge001",
						"type":        3,
					},
				},
			})
		}
	}))
}

func TestFetchRAAchievements_PopulatesCache(t *testing.T) {
	database := setupRAFetchTestDB(t)
	mockRA := newTestRAServer(t)
	defer mockRA.Close()

	// Create ROM file
	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	os.MkdirAll(romDir, 0o755)
	os.WriteFile(filepath.Join(romDir, "game.nes"), testROMContent, 0o644)

	// Create console + game
	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey: "test-api-key",
		GameDirs: []string{tmpDir},
	}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// Verify RAGameID was cached on the game record
	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, uint(99), updated.RAGameID)
	assert.True(t, updated.RAHashChecked)

	// Verify achievement cache was populated
	var cache db.GameAchievementCache
	require.NoError(t, database.Where("ra_game_id = ?", 99).First(&cache).Error)
	assert.Equal(t, "Test RA Game", cache.Title)
	assert.Equal(t, 1, cache.TotalCount)
	assert.Equal(t, game.ID, cache.GameID)
}

func TestFetchRAAchievements_SkipsWhenCacheFresh(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
		RAGameID: 99, RAHashChecked: true,
	}
	require.NoError(t, database.Create(&game).Error)

	// Pre-populate cache
	database.Create(&db.GameAchievementCache{
		RAGameID: 99, GameID: game.ID, Title: "Cached",
		AchievementJSON: "[]", TotalCount: 5, TotalPoints: 50,
	})

	// No RA client needed — should be a no-op
	s := &Scraper{DB: database}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// Cache should be untouched
	var cache db.GameAchievementCache
	database.Where("ra_game_id = ?", 99).First(&cache)
	assert.Equal(t, "Cached", cache.Title)
}

func TestFetchRAAchievements_SetsHashCheckedOnNoMatch(t *testing.T) {
	database := setupRAFetchTestDB(t)

	// Mock RA server that returns GameID=0 for all hashes
	mockRA := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]interface{}{
			"Success": true,
			"GameID":  float64(0),
		})
	}))
	defer mockRA.Close()

	tmpDir := t.TempDir()
	romDir := filepath.Join(tmpDir, "roms")
	os.MkdirAll(romDir, 0o755)
	os.WriteFile(filepath.Join(romDir, "unknown.nes"), []byte("unknown rom"), 0o644)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Unknown Game", FileName: "unknown.nes", FilePath: "roms/unknown.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{
		DB:       database,
		RAClient: &retroachievements.RAClient{BaseURL: mockRA.URL, HTTPClient: mockRA.Client()},
		RAAPIKey: "test-api-key",
		GameDirs: []string{tmpDir},
	}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)

	// RAHashChecked should be true, RAGameID should remain 0
	var updated db.Game
	require.NoError(t, database.First(&updated, game.ID).Error)
	assert.Equal(t, uint(0), updated.RAGameID)
	assert.True(t, updated.RAHashChecked)
}

func TestFetchRAAchievements_SkipsWhenHashCheckedAndNoMatch(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "No RA Game", FileName: "game.nes", FilePath: "roms/game.nes",
		RAGameID: 0, RAHashChecked: true, // Already checked, no match
	}
	require.NoError(t, database.Create(&game).Error)

	// No RA client needed — should skip immediately
	s := &Scraper{DB: database}

	err := s.FetchRAAchievements(&game)
	require.NoError(t, err)
}

func TestFetchRAAchievements_NoRAConfig(t *testing.T) {
	database := setupRAFetchTestDB(t)

	console := db.Console{Name: "NES", Abbreviation: "nes", Playable: true}
	require.NoError(t, database.Create(&console).Error)
	game := db.Game{
		ConsoleID: console.ID, Console: console,
		Title: "Test Game", FileName: "game.nes", FilePath: "roms/game.nes",
	}
	require.NoError(t, database.Create(&game).Error)

	s := &Scraper{DB: database} // No RAClient or RAAPIKey

	err := s.FetchRAAchievements(&game)
	assert.Error(t, err)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/scraper/ -run "TestFetchRAAchievements" -v`
Expected: FAIL — `FetchRAAchievements` not defined

- [ ] **Step 3: Implement `FetchRAAchievements`**

Add to `server/internal/scraper/scraper_ra.go`:

```go
// FetchRAAchievements fetches RetroAchievements data for a single game using
// the server-level API key. This is called by the queue worker for "ra_fetch"
// items, triggered when a user views a game detail page without cached achievements.
//
// The function is idempotent:
//   - If the GameAchievementCache is already fresh (< 24h), it's a no-op.
//   - If RAHashChecked=true and RAGameID=0, the game has no RA match — skip.
//   - If RAGameID > 0, skip the hash lookup and go straight to fetching achievements.
//
// See the Game model for the RAHashChecked + RAGameID sentinel documentation.
func (s *Scraper) FetchRAAchievements(game *db.Game) error {
	if !s.IsRAConfigured() {
		return fmt.Errorf("RA client or API key not configured")
	}

	// If we already know RA doesn't have this game, skip.
	if game.RAHashChecked && game.RAGameID == 0 {
		return nil
	}

	raGameID := game.RAGameID

	// Resolve RAGameID if not cached yet.
	if raGameID == 0 {
		var hash string
		for _, dir := range s.GameDirs {
			candidate := filepath.Join(dir, game.FilePath)
			if h, err := retroachievements.ComputeMD5(candidate); err == nil {
				hash = h
				break
			}
		}
		if hash == "" {
			// ROM file not found — mark as checked so we don't retry.
			s.DB.Model(&db.Game{}).Where("id = ?", game.ID).
				Updates(map[string]interface{}{"ra_hash_checked": true})
			slog.Warn("RA fetch: ROM file not found", "game", game.Title, "path", game.FilePath)
			return nil
		}

		time.Sleep(500 * time.Millisecond) // RA API rate limit
		id, err := s.RAClient.GetGameIDFromHash(hash)
		if err != nil {
			// Transient error — do NOT set RAHashChecked, allow retry.
			return fmt.Errorf("RA hash lookup failed for %q: %w", game.Title, err)
		}

		// Mark hash as checked regardless of whether RA returned a match.
		updates := map[string]interface{}{"ra_hash_checked": true, "ra_game_id": id}
		s.DB.Model(&db.Game{}).Where("id = ?", game.ID).Updates(updates)

		if id == 0 {
			slog.Debug("RA fetch: no RA match for game", "game", game.Title)
			return nil
		}
		raGameID = id
	}

	// Check if cache is already fresh (< 24h).
	var cache db.GameAchievementCache
	cacheHit := s.DB.Where("ra_game_id = ?", raGameID).First(&cache).Error == nil
	if cacheHit && time.Since(cache.CachedAt) < 24*time.Hour {
		return nil // Cache is fresh, nothing to do.
	}

	// Fetch achievements from RA using server API key.
	time.Sleep(500 * time.Millisecond) // RA API rate limit
	gameInfo, err := s.RAClient.GetGameExtended(s.RAAPIKey, raGameID)
	if err != nil {
		return fmt.Errorf("RA achievement fetch failed for %q (raGameID=%d): %w", game.Title, raGameID, err)
	}

	achJSON, err := json.Marshal(gameInfo.Achievements)
	if err != nil {
		return fmt.Errorf("marshalling achievements for %q: %w", game.Title, err)
	}

	// Upsert cache entry.
	if cacheHit {
		cache.Title = gameInfo.Title
		cache.AchievementJSON = string(achJSON)
		cache.TotalCount = gameInfo.TotalCount
		cache.TotalPoints = gameInfo.TotalPoints
		cache.CachedAt = time.Now()
		cache.GameID = game.ID
		s.DB.Save(&cache)
	} else {
		s.DB.Create(&db.GameAchievementCache{
			RAGameID:        raGameID,
			GameID:          game.ID,
			Title:           gameInfo.Title,
			AchievementJSON: string(achJSON),
			TotalCount:      gameInfo.TotalCount,
			TotalPoints:     gameInfo.TotalPoints,
			CachedAt:        time.Now(),
		})
	}

	slog.Info("RA fetch: cached achievements", "game", game.Title, "raGameId", raGameID, "count", gameInfo.TotalCount)
	return nil
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd server && go test ./internal/scraper/ -run "TestFetchRAAchievements" -v`
Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add server/internal/scraper/scraper_ra.go server/internal/scraper/scraper_ra_fetch_test.go
git commit -m "feat(scraper): add FetchRAAchievements for single-game RA fetch (#410)"
```

---

### Task 5: Dispatch `ra_fetch` in queue worker

**Files:**
- Modify: `server/internal/scraper/worker.go:83-131`

- [ ] **Step 1: Add `ra_fetch` dispatch to `processItem`**

In `server/internal/scraper/worker.go`, modify the `processItem` function. Replace the block starting at "Variant group propagation" through the end of the `if !propagated` block with type-aware dispatch:

Find this code (lines ~94-120):
```go
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
			w.broadcastScrapeStatus(game.ID, "idle")
			return
		}

		// Propagate metadata to unscraped siblings in the same variant group
		if game.GroupKey != "" {
			w.scraper.propagateToGroup(&game)
		}
	}
```

Replace with:

```go
	// Dispatch based on queue item type.
	// Default to "scrape" for backward compatibility with items created before
	// the Type field was added (they have Type="" due to GORM zero-value).
	itemType := item.Type
	if itemType == "" {
		itemType = "scrape"
	}

	switch itemType {
	case "ra_fetch":
		if err := w.scraper.FetchRAAchievements(&game); err != nil {
			slog.Warn("scrape worker: RA fetch failed", "game", game.Title, "error", err)
			jobDone, _ := w.queue.MarkFailed(item, err.Error())
			w.broadcastProgress(item, &game, false, jobDone)
			w.broadcastScrapeStatus(game.ID, "idle")
			return
		}

	default: // "scrape" — full metadata scrape
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
				w.broadcastScrapeStatus(game.ID, "idle")
				return
			}

			// Propagate metadata to unscraped siblings in the same variant group
			if game.GroupKey != "" {
				w.scraper.propagateToGroup(&game)
			}
		}
	}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd server && go build ./...`
Expected: success

- [ ] **Step 3: Commit**

```bash
git add server/internal/scraper/worker.go
git commit -m "feat(scraper): dispatch ra_fetch items in queue worker (#410)"
```

---

### Task 6: Extend `RAHandler` to auto-enqueue and add handler tests

**Files:**
- Modify: `server/internal/api/ra_handler.go:22-27` (struct) and `189-326` (handler)
- Modify: `server/internal/api/router.go:176` (handler construction)
- Test: `server/internal/api/ra_handler_test.go`

- [ ] **Step 1: Write failing tests for auto-enqueue behavior**

Add to `server/internal/api/ra_handler_test.go`:

```go
func TestGetGameAchievements_AutoEnqueuesRAFetch(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Set server RA API key so auto-fetch is enabled
	cfg.DB.Create(&db.ServerSetting{Key: "ra_api_key", Value: "test-server-key"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 202 with pending status (no cache, no user RA creds)
	assert.Equal(t, http.StatusAccepted, w.Code)

	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "pending", resp["status"])
}

func TestGetGameAchievements_ReturnsPendingWhenAlreadyQueued(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	cfg.DB.Create(&db.ServerSetting{Key: "ra_api_key", Value: "test-server-key"})

	// Manually enqueue an ra_fetch item
	cfg.Scraper.Queue.EnqueueGameWithType(game.ID, nil, 100, "ra_fetch")

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	assert.Equal(t, http.StatusAccepted, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, "pending", resp["status"])
}

func TestGetGameAchievements_ReturnsCachedData(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Pre-populate the RA game ID and cache
	cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).Update("ra_game_id", 42)
	achJSON, _ := json.Marshal([]map[string]interface{}{
		{"ID": 501, "Title": "Test Achievement", "Description": "Do thing", "Points": 10, "BadgeName": "badge1", "type": "core"},
	})
	cfg.DB.Create(&db.GameAchievementCache{
		RAGameID: 42, GameID: game.ID, Title: "Test ROM Game",
		AchievementJSON: string(achJSON), TotalCount: 1, TotalPoints: 10,
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 with cached data
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	assert.Equal(t, float64(1), resp["totalCount"])
}

func TestGetGameAchievements_NoAutoFetchWithoutRAKey(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// No server RA API key configured, no user RA credentials

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 with empty response (no auto-fetch without RA key)
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Empty(t, achievements)
}

func TestGetGameAchievements_SkipsAutoFetchWhenHashCheckedNoMatch(t *testing.T) {
	mockRA, router, cfg := setupRATestEnv(t)
	defer mockRA.Close()

	token := registerAndGetToken(t, router)
	game := createGameWithROM(t, cfg)

	// Mark game as checked but no RA match
	cfg.DB.Model(&db.Game{}).Where("id = ?", game.ID).
		Updates(map[string]interface{}{"ra_hash_checked": true, "ra_game_id": 0})

	cfg.DB.Create(&db.ServerSetting{Key: "ra_api_key", Value: "test-server-key"})

	w := httptest.NewRecorder()
	req := httptest.NewRequest("GET", fmt.Sprintf("/api/games/%d/achievements", game.ID), nil)
	req.Header.Set("Authorization", "Bearer "+token)
	router.ServeHTTP(w, req)

	// Should return 200 empty — no enqueue, no pending
	assert.Equal(t, http.StatusOK, w.Code)
	var resp map[string]interface{}
	json.Unmarshal(w.Body.Bytes(), &resp)
	achievements := resp["achievements"].([]interface{})
	assert.Empty(t, achievements)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd server && go test ./internal/api/ -run "TestGetGameAchievements_Auto|TestGetGameAchievements_Returns|TestGetGameAchievements_No|TestGetGameAchievements_Skips" -v`
Expected: FAIL — handler doesn't have queue access or auto-enqueue logic yet

- [ ] **Step 3: Add queue and API key fields to RAHandler**

In `server/internal/api/ra_handler.go`, update the struct:

```go
type RAHandler struct {
	DB            *gorm.DB
	RAClient      *retroachievements.RAClient
	GameDir       string
	EncryptionKey []byte // AES-256 key for encrypting RA tokens at rest
	Queue         *scraper.ScrapeQueue // Scrape queue for async RA fetch jobs
	RAAPIKey      string               // Server-level RA API key; empty = auto-fetch disabled
}
```

Add the import for the scraper package at the top of the file:

```go
import (
	// ... existing imports ...
	"github.com/spela/server/internal/scraper"
)
```

- [ ] **Step 4: Update RAHandler construction in router.go**

In `server/internal/api/router.go`, line 176, update the handler construction:

```go
	var raQueue *scraper.ScrapeQueue
	var raAPIKey string
	if cfg.Scraper != nil {
		raQueue = cfg.Scraper.Queue
		raAPIKey = cfg.Scraper.RAAPIKey
	}
	raHandler := &RAHandler{
		DB: cfg.DB, RAClient: raClient, GameDir: cfg.GameDirs[0],
		EncryptionKey: encryptionKey, Queue: raQueue, RAAPIKey: raAPIKey,
	}
```

- [ ] **Step 5: Modify GetGameAchievements to auto-enqueue**

In `server/internal/api/ra_handler.go`, replace the `GetGameAchievements` function body. The key changes are:
1. After the cache check, if no fresh cache and no user credentials, try auto-enqueue
2. If `RAHashChecked=true` and `RAGameID=0`, return empty (no RA match)
3. Return 202 with `{ status: "pending" }` when enqueuing

Replace the section after the fresh cache return (starting from "Cache is stale or missing — try to refresh using user's RA credentials") with:

```go
	// Cache is stale or missing — try to refresh using user's RA credentials
	var cred db.RetroAchievementCredential
	hasUserCreds := h.DB.Where("user_id = ?", uid).First(&cred).Error == nil

	if hasUserCreds {
		raToken, err := h.decryptRAToken(&cred)
		if err != nil {
			slog.Error("failed to decrypt RA token", "user_id", uid, "error", err)
			// Fall through to server-key auto-fetch below
		} else {
			gameInfo, _, err := h.RAClient.GetGameInfoAndUserProgress(cred.RAUsername, raToken, raGameID)
			if err != nil {
				slog.Error("failed to fetch RA game info", "ra_game_id", raGameID, "error", err)
				// Fall through to server-key auto-fetch below
			} else {
				// Success — cache and return
				achJSON, _ := json.Marshal(gameInfo.Achievements)
				if cacheHit {
					cache.Title = gameInfo.Title
					cache.AchievementJSON = string(achJSON)
					cache.TotalCount = gameInfo.TotalCount
					cache.TotalPoints = gameInfo.TotalPoints
					cache.CachedAt = time.Now()
					cache.GameID = game.ID
					h.DB.Save(&cache)
				} else {
					h.DB.Create(&db.GameAchievementCache{
						RAGameID:        raGameID,
						GameID:          game.ID,
						Title:           gameInfo.Title,
						AchievementJSON: string(achJSON),
						TotalCount:      gameInfo.TotalCount,
						TotalPoints:     gameInfo.TotalPoints,
						CachedAt:        time.Now(),
					})
				}
				c.JSON(http.StatusOK, gin.H{
					"raGameId":     raGameID,
					"title":        gameInfo.Title,
					"achievements": gameInfo.Achievements,
					"totalCount":   gameInfo.TotalCount,
					"totalPoints":  gameInfo.TotalPoints,
				})
				return
			}
		}
	}

	// No user credentials or user fetch failed — try server-level auto-fetch via queue.
	if h.Queue != nil && h.RAAPIKey != "" {
		queued, _ := h.Queue.IsGameQueuedForType(game.ID, "ra_fetch")
		if !queued {
			if err := h.Queue.EnqueueGameWithType(game.ID, nil, 100, "ra_fetch"); err != nil {
				slog.Warn("RA auto-fetch: failed to enqueue", "game", game.Title, "error", err)
			}
		}
		c.JSON(http.StatusAccepted, gin.H{"status": "pending"})
		return
	}

	// No server RA key configured — return stale cache if available, otherwise empty.
	if cacheHit {
		var achievements []retroachievements.Achievement
		if err := json.Unmarshal([]byte(cache.AchievementJSON), &achievements); err == nil {
			c.JSON(http.StatusOK, gin.H{
				"raGameId":     raGameID,
				"title":        cache.Title,
				"achievements": achievements,
				"totalCount":   cache.TotalCount,
				"totalPoints":  cache.TotalPoints,
			})
			return
		}
	}
	c.JSON(http.StatusOK, emptyResponse)
```

Also modify the top of `GetGameAchievements` to handle the `RAHashChecked` sentinel. After `raGameID := game.RAGameID` and before the `if raGameID == 0` hash lookup block, add:

```go
	// If we already checked and RA doesn't have this game, return empty.
	if game.RAHashChecked && raGameID == 0 {
		c.JSON(http.StatusOK, emptyResponse)
		return
	}
```

And in the existing `if raGameID == 0` block where it does the hash lookup inline, also update the game's `RAHashChecked` field after a successful lookup:

```go
		// Cache the RA game ID and mark hash as checked on the game record
		h.DB.Model(&db.Game{}).Where("id = ?", game.ID).
			Updates(map[string]interface{}{"ra_game_id": raGameID, "ra_hash_checked": true})
```

**Important:** The inline hash lookup in the handler is still needed for the case where a user WITH RA credentials views a game that hasn't been hashed yet — they get results immediately via their credentials. The auto-enqueue path is for users WITHOUT credentials.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd server && go test ./internal/api/ -run "TestGetGameAchievements" -v`
Expected: all PASS (both new and existing tests)

- [ ] **Step 7: Run full RA handler test suite**

Run: `cd server && go test ./internal/api/ -run "TestLink|TestUnlink|TestGetGameAchievement|TestGetAchievement|TestRefresh" -v`
Expected: all PASS

- [ ] **Step 8: Commit**

```bash
git add server/internal/api/ra_handler.go server/internal/api/router.go server/internal/api/ra_handler_test.go
git commit -m "feat(api): auto-enqueue ra_fetch when achievements not cached (#410)"
```

---

### Task 7: Update frontend types and hook for pending status

**Files:**
- Modify: `web/src/types/api.ts:362-367`
- Modify: `web/src/hooks/use-retroachievements.ts:56-62`
- Test: `web/src/hooks/__tests__/use-retroachievements.test.ts`

- [ ] **Step 1: Write failing tests for pending→data transition**

Add to `web/src/hooks/__tests__/use-retroachievements.test.ts`, inside the existing `describe("useGameAchievements", ...)` block:

```typescript
  it("polls when status is pending and stops when data arrives", async () => {
    const pendingResponse = { status: "pending" as const };
    mockApi.get
      .mockResolvedValueOnce(pendingResponse)
      .mockResolvedValueOnce(pendingResponse)
      .mockResolvedValueOnce(mockAchievements);

    const { result } = renderHook(() => useGameAchievements("game-1"), {
      wrapper: createWrapper(),
    });

    // First call returns pending
    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.status).toBe("pending");

    // Should eventually get real data via refetch
    await waitFor(
      () => {
        expect(result.current.data?.achievements).toBeDefined();
        expect(result.current.data?.achievements?.length).toBeGreaterThan(0);
      },
      { timeout: 10000 },
    );

    expect(result.current.data?.totalCount).toBe(2);
  });
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run src/hooks/__tests__/use-retroachievements.test.ts --reporter=verbose`
Expected: FAIL — `status` field not on type, no refetch interval logic

- [ ] **Step 3: Add `status` field to `GameAchievements` type**

In `web/src/types/api.ts`, update the interface:

```typescript
export interface GameAchievements {
  status?: "pending";
  raGameId: number;
  totalCount: number;
  totalPoints: number;
  achievements: Achievement[];
}
```

- [ ] **Step 4: Update `useGameAchievements` hook to poll when pending**

In `web/src/hooks/use-retroachievements.ts`, replace the `useGameAchievements` function:

```typescript
export function useGameAchievements(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievements", gameId],
    queryFn: () => api.get<GameAchievements>(`/games/${gameId}/achievements`),
    enabled: !!gameId,
    refetchInterval: (query) => {
      // Poll every 2 seconds while the server is processing an RA fetch.
      // Stop polling once we have actual achievement data.
      if (query.state.data?.status === "pending") {
        return 2000;
      }
      return false;
    },
  });
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && npx vitest run src/hooks/__tests__/use-retroachievements.test.ts --reporter=verbose`
Expected: all PASS

- [ ] **Step 6: Run full frontend test suite for regressions**

Run: `cd web && npx vitest run --reporter=verbose`
Expected: all PASS

- [ ] **Step 7: Commit**

```bash
git add web/src/types/api.ts web/src/hooks/use-retroachievements.ts web/src/hooks/__tests__/use-retroachievements.test.ts
git commit -m "feat(web): poll for achievements when RA fetch is pending (#410)"
```

---

### Task 8: Backend integration test and full test run

**Files:**
- No new files — validation task

- [ ] **Step 1: Run full backend test suite**

Run: `cd server && go test ./... 2>&1 | tail -30`
Expected: all PASS, no regressions

- [ ] **Step 2: Run full frontend test suite**

Run: `cd web && npx vitest run`
Expected: all PASS

- [ ] **Step 3: Build both projects**

Run: `cd server && go build ./... && cd ../web && npm run build`
Expected: both succeed

- [ ] **Step 4: Commit if any fixes were needed**

Only if regressions were found and fixed in previous steps.
