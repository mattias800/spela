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
