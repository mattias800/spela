import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
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
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/featured"));
      return data as FeaturedGame[] | undefined;
    },
    staleTime: STALE_LONG,
  });
}

export function useExploreRows() {
  return useQuery({
    queryKey: ["explore", "rows"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/rows"));
      return data as ExploreRowsResponse | undefined;
    },
    staleTime: STALE_LONG,
  });
}

export function useConsoleHighlights() {
  return useQuery({
    queryKey: ["console-highlights"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/console-highlights"),
      );
      return data as ConsoleHighlightsResponse | undefined;
    },
    staleTime: STALE_LONG,
  });
}

export function useMoods() {
  return useQuery({
    queryKey: ["explore", "moods"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/moods"));
      return data as MoodDefinition[] | undefined;
    },
    staleTime: STALE_LONG,
  });
}

// --- Tier 2: Below-the-fold, lazy-loaded when scrolled into view ---

export function useForYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "for-you"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/for-you"));
      return data as ForYouResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function usePlayersLikeYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "players-like-you"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/players-like-you"),
      );
      return data as PlayersLikeYouResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useTrending(enabled = true) {
  return useQuery({
    queryKey: ["explore", "trending"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/trending"));
      return data as TrendingResponse | undefined;
    },
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useCommunityTop(enabled = true) {
  return useQuery({
    queryKey: ["explore", "community-top"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/community-top"));
      return data as CommunityTopResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useCultClassics(enabled = true) {
  return useQuery({
    queryKey: ["explore", "cult-classics"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/cult-classics"));
      return data as CultClassicsResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useActiveNow(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-now"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/active-now"));
      return data as ActiveNowResponse | undefined;
    },
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useRecentlyReviewed(enabled = true) {
  return useQuery({
    queryKey: ["explore", "recently-reviewed"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/recently-reviewed"),
      );
      return data as RecentlyReviewedResponse | undefined;
    },
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useOnThisDay(enabled = true) {
  return useQuery({
    queryKey: ["explore", "on-this-day"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/on-this-day"));
      return data as OnThisDayResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useBestOfYear(year: number, enabled = true) {
  return useQuery({
    queryKey: ["explore", "best-of-year", year],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/best-of-year/{year}", {
          params: { path: { year: String(year) } },
        }),
      );
      return data as BestOfYearResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useYourAnniversaries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "your-anniversaries"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/your-anniversaries"),
      );
      return data as YourAnniversariesResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useDecade(decade: string, enabled = true) {
  return useQuery({
    queryKey: ["explore", "decades", decade],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/decades/{decade}", {
          params: { path: { decade } },
        }),
      );
      return data as DecadeResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled: !!decade && enabled,
  });
}

export function useEasyToComplete(enabled = true) {
  return useQuery({
    queryKey: ["explore", "easy-to-complete"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/easy-to-complete"),
      );
      return data as EasyToCompleteResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useHardestGames(enabled = true) {
  return useQuery({
    queryKey: ["explore", "hardest-games"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/hardest-games"));
      return data as HardestGamesResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useAlmostDone(enabled = true) {
  return useQuery({
    queryKey: ["explore", "almost-done"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/almost-done"));
      return data as AlmostDoneResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useFreshChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "fresh-challenges"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/fresh-challenges"),
      );
      return data as FreshChallengesResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useActiveChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-challenges"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/active-challenges"),
      );
      return data as ActiveChallengesResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useThemes(enabled = true) {
  return useQuery({
    queryKey: ["themes"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/themes"));
      return data as Theme[] | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useKeywords(limit = 30, enabled = true) {
  return useQuery({
    queryKey: ["keywords", limit],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/keywords", { params: { query: { limit } } }),
      );
      return data as Keyword[] | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useFeaturedSeries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "series", "featured"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/series/featured"));
      return data as FeaturedSeries[] | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useDeveloperSpotlight(enabled = true) {
  return useQuery({
    queryKey: ["explore", "developers", "spotlight"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/developers/spotlight"),
      );
      return data as DeveloperSpotlightResponse | undefined;
    },
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useArtworkGallery(page: number, enabled = true) {
  return useQuery({
    queryKey: ["artwork-gallery", page],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/artwork", {
          params: { query: { page } },
        }),
      );
      return data as ArtworkGalleryResponse | undefined;
    },
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
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/themes/{id}/games", {
          params: {
            path: { id: themeId as string },
            query: { page, pageSize },
          },
        }),
      );
      return data as GamesResponse | undefined;
    },
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
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/keywords/{id}/games", {
          params: {
            path: { id: keywordId as string },
            query: { page, pageSize },
          },
        }),
      );
      return data as GamesResponse | undefined;
    },
    enabled: !!keywordId,
    staleTime: STALE_LONG,
  });
}

export function useSeriesDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["series", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/series/{id}", {
          params: { path: { id: id as string } },
        }),
      );
      return data as SeriesDetail | undefined;
    },
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useFranchiseDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["franchises", id],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/franchises/{id}", {
          params: { path: { id: id as string } },
        }),
      );
      return data as FranchiseDetail | undefined;
    },
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useGameSeries(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "series"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/series", {
          params: { path: { id: gameId as string } },
        }),
      );
      return data as GameSeriesLink[] | undefined;
    },
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useGameFranchises(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "franchises"],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/games/{id}/franchises", {
          params: { path: { id: gameId as string } },
        }),
      );
      return data as GameFranchiseLink[] | undefined;
    },
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useMoodGames(mood: string | undefined) {
  return useQuery({
    queryKey: ["explore", "mood", mood],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/mood/{mood}", {
          params: { path: { mood: mood as string } },
        }),
      );
      return data as Game[] | undefined;
    },
    enabled: !!mood,
    staleTime: STALE_MEDIUM,
  });
}

export function useSurpriseGame() {
  return useQuery({
    queryKey: ["explore", "surprise"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/surprise"));
      return data as Game | undefined;
    },
    enabled: false,
  });
}

export function useTasteProfile() {
  return useQuery({
    queryKey: ["user", "taste-profile"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/taste-profile"));
      return data as TasteProfile | undefined;
    },
    staleTime: STALE_MEDIUM,
  });
}

export function useDevelopers() {
  return useQuery({
    queryKey: ["explore", "developers"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/developers"));
      return data as DeveloperListResponse | undefined;
    },
    staleTime: STALE_LONG,
  });
}

export function useDeveloperDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "developers", name],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/developers/{name}", {
          params: { path: { name } },
        }),
      );
      return data as DeveloperDetailResponse | undefined;
    },
    enabled: !!name,
    staleTime: STALE_LONG,
  });
}

export function usePublisherDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "publishers", name],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/publishers/{name}", {
          params: { path: { name } },
        }),
      );
      return data as PublisherDetailResponse | undefined;
    },
    enabled: !!name,
    staleTime: STALE_LONG,
  });
}

export function useConsoleShowcase(consoleId: string) {
  return useQuery({
    queryKey: ["console-showcase", consoleId],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/consoles/{id}/showcase", {
          params: { path: { id: consoleId } },
        }),
      );
      return data as ConsoleShowcase | undefined;
    },
    enabled: !!consoleId,
    staleTime: STALE_LONG,
  });
}

export function useScreenshotGallery(
  page: number,
  filters?: { console?: string; genre?: string },
) {
  return useQuery({
    queryKey: ["screenshot-gallery", page, filters],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/screenshots", {
          params: {
            query: {
              page,
              ...(filters?.console ? { console: filters.console } : {}),
              ...(filters?.genre ? { genre: filters.genre } : {}),
            },
          },
        }),
      );
      return data as ScreenshotGalleryResponse | undefined;
    },
    staleTime: STALE_LONG,
  });
}

export function useCoverGallery(page: number, consoleFilter?: string) {
  return useQuery({
    queryKey: ["cover-gallery", page, consoleFilter],
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/covers", {
          params: {
            query: {
              page,
              ...(consoleFilter ? { console: consoleFilter } : {}),
            },
          },
        }),
      );
      return data as CoverGalleryResponse | undefined;
    },
    staleTime: STALE_LONG,
  });
}

// --- Phase 14: Wild Features ---

export function useWizardSteps() {
  return useQuery({
    queryKey: ["explore", "wizard"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/explore/wizard"));
      return data as WizardResponse | undefined;
    },
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
    queryFn: async () => {
      const data = await unwrap(
        typedApi.GET("/api/explore/wizard/results", {
          params: { query: { mood, era, vibe } },
        }),
      );
      return data as WizardResultsResponse | undefined;
    },
    enabled,
  });
}

export function useExplorerBadges() {
  return useQuery({
    queryKey: ["user", "explorer-badges"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/explorer-badges"));
      return data as ExplorerBadgesResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
  });
}

export function useCompletionistMap() {
  return useQuery({
    queryKey: ["user", "completionist-map"],
    queryFn: async () => {
      const data = await unwrap(typedApi.GET("/api/user/completionist-map"));
      return data as CompletionistMapResponse | undefined;
    },
    staleTime: STALE_MEDIUM,
  });
}
