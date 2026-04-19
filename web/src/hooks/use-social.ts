import { useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  ActivityEvent,
  ActivityFeedResponse,
  UserSearchResponse,
} from "@/types/api";

export function useOnlineUsers() {
  return useQuery({
    queryKey: ["social", "online"],
    queryFn: () => unwrap(typedApi.GET("/api/social/online")),
    refetchInterval: 30000,
  });
}

export function useActivityFeed(page: number = 1, pageSize: number = 20) {
  return useQuery({
    queryKey: ["social", "activity", page, pageSize],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/social/activity", {
          params: { query: { page, pageSize } },
        }),
      );
      return data as ActivityFeedResponse | undefined;
    },
  });
}

export function useSearchUsers(
  query: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["users", "search", query, page, pageSize],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/users/search", {
          params: { query: { q: query, page, pageSize } },
        }),
      );
      return data as UserSearchResponse | undefined;
    },
  });
}

export function useRecentPartners() {
  return useQuery({
    queryKey: ["users", "recent-partners"],
    queryFn: () => unwrap(typedApi.GET("/api/users/recent-partners")),
  });
}

export function useActivityRealtime() {
  const queryClient = useQueryClient();

  useWebSocketEvent("activity_new", (payload: ActivityEvent) => {
    queryClient.setQueryData<ActivityFeedResponse>(
      ["social", "activity", 1, 20],
      (old) => {
        if (!old) return old;
        return {
          ...old,
          data: [payload, ...(old.data ?? [])],
          total: old.total + 1,
        };
      },
    );
  });

  useWebSocketEvent("online_status", () => {
    queryClient.invalidateQueries({ queryKey: ["social", "online"] });
  });
}
