import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ExplorePage } from "@/pages/explore-page";
import type { FeaturedGame, FeaturedSeries, ExploreRowsResponse, Theme, Keyword, MoodDefinition, ForYouResponse, PlayersLikeYouResponse, DeveloperSpotlightResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useExploreFeatured: vi.fn(),
  useExploreRows: vi.fn(),
  useThemes: vi.fn(),
  useKeywords: vi.fn(),
  useFeaturedSeries: vi.fn(),
  useMoods: vi.fn(),
  useForYou: vi.fn(),
  usePlayersLikeYou: vi.fn(),
  useDeveloperSpotlight: vi.fn(),
  useConsoleHighlights: vi.fn(),
  useArtworkGallery: vi.fn(),
  useTrending: vi.fn(),
  useCommunityTop: vi.fn(),
  useCultClassics: vi.fn(),
  useRecentlyReviewed: vi.fn(),
  useActiveNow: vi.fn(),
  useOnThisDay: vi.fn(),
  useBestOfYear: vi.fn(),
  useYourAnniversaries: vi.fn(),
  useDecade: vi.fn(),
  useEasyToComplete: vi.fn(),
  useHardestGames: vi.fn(),
  useAlmostDone: vi.fn(),
  useFreshChallenges: vi.fn(),
  useActiveChallenges: vi.fn(),
  useSurpriseGame: vi.fn(() => ({
    data: undefined,
    refetch: vi.fn(),
    isSuccess: false,
  })),
}));

vi.mock("@/hooks/use-games", () => ({
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({ user: { username: "testuser", role: "admin" }, isAdmin: true }),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

vi.mock("@/hooks/use-in-view", () => ({
  useInView: () => ({ observe: vi.fn(), isInView: true }),
}));

import { useExploreFeatured, useExploreRows, useThemes, useKeywords, useFeaturedSeries, useMoods, useForYou, usePlayersLikeYou, useDeveloperSpotlight, useConsoleHighlights, useArtworkGallery, useTrending, useCommunityTop, useCultClassics, useRecentlyReviewed, useActiveNow, useOnThisDay, useBestOfYear, useYourAnniversaries, useDecade, useEasyToComplete, useHardestGames, useAlmostDone, useFreshChallenges, useActiveChallenges } from "@/hooks/use-explore";
import { makeGame } from "@/test-utils/fixtures";

const mockUseExploreFeatured = useExploreFeatured as ReturnType<typeof vi.fn>;
const mockUseExploreRows = useExploreRows as ReturnType<typeof vi.fn>;
const mockUseThemes = useThemes as ReturnType<typeof vi.fn>;
const mockUseKeywords = useKeywords as ReturnType<typeof vi.fn>;
const mockUseFeaturedSeries = useFeaturedSeries as ReturnType<typeof vi.fn>;
const mockUseMoods = useMoods as ReturnType<typeof vi.fn>;
const mockUseForYou = useForYou as ReturnType<typeof vi.fn>;
const mockUsePlayersLikeYou = usePlayersLikeYou as ReturnType<typeof vi.fn>;
const mockUseDeveloperSpotlight = useDeveloperSpotlight as ReturnType<typeof vi.fn>;
const mockUseConsoleHighlights = useConsoleHighlights as ReturnType<typeof vi.fn>;
const mockUseArtworkGallery = useArtworkGallery as ReturnType<typeof vi.fn>;
const mockUseTrending = useTrending as ReturnType<typeof vi.fn>;
const mockUseCommunityTop = useCommunityTop as ReturnType<typeof vi.fn>;
const mockUseCultClassics = useCultClassics as ReturnType<typeof vi.fn>;
const mockUseRecentlyReviewed = useRecentlyReviewed as ReturnType<typeof vi.fn>;
const mockUseActiveNow = useActiveNow as ReturnType<typeof vi.fn>;
const mockUseOnThisDay = useOnThisDay as ReturnType<typeof vi.fn>;
const mockUseBestOfYear = useBestOfYear as ReturnType<typeof vi.fn>;
const mockUseYourAnniversaries = useYourAnniversaries as ReturnType<typeof vi.fn>;
const mockUseDecade = useDecade as ReturnType<typeof vi.fn>;
const mockUseEasyToComplete = useEasyToComplete as ReturnType<typeof vi.fn>;
const mockUseHardestGames = useHardestGames as ReturnType<typeof vi.fn>;
const mockUseAlmostDone = useAlmostDone as ReturnType<typeof vi.fn>;
const mockUseFreshChallenges = useFreshChallenges as ReturnType<typeof vi.fn>;
const mockUseActiveChallenges = useActiveChallenges as ReturnType<typeof vi.fn>;

function makeFeaturedGame(overrides: Partial<FeaturedGame> = {}): FeaturedGame {
  return {
    gameId: "1",
    title: "Featured Game",
    heroUrl: "/hero/test.jpg",
    logoUrl: "/logo/test.png",
    consoleId: "2",
    consoleName: "Super Nintendo",
    consoleAbbreviation: "snes",
    consoleColor: "#805ad5",
    igdbCriticsRating: 92.5,
    genre: "RPG",
    isFavorite: false,
    isPlayLater: false,
    ...overrides,
  };
}


const mockFeatured: FeaturedGame[] = [
  makeFeaturedGame({ gameId: "1", title: "Hero Game One" }),
  makeFeaturedGame({ gameId: "2", title: "Hero Game Two" }),
];

const mockThemes: Theme[] = [
  { id: "17", name: "Science Fiction", gameCount: 42 },
  { id: "18", name: "Horror", gameCount: 28 },
];

const mockKeywords: Keyword[] = [
  { id: "100", name: "Time Travel", gameCount: 15 },
  { id: "101", name: "Post-Apocalyptic", gameCount: 12 },
];

const mockFeaturedSeries: FeaturedSeries[] = [
  { id: "1", name: "Super Mario", libraryGames: 5, totalGames: 12, consoleCount: 3, heroUrl: "/hero/mario.jpg" },
  { id: "2", name: "The Legend of Zelda", libraryGames: 4, totalGames: 10, consoleCount: 2, heroUrl: "/hero/zelda.jpg" },
];

const mockMoods: MoodDefinition[] = [
  { id: "chill", name: "Chill", description: "Relaxing games", icon: "😌", gradient: ["#1a237e", "#4a148c"] },
  { id: "intense", name: "Intense", description: "Heart-pounding action", icon: "🔥", gradient: ["#b71c1c", "#ff6f00"] },
];

const mockForYou: ForYouResponse = {
  rows: [
    {
      type: "because_you_played",
      title: "Because you played Chrono Trigger",
      genre: "",
      sourceGame: makeGame({ id: "ct", title: "Chrono Trigger", coverUrl: "/covers/ct.jpg" }),
      games: [makeGame({ id: "fy1", title: "For You Game 1" })],
    },
  ],
};

const mockPlayersLikeYou: PlayersLikeYouResponse = {
  games: [makeGame({ id: "ply1", title: "Players Pick" })],
  similarUsersCount: 5,
};

const mockDeveloperSpotlight: DeveloperSpotlightResponse = {
  name: "Capcom",
  gameCount: 10,
  avgRating: 85.5,
  consoles: ["SNES", "GBA"],
  topGames: [makeGame({ id: "sp1", title: "Spotlight Game 1" })],
  heroUrl: "/hero/capcom.jpg",
};

const mockRows: ExploreRowsResponse = {
  rows: [
    {
      id: "top-rated",
      title: "Top Rated",
      games: [
        makeGame({ id: "10", title: "Top Game A" }),
        makeGame({ id: "11", title: "Top Game B" }),
      ],
    },
    {
      id: "recently-added",
      title: "Recently Added",
      games: [
        makeGame({ id: "20", title: "New Game A" }),
      ],
    },
    {
      id: "hidden-gems",
      title: "Hidden Gems",
      games: [
        makeGame({ id: "30", title: "Gem Game" }),
      ],
    },
  ],
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <ExplorePage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExplorePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();

    // Mock matchMedia for prefers-reduced-motion
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    mockUseExploreFeatured.mockReturnValue({
      data: mockFeatured,
      isLoading: false,
    });
    mockUseExploreRows.mockReturnValue({
      data: mockRows,
      isLoading: false,
    });
    mockUseThemes.mockReturnValue({
      data: mockThemes,
      isLoading: false,
    });
    mockUseKeywords.mockReturnValue({
      data: mockKeywords,
      isLoading: false,
    });
    mockUseFeaturedSeries.mockReturnValue({
      data: mockFeaturedSeries,
      isLoading: false,
    });
    mockUseMoods.mockReturnValue({
      data: mockMoods,
      isLoading: false,
    });
    mockUseForYou.mockReturnValue({
      data: mockForYou,
      isLoading: false,
    });
    mockUsePlayersLikeYou.mockReturnValue({
      data: mockPlayersLikeYou,
      isLoading: false,
    });
    mockUseDeveloperSpotlight.mockReturnValue({
      data: mockDeveloperSpotlight,
      isLoading: false,
    });
    mockUseConsoleHighlights.mockReturnValue({
      data: { consoles: [] },
      isLoading: false,
    });
    mockUseArtworkGallery.mockReturnValue({
      data: { artworks: [], page: 1, totalPages: 0, totalCount: 0 },
      isLoading: false,
    });
    mockUseTrending.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseCommunityTop.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseCultClassics.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseRecentlyReviewed.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseActiveNow.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseOnThisDay.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseBestOfYear.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseYourAnniversaries.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseDecade.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseEasyToComplete.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseHardestGames.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseAlmostDone.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseFreshChallenges.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    mockUseActiveChallenges.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
  });

  it("renders the page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Explore", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders the hero carousel", () => {
    renderPage();
    expect(screen.getByTestId("hero-carousel")).toBeInTheDocument();
  });

  it("renders all shelf rows", () => {
    renderPage();
    expect(screen.getByTestId("shelf-Top Rated")).toBeInTheDocument();
    expect(screen.getByTestId("shelf-Recently Added")).toBeInTheDocument();
    expect(screen.getByTestId("shelf-Hidden Gems")).toBeInTheDocument();
  });

  it("renders games in shelf rows", () => {
    renderPage();
    expect(screen.getByText("Top Game A")).toBeInTheDocument();
    expect(screen.getByText("Top Game B")).toBeInTheDocument();
    expect(screen.getByText("New Game A")).toBeInTheDocument();
    expect(screen.getByText("Gem Game")).toBeInTheDocument();
  });

  it("shows empty library state when no data", () => {
    mockUseExploreFeatured.mockReturnValue({
      data: [],
      isLoading: false,
    });
    mockUseExploreRows.mockReturnValue({
      data: { rows: [] },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("Nothing to explore yet")).toBeInTheDocument();
  });

  it("shows admin scan link in empty state for admins", () => {
    mockUseExploreFeatured.mockReturnValue({
      data: [],
      isLoading: false,
    });
    mockUseExploreRows.mockReturnValue({
      data: { rows: [] },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("Scan Library")).toBeInTheDocument();
  });

  it("shows loading skeletons while data is loading", () => {
    mockUseExploreFeatured.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    mockUseExploreRows.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("hero-carousel-skeleton")).toBeInTheDocument();
    expect(screen.getByTestId("shelf-Top Rated-skeleton")).toBeInTheDocument();
    expect(screen.getByTestId("shelf-Recently Added-skeleton")).toBeInTheDocument();
  });

  it("renders hero carousel and rows independently when one loads first", () => {
    mockUseExploreFeatured.mockReturnValue({
      data: mockFeatured,
      isLoading: false,
    });
    mockUseExploreRows.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    // Carousel should render
    expect(screen.getByTestId("hero-carousel")).toBeInTheDocument();
    // Rows should show skeleton
    expect(screen.getByTestId("shelf-Top Rated-skeleton")).toBeInTheDocument();
  });

  it("does not render carousel when featured games empty but rows exist", () => {
    mockUseExploreFeatured.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("hero-carousel")).not.toBeInTheDocument();
    // Rows should still render
    expect(screen.getByTestId("shelf-Top Rated")).toBeInTheDocument();
  });

  it("omits shelf rows with empty games arrays", () => {
    mockUseExploreRows.mockReturnValue({
      data: {
        rows: [
          { id: "top-rated", title: "Top Rated", games: [] },
          {
            id: "recently-added",
            title: "Recently Added",
            games: [makeGame({ id: "20", title: "New Game A" })],
          },
        ],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("shelf-Top Rated")).not.toBeInTheDocument();
    expect(screen.getByTestId("shelf-Recently Added")).toBeInTheDocument();
  });

  it("renders the theme grid section", () => {
    renderPage();
    expect(screen.getByTestId("theme-grid")).toBeInTheDocument();
    expect(screen.getByText("Science Fiction")).toBeInTheDocument();
    expect(screen.getByText("Horror")).toBeInTheDocument();
  });

  it("renders the keyword chips section", () => {
    renderPage();
    expect(screen.getByTestId("keyword-chips")).toBeInTheDocument();
    expect(screen.getByText("Time Travel")).toBeInTheDocument();
    expect(screen.getByText("Post-Apocalyptic")).toBeInTheDocument();
  });

  it("hides theme grid when no themes available", () => {
    mockUseThemes.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("theme-grid")).not.toBeInTheDocument();
  });

  it("hides keyword chips when no keywords available", () => {
    mockUseKeywords.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("keyword-chips")).not.toBeInTheDocument();
  });

  it("shows theme grid skeleton while loading", () => {
    mockUseThemes.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("theme-grid-skeleton")).toBeInTheDocument();
  });

  it("shows keyword chips skeleton while loading", () => {
    mockUseKeywords.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("keyword-chips-skeleton")).toBeInTheDocument();
  });

  it("renders the series shelf section", () => {
    renderPage();
    expect(screen.getByTestId("series-shelf")).toBeInTheDocument();
    expect(screen.getByText("Super Mario")).toBeInTheDocument();
    expect(screen.getByText("The Legend of Zelda")).toBeInTheDocument();
  });

  it("hides series shelf when no featured series available", () => {
    mockUseFeaturedSeries.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("series-shelf")).not.toBeInTheDocument();
  });

  it("shows series shelf skeleton while loading", () => {
    mockUseFeaturedSeries.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("series-shelf-skeleton")).toBeInTheDocument();
  });

  it("renders the mood picker section", () => {
    renderPage();
    expect(screen.getByTestId("mood-picker")).toBeInTheDocument();
    expect(screen.getByText("Chill")).toBeInTheDocument();
    expect(screen.getByText("Intense")).toBeInTheDocument();
  });

  it("hides mood picker when no moods available", () => {
    mockUseMoods.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("mood-picker")).not.toBeInTheDocument();
  });

  it("shows mood picker skeleton while loading", () => {
    mockUseMoods.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("mood-picker-skeleton")).toBeInTheDocument();
  });

  it("renders the For You section", () => {
    renderPage();
    expect(screen.getByTestId("for-you-section")).toBeInTheDocument();
    expect(screen.getByText("For You Game 1")).toBeInTheDocument();
  });

  it("renders the Players Like You shelf", () => {
    renderPage();
    expect(screen.getByTestId("players-like-you-shelf")).toBeInTheDocument();
    expect(screen.getByText("Players Pick")).toBeInTheDocument();
  });

  it("hides For You section when no data", () => {
    mockUseForYou.mockReturnValue({
      data: { rows: [] },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("for-you-section")).not.toBeInTheDocument();
  });

  it("hides Players Like You shelf when no data", () => {
    mockUsePlayersLikeYou.mockReturnValue({
      data: { games: [], similarUsersCount: 0 },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("players-like-you-shelf")).not.toBeInTheDocument();
  });

  it("shows For You skeleton while loading", () => {
    mockUseForYou.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("for-you-skeleton")).toBeInTheDocument();
  });

  it("shows Players Like You skeleton while loading", () => {
    mockUsePlayersLikeYou.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("players-like-you-shelf-skeleton")).toBeInTheDocument();
  });

  it("renders the developer spotlight section", () => {
    renderPage();
    expect(screen.getByTestId("developer-spotlight")).toBeInTheDocument();
    expect(screen.getByText("Spotlight Game 1")).toBeInTheDocument();
  });

  it("hides developer spotlight when no data", () => {
    mockUseDeveloperSpotlight.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("developer-spotlight")).not.toBeInTheDocument();
  });

  it("shows developer spotlight skeleton while loading", () => {
    mockUseDeveloperSpotlight.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("developer-spotlight-skeleton")).toBeInTheDocument();
  });
});
