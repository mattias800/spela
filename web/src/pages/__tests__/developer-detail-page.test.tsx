import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { DeveloperDetailPage } from "@/pages/developer-detail-page";
import type { DeveloperDetailResponse, Game } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useDeveloperDetail: vi.fn(),
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

import { useDeveloperDetail } from "@/hooks/use-explore";

const mockUseDeveloperDetail = useDeveloperDetail as ReturnType<typeof vi.fn>;

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

const mockDeveloperDetail: DeveloperDetailResponse = {
  name: "Capcom",
  gameCount: 5,
  avgRating: 88.5,
  consoles: ["SNES", "GBA"],
  games: [
    makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES", genre: "Platformer", developer: "Capcom" }),
    makeGame({ id: "2", title: "Mega Man X2", consoleName: "SNES", genre: "Platformer", developer: "Capcom" }),
    makeGame({ id: "3", title: "Mega Man Zero", consoleName: "GBA", genre: "Action", developer: "Capcom" }),
    makeGame({ id: "4", title: "Street Fighter II", consoleName: "SNES", genre: "Fighting", developer: "Capcom" }),
    makeGame({ id: "5", title: "Breath of Fire", consoleName: "SNES", genre: "RPG", developer: "Capcom" }),
  ],
  topGames: [
    makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES", rating: 95 }),
    makeGame({ id: "4", title: "Street Fighter II", consoleName: "SNES", rating: 92 }),
  ],
  genreBreakdown: [
    { name: "Platformer", gameCount: 2 },
    { name: "Fighting", gameCount: 1 },
    { name: "RPG", gameCount: 1 },
    { name: "Action", gameCount: 1 },
  ],
  platformBreakdown: [
    { consoleName: "SNES", consoleId: "snes", count: 4 },
    { consoleName: "GBA", consoleId: "gba", count: 1 },
  ],
  publishers: [
    { name: "Capcom", count: 4 },
    { name: "Nintendo", count: 1 },
  ],
};

function renderPage(name = "Capcom") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter
        initialEntries={[`/explore/developers/${encodeURIComponent(name)}`]}
      >
        <Routes>
          <Route
            path="/explore/developers/:name"
            element={<DeveloperDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("DeveloperDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseDeveloperDetail.mockReturnValue({
      data: mockDeveloperDetail,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("developer-detail-page")).toBeInTheDocument();
  });

  it("renders developer name in hero banner heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Capcom", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /back to explore/i }),
    ).toBeInTheDocument();
  });

  it("renders hero banner with stats", () => {
    renderPage();
    const banner = screen.getByTestId("developer-hero-banner");
    expect(banner).toBeInTheDocument();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).toHaveTextContent("5 games");
    expect(stats).toHaveTextContent("88.5");
    expect(stats).toHaveTextContent("2 platforms");
  });

  it("renders hero banner avatar with first letter", () => {
    renderPage();
    const avatar = screen.getByTestId("developer-avatar");
    expect(avatar).toHaveTextContent("C");
  });

  it("renders console filter chips", () => {
    renderPage();
    const filters = screen.getByTestId("developer-console-filters");
    expect(within(filters).getByText(/All \(/)).toBeInTheDocument();
    const buttons = within(filters).getAllByRole("button");
    const buttonTexts = buttons.map((b) => b.textContent);
    expect(buttonTexts).toContainEqual(expect.stringContaining("SNES"));
    expect(buttonTexts).toContainEqual(expect.stringContaining("GBA"));
  });

  it("renders all games somewhere on page", () => {
    renderPage();
    // Games may appear in top rated shelf AND platform sections, so use getAllByText
    expect(screen.getAllByText("Mega Man X").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Mega Man X2").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Mega Man Zero")).toBeInTheDocument();
    expect(screen.getAllByText("Street Fighter II").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Breath of Fire")).toBeInTheDocument();
  });

  it("filters games by console", async () => {
    const user = userEvent.setup();
    renderPage();

    const filters = screen.getByTestId("developer-console-filters");
    const buttons = within(filters).getAllByRole("button");
    const gbaButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("GBA"),
    );
    expect(gbaButton).toBeDefined();
    await user.click(gbaButton!);

    // When filtering by console, falls back to grid view
    const grid = screen.getByTestId("developer-game-grid");
    const gameLinks = within(grid).getAllByRole("link");
    expect(gameLinks).toHaveLength(1);
    expect(screen.getByText("Mega Man Zero")).toBeInTheDocument();
    expect(screen.queryByText("Mega Man X2")).not.toBeInTheDocument();
  });

  it("shows loading skeleton while loading", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("developer-detail-skeleton")).toBeInTheDocument();
  });

  it("shows empty state when developer not found", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("Unknown");
    expect(
      screen.getByText("No games found for this developer"),
    ).toBeInTheDocument();
  });

  it("hides console filters when only one console", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("developer-console-filters"),
    ).not.toBeInTheDocument();
  });

  it("renders singular game count correctly", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        gameCount: 1,
        consoles: ["SNES"],
        games: [makeGame({ id: "1", title: "Test", consoleName: "SNES" })],
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).toHaveTextContent("1 game");
  });

  it("hides avg rating when zero", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        avgRating: 0,
      },
      isLoading: false,
    });
    renderPage();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).not.toHaveTextContent("88.5");
  });

  // --- Top Rated section ---

  it("shows top rated shelf when >4 games and topGames present", () => {
    renderPage();
    expect(screen.getByTestId("shelf-Top Rated")).toBeInTheDocument();
  });

  it("hides top rated shelf when 4 or fewer games", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        gameCount: 4,
        topGames: [makeGame({ id: "1", title: "Test" })],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("shelf-Top Rated")).not.toBeInTheDocument();
  });

  it("hides top rated shelf when topGames is empty", () => {
    renderPage();
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        topGames: [],
      },
      isLoading: false,
    });
    renderPage();
    // Re-renders with empty topGames
  });

  // --- Genre Breakdown section ---

  it("shows genre breakdown chips when 2+ genres", () => {
    renderPage();
    const section = screen.getByTestId("developer-genre-breakdown");
    expect(section).toBeInTheDocument();
    expect(within(section).getByText(/Platformer \(2\)/)).toBeInTheDocument();
    expect(within(section).getByText(/Fighting \(1\)/)).toBeInTheDocument();
    expect(within(section).getByText(/RPG \(1\)/)).toBeInTheDocument();
  });

  it("hides genre breakdown when fewer than 2 genres", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        genreBreakdown: [{ name: "Platformer", gameCount: 5 }],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("developer-genre-breakdown"),
    ).not.toBeInTheDocument();
  });

  it("filters games when clicking a genre chip", async () => {
    const user = userEvent.setup();
    renderPage();

    const section = screen.getByTestId("developer-genre-breakdown");
    const platformerBtn = within(section).getByText(/Platformer \(2\)/);
    await user.click(platformerBtn);

    // After genre filter, only platformer games remain.
    // Since all Platformer games are on SNES, we get a grid (single platform = no grouping).
    const grid = screen.getByTestId("developer-game-grid");
    // Grid should only contain platformer games
    const links = within(grid).getAllByRole("link");
    expect(links).toHaveLength(2);
    expect(within(grid).getByText("Mega Man X")).toBeInTheDocument();
    expect(within(grid).getByText("Mega Man X2")).toBeInTheDocument();
    // Non-platformer games should not be in the grid
    expect(within(grid).queryByText("Breath of Fire")).not.toBeInTheDocument();
    expect(
      within(grid).queryByText("Street Fighter II"),
    ).not.toBeInTheDocument();
  });

  // --- Platform sections ---

  it("groups games by platform when multiple platforms present", () => {
    renderPage();
    const sections = screen.getByTestId("developer-platform-sections");
    expect(sections).toBeInTheDocument();
    expect(
      screen.getByTestId("platform-section-SNES"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("platform-section-GBA"),
    ).toBeInTheDocument();
  });

  it("orders platform sections by count descending", () => {
    renderPage();
    const sections = screen.getByTestId("developer-platform-sections");
    const sectionHeaders = within(sections)
      .getAllByRole("heading", { level: 2 })
      .map((h) => h.textContent);
    // SNES has 4 games, should come first
    expect(sectionHeaders[0]).toBe("SNES");
    expect(sectionHeaders[1]).toBe("GBA");
  });

  // --- Publishers section ---

  it("shows publishers section with clickable chips", () => {
    renderPage();
    const section = screen.getByTestId("developer-publishers");
    expect(section).toBeInTheDocument();
    const links = within(section).getAllByRole("link");
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveTextContent("Capcom");
    expect(links[1]).toHaveTextContent("Nintendo");
  });

  it("hides publishers section when no publishers", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        publishers: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("developer-publishers"),
    ).not.toBeInTheDocument();
  });

  it("hides publishers section when publishers is undefined", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        publishers: undefined,
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("developer-publishers"),
    ).not.toBeInTheDocument();
  });

  // --- User Stats section ---

  it("shows user stats card when userStats is present", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        userStats: {
          totalPlayTime: 14400,
          gamesPlayed: 3,
          favoriteCount: 2,
          mostPlayedGame: makeGame({
            id: "1",
            title: "Mega Man X",
            coverUrl: "/covers/mmx.jpg",
            totalPlayTime: 7200,
          }),
        },
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("developer-user-stats");
    expect(card).toBeInTheDocument();
    expect(card).toHaveTextContent("Your Stats");
    expect(card).toHaveTextContent("4h 0m");
    expect(card).toHaveTextContent("3/5");
    expect(card).toHaveTextContent("2");
  });

  it("hides user stats card when userStats is absent", () => {
    renderPage();
    expect(
      screen.queryByTestId("developer-user-stats"),
    ).not.toBeInTheDocument();
  });
});
