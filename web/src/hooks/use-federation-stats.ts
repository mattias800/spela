import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { AggregatedStat } from "@/generated/schemas";

export type StatMetric = "game_play" | "player_play";

// Federated (mesh) aggregate stats: most-played games (game_play) or most-active
// players (player_play) summed across this server + connected servers. Pass
// `enabled` so a section only fetches when the viewer toggles to the mesh scope.
export function useFederationAggregatedStats(
  metric: StatMetric,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: ["federation", "stats", "aggregated", metric],
    queryFn: async (): Promise<AggregatedStat[]> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/stats/aggregated", {
          params: { query: { metric } },
        }),
      );
      return data?.stats ?? [];
    },
    enabled: options?.enabled ?? true,
    staleTime: 30 * 1000,
  });
}
