import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function usePublicShowcase(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "showcase"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/users/{id}/achievements/showcase", {
          params: { path: { id: userId } },
        }),
      ),
    enabled: !!userId,
  });
}

export function useOwnShowcase() {
  return useQuery({
    queryKey: ["user", "showcase"],
    queryFn: () => unwrap(typedApi.GET("/api/user/achievements/showcase")),
  });
}

export function useUnlockedAchievements() {
  return useQuery({
    queryKey: ["user", "achievements", "unlocked"],
    queryFn: () => unwrap(typedApi.GET("/api/user/achievements/unlocked")),
  });
}

export function useUpdateShowcase() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (
      entries: Array<{ achievementRaId: number; raGameId: number }>,
    ) =>
      unwrap(
        typedApi.PUT("/api/user/achievements/showcase", { body: entries }),
      ),
    onSuccess: (data) => {
      queryClient.setQueryData(["user", "showcase"], data);
      queryClient.invalidateQueries({
        queryKey: ["users"],
        predicate: (query) =>
          query.queryKey.length >= 3 && query.queryKey[2] === "showcase",
      });
    },
  });
}
