import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { TopListGame, TopRatedGame, LongestGame } from "@/types/api";

export function useTopRated() {
  return useQuery({
    queryKey: ["top-lists", "top-rated"],
    queryFn: () => api.get<TopListGame[]>("/top-lists/top-rated"),
  });
}

export function useTopRatedCritics() {
  return useQuery({
    queryKey: ["top-lists", "top-rated-critics"],
    queryFn: () => api.get<TopListGame[]>("/top-lists/top-rated-critics"),
  });
}

export function useLongestGames() {
  return useQuery({
    queryKey: ["top-lists", "longest"],
    queryFn: () => api.get<LongestGame[]>("/top-lists/longest"),
  });
}

export function useTopRatedGlobal() {
  return useQuery({
    queryKey: ["top-rated-global"],
    queryFn: () => api.get<TopRatedGame[]>("/top-rated"),
  });
}
