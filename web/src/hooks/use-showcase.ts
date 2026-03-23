import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  ShowcaseAchievement,
  UnlockedAchievementsResponse,
} from "@/types/api";

export function usePublicShowcase(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "showcase"],
    queryFn: () =>
      api.get<ShowcaseAchievement[]>(
        `/users/${userId}/achievements/showcase`,
      ),
    enabled: !!userId,
  });
}

export function useOwnShowcase() {
  return useQuery({
    queryKey: ["user", "showcase"],
    queryFn: () =>
      api.get<ShowcaseAchievement[]>("/user/achievements/showcase"),
  });
}

export function useUnlockedAchievements() {
  return useQuery({
    queryKey: ["user", "achievements", "unlocked"],
    queryFn: () =>
      api.get<UnlockedAchievementsResponse>(
        "/user/achievements/unlocked",
      ),
  });
}

export function useUpdateShowcase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (
      entries: Array<{ achievementRaId: number; raGameId: number }>,
    ) =>
      api.put<ShowcaseAchievement[]>(
        "/user/achievements/showcase",
        entries,
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(["user", "showcase"], data);
      // Also invalidate any public showcase queries
      queryClient.invalidateQueries({
        queryKey: ["users"],
        predicate: (query) =>
          query.queryKey.length >= 3 &&
          query.queryKey[2] === "showcase",
      });
    },
  });
}
