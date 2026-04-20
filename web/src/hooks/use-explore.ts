import { useQuery } from "@tanstack/react-query";
import { typedApi, unwrap } from "@/lib/api-client";
import { invariant } from "@/lib/invariant";

// Cache durations for explore data that changes infrequently.
// Prevents re-fetching 25+ queries every time the user navigates back.
const STALE_LONG = 5 * 60 * 1000; // 5 min — static catalog data (themes, keywords, series)
const STALE_MEDIUM = 2 * 60 * 1000; // 2 min — personalized data (for-you, badges)
const STALE_SHORT = 30 * 1000; // 30s — live data (trending, active-now)

// --- Tier 1: Above-the-fold, always loaded ---

export function useExploreFeatured() {
  return useQuery({
    queryKey: ["explore", "featured"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/featured")),
    staleTime: STALE_LONG,
  });
}

export function useExploreRows() {
  return useQuery({
    queryKey: ["explore", "rows"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/rows")),
    staleTime: STALE_LONG,
  });
}

export function useConsoleHighlights() {
  return useQuery({
    queryKey: ["console-highlights"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/console-highlights")),
    staleTime: STALE_LONG,
  });
}

export function useMoods() {
  return useQuery({
    queryKey: ["explore", "moods"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/moods")),
    staleTime: STALE_LONG,
  });
}

// --- Tier 2: Below-the-fold, lazy-loaded when scrolled into view ---

export function useForYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "for-you"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/for-you")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function usePlayersLikeYou(enabled = true) {
  return useQuery({
    queryKey: ["explore", "players-like-you"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/players-like-you")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useTrending(enabled = true) {
  return useQuery({
    queryKey: ["explore", "trending"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/trending")),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useCommunityTop(enabled = true) {
  return useQuery({
    queryKey: ["explore", "community-top"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/community-top")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useCultClassics(enabled = true) {
  return useQuery({
    queryKey: ["explore", "cult-classics"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/cult-classics")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useActiveNow(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-now"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/active-now")),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useRecentlyReviewed(enabled = true) {
  return useQuery({
    queryKey: ["explore", "recently-reviewed"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/recently-reviewed")),
    staleTime: STALE_SHORT,
    enabled,
  });
}

export function useOnThisDay(enabled = true) {
  return useQuery({
    queryKey: ["explore", "on-this-day"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/on-this-day")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useBestOfYear(year: number, enabled = true) {
  return useQuery({
    queryKey: ["explore", "best-of-year", year],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/best-of-year/{year}", {
          params: { path: { year: String(year) } },
        }),
      ),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useYourAnniversaries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "your-anniversaries"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/your-anniversaries")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useDecade(decade: string, enabled = true) {
  return useQuery({
    queryKey: ["explore", "decades", decade],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/decades/{decade}", {
          params: { path: { decade } },
        }),
      ),
    staleTime: STALE_LONG,
    enabled: !!decade && enabled,
  });
}

export function useEasyToComplete(enabled = true) {
  return useQuery({
    queryKey: ["explore", "easy-to-complete"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/easy-to-complete")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useHardestGames(enabled = true) {
  return useQuery({
    queryKey: ["explore", "hardest-games"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/hardest-games")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useAlmostDone(enabled = true) {
  return useQuery({
    queryKey: ["explore", "almost-done"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/almost-done")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useFreshChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "fresh-challenges"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/fresh-challenges")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useActiveChallenges(enabled = true) {
  return useQuery({
    queryKey: ["explore", "active-challenges"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/active-challenges")),
    staleTime: STALE_MEDIUM,
    enabled,
  });
}

export function useThemes(enabled = true) {
  return useQuery({
    queryKey: ["themes"],
    queryFn: () => unwrap(typedApi.GET("/api/themes")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useKeywords(limit = 30, enabled = true) {
  return useQuery({
    queryKey: ["keywords", limit],
    queryFn: () =>
      unwrap(typedApi.GET("/api/keywords", { params: { query: { limit } } })),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useFeaturedSeries(enabled = true) {
  return useQuery({
    queryKey: ["explore", "series", "featured"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/series/featured")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useDeveloperSpotlight(enabled = true) {
  return useQuery({
    queryKey: ["explore", "developers", "spotlight"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/developers/spotlight")),
    staleTime: STALE_LONG,
    enabled,
  });
}

export function useArtworkGallery(page: number, enabled = true) {
  return useQuery({
    queryKey: ["artwork-gallery", page],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/artwork", {
          params: { query: { page } },
        }),
      ),
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
      unwrap(
        typedApi.GET("/api/themes/{id}/games", {
          params: {
            path: { id: invariant(themeId, "themeId") },
            query: { page, pageSize },
          },
        }),
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
      unwrap(
        typedApi.GET("/api/keywords/{id}/games", {
          params: {
            path: { id: invariant(keywordId, "keywordId") },
            query: { page, pageSize },
          },
        }),
      ),
    enabled: !!keywordId,
    staleTime: STALE_LONG,
  });
}

export function useSeriesDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["series", id],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/series/{id}", {
          params: { path: { id: invariant(id, "id") } },
        }),
      ),
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useFranchiseDetail(id: string | undefined) {
  return useQuery({
    queryKey: ["franchises", id],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/franchises/{id}", {
          params: { path: { id: invariant(id, "id") } },
        }),
      ),
    enabled: !!id,
    staleTime: STALE_LONG,
  });
}

export function useGameSeries(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "series"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/series", {
          params: { path: { id: invariant(gameId, "gameId") } },
        }),
      ),
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useGameFranchises(gameId: string | undefined) {
  return useQuery({
    queryKey: ["games", gameId, "franchises"],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/games/{id}/franchises", {
          params: { path: { id: invariant(gameId, "gameId") } },
        }),
      ),
    enabled: !!gameId,
    staleTime: STALE_LONG,
  });
}

export function useMoodGames(mood: string | undefined) {
  return useQuery({
    queryKey: ["explore", "mood", mood],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/mood/{mood}", {
          params: { path: { mood: invariant(mood, "mood") } },
        }),
      ),
    enabled: !!mood,
    staleTime: STALE_MEDIUM,
  });
}

export function useSurpriseGame() {
  return useQuery({
    queryKey: ["explore", "surprise"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/surprise")),
    enabled: false,
  });
}

export function useTasteProfile() {
  return useQuery({
    queryKey: ["user", "taste-profile"],
    queryFn: () => unwrap(typedApi.GET("/api/user/taste-profile")),
    staleTime: STALE_MEDIUM,
  });
}

export function useDevelopers() {
  return useQuery({
    queryKey: ["explore", "developers"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/developers")),
    staleTime: STALE_LONG,
  });
}

export function useDeveloperDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "developers", name],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/developers/{name}", {
          params: { path: { name } },
        }),
      ),
    enabled: !!name,
    staleTime: STALE_LONG,
  });
}

export function usePublisherDetail(name: string) {
  return useQuery({
    queryKey: ["explore", "publishers", name],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/publishers/{name}", {
          params: { path: { name } },
        }),
      ),
    enabled: !!name,
    staleTime: STALE_LONG,
  });
}

export function useConsoleShowcase(consoleId: string) {
  return useQuery({
    queryKey: ["console-showcase", consoleId],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/consoles/{id}/showcase", {
          params: { path: { id: consoleId } },
        }),
      ),
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
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/screenshots", {
          params: {
            query: {
              page,
              ...(filters?.console ? { console: filters.console } : {}),
              ...(filters?.genre ? { genre: filters.genre } : {}),
            },
          },
        }),
      ),
    staleTime: STALE_LONG,
  });
}

export function useCoverGallery(page: number, consoleFilter?: string) {
  return useQuery({
    queryKey: ["cover-gallery", page, consoleFilter],
    queryFn: () =>
      unwrap(
        typedApi.GET("/api/explore/covers", {
          params: {
            query: {
              page,
              ...(consoleFilter ? { console: consoleFilter } : {}),
            },
          },
        }),
      ),
    staleTime: STALE_LONG,
  });
}

// --- Phase 14: Wild Features ---

export function useWizardSteps() {
  return useQuery({
    queryKey: ["explore", "wizard"],
    queryFn: () => unwrap(typedApi.GET("/api/explore/wizard")),
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
      unwrap(
        typedApi.GET("/api/explore/wizard/results", {
          params: { query: { mood, era, vibe } },
        }),
      ),
    enabled,
  });
}

export function useExplorerBadges() {
  return useQuery({
    queryKey: ["user", "explorer-badges"],
    queryFn: () => unwrap(typedApi.GET("/api/user/explorer-badges")),
    staleTime: STALE_MEDIUM,
  });
}

export function useCompletionistMap() {
  return useQuery({
    queryKey: ["user", "completionist-map"],
    queryFn: () => unwrap(typedApi.GET("/api/user/completionist-map")),
    staleTime: STALE_MEDIUM,
  });
}
