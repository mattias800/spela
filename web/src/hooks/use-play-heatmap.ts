import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useMyPlayHeatmap() {
  return useQuery({
    queryKey: ["user", "play-heatmap"],
    queryFn: () => unwrap(typedApi.GET("/api/user/play-heatmap")),
  });
}

export function useUserPlayHeatmap(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "play-heatmap"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/users/{id}/play-heatmap", {
          params: { path: { id: userId } },
        }),
      ),
    enabled: !!userId,
  });
}
