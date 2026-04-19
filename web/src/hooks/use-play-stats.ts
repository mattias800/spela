import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function usePlayStats() {
  return useQuery({
    queryKey: ["user", "play-stats"],
    queryFn: () => unwrap(typedApi.GET("/api/user/play-stats")),
    staleTime: 60_000,
  });
}
