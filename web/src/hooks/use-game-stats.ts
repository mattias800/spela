import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useGameStats(gameId: string) {
  return useQuery({
    queryKey: ["game-stats", gameId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/stats", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}
