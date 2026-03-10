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
  ConsoleShowcase,
  ConsoleHighlightsResponse,
  ScreenshotGalleryResponse,
  ArtworkGalleryResponse,
  CoverGalleryResponse,
  TrendingResponse,
  CommunityTopResponse,
  CultClassicsResponse,
  RecentlyReviewedResponse,
  ActiveNowResponse,
  OnThisDayResponse,
  BestOfYearResponse,
  YourAnniversariesResponse,
  DecadeResponse,
  EasyToCompleteResponse,
  HardestGamesResponse,
  AlmostDoneResponse,
  FreshChallengesResponse,
  ActiveChallengesResponse,
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

export function useConsoleShowcase(consoleId: string) {
  return useQuery({
    queryKey: ["console-showcase", consoleId],
    queryFn: () =>
      api.get<ConsoleShowcase>(
        `/explore/consoles/${encodeURIComponent(consoleId)}/showcase`,
      ),
    enabled: !!consoleId,
  });
}

export function useConsoleHighlights() {
  return useQuery({
    queryKey: ["console-highlights"],
    queryFn: () =>
      api.get<ConsoleHighlightsResponse>("/explore/console-highlights"),
  });
}

export function useScreenshotGallery(
  page: number,
  filters?: { console?: string; genre?: string },
) {
  const params = new URLSearchParams({ page: String(page) });
  if (filters?.console) params.set("console", filters.console);
  if (filters?.genre) params.set("genre", filters.genre);
  return useQuery({
    queryKey: ["screenshot-gallery", page, filters],
    queryFn: () =>
      api.get<ScreenshotGalleryResponse>(
        `/explore/screenshots?${params}`,
      ),
  });
}

export function useArtworkGallery(page: number) {
  return useQuery({
    queryKey: ["artwork-gallery", page],
    queryFn: () =>
      api.get<ArtworkGalleryResponse>(`/explore/artwork?page=${page}`),
  });
}

export function useCoverGallery(page: number, consoleFilter?: string) {
  const params = new URLSearchParams({ page: String(page) });
  if (consoleFilter) params.set("console", consoleFilter);
  return useQuery({
    queryKey: ["cover-gallery", page, consoleFilter],
    queryFn: () =>
      api.get<CoverGalleryResponse>(`/explore/covers?${params}`),
  });
}

export function useTrending() {
  return useQuery({
    queryKey: ["explore", "trending"],
    queryFn: () => api.get<TrendingResponse>("/explore/trending"),
  });
}

export function useCommunityTop() {
  return useQuery({
    queryKey: ["explore", "community-top"],
    queryFn: () => api.get<CommunityTopResponse>("/explore/community-top"),
  });
}

export function useCultClassics() {
  return useQuery({
    queryKey: ["explore", "cult-classics"],
    queryFn: () => api.get<CultClassicsResponse>("/explore/cult-classics"),
  });
}

export function useRecentlyReviewed() {
  return useQuery({
    queryKey: ["explore", "recently-reviewed"],
    queryFn: () =>
      api.get<RecentlyReviewedResponse>("/explore/recently-reviewed"),
  });
}

export function useActiveNow() {
  return useQuery({
    queryKey: ["explore", "active-now"],
    queryFn: () => api.get<ActiveNowResponse>("/explore/active-now"),
  });
}

export function useOnThisDay() {
  return useQuery({
    queryKey: ["explore", "on-this-day"],
    queryFn: () => api.get<OnThisDayResponse>("/explore/on-this-day"),
  });
}

export function useBestOfYear(year: number) {
  return useQuery({
    queryKey: ["explore", "best-of-year", year],
    queryFn: () => api.get<BestOfYearResponse>(`/explore/best-of-year/${year}`),
  });
}

export function useYourAnniversaries() {
  return useQuery({
    queryKey: ["explore", "your-anniversaries"],
    queryFn: () =>
      api.get<YourAnniversariesResponse>("/explore/your-anniversaries"),
  });
}

export function useDecade(decade: string) {
  return useQuery({
    queryKey: ["explore", "decades", decade],
    queryFn: () => api.get<DecadeResponse>(`/explore/decades/${decade}`),
    enabled: !!decade,
  });
}

export function useEasyToComplete() {
  return useQuery({
    queryKey: ["explore", "easy-to-complete"],
    queryFn: () => api.get<EasyToCompleteResponse>("/explore/easy-to-complete"),
  });
}

export function useHardestGames() {
  return useQuery({
    queryKey: ["explore", "hardest-games"],
    queryFn: () => api.get<HardestGamesResponse>("/explore/hardest-games"),
  });
}

export function useAlmostDone() {
  return useQuery({
    queryKey: ["explore", "almost-done"],
    queryFn: () => api.get<AlmostDoneResponse>("/explore/almost-done"),
  });
}

export function useFreshChallenges() {
  return useQuery({
    queryKey: ["explore", "fresh-challenges"],
    queryFn: () => api.get<FreshChallengesResponse>("/explore/fresh-challenges"),
  });
}

export function useActiveChallenges() {
  return useQuery({
    queryKey: ["explore", "active-challenges"],
    queryFn: () => api.get<ActiveChallengesResponse>("/explore/active-challenges"),
  });
}
