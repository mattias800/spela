import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  SharedSessionsResponse,
  SharedSessionDetail,
  SharedSessionInvitationsResponse,
  SharedSessionSave,
  SharedSession,
} from "@/types/api";

export function useMySharedSessions(page = 1, pageSize = 20) {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));

  return useQuery({
    queryKey: ["shared-sessions", "mine", page, pageSize],
    queryFn: () => api.get<SharedSessionsResponse>(`/shared-sessions?${params}`),
  });
}

export function useSharedSessionInvitations() {
  return useQuery({
    queryKey: ["shared-sessions", "invitations"],
    queryFn: () =>
      api.get<SharedSessionInvitationsResponse>("/user/shared-session-invites"),
  });
}

export function usePendingInvitationCount() {
  return useQuery({
    queryKey: ["shared-sessions", "invitations", "count"],
    queryFn: () =>
      api.get<{ count: number }>("/user/shared-session-invites/count"),
    refetchInterval: 30000,
  });
}

export function useSharedSession(id: string) {
  return useQuery({
    queryKey: ["shared-sessions", "detail", id],
    queryFn: () => api.get<SharedSessionDetail>(`/shared-sessions/${id}`),
    enabled: !!id,
  });
}

export function useSharedSessionSaves(sharedSessionId: string) {
  return useQuery({
    queryKey: ["shared-sessions", "saves", sharedSessionId],
    queryFn: () => api.get<SharedSessionSave[]>(`/shared-sessions/${sharedSessionId}/saves`),
    enabled: !!sharedSessionId,
  });
}

export function useGameSharedSessions(gameId: string) {
  return useQuery({
    queryKey: ["shared-sessions", "game", gameId],
    queryFn: () => api.get<SharedSession[]>(`/games/${gameId}/shared-sessions`),
    enabled: !!gameId,
  });
}

export function useCreateSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      gameId: string;
      description?: string;
    }) => api.post<SharedSessionDetail>("/shared-sessions", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useDeleteSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.delete(`/shared-sessions/${id}`),
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
    }) => api.post(`/shared-sessions/${sharedSessionId}/invites`, { username }),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "detail", sharedSessionId] });
    },
  });
}

export function useAcceptSharedSessionInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      api.post(`/user/shared-session-invites/${invitationId}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useRejectSharedSessionInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      api.post(`/user/shared-session-invites/${invitationId}/decline`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions"] });
    },
  });
}

export function useLeaveSharedSession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (sharedSessionId: string) =>
      api.post(`/shared-sessions/${sharedSessionId}/leave`),
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
    }) => api.delete(`/shared-sessions/${sharedSessionId}/members/${userId}`),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "detail", sharedSessionId] });
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
    }) => api.delete(`/shared-sessions/${sharedSessionId}/saves/${saveId}`),
    onSuccess: (_, { sharedSessionId }) => {
      queryClient.invalidateQueries({ queryKey: ["shared-sessions", "saves", sharedSessionId] });
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
