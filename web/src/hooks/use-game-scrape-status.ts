import { useState } from "react";
import { useWebSocketEvent } from "@/hooks/use-websocket";

export type GameScrapeStatus = "idle" | "queued" | "scraping";

interface GameScrapeStatusEvent {
  gameId: number;
  status: GameScrapeStatus;
}

/**
 * Listens for game_scrape_status WebSocket events for a specific game.
 * Returns the current scrape state — "idle", "queued" (item in the
 * scrape queue but worker hasn't started), or "scraping" (worker
 * actively processing). The boolean `isScraping` is kept for
 * backward compatibility with callers that only care about the
 * actively-running case.
 */
export function useGameScrapeStatus(gameId: string) {
  const [scrapeStatus, setScrapeStatus] = useState<GameScrapeStatus>("idle");

  useWebSocketEvent("game_scrape_status", (payload: GameScrapeStatusEvent) => {
    if (String(payload.gameId) === gameId) {
      setScrapeStatus(payload.status);
    }
  });

  return { scrapeStatus, isScraping: scrapeStatus === "scraping" };
}
