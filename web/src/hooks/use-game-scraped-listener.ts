import { useCallback } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  Game,
  GamesResponse,
  CollectionDetail,
  MostPlayedGame,
  MostPlayedGamesResponse,
} from "@/types/api";

function updateGameInArray(games: Game[], scraped: Game): Game[] {
  let changed = false;
  const result = games.map((g) => {
    if (g.id === scraped.id) {
      changed = true;
      return { ...g, ...scraped };
    }
    return g;
  });
  return changed ? result : games;
}

export function useGameScrapedListener() {
  const queryClient = useQueryClient();

  // Handle individual game scrape events (single scrape via admin).
  // The payload is a full Game response, so we can replace cached data directly.
  useWebSocketEvent("game_scraped", (payload: Game) => {
    const scraped = payload;
    if (!scraped?.id) return;

    // Invalidate single game query so it re-fetches with user-specific data
    queryClient.invalidateQueries({
      queryKey: ["game", scraped.id],
      exact: true,
    });

    // Optimistically update list queries with the scraped data (covers, etc.)
    queryClient.setQueriesData<GamesResponse>(
      { queryKey: ["games"] },
      (old) => {
        if (!old?.data) return old;
        const updated = updateGameInArray(old.data, scraped);
        return updated === old.data ? old : { ...old, data: updated };
      },
    );

    queryClient.setQueriesData<Game[]>(
      { queryKey: ["consoles"] },
      (old) => {
        if (!Array.isArray(old)) return old;
        return updateGameInArray(old, scraped);
      },
    );

    queryClient.setQueriesData<CollectionDetail>(
      { queryKey: ["collection"] },
      (old) => {
        if (!old?.games) return old;
        const updated = updateGameInArray(old.games, scraped);
        return updated === old.games ? old : { ...old, games: updated };
      },
    );

    queryClient.setQueriesData<MostPlayedGamesResponse>(
      { queryKey: ["most-played-games"] },
      (old) => {
        if (!old?.games) return old;
        let changed = false;
        const updated = old.games.map((entry: MostPlayedGame) => {
          if (entry.game.id === scraped.id) {
            changed = true;
            return { ...entry, game: { ...entry.game, ...scraped } };
          }
          return entry;
        });
        return changed ? { ...old, games: updated } : old;
      },
    );
  });

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
