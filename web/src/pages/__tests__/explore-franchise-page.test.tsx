import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ExploreFranchisePage } from "@/pages/explore-franchise-page";
import type { FranchiseDetail, SeriesGame, SeriesConsole } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useFranchiseDetail: vi.fn(),
}));

import { useFranchiseDetail } from "@/hooks/use-explore";

const mockUseFranchiseDetail = useFranchiseDetail as ReturnType<typeof vi.fn>;

function makeFranchiseGame(overrides: Partial<SeriesGame> = {}): SeriesGame {
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

function makeFranchiseConsole(
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

const mockFranchiseDetail: FranchiseDetail = {
  id: "1",
  igdbFranchiseId: 500,
  name: "Mario",
  logoUrl: "",
  heroUrl: "/hero/mario-franchise.jpg",
  consoles: [
    makeFranchiseConsole({ abbreviation: "nes", name: "NES", color: "#e53e3e", gameCount: 2 }),
    makeFranchiseConsole({ abbreviation: "snes", name: "SNES", color: "#805ad5", gameCount: 3 }),
  ],
  libraryGames: 3,
  totalGames: 5,
  games: [
    makeFranchiseGame({
      igdbGameId: 1,
      name: "Super Mario Bros.",
      inLibrary: true,
      localGameId: "game-1",
      releaseDate: "1985-09-13",
      consoleAbbreviation: "nes",
      consoleName: "NES",
    }),
    makeFranchiseGame({
      igdbGameId: 2,
      name: "Mario Kart",
      inLibrary: true,
      localGameId: "game-2",
      releaseDate: "1992-08-27",
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
    makeFranchiseGame({
      igdbGameId: 3,
      name: "Paper Mario",
      inLibrary: true,
      localGameId: "game-3",
      releaseDate: "2000-08-11",
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
    makeFranchiseGame({
      igdbGameId: 4,
      name: "Mario Party",
      inLibrary: false,
      localGameId: null,
      releaseDate: "1998-12-18",
      consoleAbbreviation: "nes",
      consoleName: "NES",
    }),
    makeFranchiseGame({
      igdbGameId: 5,
      name: "Mario Unknown",
      inLibrary: false,
      localGameId: null,
      releaseDate: "",
      igdbCriticsRating: 0,
      consoleAbbreviation: "snes",
      consoleName: "SNES",
    }),
  ],
};

function renderPage(franchiseId = "1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/franchise/${franchiseId}`]}>
        <Routes>
          <Route
            path="/explore/franchise/:id"
            element={<ExploreFranchisePage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ExploreFranchisePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseFranchiseDetail.mockReturnValue({
      data: mockFranchiseDetail,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("franchise-detail-page")).toBeInTheDocument();
  });

  it("renders franchise name in hero banner", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: "Mario", level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders back button", () => {
    renderPage();
    expect(
      screen.getByTestId("page-back-button"),
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
    const timeline = screen.getByTestId("franchise-timeline");
    expect(
      within(timeline).getByText("Super Mario Bros."),
    ).toBeInTheDocument();
    expect(
      within(timeline).getByText("Mario Kart"),
    ).toBeInTheDocument();
    expect(
      within(timeline).getByText("Mario Party"),
    ).toBeInTheDocument();
  });

  it("renders games in chronological order", () => {
    renderPage();
    const timeline = screen.getByTestId("franchise-timeline");
    const gameNames = within(timeline)
      .getAllByRole("heading", { level: 3 })
      .map((el) => el.textContent);
    expect(gameNames[0]).toBe("Super Mario Bros.");
    expect(gameNames[1]).toBe("Mario Kart");
    expect(gameNames[2]).toBe("Mario Party");
    expect(gameNames[3]).toBe("Paper Mario");
    // Game without release date sorts to end
    expect(gameNames[4]).toBe("Mario Unknown");
  });

  it("renders in-library games as links", () => {
    renderPage();
    const link = screen.getByText("Super Mario Bros.").closest("a");
    expect(link).toHaveAttribute("href", "/games/game-1");
  });

  it("renders not-in-library games without dimming", () => {
    renderPage();
    const notInLibraryGame = screen.getByTestId("franchise-game-4");
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
    const buttons = within(filters).getAllByRole("button");
    const nesButton = buttons.find(
      (b) => b.textContent?.trim().startsWith("NES"),
    );
    expect(nesButton).toBeDefined();
    await user.click(nesButton!);

    const timeline = screen.getByTestId("franchise-timeline");
    const gameHeadings = within(timeline).getAllByRole("heading", { level: 3 });

    // Only NES games should be shown (2 NES games from mock data)
    expect(gameHeadings).toHaveLength(2);
    expect(gameHeadings[0].textContent).toBe("Super Mario Bros.");
    expect(gameHeadings[1].textContent).toBe("Mario Party");
  });

  it("shows loading skeleton while loading", () => {
    mockUseFranchiseDetail.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(screen.getByTestId("franchise-detail-skeleton")).toBeInTheDocument();
  });

  it("shows not found when franchise does not exist", () => {
    mockUseFranchiseDetail.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("999");
    expect(screen.getByText("Franchise not found")).toBeInTheDocument();
  });

  it("renders hero image when heroUrl is present", () => {
    renderPage();
    const heroImg = screen
      .getByTestId("franchise-detail-page")
      .querySelector("img[src='/hero/mario-franchise.jpg']");
    expect(heroImg).toBeInTheDocument();
  });

  it("renders release year for games", () => {
    renderPage();
    expect(screen.getByText("1985")).toBeInTheDocument();
    expect(screen.getByText("1992")).toBeInTheDocument();
  });
});
