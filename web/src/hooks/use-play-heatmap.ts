import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { DailyPlayActivity } from "@/types/api";

export function useMyPlayHeatmap() {
  return useQuery({
    queryKey: ["user", "play-heatmap"],
    queryFn: () => api.get<DailyPlayActivity[]>("/user/play-heatmap"),
  });
}

export function useUserPlayHeatmap(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "play-heatmap"],
    queryFn: () =>
      api.get<DailyPlayActivity[]>(`/users/${userId}/play-heatmap`),
    enabled: !!userId,
  });
}
