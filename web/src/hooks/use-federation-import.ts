import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { CatalogAvailability, ImportJob } from "@/generated/schemas";

// A job is still doing work until it reaches a terminal status.
export function isImportActive(job: ImportJob): boolean {
  return job.status !== "completed" && job.status !== "failed";
}

// Fetch a single connected-server catalog entry by its cross-key. The remote-game
// page uses this on a deep link / refresh, when no row was handed over via
// navigation state. Resolves to undefined when no connected server offers the key.
export function useRemoteGame(key: string) {
  return useQuery({
    queryKey: ["federation", "catalog", "entry", key],
    queryFn: async (): Promise<CatalogAvailability | undefined> => {
      const data = await unwrap(
        typedApi.GET("/api/federation/catalog/available", {
          params: { query: { key, remoteOnly: true } },
        }),
      );
      return data?.games[0];
    },
    enabled: key.length > 0,
    staleTime: 30 * 1000,
  });
}

// Import job queue + per-job progress. Gated to import-capable users (the server
// returns 403 otherwise), so only enable it when the caller may import. Polls
// quickly while any job is in flight and slowly when the queue is idle.
export function useImports(enabled: boolean) {
  return useQuery({
    queryKey: ["federation", "imports"],
    queryFn: async (): Promise<ImportJob[]> => {
      const data = await unwrap(typedApi.GET("/api/federation/imports"));
      return data?.imports ?? [];
    },
    enabled,
    refetchInterval: (query) => {
      const jobs = query.state.data;
      return jobs?.some(isImportActive) ? 1500 : 10000;
    },
  });
}

// Start importing a connected-server game into the local library.
export function useStartImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (game: { key: string; title: string; console: string }) =>
      unwrap(
        typedApi.POST("/api/federation/import", {
          body: {
            key: game.key,
            title: game.title,
            console: game.console,
          },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["federation", "imports"] });
    },
  });
}
