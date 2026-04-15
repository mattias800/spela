# Auto-Fetch RetroAchievements on Game Detail Page

**Issue:** #410
**Date:** 2026-04-15

## Summary

When a user opens a game detail page, automatically fetch RetroAchievements
data if the game doesn't have cached achievements yet. Uses the server's RA
API key so achievements are visible to all users — even those who haven't
linked their personal RA account. This lets achievements serve as an incentive
for users to register their own credentials and start tracking progress.

## Approach

Extend the scrape queue with a new `ra_fetch` job type. The
`GET /games/{id}/achievements` endpoint enqueues an `ra_fetch` job when no
fresh cache exists, returns a `{ status: "pending" }` response, and the
frontend refetches until data is available. The queue worker handles hash
computation and RA API calls, avoiding HTTP timeout issues with large ROMs.

## Backend Changes

### 1. New queue item type: `ra_fetch`

Add a `Type string` field to `ScrapeQueueItem` with a default value of
`"scrape"`. Existing rows with an empty type are treated as `"scrape"` for
backward compatibility. The worker dispatches based on type:

```go
switch item.Type {
case "scrape":     w.scraper.ScrapeGame(&game)
case "ra_fetch":   w.scraper.FetchRAAchievements(&game)
}
```

`FetchRAAchievements` is a new function that:
1. Checks if `GameAchievementCache` is fresh — if so, no-op
2. Resolves `RAGameID` (cached on game record, or compute MD5 hash + RA lookup)
3. Fetches achievements via `GetGameExtended()` using the server's RA API key
4. Populates `GameAchievementCache`

This is idempotent: if the cache is already fresh, it's a no-op. If a full
`scrape` also populates RA data in the future (#413), there's no conflict —
both write to the same cache.

### 2. "No RA match" marker on Game model

Add `RAHashChecked bool` to the Game model to avoid re-hashing large ROMs
when RA doesn't recognize the game.

```
# IMPORTANT: RAGameID + RAHashChecked sentinel logic
#
# These two fields together determine whether we need to attempt
# an RA hash lookup for this game:
#
#   RAHashChecked=false, RAGameID=0  → Not yet looked up. Compute ROM
#                                      MD5 hash and query RA API.
#   RAHashChecked=true,  RAGameID=0  → Looked up, but RA doesn't have
#                                      this game. Do NOT retry.
#   RAHashChecked=true,  RAGameID>0  → Valid RA game ID cached.
#                                      (RAHashChecked is redundant here
#                                      but kept consistent.)
#
# RAHashChecked is ONLY set to true after a SUCCESSFUL lookup attempt
# (whether RA returned a game ID or not). If the lookup fails due to
# a transient error (network, 403, timeout), RAHashChecked stays false
# so the next visit retries.
```

### 3. Extend `GetGameAchievements` handler

The existing `GET /games/{id}/achievements` handler is extended:

1. Look up game, check console supports RA (existing)
2. Check `GameAchievementCache` — if fresh, return cached data (existing)
3. **New:** If no fresh cache AND server RA key is configured:
   - Check if an `ra_fetch` job is already pending/in-progress for this game
   - If not, enqueue one at priority 100
   - Return `{ status: "pending" }` with HTTP 202
4. If already queued, return `{ status: "pending" }` with HTTP 202
5. If server RA key not configured, return stale cache or empty response
6. If console doesn't support RA, return empty response immediately

### 4. Server RA API key access

The handler reads the server-level RA API key from `ServerSetting` (the
`ra_api_key` key), the same way the scraper does. If not configured, the
auto-fetch is skipped entirely.

## Frontend Changes

### 1. Update `useGameAchievements` hook

When the endpoint returns `{ status: "pending" }`, enable a short
`refetchInterval` (e.g., 2 seconds) on the TanStack Query. Once the
response contains actual achievement data, disable the interval.

This gives the user a natural loading → populated transition. The
achievements section shows a loading/skeleton state while pending, then
renders achievements when the data arrives.

### 2. No other frontend changes needed

The existing achievement display components, progress queries, and
leaderboard all work as-is once the cache is populated.

## Error Handling

### RA API errors (403, timeout, rate limit)

- Worker marks the queue item as failed with the error message
- Handler returns stale cache if available, or empty response
- No automatic retry — next page visit enqueues a new `ra_fetch` if cache
  is still empty

### Hash lookup failures

- **ROM file missing on disk:** Set `RAHashChecked=true`, `RAGameID=0`.
  Log warning. Don't retry.
- **RA doesn't recognize hash:** Set `RAHashChecked=true`, `RAGameID=0`.
  Game doesn't have RA support.
- **Transient API error during hash lookup:** Don't set `RAHashChecked`.
  Leave for retry on next visit.

### Server RA key not configured

Return empty response immediately. Achievements section doesn't render.

### Duplicate enqueue prevention

Before enqueuing, check if an `ra_fetch` item is already pending or
in-progress for this game. If so, return 202 "already queued." Same
pattern as `scrape-if-needed`.

### Console doesn't support RA

Early return with empty response. No enqueue, no hash computation.

## Testing

### Backend (Go unit tests)

- **Auto-enqueue flow:** No cache → handler enqueues `ra_fetch` → returns
  pending status
- **Duplicate prevention:** Second request while job is pending returns
  "already queued"
- **`RAHashChecked` sentinel:** After "not found" hash lookup, subsequent
  requests skip the hash step
- **`ra_fetch` worker path:** Worker receives `ra_fetch` item, computes
  hash, calls RA API, populates cache
- **Error cases:** Missing ROM, RA 403, no server key configured — all
  return gracefully without crashing

### Frontend (Vitest + Playwright)

- **Unit test (Vitest):** Mock endpoint returning `{ status: "pending" }`
  then cached data — verify the hook transitions from loading to populated
  and disables refetch interval
- **E2E test (Playwright):** On a seeded game, navigate to game detail page,
  verify achievements section appears

## Out of Scope

- Including RA in the per-game `ScrapeGame()` flow — tracked in #413
- Decomposing the `scrape` job type into sub-jobs (unwrapping)
- User-credential-based auto-fetch (existing handler behavior, unchanged)
