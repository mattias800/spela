import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

// Searches the federated catalog (games on connected servers) by title.
// remoteOnly=true so results don't duplicate games already in the local
// library — those already show in the main search results.
export function useFederatedGameSearch(query: string) {
  return useQuery({
    queryKey: ["federation", "catalog", "search", query],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/federation/catalog/available", {
          params: { query: { q: query, remoteOnly: true } },
        }),
      ),
    enabled: query.length >= 2,
    staleTime: 30 * 1000,
  });
}
