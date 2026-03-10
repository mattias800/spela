import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  FeaturedGame,
  ExploreRowsResponse,
  Theme,
  Keyword,
  GamesResponse,
} from "@/types/api";

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

export function useThemes() {
  return useQuery({
    queryKey: ["themes"],
    queryFn: () => api.get<Theme[]>("/themes"),
  });
}

export function useThemeGames(
  themeId: string | undefined,
  page: number,
  pageSize: number,
) {
  return useQuery({
    queryKey: ["themes", themeId, "games", page, pageSize],
    queryFn: () =>
      api.get<GamesResponse>(
        `/themes/${themeId}/games?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!themeId,
  });
}

export function useKeywords(limit = 30) {
  return useQuery({
    queryKey: ["keywords", limit],
    queryFn: () => api.get<Keyword[]>(`/keywords?limit=${limit}`),
  });
}

export function useKeywordGames(
  keywordId: string | undefined,
  page: number,
  pageSize: number,
) {
  return useQuery({
    queryKey: ["keywords", keywordId, "games", page, pageSize],
    queryFn: () =>
      api.get<GamesResponse>(
        `/keywords/${keywordId}/games?page=${page}&pageSize=${pageSize}`,
      ),
    enabled: !!keywordId,
  });
}
