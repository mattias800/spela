import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  NetplaySession,
  NetplaySessionsResponse,
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
