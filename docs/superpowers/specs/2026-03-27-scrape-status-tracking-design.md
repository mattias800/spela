# Scrape Status Tracking — Per-Source Results

**Date:** 2026-03-27
**Status:** Approved

## Overview

Track scrape outcomes per source per game, giving admins visibility into the library's metadata completeness and fine-grained control over what to retry. Replaces the current implicit status (derived from `scraper_id`) with explicit per-source result rows.

## Data Model

**New table: `game_scrape_results`**

| Column | Type | Constraints | Purpose |
|--------|------|-------------|---------|
| `id` | uint | PK, auto | Row ID |
| `game_id` | uint | FK → games, not null | Which game |
| `source` | string | not null | `"igdb"`, `"libretro"`, `"steamgriddb"` |
| `status` | string | not null | `"matched"`, `"not_found"`, `"error"`, `"not_attempted"` |
| `source_id` | string | | Source-specific ID (e.g., IGDB game ID) |
| `last_attempt_at` | *time.Time | nullable | When the last attempt happened |
| `error_message` | string | | Error details for `"error"` status |

**Unique constraint:** `(game_id, source)` — one row per game per source.

**Status values:**

| Status | Meaning |
|--------|---------|
| `matched` | Source returned a match, data applied |
| `not_found` | Source searched, no match found |
| `error` | API/network error during attempt |
| `not_attempted` | Source exists but hasn't been tried for this game |

Games with `scrape_attempts = 0` have no rows at all — they are "unscraped."

**Existing fields kept as-is:**
- `scraper_id` — still stores the primary source (`"igdb:1234"`, `"libretro"`, etc.)
- `scrape_attempts` — still incremented on every attempt

**Cooldown:** 7 days. Games with `status = 'error'` or `'not_found'` where `last_attempt_at` is within 7 days are skipped during batch scrapes. The "eligible" count shown in the UI excludes these.

## Scraper Logic Changes

**ScrapeGame updates result rows after each source runs:**

```
ScrapeGame(game):
  1. Try IGDB → upsert game_scrape_results (game_id, "igdb", outcome)
  2. Try LibRetro art → upsert game_scrape_results (game_id, "libretro", outcome)
  3. Try SteamGridDB art → upsert game_scrape_results (game_id, "steamgriddb", outcome)
  4. Increment scrape_attempts, update scraper_id as before
```

Each source's result is recorded independently. A single ScrapeGame call produces up to 3 upserts.

**ScrapeAll extended with source+status filtering:**

The existing `mode` parameter still works for backward compatibility. New parameters:

| Param | Filter |
|---|---|
| `source=igdb&status=not_found` | IGDB not-found games, with cooldown |
| `source=igdb&status=error` | IGDB errors, with cooldown |
| `source=igdb&status=not_attempted` | Games never tried on IGDB |
| `mode=all` | Everything, ignoring status (existing behavior) |

When `source` + `status` is provided, only that source is retried for matching games (not the full pipeline).

## API

**New endpoint — scrape status counts:**

`GET /api/admin/scrape/status`

```json
{
  "sources": [
    {
      "source": "igdb",
      "matched": 12000,
      "not_found": 800,
      "not_found_eligible": 358,
      "error": 50,
      "error_eligible": 50,
      "not_attempted": 200
    },
    {
      "source": "libretro",
      "matched": 12580,
      "not_found": 220,
      "not_found_eligible": 220,
      "error": 0,
      "error_eligible": 0,
      "not_attempted": 250
    },
    {
      "source": "steamgriddb",
      "matched": 11200,
      "not_found": 1400,
      "not_found_eligible": 980,
      "error": 0,
      "error_eligible": 0,
      "not_attempted": 450
    }
  ]
}
```

"Eligible" = games in that status with `last_attempt_at` outside the 7-day cooldown (or null).

**Modified scrape trigger:**

`POST /api/admin/scrape` extended to accept:

```json
{ "source": "igdb", "status": "not_found" }
```

Existing `{ "mode": "new" }` / `"all"` / `"fallback"` still works.

**No changes to:**
- `POST /api/admin/games/:id/scrape` (single game — always scrapes all sources)
- `POST /api/admin/games/:id/igdb-match` (Fix Match)
- Game detail API responses

## Admin Dashboard UI

A **Library Scrape Status** card on the existing admin console page:

```
┌─ Library Scrape Status ─────────────────────────────┐
│                                                      │
│  IGDB                                                │
│  ● 12,000 matched                                    │
│  ● 800 not found (358 eligible)     [Retry now]      │
│  ● 50 errors (50 eligible)          [Retry now]      │
│  ● 200 not attempted                [Scrape now]     │
│                                                      │
│  LibRetro Thumbnails                                 │
│  ● 12,580 matched                                    │
│  ● 220 not found                                     │
│  ● 250 not attempted                [Scrape now]     │
│                                                      │
│  SteamGridDB                                         │
│  ● 11,200 matched                                    │
│  ● 1,400 not found                                   │
│  ● 450 not attempted                [Scrape now]     │
│                                                      │
└──────────────────────────────────────────────────────┘
```

Action buttons trigger scrape with the corresponding `source` + `status` filter.

## Migration

On startup, backfill `game_scrape_results` from existing data for games with `scrape_attempts > 0`:

| Existing state | IGDB row | LibRetro row | SteamGridDB row |
|---|---|---|---|
| `scraper_id LIKE 'igdb:%'` | `matched` | `matched` (if cover exists) | `matched` (if hero exists) |
| `scraper_id = 'libretro'` | `not_found` | `matched` | Check if hero exists |
| `scraper_id = ''`, `scrape_attempts > 0` | `not_found` | `not_found` | `not_attempted` |
| `scraper_id = ''`, `scrape_attempts = 0` | No rows | No rows | No rows |

For migrated rows, `last_attempt_at` is set to the game's `updated_at` timestamp.

## Testing

**Server (Go unit tests):**
- Migration backfills correctly from existing scraper_id values
- ScrapeGame creates/updates correct result rows per source
- Status counts endpoint returns accurate breakdown
- Cooldown: games within 7 days excluded from eligible count
- Source+status filter only processes matching games

**Web (Vitest):**
- Status card renders counts correctly
- Action buttons trigger correct API calls
- "Eligible" count shown only for not_found and error

**No player app changes.**

## Out of Scope

- Per-game scrape result display in the web admin game detail (can be added later)
- Player app scrape status visibility
- Configurable cooldown period (hardcoded 7 days)
- Per-source "Fix Match" (only IGDB has manual matching)
