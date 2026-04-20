import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { User } from "@/types/api";

export function useAdminUsers() {
  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/admin/users"));
      return data as User[] | undefined;
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

export interface ScrapeStartResponse {
  message: string;
  total: number;
}

export type ScrapeMode = "new" | "all" | "fallback" | "ra";

export function useScrapeMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
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
      const data = await unwrap(
        typedApi.POST("/api/admin/scrape", { params: { query } }),
      );
      return data as ScrapeStartResponse | undefined;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "scrape-counts"] });
    },
  });
}

export interface ScrapeSourceCounts {
  source: string;
  matched: number;
  notFound: number;
  notFoundEligible: number;
  error: number;
  errorEligible: number;
  notAttempted: number;
}

export interface ScrapeStatusCountsResponse {
  sources: ScrapeSourceCounts[];
}

export function useScrapeStatusCounts() {
  return useQuery({
    queryKey: ["admin", "scrape-counts"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/admin/scrape/counts"));
      return data as ScrapeStatusCountsResponse | undefined;
    },
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

export interface ScanStatus {
  active: boolean;
  phase?: string;
  current?: number;
  total?: number;
  message?: string;
}

export function useScanStatus() {
  return useQuery({
    queryKey: ["admin", "scan-status"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/admin/games/scan/status"),
      );
      return data as ScanStatus | undefined;
    },
    refetchInterval: 3000,
  });
}

export interface CoverOption {
  source: "libretro" | "igdb" | "custom" | "libretro-regional";
  url: string;
  label?: string;
  libretroName?: string;
}

export interface GameCoversResponse {
  active: string;
  covers: CoverOption[];
}

export function useGameCovers(gameId: string) {
  return useQuery({
    queryKey: ["admin", "game-covers", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/admin/games/{id}/covers", {
          params: { path: { id: gameId } },
        }),
      );
      return data as GameCoversResponse | undefined;
    },
    enabled: !!gameId,
  });
}

export function useSetGameCover() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      source,
      libretroName,
    }: {
      gameId: string;
      source: CoverOption["source"];
      libretroName?: string;
    }) => {
      await unwrap(
        typedApi.PUT("/api/admin/games/{id}/covers", {
          params: { path: { id: gameId } },
          body: { source, libretroName },
        }),
      );
    },
    onSuccess: (_data, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({
        queryKey: ["admin", "game-covers", gameId],
      });
    },
  });
}

export interface HeroOption {
  url: string;
  thumb: string;
  id: number;
}

export interface GameHeroesResponse {
  activeUrl: string;
  heroes: HeroOption[];
}

export function useGameHeroes(gameId: string) {
  return useQuery({
    queryKey: ["admin", "game-heroes", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/admin/games/{id}/heroes", {
          params: { path: { id: gameId } },
        }),
      );
      return data as GameHeroesResponse | undefined;
    },
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
