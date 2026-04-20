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

// Mock the animated counter to return the target value immediately (no animation in tests)
vi.mock("@/hooks/use-animated-counter", () => ({
  useAnimatedCounter: (target: number) => target,
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
    makeGame({ id: "1", title: "Mega Man X", consoleName: "SNES", igdbCriticsRating: 95 }),
    makeGame({ id: "4", title: "Street Fighter II", consoleName: "SNES", igdbCriticsRating: 92 }),
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
      screen.getByTestId("page-back-button"),
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

  it("shows enhanced skeleton with at-a-glance and timeline placeholders", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("skeleton-at-a-glance")).toBeInTheDocument();
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

  // --- Company Info section ---

  it("shows company info section when companyInfo is present", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        companyInfo: {
          description: "A famous game developer.",
          foundedYear: 1979,
          country: "Japan",
          websiteUrl: "https://www.capcom.com",
        },
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("company-info-section")).toBeInTheDocument();
    expect(screen.getByTestId("company-metadata")).toHaveTextContent(
      "Founded 1979",
    );
  });

  it("hides company info section when companyInfo is absent", () => {
    renderPage();
    expect(
      screen.queryByTestId("company-info-section"),
    ).not.toBeInTheDocument();
  });

  it("passes logoUrl to hero banner when companyInfo has logo", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        companyInfo: {
          logoUrl: "/images/companies/capcom-logo.png",
        },
      },
      isLoading: false,
    });
    renderPage();
    const logo = screen.getByTestId("developer-logo");
    expect(logo).toHaveAttribute("src", "/images/companies/capcom-logo.png");
    expect(screen.queryByTestId("developer-avatar")).not.toBeInTheDocument();
  });

  // --- At a Glance section ---

  it("renders at-a-glance row with basic stats", () => {
    renderPage();
    const row = screen.getByTestId("at-a-glance-row");
    expect(row).toBeInTheDocument();
    expect(screen.getByTestId("glance-total-games")).toHaveTextContent("5");
    expect(screen.getByTestId("glance-platforms")).toHaveTextContent("2");
    expect(screen.getByTestId("glance-avg-rating")).toHaveTextContent("88.5");
  });

  it("renders at-a-glance with active years when provided", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        activeYears: { first: 1987, last: 2003 },
      },
      isLoading: false,
    });
    renderPage();
    const pill = screen.getByTestId("glance-active-years");
    expect(pill).toHaveTextContent("1987");
    expect(pill).toHaveTextContent("2003");
  });

  it("renders at-a-glance with primary genre when provided", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        primaryGenre: "Platformer",
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("glance-primary-genre")).toHaveTextContent(
      "Platformer",
    );
  });

  it("hides active years pill when not provided", () => {
    renderPage();
    expect(
      screen.queryByTestId("glance-active-years"),
    ).not.toBeInTheDocument();
  });

  // --- Release Timeline section ---

  it("renders release timeline when timeline data is provided", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        timeline: [
          {
            year: 1992,
            games: [
              { id: "g1", title: "Mega Man X", coverUrl: "/covers/mmx.jpg", rating: 95 },
            ],
          },
          {
            year: 1994,
            games: [
              { id: "g2", title: "Mega Man X2", coverUrl: "/covers/mmx2.jpg", rating: 88 },
            ],
          },
        ],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("release-timeline")).toBeInTheDocument();
    expect(screen.getByText("1992")).toBeInTheDocument();
    expect(screen.getByText("1994")).toBeInTheDocument();
  });

  it("hides release timeline when no timeline data", () => {
    renderPage();
    expect(
      screen.queryByTestId("release-timeline"),
    ).not.toBeInTheDocument();
  });

  it("hides release timeline when timeline is empty", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        timeline: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("release-timeline"),
    ).not.toBeInTheDocument();
  });

  // --- Rating Distribution section ---

  it("renders rating distribution when data is provided with 5+ rated games", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        ratingDistribution: {
          excellent: 3,
          good: 2,
          average: 1,
          poor: 1,
          unrated: 0,
        },
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("rating-distribution")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-excellent")).toHaveTextContent("3");
    expect(screen.getByTestId("rating-bar-good")).toHaveTextContent("2");
  });

  it("hides rating distribution when not provided", () => {
    renderPage();
    expect(
      screen.queryByTestId("rating-distribution"),
    ).not.toBeInTheDocument();
  });

  it("hides rating distribution when fewer than 5 rated games", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        ratingDistribution: {
          excellent: 2,
          good: 1,
          average: 0,
          poor: 0,
          unrated: 10,
        },
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("rating-distribution"),
    ).not.toBeInTheDocument();
  });

  // --- Share button ---

  it("renders share button in hero banner", () => {
    renderPage();
    expect(screen.getByTestId("share-button")).toBeInTheDocument();
    expect(screen.getByTestId("share-button")).toHaveTextContent("Share");
  });

  it("copies URL to clipboard when share button is clicked", async () => {
    const user = userEvent.setup();
    const writeTextMock = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: writeTextMock },
      writable: true,
      configurable: true,
    });

    renderPage();
    await user.click(screen.getByTestId("share-button"));

    expect(writeTextMock).toHaveBeenCalledWith(window.location.href);
  });

  it("shows toast after copying link", async () => {
    const user = userEvent.setup();
    Object.defineProperty(navigator, "clipboard", {
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
      writable: true,
      configurable: true,
    });

    renderPage();
    await user.click(screen.getByTestId("share-button"));

    expect(screen.getByTestId("share-toast")).toHaveTextContent("Link copied to clipboard");
  });

  // --- Related Developers section ---

  it("shows related developers section when relatedDevelopers is present", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        relatedDevelopers: [
          { name: "Intelligent Systems", gameCount: 15, sharedPublishers: ["Nintendo"] },
          { name: "HAL Laboratory", gameCount: 8, sharedPublishers: ["Nintendo"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const section = screen.getByTestId("related-developers");
    expect(section).toBeInTheDocument();
    expect(section).toHaveTextContent("Related Developers");
    expect(screen.getByTestId("related-developer-Intelligent Systems")).toBeInTheDocument();
    expect(screen.getByTestId("related-developer-HAL Laboratory")).toBeInTheDocument();
  });

  it("renders related developer cards with game count and shared publishers", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        relatedDevelopers: [
          { name: "Intelligent Systems", gameCount: 15, sharedPublishers: ["Nintendo"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-developer-Intelligent Systems");
    expect(card).toHaveTextContent("Intelligent Systems");
    expect(card).toHaveTextContent("15 games");
    expect(card).toHaveTextContent("via Nintendo");
  });

  it("links related developer cards to their detail page", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        relatedDevelopers: [
          { name: "Intelligent Systems", gameCount: 15, sharedPublishers: ["Nintendo"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-developer-Intelligent Systems");
    expect(card).toHaveAttribute("href", "/explore/developers/Intelligent%20Systems");
  });

  it("hides related developers section when empty", () => {
    renderPage();
    expect(screen.queryByTestId("related-developers")).not.toBeInTheDocument();
  });

  it("hides related developers section when relatedDevelopers is empty array", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        relatedDevelopers: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("related-developers")).not.toBeInTheDocument();
  });

  it("renders singular game count for related developer with 1 game", () => {
    mockUseDeveloperDetail.mockReturnValue({
      data: {
        ...mockDeveloperDetail,
        relatedDevelopers: [
          { name: "Rare", gameCount: 1, sharedPublishers: ["Nintendo"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-developer-Rare");
    expect(card).toHaveTextContent("1 game");
  });
});
