import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  SharedSessionDetailResponse,
  SharedSessionMemberResponse,
  SharedSessionResponse,
} from "@/generated/schemas";
import type {
  SharedSessionDetail,
  SharedSessionMember,
  SharedSession,
} from "@/types/api";
import {
  asSharedSessionMemberRole,
  asSharedSessionStatus,
} from "@/types/view-model-narrowing";

// Map wire shapes (status/role emitted as `string`) to the view-model
// shapes (literal unions). If the server ever emits a value outside the
// claimed union, the narrowing helper throws — loud, not silent.
function toSharedSession(wire: SharedSessionResponse): SharedSession {
  return {
    ...wire,
    status: asSharedSessionStatus(wire.status),
  };
}

function toSharedSessionMember(
  wire: SharedSessionMemberResponse,
): SharedSessionMember {
  return {
    ...wire,
    role: asSharedSessionMemberRole(wire.role),
  };
}

function toSharedSessionDetail(
  wire: SharedSessionDetailResponse,
): SharedSessionDetail {
  return {
    ...wire,
    status: asSharedSessionStatus(wire.status),
    members: wire.members?.map(toSharedSessionMember) ?? null,
  };
}

export function useMySharedSessions() {
  return useQuery({
    queryKey: ["shared-sessions", "mine"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/shared-sessions"));
      return data?.map(toSharedSession);
    },
  });
}

export function useSharedSessionInvitations() {
  return useQuery({
    queryKey: ["shared-sessions", "invitations"],
    queryFn: async () => {
      // SharedSessionInvitation is a pure alias of the wire shape — no
      // narrowing needed. openapi-fetch returns the correct type already.
      const data = await unwrap(
        typedApi.GET("/api/user/shared-session-invites"),
      );
      return data;
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
      return data && toSharedSessionDetail(data);
    },
    enabled: !!id,
  });
}

export function useSharedSessionSaves(sharedSessionId: string) {
  return useQuery({
    queryKey: ["shared-sessions", "saves", sharedSessionId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/shared-sessions/{id}/saves", {
          params: { path: { id: sharedSessionId } },
        }),
      ),
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
      return data?.map(toSharedSession);
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
      return result && toSharedSessionDetail(result);
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
