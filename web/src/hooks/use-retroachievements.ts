import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
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
    queryFn: () => api.get<RAStatus>("/user/ra/status"),
  });
}

export function useLinkRA() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: RALinkRequest) =>
      api.post<RAStatus>("/user/ra/link", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-status"] });
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}

export function useUnlinkRA() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api.delete("/user/ra/link"),
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
      api.put<RAStatus>("/user/ra/settings", data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["ra-status"] });
      queryClient.invalidateQueries({ queryKey: ["user", "preferences"] });
    },
  });
}

export function useGameAchievements(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievements", gameId],
    queryFn: () => api.get<GameAchievements>(`/games/${gameId}/achievements`),
    enabled: !!gameId,
  });
}

export function useGameAchievementProgress(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievement-progress", gameId],
    queryFn: () =>
      api
        .get<GameAchievementProgressResponse>(
          `/games/${gameId}/achievements/progress`,
        )
        .then((res) => res.progress ?? null),
    enabled: !!gameId,
  });
}

export function useAchievementLeaderboard(gameId: string | undefined) {
  return useQuery({
    queryKey: ["achievement-leaderboard", gameId],
    queryFn: () =>
      api.get<AchievementLeaderboard>(
        `/games/${gameId}/achievements/leaderboard`,
      ),
    enabled: !!gameId,
  });
}

export function useAchievementTimeline(gameId: string | undefined) {
  return useQuery({
    queryKey: ["achievement-timeline", gameId],
    queryFn: () =>
      api.get<AchievementTimeline>(`/games/${gameId}/achievements/timeline`),
    enabled: !!gameId,
  });
}

export function useRecentAchievements() {
  return useQuery({
    queryKey: ["recent-achievements"],
    queryFn: () =>
      api
        .get<{ achievements: RecentAchievement[] }>("/user/achievements/recent")
        .then((res) => res.achievements),
  });
}
