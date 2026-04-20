import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useGameSessions(gameId: string) {
  return useQuery({
    queryKey: ["game-sessions", gameId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/sessions", {
          params: { path: { id: gameId } },
        }),
      ),
    enabled: !!gameId,
  });
}

export function useCreateSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { gameId: string; name: string }) =>
      unwrap(
        typedApi.POST("/api/games/{id}/sessions", {
          params: { path: { id: data.gameId } },
          body: { name: data.name },
        }),
      ),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useRenameSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, name }: { id: string; gameId: string; name: string }) =>
      unwrap(
        typedApi.PUT("/api/sessions/{id}", {
          params: { path: { id } },
          body: { name },
        }),
      ),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useDeleteSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id }: { id: string; gameId: string }) =>
      unwrap(
        typedApi.DELETE("/api/sessions/{id}", {
          params: { path: { id } },
        }),
      ),
    onSuccess: (_, { gameId }) => {
      queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
    },
  });
}

export function useSession(sessionId: string) {
  return useQuery({
    queryKey: ["session", sessionId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/sessions/{id}", {
          params: { path: { id: sessionId } },
        }),
      ),
    enabled: !!sessionId,
  });
}

export function useSessionSaves(sessionId: string) {
  return useQuery({
    queryKey: ["session-saves", sessionId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/sessions/{id}/saves", {
          params: { path: { id: sessionId } },
        }),
      ),
    enabled: !!sessionId,
  });
}

export function useSessionCheats(sessionId: string) {
  return useQuery({
    queryKey: ["session-cheats", sessionId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/sessions/{id}/cheats", {
          params: { path: { id: sessionId } },
        }),
      ),
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
      unwrap(
        typedApi.PUT("/api/sessions/{id}/cheats", {
          params: { path: { id: sessionId } },
          body: { cheatsEnabled, enabledIndices },
        }),
      ),
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
      unwrap(
        typedApi.POST("/api/sessions/{id}/duplicate", {
          params: { path: { id } },
          body: name ? { name } : {},
        }),
      ),
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
    }) =>
      unwrap(
        typedApi.DELETE("/api/sessions/{id}/saves/{saveId}", {
          params: { path: { id: sessionId, saveId } },
        }),
      ),
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
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/sessions/{id}/saves", {
          params: { path: { id: sessionId as string } },
        }),
      ),
    enabled: !!sessionId,
    select: (saves) => saves?.find((s) => s.isAuto) ?? null,
  });
}
