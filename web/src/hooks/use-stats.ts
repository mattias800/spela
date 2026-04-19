import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useMostPlayedGames() {
  return useQuery({
    queryKey: ["stats", "most-played"],
    queryFn: () => unwrap(typedApi.GET("/api/stats/most-played")),
  });
}

export function useMostActivePlayers() {
  return useQuery({
    queryKey: ["stats", "most-active-players"],
    queryFn: () => unwrap(typedApi.GET("/api/stats/most-active-players")),
  });
}

export function useUserStats() {
  return useQuery({
    queryKey: ["user-stats"],
    queryFn: () => unwrap(typedApi.GET("/api/user/stats")),
  });
}
