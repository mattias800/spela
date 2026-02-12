import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  RAStatus,
  RALinkRequest,
  RASettingsRequest,
  GameAchievements,
  GameAchievementProgress,
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
    queryFn: () =>
      api.get<GameAchievements>(`/games/${gameId}/achievements`),
    enabled: !!gameId,
  });
}

export function useGameAchievementProgress(gameId: string | undefined) {
  return useQuery({
    queryKey: ["game-achievement-progress", gameId],
    queryFn: () =>
      api.get<GameAchievementProgress[]>(
        `/games/${gameId}/achievements/progress`,
      ),
    enabled: !!gameId,
  });
}
