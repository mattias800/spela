import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ExploreKeywordPage } from "@/pages/explore-keyword-page";
import type { Keyword, Game, GamesResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useKeywords: vi.fn(),
  useKeywordGames: vi.fn(),
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

import { useKeywords, useKeywordGames } from "@/hooks/use-explore";

const mockUseKeywords = useKeywords as ReturnType<typeof vi.fn>;
const mockUseKeywordGames = useKeywordGames as ReturnType<typeof vi.fn>;

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
    ...overrides,
  };
}

const mockKeywords: Keyword[] = [
  { id: "100", name: "Time Travel", gameCount: 15 },
  { id: "101", name: "Post-Apocalyptic", gameCount: 12 },
];

const mockGamesResponse: GamesResponse = {
  data: [
    makeGame({ id: "1", title: "Chrono Trigger", rating: 95 }),
    makeGame({ id: "2", title: "Chrono Cross", rating: 88 }),
  ],
  total: 2,
  page: 1,
  pageSize: 48,
};

function renderPage(keywordId = "100") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/keywords/${keywordId}`]}>
        <Routes>
          <Route
            path="/explore/keywords/:id"
            element={<ExploreKeywordPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreKeywordPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseKeywords.mockReturnValue({
      data: mockKeywords,
      isLoading: false,
    });
    mockUseKeywordGames.mockReturnValue({
      data: mockGamesResponse,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("keyword-detail-page")).toBeInTheDocument();
  });

  it("renders keyword name in heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Time Travel", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders game count", () => {
    renderPage();
    expect(screen.getByText("2 games")).toBeInTheDocument();
  });

  it("renders games in grid", () => {
    renderPage();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
    expect(screen.getByText("Chrono Cross")).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /back to explore/i }),
    ).toBeInTheDocument();
  });

  it("renders sort select", () => {
    renderPage();
    expect(screen.getByLabelText("Sort games")).toBeInTheDocument();
  });

  it("shows loading skeletons while loading", () => {
    mockUseKeywordGames.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows empty state when no games", () => {
    mockUseKeywordGames.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 48 },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No games found")).toBeInTheDocument();
  });

  it("renders fallback keyword name when keyword not found", () => {
    mockUseKeywords.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage("999");
    expect(
      screen.getByRole("heading", { name: "Keyword", level: 1 }),
    ).toBeInTheDocument();
  });
});
