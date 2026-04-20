import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  SharedSessionsResponse,
  SharedSessionDetail,
  SharedSessionInvitation,
  SharedSessionSave,
  SharedSession,
} from "@/types/api";

export function useMySharedSessions(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ["shared-sessions", "mine", page, pageSize],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/shared-sessions"));
      return data as SharedSessionsResponse | undefined;
    },
  });
}

export function useSharedSessionInvitations() {
  return useQuery({
    queryKey: ["shared-sessions", "invitations"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/user/shared-session-invites"),
      );
      return data as SharedSessionInvitation[] | null | undefined;
    },
  });
}

export function usePendingInvitationCount() {
  return useQuery({
    queryKey: ["shared-sessions", "invitations", "count"],
    queryFn: () =>
      unwrap(typedApi.GET("/api/user/shared-session-invites/count")),
    refetchInterval: 30000,
  });
}

export function useSharedSession(id: string) {
  return useQuery({
    queryKey: ["shared-sessions", "detail", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/shared-sessions/{id}", {
          params: { path: { id } },
        }),
      );
      return data as SharedSessionDetail | undefined;
    },
    enabled: !!id,
  });
}

export function useSharedSessionSaves(sharedSessionId: string) {
  return useQuery({
    queryKey: ["shared-sessions", "saves", sharedSessionId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/shared-sessions/{id}/saves", {
          params: { path: { id: sharedSessionId } },
        }),
      );
      return data as SharedSessionSave[] | undefined;
    },
    enabled: !!sharedSessionId,
  });
}

export function useGameSharedSessions(gameId: string) {
  return useQuery({
    queryKey: ["shared-sessions", "game", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/shared-sessions", {
          params: { path: { id: gameId } },
        }),
      );
      return data as SharedSession[] | undefined;
    },
    enabled: !!gameId,
  });
}

export function useCreateSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: {
      name: string;
      gameId: string;
      description?: string;
    }) => {
      const result = await unwrap(
        typedApi.POST("/api/shared-sessions", { body: data }),
      );
      return result as SharedSessionDetail | undefined;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useDeleteSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.DELETE("/api/shared-sessions/{id}", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useInviteToSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      sharedSessionId,
      username,
    }: {
      sharedSessionId: string;
      username: string;
    }) =>
      unwrap(
        typedApi.POST("/api/shared-sessions/{id}/invites", {
          params: { path: { id: sharedSessionId } },
          body: { username },
        }),
      ),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["shared-sessions", "detail", sharedSessionId],
      });
    },
  });
}

export function useAcceptSharedSessionInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      unwrap(
        typedApi.POST("/api/user/shared-session-invites/{id}/accept", {
          params: { path: { id: invitationId } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useRejectSharedSessionInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      unwrap(
        typedApi.POST("/api/user/shared-session-invites/{id}/decline", {
          params: { path: { id: invitationId } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useLeaveSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (sharedSessionId: string) =>
      unwrap(
        typedApi.POST("/api/shared-sessions/{id}/leave", {
          params: { path: { id: sharedSessionId } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useRemoveSharedSessionMember() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      sharedSessionId,
      userId,
    }: {
      sharedSessionId: string;
      userId: string;
    }) =>
      unwrap(
        typedApi.DELETE("/api/shared-sessions/{id}/members/{userId}", {
          params: { path: { id: sharedSessionId, userId } },
        }),
      ),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["shared-sessions", "detail", sharedSessionId],
      });
    },
  });
}

export function useDeleteSharedSessionSave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      sharedSessionId,
      saveId,
    }: {
      sharedSessionId: string;
      saveId: string;
    }) =>
      unwrap(
        typedApi.DELETE("/api/shared-sessions/{id}/saves/{saveId}", {
          params: { path: { id: sharedSessionId, saveId } },
        }),
      ),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["shared-sessions", "saves", sharedSessionId],
      });
    },
  });
}

export function useSharedSessionRealtime(sharedSessionId?: string) {
  const queryClient = useQueryClient();

  useWebSocketEvent("shared_session_invite_sent", () => {
    queryClient.invalidateQueries({ queryKey: ["shared-sessions", "invitations"] });
    queryClient.invalidateQueries({
      queryKey: ["shared-sessions", "invitations", "count"],
    });
  });

  useWebSocketEvent("shared_session_save_new", () => {
    if (sharedSessionId) {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "saves", sharedSessionId] });
    }
  });

  useWebSocketEvent("shared_session_invite_accepted", () => {
    if (sharedSessionId) {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "detail", sharedSessionId] });
    }
    queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
  });

  useWebSocketEvent("shared_session_member_left", () => {
    if (sharedSessionId) {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "detail", sharedSessionId] });
    }
    queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
  });

  useWebSocketEvent("shared_session_member_removed", () => {
    if (sharedSessionId) {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "detail", sharedSessionId] });
    }
    queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
  });
}
