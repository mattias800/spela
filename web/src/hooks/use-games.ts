import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Game, GamesResponse, GameFilters, SaveState, PlayHistory, Favorite } from "@/types/api";

export function useGames(filters?: GameFilters) {
  const params = new URLSearchParams();
  if (filters?.search) params.set("search", filters.search);
  if (filters?.consoleId) params.set("consoleId", filters.consoleId);
  if (filters?.genre) params.set("genre", filters.genre);
  if (filters?.sortBy) params.set("sortBy", filters.sortBy);
  if (filters?.sortOrder) params.set("sortOrder", filters.sortOrder);
  if (filters?.page) params.set("page", String(filters.page));
  if (filters?.pageSize) params.set("pageSize", String(filters.pageSize));

  const query = params.toString();

  return useQuery({
    queryKey: ["games", filters],
    queryFn: () => api.get<GamesResponse>(`/games${query ? `?${query}` : ""}`),
  });
}

export function useGame(id: string) {
  return useQuery({
    queryKey: ["game", id],
    queryFn: () => api.get<Game>(`/games/${id}`),
    enabled: !!id,
  });
}

export function useRecentGames() {
  return useQuery({
    queryKey: ["games", "recent"],
    queryFn: async () => {
      const history = await api.get<PlayHistory[]>("/user/recent");
      return history.map((h) => h.game).filter((g): g is Game => !!g);
    },
  });
}

export function useFavoriteGames() {
  return useQuery({
    queryKey: ["games", "favorites"],
    queryFn: async () => {
      const favorites = await api.get<Favorite[]>("/user/favorites");
      return favorites.map((f) => f.game).filter((g): g is Game => !!g);
    },
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ gameId, isFavorite }: { gameId: number; isFavorite: boolean }) => {
      if (isFavorite) {
        await api.delete(`/user/favorites/${gameId}`);
      } else {
        await api.post(`/user/favorites/${gameId}`);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}

export function useGameSaves(gameId: string) {
  return useQuery({
    queryKey: ["saves", gameId],
    queryFn: () => api.get<SaveState[]>(`/games/${gameId}/saves`),
    enabled: !!gameId,
  });
}

export function useDeleteSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ gameId, saveId }: { gameId: number; saveId: number }) => {
      await api.delete(`/games/${gameId}/saves/${saveId}`);
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["saves", String(gameId)] });
    },
  });
}
