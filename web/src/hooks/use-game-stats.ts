import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { GameStats } from "@/types/api";

export function useGameStats(gameId: string) {
  return useQuery({
    queryKey: ["game-stats", gameId],
    queryFn: () => api.get<GameStats>(`/games/${gameId}/stats`),
    enabled: !!gameId,
  });
}
