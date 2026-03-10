import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  FeaturedGame,
  FeaturedSeries,
  SeriesDetail,
  GameSeriesLink,
  GameFranchiseLink,
  ExploreRowsResponse,
  Theme,
  Keyword,
  GamesResponse,
  MoodDefinition,
  Game,
  ForYouResponse,
  TasteProfile,
  PlayersLikeYouResponse,
  DeveloperListResponse,
  DeveloperDetailResponse,
  PublisherDetailResponse,
  DeveloperSpotlightResponse,
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

export function useFeaturedSeries() {
  return useQuery({
    queryKey: ["explore", "series", "featured"],
    queryFn: () => api.get<FeaturedSeries[]>("/explore/series/featured"),
  });
}

export function useSeriesDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["series", id],
    queryFn: () => api.get<SeriesDetail>(`/series/${id}`),
    enabled: !!id,
  });
}

export function useGameSeries(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "series"],
    queryFn: () => api.get<GameSeriesLink[]>(`/games/${gameId}/series`),
    enabled: !!gameId,
  });
}

export function useGameFranchises(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "franchises"],
    queryFn: () =>
      api.get<GameFranchiseLink[]>(`/games/${gameId}/franchises`),
    enabled: !!gameId,
  });
}

export function useMoods() {
  return useQuery({
    queryKey: ["explore", "moods"],
    queryFn: () => api.get<MoodDefinition[]>("/explore/moods"),
  });
}

export function useMoodGames(mood: string | undefined) {
  return useQuery({
    queryKey: ["explore", "mood", mood],
    queryFn: () => api.get<Game[]>(`/explore/mood/${mood}`),
    enabled: !!mood,
  });
}

export function useSurpriseGame() {
  return useQuery({
    queryKey: ["explore", "surprise"],
    queryFn: () => api.get<Game>("/explore/surprise"),
    enabled: false,
  });
}

export function useForYou() {
  return useQuery({
    queryKey: ["explore", "for-you"],
    queryFn: () => api.get<ForYouResponse>("/explore/for-you"),
  });
}

export function useTasteProfile() {
  return useQuery({
    queryKey: ["user", "taste-profile"],
    queryFn: () => api.get<TasteProfile>("/user/taste-profile"),
  });
}

export function usePlayersLikeYou() {
  return useQuery({
    queryKey: ["explore", "players-like-you"],
    queryFn: () =>
      api.get<PlayersLikeYouResponse>("/explore/players-like-you"),
  });
}

export function useDevelopers() {
  return useQuery({
    queryKey: ["explore", "developers"],
    queryFn: () => api.get<DeveloperListResponse>("/explore/developers"),
  });
}

export function useDeveloperDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "developers", name],
    queryFn: () =>
      api.get<DeveloperDetailResponse>(
        `/explore/developers/${encodeURIComponent(name)}`,
      ),
    enabled: !!name,
  });
}

export function usePublisherDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "publishers", name],
    queryFn: () =>
      api.get<PublisherDetailResponse>(
        `/explore/publishers/${encodeURIComponent(name)}`,
      ),
    enabled: !!name,
  });
}

export function useDeveloperSpotlight() {
  return useQuery({
    queryKey: ["explore", "developers", "spotlight"],
    queryFn: () =>
      api.get<DeveloperSpotlightResponse>("/explore/developers/spotlight"),
  });
}
