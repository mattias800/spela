# Per-Game Scrape Status WebSocket Events

## Problem

After moving to the persistent scrape queue, the "Scrape Metadata" action
enqueues the game and returns immediately. The frontend has no way to know
when the game starts or finishes scraping, so the loading indicator is broken.

## Solution

A new WebSocket event `game_scrape_status` broadcasts per-game scraping state
changes. The worker emits `scraping` when it starts processing a game and
`idle` when it finishes (regardless of success or failure).

## Event Format

```json
{"type": "game_scrape_status", "payload": {"gameId": 123, "status": "scraping"}}
{"type": "game_scrape_status", "payload": {"gameId": 123, "status": "idle"}}
```

Two states only: `scraping` and `idle`. Success/failure is not part of the
status — the frontend refetches game data on `idle` to discover the outcome.

## Backend Changes

**`server/internal/scraper/worker.go`**

In `processItem`:
- Broadcast `game_scrape_status` with `status: "scraping"` at the start,
  after loading the game but before calling `ScrapeGame` or
  `propagateGroupMetadata`.
- Broadcast `game_scrape_status` with `status: "idle"` at the end, after
  `MarkCompleted` or `MarkFailed`.

Remove the existing `game_scraped` event broadcast (the `if item.Priority >= 100`
block in `broadcastProgress`). The `game_scrape_status` event replaces it.

The `scrape_progress` event (for bulk job progress tracking) is unchanged.

## Frontend Changes

**New hook: `web/src/hooks/use-game-scrape-status.ts`**

```ts
function useGameScrapeStatus(gameId: string): { isScraping: boolean }
```

Listens for `game_scrape_status` WebSocket events matching the given game ID.
Returns `isScraping: true` when status is `"scraping"`, `false` on `"idle"`.

**Modify: `web/src/hooks/use-game-scraped-listener.ts`**

- Replace the `game_scraped` event handler with a `game_scrape_status` handler.
- On `status: "idle"`: invalidate the game query (`["game", gameId]`) and list
  queries (`["games"]`, `["consoles"]`, etc.) so cached data is refetched.
- Remove the optimistic update logic that patched full game objects into the
  query cache — a simple `invalidateQueries` is sufficient.

**Modify: `web/src/features/game-detail/components/game-hero.tsx`**

- Use `useGameScrapeStatus(game.id)` to determine whether to show the
  scraping spinner next to the action buttons.
- Remove dependency on the old `isScraping` prop/state if it exists.

**Modify: `web/src/pages/game-detail-page.tsx`**

- Wire the new hook if the scraping state is managed at page level.

**Remove: `game_scraped` event type**

- Backend: remove from `worker.go` (`broadcastProgress`)
- Frontend: remove handler from `use-game-scraped-listener.ts`

## Volume

2 events per game during bulk scrape. At ~1 game per 2 seconds, that is
1 event/second — negligible overhead.

## Out of Scope

- Enriching `game_scrape_status` with metadata (use a separate
  `game_metadata_updated` event in the future if needed).
- Showing scrape status on game cards in carousels/grids (only game detail
  page for now).
