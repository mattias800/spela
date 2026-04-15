# Include RetroAchievements in Per-Game Scrape Queue Processing

**Issue:** #413
**Date:** 2026-04-15

## Summary

Call `FetchRAAchievements()` at the end of `ScrapeGame()` so every game gets
achievement data during normal scraping. Includes a circuit breaker to stop
RA requests if the API starts failing mid-scrape.

## Approach

Add a `FetchRAAchievements(game)` call at the end of `ScrapeGame()` and
`ScrapeGameWithIGDBMatch()`, guarded by `IsRAConfigured()` and a circuit
breaker. RA failures are logged but never fail the overall scrape.

## Changes

### 1. Call FetchRAAchievements in ScrapeGame()

After the game is saved to DB (after `s.DB.Save(game)`) but before the
return, add:

```
if s.IsRAConfigured() && !s.raCircuitOpen {
    if err := s.FetchRAAchievements(game); err != nil {
        s.recordRAFailure(game.Title, err)
    } else {
        s.raConsecutiveFailures = 0
    }
}
```

Same pattern in `ScrapeGameWithIGDBMatch()`.

### 2. Circuit breaker on Scraper struct

Add two fields to the `Scraper` struct:

- `raCircuitOpen bool` — when true, skip all RA calls
- `raConsecutiveFailures int` — count of consecutive RA failures

`recordRAFailure()` increments the counter. After 5 consecutive failures,
sets `raCircuitOpen = true` and logs:

```
slog.Warn("RA achievements disabled for remainder of scrape",
    "consecutiveFailures", 5, "lastError", err)
```

On success, `raConsecutiveFailures` resets to 0.

### 3. Natural reset

Both fields are non-persisted struct state. They reset to zero values on:
- Server restart
- Any new Scraper instance

This means the circuit breaker automatically re-closes on the next scrape
job — no admin action required.

### 4. Error handling

RA fetch errors are logged as warnings but never cause `ScrapeGame()` to
return an error. A game with good metadata but no achievements is still
a successful scrape.

## Testing

- `ScrapeGame()` populates achievement cache when RA is configured and
  the ROM hash is recognized by RA
- Circuit breaker trips after 5 consecutive failures and skips subsequent
  games
- A success after failures resets the counter
- `ScrapeGame()` still succeeds when RA fetch fails (error is swallowed)

## Out of Scope

- Admin UI notification for circuit breaker trips — tracked in #415
- Changes to the bulk `ScrapeRAAchievements()` function
- Frontend changes
