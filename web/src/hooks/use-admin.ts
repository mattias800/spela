import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  User,
  DeletedUser,
  ServerSettingsMap,
  MetadataMatchesResponse,
  IgdbSearchResult,
  RateLimitStatus,
  CoreCompatibilityResponse,
} from "@/types/api";

export function useAdminUsers() {
  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => api.get<User[]>("/admin/users"),
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
      await api.put(`/admin/users/${id}`, data);
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
      await api.post("/admin/users", data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
    },
  });
}

export function useServerSettings() {
  return useQuery({
    queryKey: ["admin", "settings"],
    queryFn: () => api.get<ServerSettingsMap>("/admin/settings"),
  });
}

export function useUpdateSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (settings: Record<string, string>) => {
      await api.put("/admin/settings", settings);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin", "settings"] });
    },
  });
}

export function useScanLibrary() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (opts?: { console?: string }) => {
      const path = opts?.console
        ? (`/admin/games/scan?console=${encodeURIComponent(opts.console)}` as const)
        : "/admin/games/scan";
      return api.post<Record<string, unknown>>(path);
    },
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

export type ScrapeMode = "new" | "all" | "fallback";

export function useScrapeMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      mode = "new",
      console,
    }: {
      mode?: ScrapeMode;
      console?: string;
    }) => {
      const params = new URLSearchParams();
      if (mode !== "new") params.set("mode", mode);
      if (console) params.set("console", console);
      const qs = params.toString();
      return api.post<ScrapeStartResponse>(
        qs ? `/admin/scrape?${qs}` : "/admin/scrape",
      );
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}

export function useCancelScrape() {
  return useMutation({
    mutationFn: () => api.delete("/admin/scrape"),
  });
}

export function useMetadataMatches() {
  return useQuery({
    queryKey: ["admin", "metadata-matches"],
    queryFn: () => api.get<MetadataMatchesResponse>("/admin/metadata-matches"),
  });
}

export function useDeletedUsers() {
  return useQuery({
    queryKey: ["admin", "users", "deleted"],
    queryFn: () => api.get<DeletedUser[]>("/admin/users/deleted"),
  });
}

export function useHardDeleteUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/admin/users/${id}/permanent`);
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
      await api.delete(`/admin/users/${id}`);
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
      await api.post(`/admin/games/${gameId}/scrape`);
    },
    onSuccess: (_data, gameId) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "metadata-matches"] });
    },
  });
}

export function useAdminStats() {
  return useQuery({
    queryKey: ["admin", "stats"],
    queryFn: () =>
      api.get<{
        users: number;
        games: number;
        consoles: number;
        saves: number;
      }>("/admin/stats"),
  });
}

export function useTestIgdbCredentials() {
  return useMutation({
    mutationFn: async (data: { clientId: string; clientSecret: string }) => {
      return api.post<{ success: boolean; error?: string }>(
        "/admin/igdb/test",
        data,
      );
    },
  });
}

export interface ScrapeStatus {
  active: boolean;
  current?: number;
  total?: number;
  gameId?: number;
  gameName?: string;
  consoleName?: string;
  consoleAbbr?: string;
  successes?: number;
  failures?: number;
  verified?: number;
}

export function useScrapeStatus() {
  return useQuery({
    queryKey: ["admin", "scrape-status"],
    queryFn: () => api.get<ScrapeStatus>("/admin/scrape/status"),
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
    queryFn: () => api.get<ScanStatus>("/admin/games/scan/status"),
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
    queryFn: () => api.get<GameCoversResponse>(`/admin/games/${gameId}/covers`),
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
      await api.put(`/admin/games/${gameId}/covers`, { source, libretroName });
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

export function useIgdbStatus() {
  return useQuery({
    queryKey: ["admin", "igdb-status"],
    queryFn: () =>
      api.get<{
        configured: boolean;
        status: "connected" | "not_configured" | "error";
        source: "env" | "database" | "none";
        error?: string;
      }>("/admin/igdb/status"),
  });
}

export function useSteamGridDBStatus() {
  return useQuery({
    queryKey: ["admin", "steamgriddb-status"],
    queryFn: () =>
      api.get<{
        configured: boolean;
        source: "env" | "database" | "none";
      }>("/admin/steamgriddb/status"),
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
      await api.post(`/admin/games/${gameId}/metadata`, metadata);
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
      api.get<IgdbSearchResult[]>(
        `/admin/games/${gameId}/igdb-search?q=${encodeURIComponent(query)}`,
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
      await api.post(`/admin/games/${gameId}/igdb-match`, { igdbId });
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
      api.get<RateLimitStatus>(`/admin/users/${userId}/rate-limit`),
    enabled: !!userId,
  });
}

export function useResetRateLimit() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (userId: string) => {
      await api.delete(`/admin/users/${userId}/rate-limit`);
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
    queryFn: () =>
      api.get<CoreCompatibilityResponse>("/admin/core-compatibility"),
  });
}
