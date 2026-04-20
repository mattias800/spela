import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { UserResponse } from "@/generated/schemas";
import type { User } from "@/types/api";
import { asUserRole } from "@/types/view-model-narrowing";

// Map the wire User (role: string) to the view-model User (role: UserRole).
// Shared between use-admin.ts and use-auth.tsx.
export function toUser(wire: UserResponse): User {
  return {
    ...wire,
    role: asUserRole(wire.role),
  };
}

export function useAdminUsers() {
  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/admin/users"));
      return data?.map(toUser);
    },
  });
}

export function useUpdateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      id,
      data,
    }: {
      id: string;
      data: {
        role?: string;
        email?: string;
        password?: string;
        disabled?: boolean;
        pendingApproval?: boolean;
      };
    }) => {
      await unwrap(
        typedApi.PUT("/api/admin/users/{id}", {
          params: { path: { id } },
          body: data as Record<string, unknown>,
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useCreateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: {
      username: string;
      email: string;
      password: string;
      role: string;
    }) => {
      await unwrap(
        typedApi.POST("/api/admin/users", { body: data }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useServerSettings() {
  return useQuery({
    queryKey: ["admin", "settings"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/settings")),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (settings: Record<string, string>) => {
      await unwrap(
        typedApi.PUT("/api/admin/settings", {
          body: settings,
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "settings"] });
    },
  });
}

export function useScanLibrary() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (opts?: { console?: string }) =>
      unwrap(
        typedApi.POST("/api/admin/games/scan", {
          params: {
            query: opts?.console ? { console: opts.console } : undefined,
          },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["consoles"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "stats"] });
    },
  });
}

export type ScrapeMode = "new" | "all" | "fallback" | "ra";

export function useScrapeMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      mode = "new",
      console,
      source,
      status,
    }: {
      mode?: ScrapeMode;
      console?: string;
      source?: string;
      status?: string;
    }) => {
      const query: Record<string, string | undefined> = {};
      if (source && status) {
        query.source = source;
        query.status = status;
      } else if (mode !== "new") {
        query.mode = mode;
      }
      if (console) query.console = console;
      return unwrap(typedApi.POST("/api/admin/scrape", { params: { query } }));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "scrape-counts"] });
    },
  });
}

export function useScrapeStatusCounts() {
  return useQuery({
    queryKey: ["admin", "scrape-counts"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/scrape/counts")),
  });
}

export function useCancelScrape() {
  return useMutation({
    mutationFn: () => unwrap(typedApi.DELETE("/api/admin/scrape")),
  });
}

export function useMetadataMatches() {
  return useQuery({
    queryKey: ["admin", "metadata-matches"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/metadata-matches")),
  });
}

export function useDeletedUsers() {
  return useQuery({
    queryKey: ["admin", "users", "deleted"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/users/deleted")),
  });
}

export function useHardDeleteUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await unwrap(
        typedApi.DELETE("/api/admin/users/{id}/permanent", {
          params: { path: { id } },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useDeleteUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await unwrap(
        typedApi.DELETE("/api/admin/users/{id}", {
          params: { path: { id } },
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useScrapeGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (gameId: string) => {
      await unwrap(
        typedApi.POST("/api/admin/games/{id}/scrape", {
          params: { path: { id: gameId } },
        }),
      );
    },
    onSuccess: (_data, gameId) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "metadata-matches"] });
    },
  });
}

export function useRefreshAchievements() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (gameId: string) => {
      await unwrap(
        typedApi.POST("/api/admin/games/{id}/achievements/refresh", {
          params: { path: { id: gameId } },
        }),
      );
    },
    onSuccess: (_data, gameId) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["achievements", gameId] });
    },
  });
}

export function useAdminStats() {
  return useQuery({
    queryKey: ["admin", "stats"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/stats")),
  });
}

export function useTestIgdbCredentials() {
  return useMutation({
    mutationFn: (data: { clientId: string; clientSecret: string }) =>
      unwrap(typedApi.POST("/api/admin/igdb/test", { body: data })),
  });
}

export function useScrapeStatus() {
  return useQuery({
    queryKey: ["admin", "scrape-status"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/scrape/status")),
    refetchInterval: 3000, // Poll every 3s to catch status changes
  });
}

export function useScanStatus() {
  return useQuery({
    queryKey: ["admin", "scan-status"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/games/scan/status")),
    refetchInterval: 3000,
  });
}

export function useGameCovers(gameId: string) {
  return useQuery({
    queryKey: ["admin", "game-covers", gameId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/games/{id}/covers", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useSetGameCover() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      gameId,
      source,
      libretroName,
    }: {
      gameId: string;
      source: string;
      libretroName?: string;
    }) =>
      unwrap(
        typedApi.PUT("/api/admin/games/{id}/covers", {
          params: { path: { id: gameId } },
          body: { source, libretroName },
        }),
      ),
    onSuccess: (_data, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({
        queryKey: ["admin", "game-covers", gameId],
      });
    },
  });
}

export function useGameHeroes(gameId: string) {
  return useQuery({
    queryKey: ["admin", "game-heroes", gameId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/games/{id}/heroes", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useSetGameHero() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ gameId, url }: { gameId: string; url: string }) => {
      await unwrap(
        typedApi.PUT("/api/admin/games/{id}/heroes", {
          params: { path: { id: gameId } },
          body: { url },
        }),
      );
    },
    onSuccess: (_data, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({
        queryKey: ["admin", "game-heroes", gameId],
      });
    },
  });
}

export function useIgdbStatus() {
  return useQuery({
    queryKey: ["admin", "igdb-status"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/igdb/status")),
  });
}

export function useSteamGridDBStatus() {
  return useQuery({
    queryKey: ["admin", "steamgriddb-status"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/steamgriddb/status")),
  });
}

export function useRAStatus() {
  return useQuery({
    queryKey: ["admin", "ra-status"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/ra/status")),
  });
}

export function useUpdateGameMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      metadata,
    }: {
      gameId: string;
      metadata: Record<string, unknown>;
    }) => {
      await unwrap(
        typedApi.POST("/api/admin/games/{id}/metadata", {
          params: { path: { id: gameId } },
          body: metadata,
        }),
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
      queryClient.invalidateQueries({
        queryKey: ["admin", "metadata-matches"],
      });
    },
  });
}

export function useIgdbSearch(gameId: string, query: string) {
  return useQuery({
    queryKey: ["admin", "igdb-search", gameId, query],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/games/{id}/igdb-search", {
          params: { path: { id: gameId }, query: { q: query } },
        }),
      ),
    enabled: query.length >= 2,
  });
}

export function useApplyIgdbMatch() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      igdbId,
    }: {
      gameId: string;
      igdbId: number;
    }) => {
      await unwrap(
        typedApi.POST("/api/admin/games/{id}/igdb-match", {
          params: { path: { id: gameId } },
          body: { igdbId },
        }),
      );
    },
    onSuccess: (_data, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({
        queryKey: ["admin", "metadata-matches"],
      });
    },
  });
}

export function useUserRateLimit(userId: string) {
  return useQuery({
    queryKey: ["admin", "users", userId, "rate-limit"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/admin/users/{id}/rate-limit", {
          params: { path: { id: userId } },
        }),
      ),
    enabled: !!userId,
  });
}

export function useResetRateLimit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (userId: string) => {
      await unwrap(
        typedApi.DELETE("/api/admin/users/{id}/rate-limit", {
          params: { path: { id: userId } },
        }),
      );
    },
    onSuccess: (_data, userId) => {
      queryClient.invalidateQueries({
        queryKey: ["admin", "users", userId, "rate-limit"],
      });
    },
  });
}

export function useCoreCompatibility() {
  return useQuery({
    queryKey: ["admin", "core-compatibility"],
    queryFn: () => unwrap(typedApi.GET("/api/admin/core-compatibility")),
  });
}
