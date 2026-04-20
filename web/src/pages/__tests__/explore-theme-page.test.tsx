import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ExploreThemePage } from "@/pages/explore-theme-page";
import type { Theme, Game, GamesResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useThemes: vi.fn(),
  useThemeGames: vi.fn(),
}));

vi.mock("@/hooks/use-games", () => ({
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

import { useThemes, useThemeGames } from "@/hooks/use-explore";

const mockUseThemes = useThemes as ReturnType<typeof vi.fn>;
const mockUseThemeGames = useThemeGames as ReturnType<typeof vi.fn>;

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "1",
    title: "Test Game",
    consoleId: "nes",
    consoleName: "NES",
    fileName: "test.nes",
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
    coverUrl: "",
    description: "",
    developer: "",
    genre: "",
    igdbCriticsRating: 0,
    isPreRelease: false,
    lastPlayedAt: null,
    players: 0,
    publisher: "",
    releaseDate: "",
    ...overrides,
  };
}

const mockThemes: Theme[] = [
  { id: "17", name: "Science Fiction", gameCount: 42 },
  { id: "18", name: "Horror", gameCount: 28 },
];

const mockGamesResponse: GamesResponse = {
  data: [
    makeGame({ id: "1", title: "Metroid", igdbCriticsRating: 90 }),
    makeGame({ id: "2", title: "Star Fox", igdbCriticsRating: 85 }),
    makeGame({ id: "3", title: "Mega Man X", igdbCriticsRating: 88 }),
  ],
  total: 3,
  page: 1,
  pageSize: 48,
};

function renderPage(themeId = "17") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/themes/${themeId}`]}>
        <Routes>
          <Route
            path="/explore/themes/:id"
            element={<ExploreThemePage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreThemePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseThemes.mockReturnValue({
      data: mockThemes,
      isLoading: false,
    });
    mockUseThemeGames.mockReturnValue({
      data: mockGamesResponse,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("theme-detail-page")).toBeInTheDocument();
  });

  it("renders theme name in heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", {
        name: "Science Fiction Games",
        level: 1,
      }),
    ).toBeInTheDocument();
  });

  it("renders game count", () => {
    renderPage();
    expect(screen.getByText("3 games")).toBeInTheDocument();
  });

  it("renders games in grid", () => {
    renderPage();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
    expect(screen.getByText("Star Fox")).toBeInTheDocument();
    expect(screen.getByText("Mega Man X")).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByTestId("page-back-button"),
    ).toBeInTheDocument();
  });

  it("renders sort select", () => {
    renderPage();
    expect(screen.getByLabelText("Sort games")).toBeInTheDocument();
  });

  it("shows loading skeletons while loading", () => {
    mockUseThemeGames.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows empty state when no games", () => {
    mockUseThemeGames.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 48 },
      isLoading: false,
    });
    mockUseThemes.mockReturnValue({
      data: mockThemes,
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No games found")).toBeInTheDocument();
  });

  it("renders fallback theme name when theme not found", () => {
    mockUseThemes.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage("999");
    expect(
      screen.getByRole("heading", { name: "Theme Games", level: 1 }),
    ).toBeInTheDocument();
  });
});
