# Persistent Scrape Queue

## Problem

Scraping metadata for a full 40k-game library takes over 24 hours. If the
backend is restarted (deploy, crash, maintenance), the entire scrape must be
restarted from scratch. There is no way to pause, resume, or track job-level
progress persistently.

## Solution

Replace the current in-memory, inline scraping loop with a **SQLite-backed
persistent queue**. All scraping — bulk, manual, and startup auto-scrape —
flows through a single pipeline. The queue survives server restarts, and the
worker automatically resumes on startup.

## Data Model

### `scrape_jobs` table

| Column           | Type      | Purpose                                               |
|------------------|-----------|-------------------------------------------------------|
| `id`             | uint (PK) | Job ID                                                |
| `status`         | string    | `pending`, `running`, `completed`, `cancelled`        |
| `mode`           | string    | `new`, `all`, `fallback`                              |
| `source_filter`  | string    | Optional: `igdb`, `steamgriddb`, etc.                 |
| `status_filter`  | string    | Optional: `not_found`, `not_attempted`, etc.          |
| `console_filter` | string    | Optional: console abbreviation                        |
| `total_items`    | int       | Total games enqueued                                  |
| `completed_items`| int       | Games successfully processed                          |
| `failed_items`   | int       | Games that errored                                    |
| `created_at`     | timestamp | When job was created                                  |
| `started_at`     | timestamp | When processing began                                 |
| `completed_at`   | timestamp | When job finished (nullable)                          |

Denormalized counters (`total_items`, `completed_items`, `failed_items`) avoid
`COUNT(*)` queries on 40k rows when the UI polls for progress.

### `scrape_queue_items` table

| Column           | Type      | Purpose                                               |
|------------------|-----------|-------------------------------------------------------|
| `id`             | uint (PK) | Item ID                                               |
| `job_id`         | uint (FK) | Which job this belongs to (nullable for manual scrapes)|
| `game_id`        | uint (FK) | Game to scrape                                        |
| `priority`       | int       | Higher = processed first. Bulk: 0, manual: 100        |
| `status`         | string    | `pending`, `in_progress`, `completed`, `failed`       |
| `error_message`  | string    | If failed, why                                        |
| `created_at`     | timestamp |                                                       |
| `completed_at`   | timestamp |                                                       |

**Index**: `(status, priority DESC, created_at ASC)` for efficient dequeue.

## Queue Worker

A single `ScrapeWorker` goroutine runs for the lifetime of the server.

### Main loop

```
ScrapeWorker starts on server boot
  │
  ├─ Check for incomplete job (status = 'running')
  │   └─ Reset any 'in_progress' items back to 'pending'
  │      (they were interrupted mid-flight)
  │
  └─ Enter main loop:
      ├─ Query next pending item (ORDER BY priority DESC, created_at ASC, LIMIT 1)
      │
      ├─ No item → sleep 2s, retry (idle polling)
      │
      ├─ Item found:
      │   ├─ Set item status = 'in_progress'
      │   ├─ Call existing ScrapeGame() (unchanged)
      │   ├─ Set item status = 'completed' or 'failed'
      │   ├─ Increment job counters
      │   ├─ If all items done → mark job 'completed'
      │   └─ Broadcast progress via WebSocket
      │
      └─ Check shutdown signal before next iteration
```

### Properties

- **Single goroutine**: no concurrency on the queue itself. Parallelism stays
  inside `ScrapeGame()` (concurrent IGDB + LibRetro + SteamGridDB calls).
- **Idle polling**: when queue is empty, worker sleeps 2s and rechecks. Manual
  scrapes enqueued at any time get picked up within seconds.
- **Graceful shutdown**: worker receives server context. On SIGTERM, finishes
  the current game, does not dequeue next item, exits. Job stays `running` so
  startup knows to resume.
- **Hard kill recovery**: if killed with SIGKILL/power loss, `in_progress`
  items are reset to `pending` on next startup. Re-scraping a game is
  idempotent (clears and rewrites metadata).

## API Changes

### `POST /api/admin/scrape` — TriggerScrape

Same query params as today (`mode`, `console`, `source`, `status`).

New optional param: **`conflict`** (`reject` | `replace` | `merge`, default:
`reject`).

Changed behavior:
1. Query matching games (same logic as today)
2. Create `scrape_job` row
3. Bulk-insert `scrape_queue_items` (priority 0)
4. Return job ID + total count

Conflict handling:
- `reject` (default): return 409 with active job info if a job is running. UI
  can present replace/merge/cancel options.
- `replace`: cancel current job (mark remaining items cancelled), create new
  job.
- `merge`: deduplicate against existing pending items, add new games to the
  current job (same `job_id`), update `total_items`. The merged items appear in
  the same queue and progress counts.

### `DELETE /api/admin/scrape` — CancelScrape

- Mark active job as `cancelled`
- Mark all pending queue items as `cancelled`
- Current in-progress game finishes (no mid-game interruption)

### `GET /api/admin/scrape/status` — ScrapeStatus

- Returns data from `scrape_job` row: status, progress counts, created_at,
  elapsed time
- WebSocket progress events continue as today

### `POST /api/admin/games/:id/scrape` — ScrapeGame (single)

- Insert queue item with priority 100 (jumps the line)
- `job_id` nullable — attaches to active job or stands alone
- Returns immediately; worker picks it up within seconds

### `POST /api/games/:id/scrape-if-needed` — User auto-scrape

- Same as single-game: enqueue with high priority

## Startup & Shutdown Lifecycle

### Startup

```
Server starts
  │
  ├─ GORM auto-migrate (adds new tables)
  │
  ├─ Start ScrapeWorker goroutine with server context
  │   └─ Check for incomplete job (status = 'running')
  │       └─ Reset 'in_progress' items → 'pending'
  │       └─ Resume processing
  │
  ├─ Library scan (existing)
  │   └─ New games found + IGDB configured:
  │       └─ Enqueue via conflict=merge
  │       └─ If no existing job: creates new job
  │       └─ If existing job: adds new games to it
  │
  ├─ RetroAchievements scrape → also enqueued through same pipeline
  │
  └─ HTTP server starts
```

### Shutdown

```
SIGTERM/SIGINT received
  │
  ├─ Server context cancelled
  │
  ├─ ScrapeWorker:
  │   ├─ Finishes current game
  │   ├─ Does NOT dequeue next item
  │   ├─ Job stays 'running' (resume on next startup)
  │   └─ Goroutine exits
  │
  └─ HTTP server drains (existing 15s timeout)
```

## Error Handling

- **Per-item errors**: item marked `failed` with error message, `failed_items`
  incremented, worker moves to next item. One bad game never blocks the queue.
- **`GameScrapeResult` unchanged**: queue tracks job progress (did we attempt
  this game?), `GameScrapeResult` tracks scrape outcomes per source (did IGDB
  find a match?). Complementary, not redundant.
- **No automatic retries at the queue level**: failed items stay failed. Users
  can trigger a new scrape with `source=igdb&status=error` to retry. The UI
  can surface a "Retry failed" button for this.

## Code Organization

### New files

| File | Purpose |
|------|---------|
| `server/internal/scraper/queue.go` | `ScrapeQueue` — enqueue, dequeue, mark complete/failed, cancel, merge |
| `server/internal/scraper/worker.go` | `ScrapeWorker` — background goroutine, main loop, startup resume |
| `server/internal/db/scrape_job.go` | GORM models: `ScrapeJob`, `ScrapeQueueItem` |

### Modified files

| File | Change |
|------|--------|
| `server/internal/scraper/scraper.go` | Remove in-memory mutex lock. Add `ScrapeQueue` and `ScrapeWorker` as fields on `MetaScraper`. |
| `server/internal/scraper/scraper_batch.go` | `TriggerScrape()` → enqueue + return. Remove `ScrapeAll()` loop (query logic moves to enqueue step). |
| `server/internal/api/admin_scraper.go` | Return job ID, accept `conflict` param, updated status response. |
| `server/cmd/server/main.go` | Start `ScrapeWorker`, wire startup auto-scrape through queue. |

### Unchanged

- `ScrapeGame()` — the unit of work, called by worker exactly as before.
- All external scraper clients (IGDB, LibRetro, SteamGridDB, Pouet, RA).
- `GameScrapeResult` tracking.
- WebSocket progress events (same shape, emitted from worker instead of `ScrapeAll`).
