import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { TopListGame, LongestGame } from "@/types/api";

export function useTopRated() {
  return useQuery({
    queryKey: ["top-lists", "top-rated"],
    queryFn: () => api.get<TopListGame[]>("/top-lists/top-rated"),
  });
}

export function useLongestGames() {
  return useQuery({
    queryKey: ["top-lists", "longest"],
    queryFn: () => api.get<LongestGame[]>("/top-lists/longest"),
  });
}
