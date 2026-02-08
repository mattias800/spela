import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { User, ServerSettingsMap, MetadataMatch } from "@/types/api";

export function useAdminUsers() {
  return useQuery({
    queryKey: ["admin", "users"],
    queryFn: () => api.get<User[]>("/admin/users"),
  });
}

export function useUpdateUser() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, data }: { id: string; data: { role?: string; email?: string; password?: string; disabled?: boolean } }) => {
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
    mutationFn: async (data: { username: string; email: string; password: string; role: string }) => {
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
  return useMutation({
    mutationFn: () => api.post<Record<string, unknown>>("/games/scan"),
  });
}

export function useScrapeMetadata() {
  return useMutation({
    mutationFn: () => api.post<void>("/admin/scrape"),
  });
}

export function useMetadataMatches() {
  return useQuery({
    queryKey: ["admin", "metadata-matches"],
    queryFn: () => api.get<MetadataMatch[]>("/admin/metadata-matches"),
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
    },
  });
}

export function useAdminStats() {
  return useQuery({
    queryKey: ["admin", "stats"],
    queryFn: () => api.get<{ users: number; games: number; consoles: number; saves: number }>("/admin/stats"),
  });
}

export function useUpdateGameMetadata() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ gameId, metadata }: { gameId: string; metadata: Record<string, unknown> }) => {
      await api.post(`/games/${gameId}/metadata`, metadata);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "metadata-matches"] });
    },
  });
}
