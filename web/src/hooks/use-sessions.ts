import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  GameSession,
  SessionSave,
  SessionCheatConfig,
} from "@/types/api";

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

export function useSession(sessionId: string) {
  return useQuery({
    queryKey: ["session", sessionId],
    queryFn: () => api.get<GameSession>(`/sessions/${sessionId}`),
    enabled: !!sessionId,
  });
}

export function useSessionSaves(sessionId: string) {
  return useQuery({
    queryKey: ["session-saves", sessionId],
    queryFn: () => api.get<SessionSave[]>(`/sessions/${sessionId}/saves`),
    enabled: !!sessionId,
  });
}

export function useSessionCheats(sessionId: string) {
  return useQuery({
    queryKey: ["session-cheats", sessionId],
    queryFn: () =>
      api.get<SessionCheatConfig>(`/sessions/${sessionId}/cheats`),
    enabled: !!sessionId,
  });
}

export function useUpdateSessionCheats() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      cheatsEnabled,
      enabledIndices,
    }: {
      sessionId: string;
      cheatsEnabled: boolean;
      enabledIndices: number[];
    }) =>
      api.put<SessionCheatConfig>(`/sessions/${sessionId}/cheats`, {
        cheatsEnabled,
        enabledIndices,
      }),
    onSuccess: (_, { sessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["session-cheats", sessionId],
      });
      queryClient.invalidateQueries({ queryKey: ["session", sessionId] });
    },
  });
}

export function useDuplicateSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      name,
    }: {
      id: string;
      gameId: string;
      name?: string;
    }) =>
      api.post<GameSession>(`/sessions/${id}/duplicate`, name ? { name } : {}),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useDeleteSessionSave() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      sessionId,
      saveId,
    }: {
      sessionId: string;
      saveId: string;
    }) => api.delete(`/sessions/${sessionId}/saves/${saveId}`),
    onSuccess: (_, { sessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["session-saves", sessionId],
      });
    },
  });
}

/** Fetch the latest auto-save metadata (not the file) to check core compatibility. */
export function useAutoSaveInfo(sessionId: string | undefined) {
  return useQuery({
    queryKey: ["session-saves", sessionId],
    queryFn: () => api.get<SessionSave[]>(`/sessions/${sessionId}/saves`),
    enabled: !!sessionId,
    select: (saves) => saves.find((s) => s.isAuto) ?? null,
  });
}

