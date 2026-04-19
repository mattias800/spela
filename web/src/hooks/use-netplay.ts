import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  NetplaySession,
  NetplaySessionsResponse,
  NetplayInvite,
  NetplayInvitesResponse,
} from "@/types/api";

export function useNetplaySessions(page = 1, pageSize = 20) {
  return useQuery({
    queryKey: ["netplay", "sessions", page, pageSize],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/netplay/sessions", {
          params: { query: { page, pageSize } },
        }),
      );
      return data as NetplaySessionsResponse | undefined;
    },
  });
}

export function useNetplaySession(id: string) {
  return useQuery({
    queryKey: ["netplay", "sessions", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/netplay/sessions/{id}", {
          params: { path: { id } },
        }),
      );
      return data as NetplaySession | undefined;
    },
    enabled: !!id,
  });
}

export function useCreateNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: { gameId: string; inputDelay?: number }) => {
      const result = await unwrap(
        typedApi.POST("/api/netplay/sessions", { body: data }),
      );
      return result as NetplaySession | undefined;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useJoinNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (inviteCode: string) => {
      const data = await unwrap(
        typedApi.POST("/api/netplay/sessions/join", {
          body: { inviteCode },
        }),
      );
      return data as NetplaySession | undefined;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useLeaveNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.POST("/api/netplay/sessions/{id}/leave", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

export function useUpdateNetplaySettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      id,
      inputDelay,
    }: {
      id: string;
      inputDelay: number;
    }) => {
      const data = await unwrap(
        typedApi.PUT("/api/netplay/sessions/{id}/settings", {
          params: { path: { id } },
          body: { inputDelay },
        }),
      );
      return data as NetplaySession | undefined;
    },
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions", id] });
    },
  });
}

export function useDeleteNetplaySession() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) =>
      unwrap(
        typedApi.DELETE("/api/netplay/sessions/{id}", {
          params: { path: { id } },
        }),
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["netplay", "sessions"] });
    },
  });
}

// ── Netplay Invites ──────────────────────────────────────────────────────────

export function useNetplayInvites() {
  return useQuery({
    queryKey: ["netplay", "invites"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/netplay/invites"));
      return data as NetplayInvitesResponse | undefined;
    },
  });
}

export function usePendingNetplayInviteCount() {
  return useQuery({
    queryKey: ["netplay", "invites", "count"],
    queryFn: () => unwrap(typedApi.GET("/api/netplay/invites/count")),
    refetchInterval: 30000,
  });
}

export function useSessionNetplayInvites(sessionId: string) {
  return useQuery({
    queryKey: ["netplay", "sessions", sessionId, "invites"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/netplay/sessions/{id}/invites", {
          params: { path: { id: sessionId } },
        }),
      );
      return data as NetplayInvite[] | undefined;
    },
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
    }) =>
      unwrap(
        typedApi.POST("/api/netplay/sessions/{id}/invites", {
          params: { path: { id: sessionId } },
          body: { username },
        }),
      ),
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
      unwrap(
        typedApi.POST("/api/netplay/invites/{inviteId}/accept", {
          params: { path: { inviteId } },
        }),
      ),
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
      unwrap(
        typedApi.POST("/api/netplay/invites/{inviteId}/decline", {
          params: { path: { inviteId } },
        }),
      ),
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
