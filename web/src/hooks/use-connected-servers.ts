import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type {
  CatalogAvailability,
  CatalogConsoleCount,
} from "@/generated/schemas";

// Per-console game counts across connected servers — powers the browse
// overview. Cheap: the server resolves no cover art for this endpoint.
export function useConnectedServerConsoles() {
  return useQuery({
    queryKey: ["federation", "catalog", "consoles"],
    queryFn: async (): Promise<CatalogConsoleCount[]> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/catalog/consoles", {
          params: { query: { remoteOnly: true } },
        }),
      );
      return data?.consoles ?? [];
    },
    staleTime: 30 * 1000,
  });
}

// Connected-server games for a single console. Covers are resolved server-side,
// bounded to this console (not the whole catalog).
export function useConnectedServerGames(console: string) {
  return useQuery({
    queryKey: ["federation", "catalog", "console", console],
    queryFn: async (): Promise<CatalogAvailability[]> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/catalog/available", {
          params: { query: { remoteOnly: true, console } },
        }),
      );
      return data?.games ?? [];
    },
    enabled: console.length > 0,
    staleTime: 30 * 1000,
  });
}
