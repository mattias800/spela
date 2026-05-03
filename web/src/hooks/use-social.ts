import { useQuery, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";

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
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/social/activity", {
          params: { query: { page, pageSize } },
        }),
      ),
  });
}

export function useSearchUsers(
  query: string,
  page: number = 1,
  pageSize: number = 20,
) {
  return useQuery({
    queryKey: ["users", "search", query, page, pageSize],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/users/search", {
          params: { query: { q: query, page, pageSize } },
        }),
      ),
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

  useWebSocketEvent("activity_new", () => {
    // Invalidate every activity feed cache regardless of (page, pageSize) —
    // the previous setQueryData targeted only ["social", "activity", 1, 20]
    // and silently dropped realtime updates for any caller that passed a
    // different pageSize (e.g. the home dashboard's maxItems=5 feed).
    queryClient.invalidateQueries({ queryKey: ["social", "activity"] });
  });

  useWebSocketEvent("online_status", () => {
    queryClient.invalidateQueries({ queryKey: ["social", "online"] });
  });
}
