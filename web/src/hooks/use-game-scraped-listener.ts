import { useQueryClient } from "@tanstack/react-query";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  Game,
  GamesResponse,
  CollectionDetail,
  MostPlayedGame,
  MostPlayedGamesResponse,
} from "@/types/api";

/** Fields that come from scraping — everything else is user-specific and must be preserved */
const SCRAPE_FIELDS = [
  "coverUrl",
  "description",
  "developer",
  "publisher",
  "releaseDate",
  "genre",
  "players",
  "rating",
  "scraperId",
  "scrapeAttempts",
  "screenshotUrls",
  "updatedAt",
] as const;

function mergeScrapedData(cached: Game, scraped: Game): Game {
  const merged = { ...cached };
  for (const key of SCRAPE_FIELDS) {
    if (key in scraped) {
      (merged as Record<string, unknown>)[key] = scraped[key];
    }
  }
  return merged;
}

function updateGameInArray(games: Game[], scraped: Game): Game[] {
  let changed = false;
  const result = games.map((g) => {
    if (g.id === scraped.id) {
      changed = true;
      return mergeScrapedData(g, scraped);
    }
    return g;
  });
  return changed ? result : games;
}

export function useGameScrapedListener() {
  const queryClient = useQueryClient();

  useWebSocketEvent("game_scraped", (payload: Game) => {
    const scraped = payload;
    if (!scraped?.id) return;

    // Update single game query: ["game", id]
    queryClient.setQueriesData<Game>(
      { queryKey: ["game", scraped.id] },
      (old) => (old ? mergeScrapedData(old, scraped) : old),
    );

    // Update paginated games: ["games", ...] → GamesResponse
    queryClient.setQueriesData<GamesResponse>(
      { queryKey: ["games"] },
      (old) => {
        if (!old?.data) return old;
        const updated = updateGameInArray(old.data, scraped);
        return updated === old.data ? old : { ...old, data: updated };
      },
    );

    // Update console games: ["consoles", consoleId, "games"] → Game[]
    queryClient.setQueriesData<Game[]>(
      { queryKey: ["consoles"] },
      (old) => {
        if (!Array.isArray(old)) return old;
        return updateGameInArray(old, scraped);
      },
    );

    // Update collection details: ["collection", id] → CollectionDetail
    queryClient.setQueriesData<CollectionDetail>(
      { queryKey: ["collection"] },
      (old) => {
        if (!old?.games) return old;
        const updated = updateGameInArray(old.games, scraped);
        return updated === old.games ? old : { ...old, games: updated };
      },
    );

    // Update most-played games: ["most-played-games"] → MostPlayedGamesResponse
    queryClient.setQueriesData<MostPlayedGamesResponse>(
      { queryKey: ["most-played-games"] },
      (old) => {
        if (!old?.games) return old;
        let changed = false;
        const updated = old.games.map((entry: MostPlayedGame) => {
          if (entry.game.id === scraped.id) {
            changed = true;
            return { ...entry, game: mergeScrapedData(entry.game, scraped) };
          }
          return entry;
        });
        return changed ? { ...old, games: updated } : old;
      },
    );
  });
}
