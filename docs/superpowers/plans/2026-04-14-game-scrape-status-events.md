# Per-Game Scrape Status Events Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Broadcast per-game scrape status (`scraping` / `idle`) via WebSocket so the frontend can show a loading indicator on the game detail page whenever a game is being scraped — whether triggered manually or by a bulk job.

**Architecture:** The worker broadcasts `game_scrape_status` events at the start and end of processing each queue item. A new frontend hook `useGameScrapeStatus` listens for events matching a game ID. The existing `game_scraped` event is removed — the frontend refetches game data on `idle` instead.

**Tech Stack:** Go (WebSocket hub), React (TanStack Query, custom hooks)

**Spec:** `docs/superpowers/specs/2026-04-14-game-scrape-status-events-design.md`

---

### Task 1: Add `game_scrape_status` broadcasts to worker

**Files:**
- Modify: `server/internal/scraper/worker.go`

- [ ] **Step 1: Add `broadcastScrapeStatus` helper method**

Add this method to `ScrapeWorker` in `worker.go`, after the `broadcastProgress` method:

```go
func (w *ScrapeWorker) broadcastScrapeStatus(gameID uint, status string) {
	if w.hub == nil {
		return
	}
	w.hub.Broadcast(ws.Event{
		Type: "game_scrape_status",
		Payload: map[string]interface{}{
			"gameId": gameID,
			"status": status,
		},
	})
}
```

- [ ] **Step 2: Broadcast `scraping` at start of `processItem`**

In `processItem`, add the broadcast right after loading the game (after the `Preload("Console").First(&game, ...)` block, before the variant group propagation):

```go
	// Broadcast scraping status
	w.broadcastScrapeStatus(game.ID, "scraping")
```

- [ ] **Step 3: Broadcast `idle` at every exit point of `processItem`**

There are three exit points in `processItem` where processing ends:

1. **Game not found** (line ~87): after `MarkFailed`, add:
   ```go
   w.broadcastScrapeStatus(item.GameID, "idle")
   ```

2. **Scrape failed** (line ~108): after `broadcastProgress`, add:
   ```go
   w.broadcastScrapeStatus(game.ID, "idle")
   ```

3. **Success** (line ~125): after `broadcastProgress` at the end of the method, add:
   ```go
   w.broadcastScrapeStatus(game.ID, "idle")
   ```

- [ ] **Step 4: Remove the `game_scraped` broadcast**

Delete the block at the end of `broadcastProgress` that broadcasts `game_scraped` for high-priority items:

```go
	// For high-priority items (manual scrapes), broadcast game_scraped
	// so the frontend can update the game detail page.
	if item.Priority >= 100 {
		w.db.Preload("Console").Preload("Screenshots").First(game, game.ID)
		w.hub.Broadcast(ws.Event{
			Type:    "game_scraped",
			Payload: game,
		})
	}
```

- [ ] **Step 5: Verify build**

Run: `cd server && go build ./...`
Expected: Build succeeds.

- [ ] **Step 6: Commit**

```bash
cd server && git add internal/scraper/worker.go
git commit -m "feat: broadcast game_scrape_status events from worker"
```

---

### Task 2: Create `useGameScrapeStatus` hook

**Files:**
- Create: `web/src/hooks/use-game-scrape-status.ts`
- Create: `web/src/hooks/__tests__/use-game-scrape-status.test.ts`

- [ ] **Step 1: Write the test**

Create `web/src/hooks/__tests__/use-game-scrape-status.test.ts`:

```ts
import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useGameScrapeStatus } from "../use-game-scrape-status";

// Mock useWebSocketEvent to capture the callback
const mockCallbacks = new Map<string, (payload: unknown) => void>();
vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: (type: string, callback: (payload: unknown) => void) => {
    mockCallbacks.set(type, callback);
  },
}));

describe("useGameScrapeStatus", () => {
  beforeEach(() => {
    mockCallbacks.clear();
  });

  it("returns isScraping false by default", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    expect(result.current.isScraping).toBe(false);
  });

  it("returns isScraping true when scraping event received for matching game", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 42, status: "scraping" }));

    expect(result.current.isScraping).toBe(true);
  });

  it("returns isScraping false when idle event received", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 42, status: "scraping" }));
    expect(result.current.isScraping).toBe(true);

    act(() => callback({ gameId: 42, status: "idle" }));
    expect(result.current.isScraping).toBe(false);
  });

  it("ignores events for other game IDs", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 99, status: "scraping" }));

    expect(result.current.isScraping).toBe(false);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && npx vitest run src/hooks/__tests__/use-game-scrape-status.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement the hook**

Create `web/src/hooks/use-game-scrape-status.ts`:

```ts
import { useState } from "react";
import { useWebSocketEvent } from "@/hooks/use-websocket";

interface GameScrapeStatusEvent {
  gameId: number;
  status: "scraping" | "idle";
}

/**
 * Listens for game_scrape_status WebSocket events for a specific game.
 * Returns whether the game is currently being scraped.
 */
export function useGameScrapeStatus(gameId: string) {
  const [isScraping, setIsScraping] = useState(false);

  useWebSocketEvent("game_scrape_status", (payload: GameScrapeStatusEvent) => {
    if (String(payload.gameId) === gameId) {
      setIsScraping(payload.status === "scraping");
    }
  });

  return { isScraping };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && npx vitest run src/hooks/__tests__/use-game-scrape-status.test.ts`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add web/src/hooks/use-game-scrape-status.ts web/src/hooks/__tests__/use-game-scrape-status.test.ts
git commit -m "feat: add useGameScrapeStatus hook"
```

---

### Task 3: Wire hook into game detail page

**Files:**
- Modify: `web/src/pages/game-detail-page.tsx`

- [ ] **Step 1: Replace `scrapeGame.isPending` with `useGameScrapeStatus`**

In `game-detail-page.tsx`:

Add import at top:
```ts
import { useGameScrapeStatus } from "@/hooks/use-game-scrape-status";
```

Find the line (around 130):
```ts
const scrapeGame = useScrapeGame();
```

Add after it:
```ts
const { isScraping } = useGameScrapeStatus(id!);
```

Find the line (around 216):
```ts
isScraping={scrapeGame.isPending}
```

Replace with:
```ts
isScraping={isScraping}
```

The `scrapeGame` mutation stays — it's still used for `onScrape={() => scrapeGame.mutate(game.id)}`. It just no longer drives the loading state.

- [ ] **Step 2: Verify build**

Run: `cd web && npx tsc -b`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/game-detail-page.tsx
git commit -m "feat: use WebSocket-driven scrape status on game detail page"
```

---

### Task 4: Update `useGameScrapedListener` to use new event

**Files:**
- Modify: `web/src/hooks/use-game-scraped-listener.ts`
- Modify: `web/src/hooks/__tests__/use-game-scraped-listener.test.ts`

- [ ] **Step 1: Replace `game_scraped` handler with `game_scrape_status` handler**

Rewrite `web/src/hooks/use-game-scraped-listener.ts`:

```ts
import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useWebSocketEvent } from "@/hooks/use-websocket";

interface GameScrapeStatusEvent {
  gameId: number;
  status: "scraping" | "idle";
}

export function useGameScrapedListener() {
  const queryClient = useQueryClient();

  // When a game finishes scraping (status: "idle"), invalidate cached data
  // so the UI refetches with updated metadata/covers.
  const handleScrapeStatus = useCallback(
    (payload: GameScrapeStatusEvent) => {
      if (payload.status !== "idle" || !payload.gameId) return;

      const gameId = String(payload.gameId);

      // Invalidate single game query
      queryClient.invalidateQueries({
        queryKey: ["game", gameId],
        exact: true,
      });

      // Invalidate list queries so covers update in grids/carousels
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["consoles"] });
    },
    [queryClient],
  );

  useWebSocketEvent("game_scrape_status", handleScrapeStatus);

  // Handle batch scrape progress — invalidate the game query for each game
  // as it's scraped so the detail page refreshes automatically.
  const handleScrapeProgress = useCallback(
    (payload: { gameId?: number }) => {
      if (!payload?.gameId) return;
      const gameId = String(payload.gameId);
      queryClient.invalidateQueries({
        queryKey: ["game", gameId],
        exact: true,
      });
    },
    [queryClient],
  );

  useWebSocketEvent("scrape_progress", handleScrapeProgress);
}
```

- [ ] **Step 2: Update tests**

Read the existing test file `web/src/hooks/__tests__/use-game-scraped-listener.test.ts` and update it to test the new `game_scrape_status` event instead of `game_scraped`. The tests should verify that `invalidateQueries` is called when a `game_scrape_status` event with `status: "idle"` is received, and NOT called for `status: "scraping"`.

- [ ] **Step 3: Run tests**

Run: `cd web && npx vitest run src/hooks/__tests__/use-game-scraped-listener.test.ts`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add web/src/hooks/use-game-scraped-listener.ts web/src/hooks/__tests__/use-game-scraped-listener.test.ts
git commit -m "refactor: replace game_scraped with game_scrape_status listener"
```

---

### Task 5: Final verification

**Files:** None (testing only)

- [ ] **Step 1: Full backend build**

Run: `cd server && go build ./...`
Expected: Clean build.

- [ ] **Step 2: Full frontend type check**

Run: `cd web && npx tsc -b`
Expected: Clean.

- [ ] **Step 3: Run all frontend tests**

Run: `cd web && npx vitest run`
Expected: All tests pass. If any tests reference `game_scraped` events, update them to use `game_scrape_status`.

- [ ] **Step 4: Run backend tests**

Run: `cd server && go test ./internal/scraper/ -v -count=1`
Expected: All tests pass.

- [ ] **Step 5: Verify no remaining references to `game_scraped`**

Run: `grep -rn "game_scraped" web/src/ server/internal/ --include="*.ts" --include="*.tsx" --include="*.go" | grep -v _test | grep -v __tests__`
Expected: No matches (all references removed from production code).
