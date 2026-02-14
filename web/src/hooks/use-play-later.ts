import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { Game } from "@/types/api";

export function usePlayLaterGames() {
  return useQuery({
    queryKey: ["games", "play-later"],
    queryFn: () => api.get<Game[]>("/user/play-later"),
  });
}

export function useAddToPlayLater() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gameId: string) => api.post(`/user/play-later/${gameId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["games"] });
      queryClient.invalidateQueries({ queryKey: ["game"] });
    },
  });
}

export function useRemoveFromPlayLater() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (gameId: string) => api.delete(`/user/play-later/${gameId}`),
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
      api.put("/user/play-later/reorder", { gameIds }),
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
        await api.delete(`/user/play-later/${gameId}`);
      } else {
        await api.post(`/user/play-later/${gameId}`);
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
