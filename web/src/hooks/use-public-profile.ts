import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function usePublicProfile(userId: string) {
  return useQuery({
    queryKey: ["users", userId, "profile"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/users/{id}/profile", {
          params: { path: { id: userId } },
        }),
      ),
    enabled: !!userId,
  });
}
