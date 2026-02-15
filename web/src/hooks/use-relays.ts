import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  RelaysResponse,
  RelayDetail,
  RelayInvitation,
  RelayInvitationsResponse,
  RelaySave,
  Relay,
} from "@/types/api";

export function useMyRelays(page = 1, pageSize = 20) {
  const params = new URLSearchParams();
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));

  return useQuery({
    queryKey: ["relays", "mine", page, pageSize],
    queryFn: () => api.get<RelaysResponse>(`/relays?${params}`),
  });
}

export function useRelayInvitations() {
  return useQuery({
    queryKey: ["relays", "invitations"],
    queryFn: () =>
      api.get<RelayInvitationsResponse>("/relays/invitations"),
  });
}

export function usePendingInvitationCount() {
  return useQuery({
    queryKey: ["relays", "invitations", "count"],
    queryFn: () =>
      api.get<{ count: number }>("/relays/invitations/count"),
    refetchInterval: 30000,
  });
}

export function useRelay(id: string) {
  return useQuery({
    queryKey: ["relays", "detail", id],
    queryFn: () => api.get<RelayDetail>(`/relays/${id}`),
    enabled: !!id,
  });
}

export function useRelaySaves(relayId: string) {
  return useQuery({
    queryKey: ["relays", "saves", relayId],
    queryFn: () => api.get<RelaySave[]>(`/relays/${relayId}/saves`),
    enabled: !!relayId,
  });
}

export function useGameRelays(gameId: string) {
  return useQuery({
    queryKey: ["relays", "game", gameId],
    queryFn: () => api.get<Relay[]>(`/games/${gameId}/relays`),
    enabled: !!gameId,
  });
}

export function useCreateRelay() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: {
      name: string;
      gameId: string;
      description?: string;
    }) => api.post<RelayDetail>("/relays", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relays"] });
    },
  });
}

export function useDeleteRelay() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => api.delete(`/relays/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relays"] });
    },
  });
}

export function useInviteToRelay() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      relayId,
      username,
    }: {
      relayId: string;
      username: string;
    }) => api.post(`/relays/${relayId}/invitations`, { username }),
    onSuccess: (_, { relayId }) => {
      queryClient.invalidateQueries({ queryKey: ["relays", "detail", relayId] });
    },
  });
}

export function useAcceptRelayInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      api.post(`/relays/invitations/${invitationId}/accept`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relays"] });
    },
  });
}

export function useRejectRelayInvitation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (invitationId: string) =>
      api.post(`/relays/invitations/${invitationId}/reject`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relays"] });
    },
  });
}

export function useLeaveRelay() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (relayId: string) =>
      api.delete(`/relays/${relayId}/members/me`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["relays"] });
    },
  });
}

export function useRemoveRelayMember() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      relayId,
      userId,
    }: {
      relayId: string;
      userId: string;
    }) => api.delete(`/relays/${relayId}/members/${userId}`),
    onSuccess: (_, { relayId }) => {
      queryClient.invalidateQueries({ queryKey: ["relays", "detail", relayId] });
    },
  });
}

export function useDeleteRelaySave() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      relayId,
      saveId,
    }: {
      relayId: string;
      saveId: string;
    }) => api.delete(`/relays/${relayId}/saves/${saveId}`),
    onSuccess: (_, { relayId }) => {
      queryClient.invalidateQueries({ queryKey: ["relays", "saves", relayId] });
    },
  });
}

export function useRelayRealtime(relayId?: string) {
  const queryClient = useQueryClient();

  useWebSocketEvent("relay_invitation", () => {
    queryClient.invalidateQueries({ queryKey: ["relays", "invitations"] });
    queryClient.invalidateQueries({
      queryKey: ["relays", "invitations", "count"],
    });
  });

  useWebSocketEvent("relay_save_new", () => {
    if (relayId) {
      queryClient.invalidateQueries({ queryKey: ["relays", "saves", relayId] });
    }
  });

  useWebSocketEvent("relay_member_joined", () => {
    if (relayId) {
      queryClient.invalidateQueries({ queryKey: ["relays", "detail", relayId] });
    }
    queryClient.invalidateQueries({ queryKey: ["relays"] });
  });

  useWebSocketEvent("relay_member_left", () => {
    if (relayId) {
      queryClient.invalidateQueries({ queryKey: ["relays", "detail", relayId] });
    }
    queryClient.invalidateQueries({ queryKey: ["relays"] });
  });
}
