import { typedApi, unwrap } from "@/lib/api-client";
import { queryClient } from "@/lib/query-client";

const THROTTLE_MS = 300;

const requested = new Set<string>();
const queue: string[] = [];
let processing = false;

async function processQueue() {
  if (processing) return;
  processing = true;

  while (queue.length > 0) {
    const gameId = queue.shift()!;
    try {
      await unwrap(
        typedApi.POST("/api/games/{id}/scrape-if-needed", {
          params: { path: { id: gameId } },
        }),
      );
      queryClient.invalidateQueries({ queryKey: ["games"] });
    } catch {
      // Server increments scrapeAttempts regardless — silently continue
    }
    if (queue.length > 0) {
      await new Promise((r) => setTimeout(r, THROTTLE_MS));
    }
  }

  processing = false;
}

export function enqueueScrape(gameId: string) {
  if (requested.has(gameId)) return;
  requested.add(gameId);
  queue.push(gameId);
  processQueue();
}

/** Reset state — only for tests */
export function _resetScrapeQueue() {
  requested.clear();
  queue.length = 0;
  processing = false;
}
