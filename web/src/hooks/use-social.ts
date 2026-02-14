import { useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import { useWebSocketEvent } from "@/hooks/use-websocket";
import type {
  OnlineUsersResponse,
  ActivityFeedResponse,
  ActivityEvent,
} from "@/types/api";

export function useOnlineUsers() {
  return useQuery({
    queryKey: ["social", "online"],
    queryFn: () => api.get<OnlineUsersResponse>("/social/online"),
    refetchInterval: 30000,
  });
}

export function useActivityFeed(page: number = 1, pageSize: number = 20) {
  return useQuery({
    queryKey: ["social", "activity", page, pageSize],
    queryFn: () =>
      api.get<ActivityFeedResponse>(
        `/social/activity?page=${page}&pageSize=${pageSize}`,
      ),
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
          data: [payload, ...old.data],
          total: old.total + 1,
        };
      },
    );
  });

  useWebSocketEvent("online_status", () => {
    queryClient.invalidateQueries({ queryKey: ["social", "online"] });
  });
}
