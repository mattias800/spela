import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { FeaturedGame, ExploreRowsResponse } from "@/types/api";

export function useExploreFeatured() {
  return useQuery({
    queryKey: ["explore", "featured"],
    queryFn: () => api.get<FeaturedGame[]>("/explore/featured"),
  });
}

export function useExploreRows() {
  return useQuery({
    queryKey: ["explore", "rows"],
    queryFn: () => api.get<ExploreRowsResponse>("/explore/rows"),
  });
}
