import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { GameSession } from "@/types/api";

export function useGameSessions(gameId: string) {
  return useQuery({
    queryKey: ["game-sessions", gameId],
    queryFn: () => api.get<GameSession[]>(`/games/${gameId}/sessions`),
    enabled: !!gameId,
  });
}

export function useCreateSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { gameId: string; name: string }) =>
      api.post<GameSession>(`/games/${data.gameId}/sessions`, {
        name: data.name,
      }),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useRenameSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, name }: { id: string; gameId: string; name: string }) =>
      api.put<GameSession>(`/sessions/${id}`, { name }),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useDeleteSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id }: { id: string; gameId: string }) =>
      api.delete(`/sessions/${id}`),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}
