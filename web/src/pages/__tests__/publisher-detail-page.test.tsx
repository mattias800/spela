import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PublisherDetailPage } from "@/pages/publisher-detail-page";
import type { PublisherDetailResponse, Game } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  usePublisherDetail: vi.fn(),
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

import { usePublisherDetail } from "@/hooks/use-explore";

const mockUsePublisherDetail = usePublisherDetail as ReturnType<typeof vi.fn>;

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

const mockPublisherDetail: PublisherDetailResponse = {
  name: "Nintendo",
  gameCount: 4,
  avgRating: 92.3,
  consoles: ["NES", "SNES"],
  games: [
    makeGame({ id: "1", title: "Super Mario World", consoleName: "SNES", publisher: "Nintendo" }),
    makeGame({ id: "2", title: "Zelda: ALTTP", consoleName: "SNES", publisher: "Nintendo" }),
    makeGame({ id: "3", title: "Super Mario Bros.", consoleName: "NES", publisher: "Nintendo" }),
    makeGame({ id: "4", title: "Metroid", consoleName: "NES", publisher: "Nintendo" }),
  ],
};

function renderPage(name = "Nintendo") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[`/explore/publishers/${encodeURIComponent(name)}`]}
      >
        <Routes>
          <Route
            path="/explore/publishers/:name"
            element={<PublisherDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("PublisherDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUsePublisherDetail.mockReturnValue({
      data: mockPublisherDetail,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("publisher-detail-page")).toBeInTheDocument();
  });

  it("renders publisher name as heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Nintendo", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /back to explore/i }),
    ).toBeInTheDocument();
  });

  it("renders stats row with game count and avg rating", () => {
    renderPage();
    const stats = screen.getByTestId("publisher-stats");
    expect(stats).toHaveTextContent("4 games");
    expect(stats).toHaveTextContent("Avg rating: 92.3");
    expect(stats).toHaveTextContent("Consoles: NES, SNES");
  });

  it("renders console filter chips", () => {
    renderPage();
    const filters = screen.getByTestId("publisher-console-filters");
    expect(within(filters).getByText(/All \(/)).toBeInTheDocument();
    const buttons = within(filters).getAllByRole("button");
    const buttonTexts = buttons.map((b) => b.textContent);
    expect(buttonTexts).toContainEqual(expect.stringContaining("NES"));
    expect(buttonTexts).toContainEqual(expect.stringContaining("SNES"));
  });

  it("renders all games in grid", () => {
    renderPage();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("Zelda: ALTTP")).toBeInTheDocument();
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
  });

  it("filters games by console", async () => {
    const user = userEvent.setup();
    renderPage();

    const filters = screen.getByTestId("publisher-console-filters");
    const buttons = within(filters).getAllByRole("button");
    const nesButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("NES"),
    );
    expect(nesButton).toBeDefined();
    await user.click(nesButton!);

    const grid = screen.getByTestId("publisher-game-grid");
    const gameLinks = within(grid).getAllByRole("link");
    expect(gameLinks).toHaveLength(2);
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
    expect(screen.queryByText("Super Mario World")).not.toBeInTheDocument();
  });

  it("shows loading skeleton while loading", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("publisher-detail-skeleton")).toBeInTheDocument();
  });

  it("shows empty state when publisher not found", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("Unknown");
    expect(
      screen.getByText("No games found for this publisher"),
    ).toBeInTheDocument();
  });

  it("hides console filters when only one console", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Test", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("publisher-console-filters"),
    ).not.toBeInTheDocument();
  });

  it("renders singular game count correctly", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        gameCount: 1,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Test", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("publisher-stats");
    expect(stats).toHaveTextContent("1 game");
  });

  it("hides avg rating when zero", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        avgRating: 0,
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("publisher-stats");
    expect(stats).not.toHaveTextContent("Avg rating");
  });
});
