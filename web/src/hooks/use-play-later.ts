import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type { Game } from "@/types/api";

export function usePlayLaterGames() {
  return useQuery({
    queryKey: ["games", "play-later"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/play-later"));
      return data as Game[] | undefined;
    },
  });
}

export function useAddToPlayLater() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gameId: string) =>
      unwrap(
        typedApi.POST("/api/user/play-later/{gameId}", {
          params: { path: { gameId } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}

export function useRemoveFromPlayLater() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gameId: string) =>
      unwrap(
        typedApi.DELETE("/api/user/play-later/{gameId}", {
          params: { path: { gameId } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}

export function useReorderPlayLater() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gameIds: string[]) =>
      unwrap(
        typedApi.PUT("/api/user/play-later/reorder", {
          body: { gameIds },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games", "play-later"] });
    },
  });
}

export function useTogglePlayLater() {
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: async ({
      gameId,
      isInPlayLater,
    }: {
      gameId: string;
      isInPlayLater: boolean;
    }) => {
      if (isInPlayLater) {
        await unwrap(
          typedApi.DELETE("/api/user/play-later/{gameId}", {
            params: { path: { gameId } },
          }),
        );
      } else {
        await unwrap(
          typedApi.POST("/api/user/play-later/{gameId}", {
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
    mutation.mutate({ gameId: game.id, isInPlayLater: game.isInPlayLater });

  return { ...mutation, toggle };
}
