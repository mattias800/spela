# System Events — Design Spec

**Issue:** #415
**Date:** 2026-04-16

## Overview

Rename and extend the existing `SecurityEvent` system into a unified
`SystemEvent` system with category support. Security events remain unchanged
in behavior; a new "operational" category surfaces infrastructure issues
(scraper failures, missing ROMs, invalid credentials) to admins through the
same UI.

## Goals

- Give self-hosted admins visibility into operational issues that currently
  only appear in server logs
- Reuse and extend the proven SecurityEvent infrastructure rather than
  building a parallel system
- Support future event categories without code changes beyond seeding a new
  row and adding event types

## Data Model

### SystemEventCategory

New table, code-seeded on startup. Not admin-editable.

```go
type SystemEventCategory struct {
    ID   uint   `gorm:"primarykey"`
    Code string `gorm:"size:32;uniqueIndex;not null"` // "security", "operational"
    Name string `gorm:"size:64;not null"`             // "Security", "Operational"
}
```

Seeded rows:

| ID | Code          | Name          |
|----|---------------|---------------|
| 1  | security      | Security      |
| 2  | operational   | Operational   |

### SystemEvent (renamed from SecurityEvent)

All existing fields preserved. New fields:

| Field        | Type       | Notes                                      |
|--------------|------------|--------------------------------------------|
| CategoryID   | uint       | FK → SystemEventCategory, not null, indexed |
| DismissedAt  | *time.Time | Nullable, indexed. Set on dismiss.          |

Existing fields (unchanged):

| Field         | Type    | Notes                                      |
|---------------|---------|--------------------------------------------|
| ID            | uint    | Primary key                                |
| CreatedAt     | time.Time | Indexed                                  |
| EventType     | string  | size:64, indexed                           |
| Reason        | string  | size:64, indexed                           |
| Username      | string  | size:128, indexed                          |
| UsernameLower | string  | size:128, indexed (case-insensitive filter) |
| UserID        | *uint   | Nullable, indexed                          |
| IP            | string  | size:64, indexed                           |
| Path          | string  | size:256                                   |
| Metadata      | string  | JSON blob                                  |

For operational events, `Username`, `UserID`, `IP`, and `Path` will
typically be empty/nil. That's fine — no structural change needed.

### Migration

1. Create `system_event_categories` table via GORM auto-migrate
2. Seed "security" and "operational" category rows
3. Rename table `security_events` → `system_events` (manual SQL)
4. Add `category_id` column, backfill all existing rows with the security
   category ID
5. Add `dismissed_at` column (nullable, defaults nil)
6. Add NOT NULL constraint on `category_id` after backfill

### Event Type Constants

Existing security types unchanged:

- `login_success`, `login_failed`, `login_locked`, `login_blocked`
- `account_locked`, `revoked_token_used`, `disabled_account_token`
- `token_user_missing`, `stale_token_version`

New operational types:

| Type                          | Category    | Description                                  |
|-------------------------------|-------------|----------------------------------------------|
| `ra_circuit_breaker_tripped`  | operational | RA API hit consecutive failure threshold      |
| `scraper_repeated_errors`     | operational | Scraper source returning persistent errors    |
| `rom_file_missing`            | operational | ROM file in DB but not found on disk          |
| `api_credentials_invalid`     | operational | External API credentials expired or invalid   |

Each type maps to exactly one category. The mapping is defined in code
alongside the type constants.

## Recording API

### SystemEventInput

```go
type SystemEventInput struct {
    Category  string         // category code: "security", "operational"
    EventType string
    Reason    string
    Username  string
    UserID    *uint
    IP        string
    Path      string
    Metadata  map[string]any
}
```

Category is a code string, resolved to `CategoryID` at write time. The
recorder caches the category lookup on first call.

### Recording Functions

```go
// Internal — resolves category, writes to DB, mirrors to slog
func recordSystemEvent(db *gorm.DB, in SystemEventInput)

// Public convenience — sets Category for the caller
func RecordSecurityEvent(db *gorm.DB, in SystemEventInput)    // Category = "security"
func RecordOperationalEvent(db *gorm.DB, in SystemEventInput)  // Category = "operational"
```

HTTP convenience wrappers that extract IP/path from gin context:

```go
func recordSecurityEventCtx(db *gorm.DB, c *gin.Context, in SystemEventInput)
func recordOperationalEventCtx(db *gorm.DB, c *gin.Context, in SystemEventInput)
```

Behavior unchanged from current SecurityEvent recording:
- Best-effort persistence (DB failure never blocks the caller)
- Always mirrors to slog
- Existing dedup guard stays for the same security event types

### Dismissal

```go
func DismissSystemEvent(db *gorm.DB, id uint) error
```

Sets `dismissed_at = time.Now()`. No undismiss — use the filter toggle to
view dismissed events.

## API Endpoints

### Renamed Routes

| Method | Path                              | Handler                  |
|--------|-----------------------------------|--------------------------|
| GET    | `/admin/system-events`            | ListSystemEvents         |
| GET    | `/admin/system-events/types`      | GetSystemEventTypes      |
| GET    | `/admin/system-events/:id`        | GetSystemEvent           |
| GET    | `/admin/system-events/categories` | GetSystemEventCategories |
| PUT    | `/admin/system-events/:id/dismiss`| DismissSystemEvent       |

### ListSystemEvents — New Query Parameters

Added to existing params (page, pageSize, eventType, username, ip, since):

| Param      | Type   | Default | Behavior                              |
|------------|--------|---------|---------------------------------------|
| `category` | string | —       | Filter by category code               |
| `dismissed`| bool   | `false` | `false` = exclude dismissed events, `true` = include all (dismissed and not)|

### GetSystemEventTypes Response

Updated to include category mapping:

```json
[
  { "type": "login_success", "category": "security" },
  { "type": "ra_circuit_breaker_tripped", "category": "operational" }
]
```

Frontend uses this to filter the type dropdown when a category is selected.

### GetSystemEventCategories Response

```json
[
  { "code": "security", "name": "Security" },
  { "code": "operational", "name": "Operational" }
]
```

## Frontend

### Page

- Route: `/admin/security-events` → `/admin/system-events`
- Page title: "Security Events" → "System Events"
- Nav item label updated to match

### Category Filter

Row of filter chips above existing filters. Options: "All" plus one chip per
category from the `/categories` endpoint. Selecting a category:

- Narrows the event type dropdown to types belonging to that category
- Persists in URL as `?category=security`

### Dismissed Filter

Toggle/checkbox: "Show dismissed". Off by default. Persisted in URL as
`?dismissed=true`.

### Dismiss Action

Each event row gets a dismiss button (subtle icon). Clicking it:

- Calls `PUT /admin/system-events/:id/dismiss`
- Optimistically removes the row from the current view
- If "Show dismissed" is on, the row stays but gets a visual indicator
  (reduced opacity or strikethrough timestamp)

### Event Badges

Existing security event badges unchanged. New operational event badges:

| Type                         | Icon         | Severity | Label               |
|------------------------------|--------------|----------|---------------------|
| `ra_circuit_breaker_tripped` | warning      | alert    | RA Circuit Breaker  |
| `scraper_repeated_errors`    | warning      | notice   | Scraper Errors      |
| `rom_file_missing`           | file-missing | notice   | ROM Missing         |
| `api_credentials_invalid`    | key/lock     | alert    | Invalid Credentials |

### Hooks

`use-security-events.ts` → `use-system-events.ts`. Same TanStack Query
pattern, updated endpoints, new query keys for category and dismissed params.

### Types

`SecurityEvent` / `SecurityEventResponse` types in `api.ts` renamed to
`SystemEvent` / `SystemEventResponse` with added `categoryCode`, `categoryName`,
and `dismissedAt` fields.

## Event Producers

### 1. RA Circuit Breaker Tripped

**File:** `server/internal/scraper/scraper.go` — `tryFetchRAAchievements()`

When `raConsecutiveFailures >= threshold` and circuit opens:

```go
RecordOperationalEvent(db, SystemEventInput{
    EventType: "ra_circuit_breaker_tripped",
    Metadata: map[string]any{
        "consecutiveFailures": s.raConsecutiveFailures,
        "lastError":           err.Error(),
    },
})
```

### 2. Scraper Repeated Errors

**File:** `server/internal/scraper/worker.go`

Track consecutive failures per source. When a threshold is hit (5
consecutive failures from the same source):

```go
RecordOperationalEvent(db, SystemEventInput{
    EventType: "scraper_repeated_errors",
    Metadata: map[string]any{
        "source":    "igdb",
        "error":     err.Error(),
        "gameTitle": game.Title,
    },
})
```

Uses the existing dedup guard pattern (tuple + time window) to avoid
flooding the DB.

### 3. ROM File Missing

**File:** Game download/serve handler.

When a ROM path exists in the DB but the file is not found on disk:

```go
RecordOperationalEvent(db, SystemEventInput{
    EventType: "rom_file_missing",
    Metadata: map[string]any{
        "gameId":       game.ID,
        "gameTitle":    game.Title,
        "expectedPath": game.RomPath,
    },
})
```

Dedup on game ID — one event per missing ROM per time window.

### 4. API Credentials Invalid

**File:** Scraper initialization or first API call returning 401/403.

When IGDB or RetroAchievements credentials fail:

```go
RecordOperationalEvent(db, SystemEventInput{
    EventType: "api_credentials_invalid",
    Metadata: map[string]any{
        "service": "retroachievements",
        "error":   err.Error(),
    },
})
```

Dedup on service name — one event per service per time window.

## Cleanup

All system events (both categories) use 90-day retention. The existing
background pruning in `StartTokenCleanup()` handles this — just rename the
function it calls from `pruneExpiredSecurityEvents` to
`pruneExpiredSystemEvents` and update the query to target `SystemEvent`.

## Testing

### Backend (Go)

- **Migration tests:** Category seeding, table rename, backfill of existing
  rows with correct category_id, dismissed_at column present
- **Recorder tests:** Existing tests adapted for renamed functions. New tests
  for operational event recording, category resolution, category ID caching,
  and dedup for operational types
- **Handler tests:** Existing tests renamed. New tests for category filter,
  dismissed filter, dismiss endpoint, categories endpoint
- **Producer tests:** Each wiring point gets a test verifying correct event
  type and metadata are recorded when the condition triggers

### Frontend (Web)

- **E2E (Playwright):** Existing security events tests updated for new
  route/naming. New tests:
  - Category filter narrows visible events
  - Dismiss button removes event from default view
  - "Show dismissed" toggle brings dismissed events back
  - Operational events display with correct badges
- **Unit (Vitest):** Badge component tests for new operational event types

No player app changes — admin-only, web-only feature.
