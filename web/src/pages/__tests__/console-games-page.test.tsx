import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ConsoleGamesPage } from "@/pages/console-games-page";
import type { Console, Game, GamesResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

vi.mock("@/hooks/use-games", () => ({
  useGames: vi.fn(),
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-bios", () => ({
  useBiosStatus: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({ isAdmin: false }),
}));

vi.mock("@/hooks/use-debounced-value", () => ({
  useDebouncedValue: (v: string) => v,
}));

vi.mock("@/hooks/use-explore", () => ({
  useThemes: () => ({ data: undefined }),
  useKeywords: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-saved-searches", () => ({
  useSavedSearches: () => ({ data: undefined }),
  useCreateSavedSearch: () => ({ mutate: vi.fn() }),
  useDeleteSavedSearch: () => ({ mutate: vi.fn() }),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

vi.mock("@/hooks/use-default-region-filters", () => ({
  useDefaultRegionFilters: () => ({ saveDefaultRegions: vi.fn() }),
}));

import { useConsoles } from "@/hooks/use-consoles";
import { useGames } from "@/hooks/use-games";

const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;
const mockUseGames = useGames as ReturnType<typeof vi.fn>;

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "1",
    title: "Test Game",
    consoleId: "snes",
    consoleName: "SNES",
    fileName: "test.sfc",
    fileSize: 1024,
    discCount: 1,
    screenshotUrls: [],
    scrapeAttempts: 1,
    coverAspectRatio: 0.75,
    playable: true,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const testConsoles: Console[] = [
  {
    id: "snes",
    name: "Super Nintendo",
    abbreviation: "snes",
    extensions: [".sfc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "#6366f1",
    generation: 4,
    iconUrl: "",
    logoUrl: "",
    gameCount: 100,
    saveStateSupport: true,
    browserPlayable: false,
    playable: true,
    code: "snes",
    maker: null,
    mediaType: null,
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "",
    updatedAt: "",
  },
];

const mockGamesResponse: GamesResponse = {
  data: [
    makeGame({ id: "1", title: "Super Mario World" }),
    makeGame({ id: "2", title: "Chrono Trigger" }),
  ],
  total: 2,
  page: 1,
  pageSize: 48,
};

function renderPage(consoleId = "snes") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/consoles/${consoleId}/games`]}>
        <Routes>
          <Route
            path="/consoles/:id/games"
            element={<ConsoleGamesPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ConsoleGamesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseConsoles.mockReturnValue({ data: testConsoles });
    mockUseGames.mockReturnValue({
      data: mockGamesResponse,
      isLoading: false,
    });
  });

  it("renders page with test id", () => {
    renderPage();
    expect(screen.getByTestId("console-games-page")).toBeInTheDocument();
  });

  it("renders console hero banner with name", () => {
    renderPage();
    expect(screen.getByTestId("console-hero-banner")).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "Super Nintendo", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button with console name", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /console/i }),
    ).toBeInTheDocument();
  });

  it("renders total game count in hero banner", () => {
    renderPage();
    expect(screen.getByText("2 games")).toBeInTheDocument();
  });

  it("renders game cards", () => {
    renderPage();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
  });

  it("hides console name and shows release year on game cards", () => {
    mockUseGames.mockReturnValue({
      data: {
        data: [
          makeGame({ id: "1", title: "Super Mario World", consoleName: "Super Nintendo", releaseDate: "1990-11-21" }),
          makeGame({ id: "2", title: "Chrono Trigger", consoleName: "Super Nintendo", releaseDate: "1995-03-11" }),
        ],
        total: 2,
        page: 1,
        pageSize: 48,
      },
      isLoading: false,
    });
    renderPage();

    // Game titles are visible
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();

    // Console name should NOT appear below game cards
    const gameCardLinks = screen.getAllByRole("link").filter(
      (el) => el.getAttribute("href")?.startsWith("/games/"),
    );
    gameCardLinks.forEach((card) => {
      const texts = Array.from(card.querySelectorAll("p")).map((p) => p.textContent);
      expect(texts).not.toContain("Super Nintendo");
    });

    // Instead, release year should be shown
    expect(screen.getByText("1990")).toBeInTheDocument();
    expect(screen.getByText("1995")).toBeInTheDocument();
  });

  it("shows empty state when no games", () => {
    mockUseGames.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 48 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No games found")).toBeInTheDocument();
  });

  it("renders loading skeletons while loading", () => {
    mockUseGames.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders search input", () => {
    renderPage();
    expect(screen.getByPlaceholderText("Search games...")).toBeInTheDocument();
  });
});
