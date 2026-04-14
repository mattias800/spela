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
