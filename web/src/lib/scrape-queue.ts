import { api } from "@/lib/api-client";

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
      await api.post(`/games/${gameId}/scrape-if-needed`);
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
