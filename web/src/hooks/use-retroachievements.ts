import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import type {
  RAStatus,
  RALinkRequest,
  RASettingsRequest,
  GameAchievements,
  GameAchievementProgressResponse,
  AchievementLeaderboard,
  AchievementTimeline,
  RecentAchievement,
} from "@/types/api";

export function useRAStatus() {
  return useQuery({
    queryKey: ["ra-status"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/ra/status"));
      return data as RAStatus | undefined;
    },
  });
}

export function useLinkRA() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: RALinkRequest) =>
      unwrap(typedApi.POST("/api/user/ra/link", { body: data })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-status"] });
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}

export function useUnlinkRA() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => unwrap(typedApi.DELETE("/api/user/ra/link")),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-status"] });
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}

export function useRASettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: RASettingsRequest) =>
      unwrap(typedApi.PUT("/api/user/ra/settings", { body: data })),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-status"] });
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}

export function useGameAchievements(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievements", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/achievements", {
          params: { path: { id: gameId as string } },
        }),
      );
      return data as GameAchievements | undefined;
    },
    enabled: !!gameId,
    refetchInterval: (query) => {
      // Poll every 2 seconds while the server is processing an RA fetch.
      // Stop polling once we have actual achievement data.
      if (query.state.data?.status === "pending") {
        return 2000;
      }
      return false;
    },
  });
}

export function useGameAchievementProgress(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievement-progress", gameId],
    queryFn: async () => {
      const data = (await unwrap(
        typedApi.GET("/api/games/{id}/achievements/progress", {
          params: { path: { id: gameId as string } },
        }),
      )) as GameAchievementProgressResponse | undefined;
      return data?.progress ?? null;
    },
    enabled: !!gameId,
  });
}

export function useAchievementLeaderboard(gameId: string | undefined) {
  return useQuery({
    queryKey: ["achievement-leaderboard", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/achievements/leaderboard", {
          params: { path: { id: gameId as string } },
        }),
      );
      return data as AchievementLeaderboard | undefined;
    },
    enabled: !!gameId,
  });
}

export function useAchievementTimeline(gameId: string | undefined) {
  return useQuery({
    queryKey: ["achievement-timeline", gameId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/achievements/timeline", {
          params: { path: { id: gameId as string } },
        }),
      );
      return data as AchievementTimeline | undefined;
    },
    enabled: !!gameId,
  });
}

export function useRecentAchievements() {
  return useQuery({
    queryKey: ["recent-achievements"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/user/achievements/recent"),
      );
      return (data?.achievements ?? []) as RecentAchievement[];
    },
  });
}
