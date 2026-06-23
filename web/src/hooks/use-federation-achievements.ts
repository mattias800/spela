import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { AchievementEntry } from "@/generated/schemas";

// Federated "top achievers" leaderboard — achievement-unlock counts per player
// across this server + connected servers, sorted by count desc. Entries carry
// `hops` (0 = this server, >= 1 = a connected server) so the UI can filter
// scope client-side from the single aggregate.
export function useFederationAchievements() {
  return useQuery({
    queryKey: ["federation", "achievements", "aggregated"],
    queryFn: async (): Promise<AchievementEntry[]> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/achievements/aggregated"),
      );
      return data?.achievements ?? [];
    },
    staleTime: 30 * 1000,
  });
}
