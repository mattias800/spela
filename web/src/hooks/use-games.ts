import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api, typedApi, unwrap } from "@/lib/api-client";
import type { Game, GamesResponse, GameFilters, ReplaceROMResponse } from "@/types/api";

export function useGames(filters?: GameFilters, options?: { enabled?: boolean }) {
  const query: Record<string, string | number | boolean | undefined> = {};
  if (filters?.search) query.search = filters.search;
  if (filters?.consoleId) query.consoleId = filters.consoleId;
  if (filters?.genre) query.genre = filters.genre;
  if (filters?.consoles?.length) query.consoles = filters.consoles.join(",");
  if (filters?.genres?.length) query.genres = filters.genres.join(",");
  if (filters?.themes?.length) query.themes = filters.themes.join(",");
  if (filters?.keywords?.length) query.keywords = filters.keywords.join(",");
  if (filters?.perspectives?.length)
    query.perspectives = filters.perspectives.join(",");
  if (filters?.regions?.length) query.region = filters.regions.join(",");
  if (filters?.developer) query.developer = filters.developer;
  if (filters?.publisher) query.publisher = filters.publisher;
  if (filters?.yearMin != null) query.yearMin = String(filters.yearMin);
  if (filters?.yearMax != null) query.yearMax = String(filters.yearMax);
  if (filters?.ratingMin != null) query.ratingMin = String(filters.ratingMin);
  if (filters?.ratingMax != null) query.ratingMax = String(filters.ratingMax);
  if (filters?.playStatus) query.playStatus = filters.playStatus;
  if (filters?.grouped === false) query.grouped = "false";
  if (filters?.hidePreRelease === false) query.hidePreRelease = "false";
  if (filters?.letter) query.letter = filters.letter;
  if (filters?.sortBy) query.sortBy = filters.sortBy;
  if (filters?.sortOrder) query.sortOrder = filters.sortOrder;
  if (filters?.page) query.page = filters.page;
  if (filters?.pageSize) query.pageSize = filters.pageSize;

  return useQuery({
    queryKey: ["games", filters],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games", { params: { query } }),
      );
      return data as GamesResponse | undefined;
    },
    enabled: options?.enabled,
  });
}

export function useGame(id: string) {
  return useQuery({
    queryKey: ["game", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}", { params: { path: { id } } }),
      );
      return data as Game | undefined;
    },
    enabled: !!id,
  });
}

export function useRecentGames() {
  return useQuery({
    queryKey: ["games", "recent"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/recent"));
      return data as Game[] | undefined;
    },
  });
}

export function useFavoriteGames() {
  return useQuery({
    queryKey: ["games", "favorites"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/favorites"));
      return data as Game[] | undefined;
    },
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
        await unwrap(
          typedApi.DELETE("/api/user/favorites/{gameId}", {
            params: { path: { gameId } },
          }),
        );
      } else {
        await unwrap(
          typedApi.POST("/api/user/favorites/{gameId}", {
            params: { path: { gameId } },
          }),
        );
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
      unwrap(
        typedApi.POST("/api/games/{id}/scrape-if-needed", {
          params: { path: { id: gameId } },
        }),
      ),
  });
}

// Multipart upload still goes through api.uploadPut — typedApi multipart
// needs a custom bodySerializer; tracked in #518.
export function useReplaceRom() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ gameId, file }: { gameId: string; file: File }) => {
      const formData = new FormData();
      formData.append("file", file);
      return api.uploadPut<ReplaceROMResponse>(
        `/admin/games/${gameId}/replace-rom`,
        formData,
      );
    },
    onSuccess: (_data, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}
