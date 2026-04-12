import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ExploreSeriesPage } from "@/pages/explore-series-page";
import type { SeriesDetail, SeriesGame, SeriesConsole } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useSeriesDetail: vi.fn(),
}));

import { useSeriesDetail } from "@/hooks/use-explore";

const mockUseSeriesDetail = useSeriesDetail as ReturnType<typeof vi.fn>;

function makeSeriesGame(overrides: Partial<SeriesGame> = {}): SeriesGame {
  return {
    igdbGameId: 1,
    name: "Test Game",
    inLibrary: true,
    localGameId: "game-1",
    coverUrl: "/cover/test.jpg",
    releaseDate: "1990-01-01",
    igdbCriticsRating: 85,
    consoleAbbreviation: "snes",
    consoleName: "SNES",
    consoleColor: "#805ad5",
    ...overrides,
  };
}

function makeSeriesConsole(
  overrides: Partial<SeriesConsole> = {},
): SeriesConsole {
  return {
    abbreviation: "snes",
    name: "SNES",
    color: "#805ad5",
    gameCount: 3,
    ...overrides,
  };
}

const mockSeriesDetail: SeriesDetail = {
  id: "1",
  name: "Super Mario",
  heroUrl: "/hero/mario.jpg",
  consoles: [
    makeSeriesConsole({ abbreviation: "nes", name: "NES", color: "#e53e3e", gameCount: 2 }),
    makeSeriesConsole({ abbreviation: "snes", name: "SNES", color: "#805ad5", gameCount: 3 }),
  ],
  libraryGames: 3,
  totalGames: 5,
  games: [
    makeSeriesGame({
      igdbGameId: 1,
      name: "Super Mario Bros.",
      inLibrary: true,
      localGameId: "game-1",
      releaseDate: "1985-09-13",
      consoleAbbreviation: "nes",
      consoleName: "NES",
    }),
    makeSeriesGame({
      igdbGameId: 2,
      name: "Super Mario Bros. 2",
      inLibrary: true,
      localGameId: "game-2",
      releaseDate: "1988-10-09",
      consoleAbbreviation: "nes",
      consoleName: "NES",
    }),
    makeSeriesGame({
      igdbGameId: 3,
      name: "Super Mario World",
      inLibrary: true,
      localGameId: "game-3",
      releaseDate: "1990-11-21",
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
    makeSeriesGame({
      igdbGameId: 4,
      name: "Super Mario World 2: Yoshi's Island",
      inLibrary: false,
      localGameId: null,
      releaseDate: "1995-08-05",
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
    makeSeriesGame({
      igdbGameId: 5,
      name: "Super Mario Unknown",
      inLibrary: false,
      localGameId: null,
      releaseDate: null,
      igdbCriticsRating: 0,
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
  ],
};

function renderPage(seriesId = "1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/series/${seriesId}`]}>
        <Routes>
          <Route
            path="/explore/series/:id"
            element={<ExploreSeriesPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreSeriesPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseSeriesDetail.mockReturnValue({
      data: mockSeriesDetail,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("series-detail-page")).toBeInTheDocument();
  });

  it("renders series name in hero banner", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Super Mario", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /explore/i }),
    ).toBeInTheDocument();
  });

  it("renders ownership progress", () => {
    renderPage();
    const progress = screen.getByTestId("ownership-progress");
    expect(progress).toBeInTheDocument();
    expect(
      within(progress).getByText("You own 3 of 5 games"),
    ).toBeInTheDocument();
  });

  it("renders progress bar with correct value", () => {
    renderPage();
    const progressBar = screen.getByRole("progressbar");
    expect(progressBar).toHaveAttribute("aria-valuenow", "3");
    expect(progressBar).toHaveAttribute("aria-valuemax", "5");
  });

  it("renders console filter badges", () => {
    renderPage();
    const filters = screen.getByTestId("console-filters");
    expect(within(filters).getByText(/All \(/)).toBeInTheDocument();
    const buttons = within(filters).getAllByRole("button");
    const buttonTexts = buttons.map((b) => b.textContent);
    expect(buttonTexts).toContainEqual(expect.stringContaining("NES"));
    expect(buttonTexts).toContainEqual(expect.stringContaining("SNES"));
  });

  it("renders games in timeline", () => {
    renderPage();
    const timeline = screen.getByTestId("series-timeline");
    expect(
      within(timeline).getByText("Super Mario Bros."),
    ).toBeInTheDocument();
    expect(
      within(timeline).getByText("Super Mario World"),
    ).toBeInTheDocument();
    expect(
      within(timeline).getByText("Super Mario World 2: Yoshi's Island"),
    ).toBeInTheDocument();
  });

  it("renders games in chronological order", () => {
    renderPage();
    const timeline = screen.getByTestId("series-timeline");
    const gameNames = within(timeline)
      .getAllByRole("heading", { level: 3 })
      .map((el) => el.textContent);
    expect(gameNames[0]).toBe("Super Mario Bros.");
    expect(gameNames[1]).toBe("Super Mario Bros. 2");
    expect(gameNames[2]).toBe("Super Mario World");
    expect(gameNames[3]).toBe("Super Mario World 2: Yoshi's Island");
    // Game without release date sorts to end
    expect(gameNames[4]).toBe("Super Mario Unknown");
  });

  it("renders in-library games as links", () => {
    renderPage();
    const link = screen.getByText("Super Mario Bros.").closest("a");
    expect(link).toHaveAttribute("href", "/games/game-1");
  });

  it("renders not-in-library games without dimming", () => {
    renderPage();
    const notInLibraryGame = screen.getByTestId("series-game-4");
    expect(notInLibraryGame).not.toHaveClass("opacity-50");
  });

  it("shows not in library text for games not in library", () => {
    renderPage();
    expect(screen.getAllByText("Not in library")).toHaveLength(2);
  });

  it("filters games by console", async () => {
    const user = userEvent.setup();
    renderPage();

    const filters = screen.getByTestId("console-filters");
    // Find the NES button (not SNES) by matching exact text content
    const buttons = within(filters).getAllByRole("button");
    const nesButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("NES"),
    );
    expect(nesButton).toBeDefined();
    await user.click(nesButton!);

    const timeline = screen.getByTestId("series-timeline");
    const gameHeadings = within(timeline).getAllByRole("heading", { level: 3 });

    // Only NES games should be shown (2 NES games from mock data)
    expect(gameHeadings).toHaveLength(2);
    expect(gameHeadings[0].textContent).toBe("Super Mario Bros.");
    expect(gameHeadings[1].textContent).toBe("Super Mario Bros. 2");
  });

  it("shows loading skeleton while loading", () => {
    mockUseSeriesDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("series-detail-skeleton")).toBeInTheDocument();
  });

  it("shows not found when series does not exist", () => {
    mockUseSeriesDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("999");
    expect(screen.getByText("Series not found")).toBeInTheDocument();
  });

  it("renders hero image when heroUrl is present", () => {
    renderPage();
    const heroImg = screen
      .getByTestId("series-detail-page")
      .querySelector("img[src='/hero/mario.jpg']");
    expect(heroImg).toBeInTheDocument();
  });

  it("renders release year for games", () => {
    renderPage();
    expect(screen.getByText("1985")).toBeInTheDocument();
    expect(screen.getByText("1990")).toBeInTheDocument();
  });
});
