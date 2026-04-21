import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { invariant } from "@/lib/invariant";

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

/**
 * Clones a session into a new session owned by the caller. See
 * `POST /api/sessions/{id}/clone` — inherits `totalPlayTime` and
 * `pinnedCoreSha256` from the source, copies SRAM/cheats/screenshot,
 * and seeds the new session with one save state (most-recent or
 * `saveId` when specified). Access: owner or any shared-session
 * member of the source session.
 */
export function useCloneSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      name,
      saveId,
    }: {
      /** Source session ID. */
      id: string;
      /**
       * Source session's gameId — not sent to the server; used purely
       * to invalidate the `game-sessions` query cache on success.
       * Optional because shared-session clones don't always know it
       * up front.
       */
      gameId?: string;
      name?: string;
      /**
       * Specific save ID to clone from. Omit or pass 0 to clone the
       * most-recent save. `SessionSave.id` is a stringified uint on
       * the wire; callers should `parseInt` before passing.
       */
      saveId?: number;
    }) =>
      unwrap(
        typedApi.POST("/api/sessions/{id}/clone", {
          params: {
            path: { id },
            query: saveId && saveId > 0 ? { saveId } : undefined,
          },
          body: name ? { name } : {},
        }),
      ),
    onSuccess: (_, { gameId }) => {
      if (gameId) {
        queryClient.invalidateQueries({ queryKey: ["game-sessions", gameId] });
      }
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
          params: { path: { id: invariant(sessionId, "sessionId") } },
        }),
      ),
    enabled: !!sessionId,
    select: (saves) => saves?.find((s) => s.isAuto) ?? null,
  });
}
