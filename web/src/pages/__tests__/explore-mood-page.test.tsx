import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ExploreMoodPage } from "@/pages/explore-mood-page";
import type { MoodDefinition, Game } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useMoods: vi.fn(),
  useMoodGames: vi.fn(),
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

import { useMoods, useMoodGames } from "@/hooks/use-explore";

const mockUseMoods = useMoods as ReturnType<typeof vi.fn>;
const mockUseMoodGames = useMoodGames as ReturnType<typeof vi.fn>;

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

const mockMoods: MoodDefinition[] = [
  {
    id: "chill",
    name: "Chill",
    description: "Relaxing and laid-back games",
    icon: "😌",
    gradient: ["#1a237e", "#4a148c"],
  },
  {
    id: "intense",
    name: "Intense",
    description: "Heart-pounding action",
    icon: "🔥",
    gradient: ["#b71c1c", "#ff6f00"],
  },
];

const mockGames: Game[] = [
  makeGame({ id: "1", title: "Stardew Valley", igdbCriticsRating: 92 }),
  makeGame({ id: "2", title: "Animal Crossing", igdbCriticsRating: 88 }),
  makeGame({ id: "3", title: "Harvest Moon", igdbCriticsRating: 85 }),
];

function renderPage(moodId = "chill") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/mood/${moodId}`]}>
        <Routes>
          <Route
            path="/explore/mood/:mood"
            element={<ExploreMoodPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreMoodPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseMoods.mockReturnValue({
      data: mockMoods,
      isLoading: false,
    });
    mockUseMoodGames.mockReturnValue({
      data: mockGames,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("mood-results-page")).toBeInTheDocument();
  });

  it("shows mood name in header", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Chill", level: 1 }),
    ).toBeInTheDocument();
  });

  it("shows mood description in header", () => {
    renderPage();
    expect(
      screen.getByText("Relaxing and laid-back games"),
    ).toBeInTheDocument();
  });

  it("shows mood icon in header", () => {
    renderPage();
    expect(screen.getByText("😌")).toBeInTheDocument();
  });

  it("shows games in grid", () => {
    renderPage();
    expect(screen.getByText("Stardew Valley")).toBeInTheDocument();
    expect(screen.getByText("Animal Crossing")).toBeInTheDocument();
    expect(screen.getByText("Harvest Moon")).toBeInTheDocument();
  });

  it("shows loading skeletons while loading", () => {
    mockUseMoodGames.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows empty state when no games", () => {
    mockUseMoodGames.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No games found")).toBeInTheDocument();
    expect(
      screen.getByText("No games match this mood yet."),
    ).toBeInTheDocument();
  });

  it("back button links to explore", () => {
    renderPage();
    expect(
      screen.getByTestId("page-back-button"),
    ).toBeInTheDocument();
  });

  it("shows fallback mood name when mood not found", () => {
    mockUseMoods.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage("unknown");
    expect(
      screen.getByRole("heading", { name: "Mood", level: 1 }),
    ).toBeInTheDocument();
  });
});
