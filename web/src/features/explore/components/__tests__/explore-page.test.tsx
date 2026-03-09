import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { ExplorePage } from "@/pages/explore-page";
import type { FeaturedGame, Game, ExploreRowsResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useExploreFeatured: vi.fn(),
  useExploreRows: vi.fn(),
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

import { useExploreFeatured, useExploreRows } from "@/hooks/use-explore";

const mockUseExploreFeatured = useExploreFeatured as ReturnType<typeof vi.fn>;
const mockUseExploreRows = useExploreRows as ReturnType<typeof vi.fn>;

function makeFeaturedGame(overrides: Partial<FeaturedGame> = {}): FeaturedGame {
  return {
    gameId: "1",
    title: "Featured Game",
    heroUrl: "/hero/test.jpg",
    logoUrl: "/logo/test.png",
    consoleAbbreviation: "snes",
    consoleColor: "#805ad5",
    rating: 92.5,
    genre: "RPG",
    isFavorite: false,
    isPlayLater: false,
    ...overrides,
  };
}

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

const mockFeatured: FeaturedGame[] = [
  makeFeaturedGame({ gameId: "1", title: "Hero Game One" }),
  makeFeaturedGame({ gameId: "2", title: "Hero Game Two" }),
];

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
    expect(screen.getByTestId("shelf-skeleton-Top Rated")).toBeInTheDocument();
    expect(screen.getByTestId("shelf-skeleton-Recently Added")).toBeInTheDocument();
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
    expect(screen.getByTestId("shelf-skeleton-Top Rated")).toBeInTheDocument();
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
});
