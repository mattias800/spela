import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  GameRating,
  GameRatingsResponse,
  RatingSummary,
} from "@/types/api";

export function useGameRatings(
  gameId: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["ratings", gameId, page, pageSize],
    queryFn: () =>
      api.get<GameRatingsResponse>(
        `/games/${gameId}/ratings?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!gameId,
  });
}

export function useGameRatingSummary(gameId: string) {
  return useQuery({
    queryKey: ["ratings", gameId, "summary"],
    queryFn: () => api.get<RatingSummary>(`/games/${gameId}/ratings/summary`),
    enabled: !!gameId,
  });
}

export function useMyRating(gameId: string) {
  return useQuery({
    queryKey: ["ratings", gameId, "mine"],
    queryFn: () => api.get<GameRating>(`/games/${gameId}/ratings/mine`),
    enabled: !!gameId,
    retry: false,
  });
}

export function useRateGame() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      gameId,
      rating,
      review,
    }: {
      gameId: string;
      rating: number;
      review?: string;
    }) => {
      return api.post<GameRating>(`/games/${gameId}/ratings`, {
        rating,
        review,
      });
    },
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["ratings", gameId] });
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}

export function useDeleteRating() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (gameId: string) => {
      await api.delete(`/games/${gameId}/ratings`);
    },
    onSuccess: (_, gameId) => {
      queryClient.invalidateQueries({ queryKey: ["ratings", gameId] });
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}
