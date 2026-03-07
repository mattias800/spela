import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  NetplaySession,
  NetplaySessionsResponse,
  NetplayInvite,
  NetplayInvitesResponse,
} from "@/types/api";

export function useNetplaySessions(page = 1, pageSize = 20) {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));

  return useQuery({
    queryKey: ["netplay", "sessions", page, pageSize],
    queryFn: () =>
      api.get<NetplaySessionsResponse>(`/netplay/sessions?${params}`),
  });
}

export function useNetplaySession(id: string) {
  return useQuery({
    queryKey: ["netplay", "sessions", id],
    queryFn: () => api.get<NetplaySession>(`/netplay/sessions/${id}`),
    enabled: !!id,
  });
}

export function useCreateNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      gameId: string;
      inputDelay?: number;
    }) => api.post<NetplaySession>("/netplay/sessions", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useJoinNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inviteCode: string) =>
      api.post<NetplaySession>("/netplay/sessions/join", { inviteCode }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useLeaveNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      api.post(`/netplay/sessions/${id}/leave`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useUpdateNetplaySettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      id,
      inputDelay,
    }: {
      id: string;
      inputDelay: number;
    }) => api.put<NetplaySession>(`/netplay/sessions/${id}/settings`, { inputDelay }),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions", id] });
    },
  });
}

export function useDeleteNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.delete(`/netplay/sessions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

// ── Netplay Invites ──────────────────────────────────────────────────────────

export function useNetplayInvites() {
  return useQuery({
    queryKey: ["netplay", "invites"],
    queryFn: () => api.get<NetplayInvitesResponse>("/netplay/invites"),
  });
}

export function usePendingNetplayInviteCount() {
  return useQuery({
    queryKey: ["netplay", "invites", "count"],
    queryFn: () => api.get<{ count: number }>("/netplay/invites/count"),
    refetchInterval: 30000,
  });
}

export function useSessionNetplayInvites(sessionId: string) {
  return useQuery({
    queryKey: ["netplay", "sessions", sessionId, "invites"],
    queryFn: () =>
      api.get<NetplayInvite[]>(`/netplay/sessions/${sessionId}/invites`),
    enabled: !!sessionId,
  });
}

export function useSendNetplayInvite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      sessionId,
      username,
    }: {
      sessionId: string;
      username: string;
    }) => api.post(`/netplay/sessions/${sessionId}/invites`, { username }),
    onSuccess: (_, { sessionId }) => {
      queryClient.invalidateQueries({
        queryKey: ["netplay", "sessions", sessionId, "invites"],
      });
    },
  });
}

export function useAcceptNetplayInvite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inviteId: string) =>
      api.post(`/netplay/invites/${inviteId}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "invites"] });
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useDeclineNetplayInvite() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inviteId: string) =>
      api.post(`/netplay/invites/${inviteId}/decline`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "invites"] });
    },
  });
}

export function useNetplayInvitesRealtime(sessionId?: string) {
  const queryClient = useQueryClient();

  useWebSocketEvent("netplay_invite_sent", () => {
    queryClient.invalidateQueries({ queryKey: ["netplay", "invites"] });
    queryClient.invalidateQueries({
      queryKey: ["netplay", "invites", "count"],
    });
    if (sessionId) {
      queryClient.invalidateQueries({
        queryKey: ["netplay", "sessions", sessionId, "invites"],
      });
    }
  });

  useWebSocketEvent("netplay_invite_accepted", () => {
    if (sessionId) {
      queryClient.invalidateQueries({
        queryKey: ["netplay", "sessions", sessionId, "invites"],
      });
      queryClient.invalidateQueries({
        queryKey: ["netplay", "sessions", sessionId],
      });
    }
    queryClient.invalidateQueries({ queryKey: ["netplay", "invites"] });
    queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
  });

  useWebSocketEvent("netplay_invite_declined", () => {
    if (sessionId) {
      queryClient.invalidateQueries({
        queryKey: ["netplay", "sessions", sessionId, "invites"],
      });
    }
    queryClient.invalidateQueries({ queryKey: ["netplay", "invites"] });
  });
}
