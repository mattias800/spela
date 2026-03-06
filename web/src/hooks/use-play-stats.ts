import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { PlayStatsEntry } from "@/types/api";

export function usePlayStats() {
  return useQuery({
    queryKey: ["user", "play-stats"],
    queryFn: () => api.get<PlayStatsEntry[]>("/user/play-stats"),
    staleTime: 60_000,
  });
}
