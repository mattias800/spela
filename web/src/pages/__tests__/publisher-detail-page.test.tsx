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

// Mock the animated counter to return the target value immediately (no animation in tests)
vi.mock("@/hooks/use-animated-counter", () => ({
  useAnimatedCounter: (target: number) => target,
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
  gameCount: 5,
  avgRating: 92.3,
  consoles: ["NES", "SNES"],
  games: [
    makeGame({ id: "1", title: "Super Mario World", consoleName: "SNES", genre: "Platformer", publisher: "Nintendo" }),
    makeGame({ id: "2", title: "Zelda: ALTTP", consoleName: "SNES", genre: "RPG", publisher: "Nintendo" }),
    makeGame({ id: "3", title: "Super Mario Bros.", consoleName: "NES", genre: "Platformer", publisher: "Nintendo" }),
    makeGame({ id: "4", title: "Metroid", consoleName: "NES", genre: "Action", publisher: "Nintendo" }),
    makeGame({ id: "5", title: "Kirby's Adventure", consoleName: "NES", genre: "Platformer", publisher: "Nintendo" }),
  ],
  topGames: [
    makeGame({ id: "1", title: "Super Mario World", consoleName: "SNES", rating: 96 }),
    makeGame({ id: "2", title: "Zelda: ALTTP", consoleName: "SNES", rating: 95 }),
  ],
  genreBreakdown: [
    { name: "Platformer", gameCount: 3 },
    { name: "RPG", gameCount: 1 },
    { name: "Action", gameCount: 1 },
  ],
  platformBreakdown: [
    { consoleName: "NES", consoleId: "nes", count: 3 },
    { consoleName: "SNES", consoleId: "snes", count: 2 },
  ],
  developers: [
    { name: "Nintendo EAD", count: 3 },
    { name: "Intelligent Systems", count: 1 },
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

  it("renders publisher name in hero banner heading", () => {
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

  it("renders hero banner with stats", () => {
    renderPage();
    const banner = screen.getByTestId("developer-hero-banner");
    expect(banner).toBeInTheDocument();
    const stats = screen.getByTestId("developer-stats");
    expect(stats).toHaveTextContent("5 games");
    expect(stats).toHaveTextContent("92.3");
    expect(stats).toHaveTextContent("2 platforms");
  });

  it("renders hero banner avatar with first letter", () => {
    renderPage();
    const avatar = screen.getByTestId("developer-avatar");
    expect(avatar).toHaveTextContent("N");
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

  it("renders all games somewhere on page", () => {
    renderPage();
    expect(screen.getAllByText("Super Mario World").length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText("Zelda: ALTTP").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("Metroid")).toBeInTheDocument();
    expect(screen.getByText("Kirby's Adventure")).toBeInTheDocument();
  });

  it("filters games by console", async () => {
    const user = userEvent.setup();
    renderPage();

    const filters = screen.getByTestId("publisher-console-filters");
    const buttons = within(filters).getAllByRole("button");
    const snesButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("SNES"),
    );
    expect(snesButton).toBeDefined();
    await user.click(snesButton!);

    // When filtering by console, falls back to grid view
    const grid = screen.getByTestId("publisher-game-grid");
    const gameLinks = within(grid).getAllByRole("link");
    expect(gameLinks).toHaveLength(2);
    expect(within(grid).getByText("Super Mario World")).toBeInTheDocument();
    expect(within(grid).getByText("Zelda: ALTTP")).toBeInTheDocument();
    // NES games should not be in the grid
    expect(within(grid).queryByText("Super Mario Bros.")).not.toBeInTheDocument();
  });

  it("shows loading skeleton while loading", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("publisher-detail-skeleton")).toBeInTheDocument();
  });

  it("shows enhanced skeleton with at-a-glance and timeline placeholders", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("skeleton-at-a-glance")).toBeInTheDocument();
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
    const stats = screen.getByTestId("developer-stats");
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
    const stats = screen.getByTestId("developer-stats");
    expect(stats).not.toHaveTextContent("92.3");
  });

  // --- Top Rated section ---

  it("shows top rated shelf when >4 games and topGames present", () => {
    renderPage();
    expect(screen.getByTestId("shelf-Top Rated")).toBeInTheDocument();
  });

  it("hides top rated shelf when 4 or fewer games", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        gameCount: 4,
        topGames: [makeGame({ id: "1", title: "Test" })],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("shelf-Top Rated")).not.toBeInTheDocument();
  });

  // --- Genre Breakdown section ---

  it("shows genre breakdown chips when 2+ genres", () => {
    renderPage();
    const section = screen.getByTestId("publisher-genre-breakdown");
    expect(section).toBeInTheDocument();
    expect(within(section).getByText(/Platformer \(3\)/)).toBeInTheDocument();
    expect(within(section).getByText(/RPG \(1\)/)).toBeInTheDocument();
    expect(within(section).getByText(/Action \(1\)/)).toBeInTheDocument();
  });

  it("hides genre breakdown when fewer than 2 genres", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        genreBreakdown: [{ name: "Platformer", gameCount: 5 }],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("publisher-genre-breakdown"),
    ).not.toBeInTheDocument();
  });

  it("filters games when clicking a genre chip", async () => {
    const user = userEvent.setup();
    renderPage();

    const section = screen.getByTestId("publisher-genre-breakdown");
    const platformerBtn = within(section).getByText(/Platformer \(3\)/);
    await user.click(platformerBtn);

    // After genre filter, platformer games are on NES and SNES, so we get platform grouping
    // Games may also appear in the top rated shelf, so use getAllByText
    expect(screen.getAllByText("Super Mario World").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    expect(screen.getByText("Kirby's Adventure")).toBeInTheDocument();
    // Non-platformer games should be gone from the main section
    // (Zelda:ALTTP is in top rated shelf but not in filtered platform sections)
    expect(screen.queryByText("Metroid")).not.toBeInTheDocument();
  });

  // --- Platform sections ---

  it("groups games by platform when multiple platforms present", () => {
    renderPage();
    const sections = screen.getByTestId("publisher-platform-sections");
    expect(sections).toBeInTheDocument();
    expect(
      screen.getByTestId("platform-section-NES"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("platform-section-SNES"),
    ).toBeInTheDocument();
  });

  it("orders platform sections by count descending", () => {
    renderPage();
    const sections = screen.getByTestId("publisher-platform-sections");
    const sectionHeaders = within(sections)
      .getAllByRole("heading", { level: 2 })
      .map((h) => h.textContent);
    // NES has 3 games, should come first
    expect(sectionHeaders[0]).toBe("NES");
    expect(sectionHeaders[1]).toBe("SNES");
  });

  // --- Developers section ---

  it("shows developers section with clickable chips", () => {
    renderPage();
    const section = screen.getByTestId("publisher-developers");
    expect(section).toBeInTheDocument();
    const links = within(section).getAllByRole("link");
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveTextContent("Nintendo EAD");
    expect(links[1]).toHaveTextContent("Intelligent Systems");
  });

  it("hides developers section when no developers", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        developers: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("publisher-developers"),
    ).not.toBeInTheDocument();
  });

  it("hides developers section when developers is undefined", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        developers: undefined,
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("publisher-developers"),
    ).not.toBeInTheDocument();
  });

  // --- User Stats section ---

  it("shows user stats card when userStats is present", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        userStats: {
          totalPlayTime: 14400,
          gamesPlayed: 3,
          favoriteCount: 2,
          mostPlayedGame: makeGame({
            id: "1",
            title: "Super Mario World",
            coverUrl: "/covers/smw.jpg",
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
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        companyInfo: {
          description: "A famous game publisher.",
          foundedYear: 1889,
          country: "Japan",
          wikipediaUrl: "https://en.wikipedia.org/wiki/Nintendo",
        },
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("company-info-section")).toBeInTheDocument();
    expect(screen.getByTestId("company-metadata")).toHaveTextContent(
      "Founded 1889",
    );
  });

  it("hides company info section when companyInfo is absent", () => {
    renderPage();
    expect(
      screen.queryByTestId("company-info-section"),
    ).not.toBeInTheDocument();
  });

  it("passes logoUrl to hero banner when companyInfo has logo", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        companyInfo: {
          logoUrl: "/images/companies/nintendo-logo.png",
        },
      },
      isLoading: false,
    });
    renderPage();
    const logo = screen.getByTestId("developer-logo");
    expect(logo).toHaveAttribute("src", "/images/companies/nintendo-logo.png");
    expect(screen.queryByTestId("developer-avatar")).not.toBeInTheDocument();
  });

  // --- At a Glance section ---

  it("renders at-a-glance row with basic stats", () => {
    renderPage();
    const row = screen.getByTestId("at-a-glance-row");
    expect(row).toBeInTheDocument();
    expect(screen.getByTestId("glance-total-games")).toHaveTextContent("5");
    expect(screen.getByTestId("glance-platforms")).toHaveTextContent("2");
    expect(screen.getByTestId("glance-avg-rating")).toHaveTextContent("92.3");
  });

  it("renders at-a-glance with active years when provided", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        activeYears: { first: 1983, last: 2020 },
      },
      isLoading: false,
    });
    renderPage();
    const pill = screen.getByTestId("glance-active-years");
    expect(pill).toHaveTextContent("1983");
    expect(pill).toHaveTextContent("2020");
  });

  it("renders at-a-glance with primary genre when provided", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
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
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        timeline: [
          {
            year: 1985,
            games: [
              { id: "g1", title: "Super Mario Bros.", coverUrl: "/covers/smb.jpg", rating: 93 },
            ],
          },
          {
            year: 1991,
            games: [
              { id: "g2", title: "Super Mario World", coverUrl: "/covers/smw.jpg", rating: 96 },
            ],
          },
        ],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("release-timeline")).toBeInTheDocument();
    expect(screen.getByText("1985")).toBeInTheDocument();
    expect(screen.getByText("1991")).toBeInTheDocument();
  });

  it("hides release timeline when no timeline data", () => {
    renderPage();
    expect(
      screen.queryByTestId("release-timeline"),
    ).not.toBeInTheDocument();
  });

  it("hides release timeline when timeline is empty", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
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
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        ratingDistribution: {
          excellent: 4,
          good: 3,
          average: 1,
          poor: 0,
          unrated: 1,
        },
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("rating-distribution")).toBeInTheDocument();
    expect(screen.getByTestId("rating-bar-excellent")).toHaveTextContent("4");
    expect(screen.getByTestId("rating-bar-good")).toHaveTextContent("3");
  });

  it("hides rating distribution when not provided", () => {
    renderPage();
    expect(
      screen.queryByTestId("rating-distribution"),
    ).not.toBeInTheDocument();
  });

  it("hides rating distribution when fewer than 5 rated games", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        ratingDistribution: {
          excellent: 1,
          good: 2,
          average: 0,
          poor: 0,
          unrated: 15,
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

  // --- Related Publishers section ---

  it("shows related publishers section when relatedPublishers is present", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        relatedPublishers: [
          { name: "Sega", gameCount: 20, sharedDevelopers: ["Sonic Team"] },
          { name: "Konami", gameCount: 12, sharedDevelopers: ["KCET"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const section = screen.getByTestId("related-publishers");
    expect(section).toBeInTheDocument();
    expect(section).toHaveTextContent("Related Publishers");
    expect(screen.getByTestId("related-publisher-Sega")).toBeInTheDocument();
    expect(screen.getByTestId("related-publisher-Konami")).toBeInTheDocument();
  });

  it("renders related publisher cards with game count and shared developers", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        relatedPublishers: [
          { name: "Sega", gameCount: 20, sharedDevelopers: ["Sonic Team"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-publisher-Sega");
    expect(card).toHaveTextContent("Sega");
    expect(card).toHaveTextContent("20 games");
    expect(card).toHaveTextContent("via Sonic Team");
  });

  it("links related publisher cards to their detail page", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        relatedPublishers: [
          { name: "Sega", gameCount: 20, sharedDevelopers: ["Sonic Team"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-publisher-Sega");
    expect(card).toHaveAttribute("href", "/explore/publishers/Sega");
  });

  it("hides related publishers section when empty", () => {
    renderPage();
    expect(screen.queryByTestId("related-publishers")).not.toBeInTheDocument();
  });

  it("hides related publishers section when relatedPublishers is empty array", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        relatedPublishers: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("related-publishers")).not.toBeInTheDocument();
  });

  it("renders singular game count for related publisher with 1 game", () => {
    mockUsePublisherDetail.mockReturnValue({
      data: {
        ...mockPublisherDetail,
        relatedPublishers: [
          { name: "THQ", gameCount: 1, sharedDevelopers: ["Rare"] },
        ],
      },
      isLoading: false,
    });
    renderPage();
    const card = screen.getByTestId("related-publisher-THQ");
    expect(card).toHaveTextContent("1 game");
  });
});
