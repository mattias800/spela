import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type {
  FeaturedGame,
  FeaturedSeries,
  SeriesDetail,
  FranchiseDetail,
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
  WizardResponse,
  WizardResultsResponse,
  ExplorerBadgesResponse,
  CompletionistMapResponse,
} from "@/types/api";

// Cache durations for explore data that changes infrequently.
// Prevents re-fetching 25+ queries every time the user navigates back.
const STALE_LONG = 5 * 60 * 1000; // 5 min — static catalog data (themes, keywords, series)
const STALE_MEDIUM = 2 * 60 * 1000; // 2 min — personalized data (for-you, badges)
const STALE_SHORT = 30 * 1000; // 30s — live data (trending, active-now)

// --- Tier 1: Above-the-fold, always loaded ---

export function useExploreFeatured() {
  return useQuery({
    queryKey: ["explore", "featured"],
    queryFn: () => api.get<FeaturedGame[]>("/explore/featured"),
    staleTime: STALE_LONG,
  });
}

export function useExploreRows() {
  return useQuery({
    queryKey: ["explore", "rows"],
    queryFn: () => api.get<ExploreRowsResponse>("/explore/rows"),
    staleTime: STALE_LONG,
  });
}

export function useConsoleHighlights() {
  return useQuery({
    queryKey: ["console-highlights"],
    queryFn: () =>
      api.get<ConsoleHighlightsResponse>("/explore/console-highlights"),
    staleTime: STALE_LONG,
  });
}

export function useMoods() {
  return useQuery({
    queryKey: ["explore", "moods"],
    queryFn: () => api.get<MoodDefinition[]>("/explore/moods"),
    staleTime: STALE_LONG,
  });
}

// --- Tier 2: Below-the-fold, lazy-loaded when scrolled into view ---

export function useForYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "for-you"],
    queryFn: () => api.get<ForYouResponse>("/explore/for-you"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function usePlayersLikeYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "players-like-you"],
    queryFn: () =>
      api.get<PlayersLikeYouResponse>("/explore/players-like-you"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useTrending(enabled = true) {
  return useQuery({
    queryKey: ["explore", "trending"],
    queryFn: () => api.get<TrendingResponse>("/explore/trending"),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useCommunityTop(enabled = true) {
  return useQuery({
    queryKey: ["explore", "community-top"],
    queryFn: () => api.get<CommunityTopResponse>("/explore/community-top"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useCultClassics(enabled = true) {
  return useQuery({
    queryKey: ["explore", "cult-classics"],
    queryFn: () => api.get<CultClassicsResponse>("/explore/cult-classics"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useActiveNow(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-now"],
    queryFn: () => api.get<ActiveNowResponse>("/explore/active-now"),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useRecentlyReviewed(enabled = true) {
  return useQuery({
    queryKey: ["explore", "recently-reviewed"],
    queryFn: () =>
      api.get<RecentlyReviewedResponse>("/explore/recently-reviewed"),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useOnThisDay(enabled = true) {
  return useQuery({
    queryKey: ["explore", "on-this-day"],
    queryFn: () => api.get<OnThisDayResponse>("/explore/on-this-day"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useBestOfYear(year: number, enabled = true) {
  return useQuery({
    queryKey: ["explore", "best-of-year", year],
    queryFn: () => api.get<BestOfYearResponse>(`/explore/best-of-year/${year}`),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useYourAnniversaries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "your-anniversaries"],
    queryFn: () =>
      api.get<YourAnniversariesResponse>("/explore/your-anniversaries"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useDecade(decade: string, enabled = true) {
  return useQuery({
    queryKey: ["explore", "decades", decade],
    queryFn: () => api.get<DecadeResponse>(`/explore/decades/${decade}`),
    staleTime: STALE_LONG,
    enabled: !!decade && enabled,
  });
}

export function useEasyToComplete(enabled = true) {
  return useQuery({
    queryKey: ["explore", "easy-to-complete"],
    queryFn: () => api.get<EasyToCompleteResponse>("/explore/easy-to-complete"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useHardestGames(enabled = true) {
  return useQuery({
    queryKey: ["explore", "hardest-games"],
    queryFn: () => api.get<HardestGamesResponse>("/explore/hardest-games"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useAlmostDone(enabled = true) {
  return useQuery({
    queryKey: ["explore", "almost-done"],
    queryFn: () => api.get<AlmostDoneResponse>("/explore/almost-done"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useFreshChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "fresh-challenges"],
    queryFn: () => api.get<FreshChallengesResponse>("/explore/fresh-challenges"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useActiveChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-challenges"],
    queryFn: () => api.get<ActiveChallengesResponse>("/explore/active-challenges"),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useThemes(enabled = true) {
  return useQuery({
    queryKey: ["themes"],
    queryFn: () => api.get<Theme[]>("/themes"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useKeywords(limit = 30, enabled = true) {
  return useQuery({
    queryKey: ["keywords", limit],
    queryFn: () => api.get<Keyword[]>(`/keywords?limit=${limit}`),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useFeaturedSeries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "series", "featured"],
    queryFn: () => api.get<FeaturedSeries[]>("/explore/series/featured"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useDeveloperSpotlight(enabled = true) {
  return useQuery({
    queryKey: ["explore", "developers", "spotlight"],
    queryFn: () =>
      api.get<DeveloperSpotlightResponse>("/explore/developers/spotlight"),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useArtworkGallery(page: number, enabled = true) {
  return useQuery({
    queryKey: ["artwork-gallery", page],
    queryFn: () =>
      api.get<ArtworkGalleryResponse>(`/explore/artwork?page=${page}`),
    staleTime: STALE_LONG,
    enabled,
  });
}

// --- Detail / sub-page hooks (not used on explore landing) ---

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
    staleTime: STALE_LONG,
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
    staleTime: STALE_LONG,
  });
}

export function useSeriesDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["series", id],
    queryFn: () => api.get<SeriesDetail>(`/series/${id}`),
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useFranchiseDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["franchises", id],
    queryFn: () => api.get<FranchiseDetail>(`/franchises/${id}`),
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useGameSeries(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "series"],
    queryFn: () => api.get<GameSeriesLink[]>(`/games/${gameId}/series`),
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useGameFranchises(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "franchises"],
    queryFn: () =>
      api.get<GameFranchiseLink[]>(`/games/${gameId}/franchises`),
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useMoodGames(mood: string | undefined) {
  return useQuery({
    queryKey: ["explore", "mood", mood],
    queryFn: () => api.get<Game[]>(`/explore/mood/${mood}`),
    enabled: !!mood,
    staleTime: STALE_MEDIUM,
  });
}

export function useSurpriseGame() {
  return useQuery({
    queryKey: ["explore", "surprise"],
    queryFn: () => api.get<Game>("/explore/surprise"),
    enabled: false,
  });
}

export function useTasteProfile() {
  return useQuery({
    queryKey: ["user", "taste-profile"],
    queryFn: () => api.get<TasteProfile>("/user/taste-profile"),
    staleTime: STALE_MEDIUM,
  });
}

export function useDevelopers() {
  return useQuery({
    queryKey: ["explore", "developers"],
    queryFn: () => api.get<DeveloperListResponse>("/explore/developers"),
    staleTime: STALE_LONG,
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
    staleTime: STALE_LONG,
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
    staleTime: STALE_LONG,
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
    staleTime: STALE_LONG,
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
    staleTime: STALE_LONG,
  });
}

export function useCoverGallery(page: number, consoleFilter?: string) {
  const params = new URLSearchParams({ page: String(page) });
  if (consoleFilter) params.set("console", consoleFilter);
  return useQuery({
    queryKey: ["cover-gallery", page, consoleFilter],
    queryFn: () =>
      api.get<CoverGalleryResponse>(`/explore/covers?${params}`),
    staleTime: STALE_LONG,
  });
}

// --- Phase 14: Wild Features ---

export function useWizardSteps() {
  return useQuery({
    queryKey: ["explore", "wizard"],
    queryFn: () => api.get<WizardResponse>("/explore/wizard"),
    staleTime: STALE_LONG,
  });
}

export function useWizardResults(
  mood: string,
  era: string,
  vibe: string,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ["explore", "wizard", "results", mood, era, vibe],
    queryFn: () =>
      api.get<WizardResultsResponse>(
        `/explore/wizard/results?mood=${mood}&era=${era}&vibe=${vibe}`,
      ),
    enabled,
  });
}

export function useExplorerBadges() {
  return useQuery({
    queryKey: ["user", "explorer-badges"],
    queryFn: () => api.get<ExplorerBadgesResponse>("/user/explorer-badges"),
    staleTime: STALE_MEDIUM,
  });
}

export function useCompletionistMap() {
  return useQuery({
    queryKey: ["user", "completionist-map"],
    queryFn: () => api.get<CompletionistMapResponse>("/user/completionist-map"),
    staleTime: STALE_MEDIUM,
  });
}

