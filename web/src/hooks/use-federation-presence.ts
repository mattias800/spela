import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { PresenceEntry } from "@/generated/schemas";

// Live cross-mesh presence ("who's playing now" across connected servers).
// Polled like the local online-users widget; pauses while the tab is
// backgrounded so we don't keep firing the federation pulls unseen.
export function useFederationPresence() {
  return useQuery({
    queryKey: ["federation", "presence", "aggregated"],
    queryFn: async (): Promise<PresenceEntry[]> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/presence/aggregated"),
      );
      return data?.presence ?? [];
    },
    refetchInterval: 30000,
    refetchIntervalInBackground: false,
  });
}
