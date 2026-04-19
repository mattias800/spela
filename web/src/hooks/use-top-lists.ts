import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";

export function useTopRated() {
  return useQuery({
    queryKey: ["top-lists", "top-rated"],
    queryFn: () => unwrap(typedApi.GET("/api/top-lists/top-rated")),
  });
}

export function useTopRatedCritics() {
  return useQuery({
    queryKey: ["top-lists", "top-rated-critics"],
    queryFn: () => unwrap(typedApi.GET("/api/top-lists/top-rated-critics")),
  });
}

export function useLongestGames() {
  return useQuery({
    queryKey: ["top-lists", "longest"],
    queryFn: () => unwrap(typedApi.GET("/api/top-lists/longest")),
  });
}

export function useTopRatedGlobal() {
  return useQuery({
    queryKey: ["top-rated-global"],
    queryFn: () => unwrap(typedApi.GET("/api/top-rated")),
  });
}
