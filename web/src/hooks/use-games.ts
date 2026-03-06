import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Game, GamesResponse, GameFilters } from "@/types/api";

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
    queryFn: () => api.get<Game[]>("/user/recent"),
  });
}

export function useFavoriteGames() {
  return useQuery({
    queryKey: ["games", "favorites"],
    queryFn: () => api.get<Game[]>("/user/favorites"),
  });
}

export function useToggleFavorite() {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async ({
      gameId,
      isFavorite,
    }: {
      gameId: string;
      isFavorite: boolean;
    }) => {
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

  const toggle = (game: Game) =>
    mutation.mutate({ gameId: game.id, isFavorite: game.isFavorite });

  return { ...mutation, toggle };
}

export function useScrapeIfNeeded() {
  return useMutation({
    mutationFn: (gameId: string) =>
      api.post(`/games/${gameId}/scrape-if-needed`),
  });
}

