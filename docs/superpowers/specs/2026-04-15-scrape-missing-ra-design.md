# Scrape Missing RetroAchievements from Library Scan Page

**Issue:** #411
**Date:** 2026-04-15

## Summary

Add a "Scrape Missing Achievements" button on the Library Scan page that
finds games without cached RA data and enqueues them as `ra_fetch` jobs.

## Backend

### Add `mode=ra` to TriggerScrape

In `admin_handler_scraper.go`, when `mode == "ra"`:

1. Find eligible games on playable consoles where:
   - `RAHashChecked = false` (never checked against RA), OR
   - `RAGameID > 0` but no fresh `GameAchievementCache` entry
2. Apply optional `ConsoleFilter` if `?console=` is provided
3. Create a `ScrapeJob` with `Mode: "ra"`
4. Enqueue all as `ra_fetch` type items via `EnqueueGameWithType`

Games with `RAHashChecked=true, RAGameID=0` are excluded — these are
known non-matches that RA doesn't recognize.

### No other backend changes

- Worker already dispatches `ra_fetch` items to `FetchRAAchievements()`
- Circuit breaker from #413 protects against RA API failures
- WebSocket progress events are generic and work for any job type

## Frontend

### Add button in ScrapeCard

Add a "Scrape Missing Achievements" button in the scrape section of the
Library Scan page, alongside the existing scrape buttons. Same pattern:

```
scrapeMetadata.mutate({ mode: "ra", console: consoleParam })
```

Disabled while a job is active, same as the other buttons. Always shown
regardless of RA configuration — if RA isn't configured, the backend
simply returns 0 eligible games and the job completes immediately.

### No progress UI changes

The existing progress bar and WebSocket event handling work unchanged.

## Testing

- Backend: `mode=ra` returns correct game count and creates `ra_fetch`
  queue items
- Backend: `mode=ra` excludes games with `RAHashChecked=true, RAGameID=0`
- Frontend: no new tests needed — existing progress UI is generic

## Out of Scope

- Retry logic for previously failed RA fetches
- Per-source status breakdown for RA on the scan page
- Admin notification when RA circuit breaker trips (#415)
