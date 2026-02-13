import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  MostPlayedGamesResponse,
  MostActivePlayersResponse,
  UserStats,
} from "@/types/api";

export function useMostPlayedGames() {
  return useQuery({
    queryKey: ["stats", "most-played"],
    queryFn: () => api.get<MostPlayedGamesResponse>("/stats/most-played"),
  });
}

export function useMostActivePlayers() {
  return useQuery({
    queryKey: ["stats", "most-active-players"],
    queryFn: () =>
      api.get<MostActivePlayersResponse>("/stats/most-active-players"),
  });
}

export function useUserStats() {
  return useQuery({
    queryKey: ["user-stats"],
    queryFn: () => api.get<UserStats>("/user/stats"),
  });
}
