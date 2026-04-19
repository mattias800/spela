import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap, ApiError } from "@/lib/api-client";

export function useGameRatings(
  gameId: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["ratings", gameId, page, pageSize],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/ratings", {
          params: { path: { id: gameId }, query: { page, pageSize } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useGameRatingSummary(gameId: string) {
  return useQuery({
    queryKey: ["ratings", gameId, "summary"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/ratings/summary", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useMyRating(gameId: string) {
  return useQuery({
    queryKey: ["ratings", gameId, "mine"],
    queryFn: async () => {
      try {
        return await unwrap(
          typedApi.GET("/api/games/{id}/ratings/mine", {
            params: { path: { id: gameId } },
          }),
        );
      } catch (e) {
        // 404 = no rating submitted yet, not an error
        if (e instanceof ApiError && e.status === 404) {
          return null;
        }
        throw e;
      }
    },
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
      return unwrap(
        typedApi.POST("/api/games/{id}/ratings", {
          params: { path: { id: gameId } },
          body: { rating, review },
        }),
      );
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
      await unwrap(
        typedApi.DELETE("/api/games/{id}/ratings", {
          params: { path: { id: gameId } },
        }),
      );
    },
    onSuccess: (_, gameId) => {
      queryClient.invalidateQueries({ queryKey: ["ratings", gameId] });
      queryClient.invalidateQueries({ queryKey: ["game", gameId] });
      queryClient.invalidateQueries({ queryKey: ["games"] });
    },
  });
}
