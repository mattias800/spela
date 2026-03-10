import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ConsoleShowcasePage } from "@/pages/console-showcase-page";
import type {
  ConsoleShowcase,
  Console,
  Game,
  GenreCount,
  DeveloperSummary,
} from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-explore", () => ({
  useConsoleShowcase: vi.fn(),
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

import { useConsoleShowcase } from "@/hooks/use-explore";

const mockUseConsoleShowcase = useConsoleShowcase as ReturnType<typeof vi.fn>;

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

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

function makeConsole(overrides: Partial<Console> = {}): Console {
  return {
    id: "snes",
    name: "Super Nintendo",
    abbreviation: "snes",
    extensions: [".sfc", ".smc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "#805ad5",
    iconUrl: "/icons/snes.png",
    logoUrl: "/logos/snes.png",
    gameCount: 42,
    saveStateSupport: true,
    browserPlayable: true,
    playable: true,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const mockShowcase: ConsoleShowcase = {
  console: makeConsole(),
  essentials: [
    makeGame({ id: "e1", title: "Chrono Trigger", averageRating: 95 }),
    makeGame({ id: "e2", title: "Super Metroid", averageRating: 94 }),
  ],
  hiddenGems: [
    makeGame({ id: "h1", title: "EarthBound", averageRating: 88 }),
    makeGame({ id: "h2", title: "Terranigma", averageRating: 85 }),
  ],
  genreBreakdown: [
    { name: "RPG", gameCount: 15 } as GenreCount,
    { name: "Action", gameCount: 10 } as GenreCount,
    { name: "Platformer", gameCount: 8 } as GenreCount,
  ],
  topDevelopers: [
    {
      name: "Square",
      gameCount: 12,
      avgRating: 90.5,
      consoles: ["SNES"],
    } as DeveloperSummary,
    {
      name: "Capcom",
      gameCount: 6,
      avgRating: 85.2,
      consoles: ["SNES"],
    } as DeveloperSummary,
  ],
  recentlyPlayed: [
    makeGame({ id: "r1", title: "Final Fantasy VI" }),
    makeGame({ id: "r2", title: "Super Mario World" }),
  ],
};

function renderPage(consoleId = "snes") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/explore/consoles/${consoleId}`]}>
        <Routes>
          <Route
            path="/explore/consoles/:id"
            element={<ConsoleShowcasePage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ConsoleShowcasePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseConsoleShowcase.mockReturnValue({
      data: mockShowcase,
      isLoading: false,
    });
  });

  it("renders the page with test id", () => {
    renderPage();
    expect(screen.getByTestId("console-showcase-page")).toBeInTheDocument();
  });

  it("renders loading skeleton while loading", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderPage();
    expect(
      screen.getByTestId("console-showcase-skeleton"),
    ).toBeInTheDocument();
  });

  it("renders console name", () => {
    renderPage();
    expect(screen.getByTestId("console-name")).toHaveTextContent(
      "Super Nintendo",
    );
  });

  it("renders console stats with game count", () => {
    renderPage();
    expect(screen.getByTestId("console-stats")).toHaveTextContent(
      "42 games in library",
    );
  });

  it("renders console logo", () => {
    renderPage();
    expect(screen.getByTestId("console-logo")).toBeInTheDocument();
  });

  it("renders essentials shelf with games", () => {
    renderPage();
    expect(screen.getByTestId("shelf-Essentials")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
    expect(screen.getByText("Super Metroid")).toBeInTheDocument();
  });

  it("renders hidden gems shelf with games", () => {
    renderPage();
    expect(screen.getByTestId("shelf-Hidden Gems")).toBeInTheDocument();
    expect(screen.getByText("EarthBound")).toBeInTheDocument();
    expect(screen.getByText("Terranigma")).toBeInTheDocument();
  });

  it("renders genre breakdown", () => {
    renderPage();
    const genreSection = screen.getByTestId("genre-breakdown");
    expect(genreSection).toBeInTheDocument();
    expect(screen.getByTestId("genre-card-RPG")).toBeInTheDocument();
    expect(screen.getByTestId("genre-card-Action")).toBeInTheDocument();
    expect(screen.getByTestId("genre-card-Platformer")).toBeInTheDocument();
    // Check genre names are rendered
    expect(screen.getByText("RPG")).toBeInTheDocument();
    expect(screen.getByText("Action")).toBeInTheDocument();
    expect(screen.getByText("Platformer")).toBeInTheDocument();
  });

  it("renders top developers", () => {
    renderPage();
    expect(screen.getByTestId("top-developers")).toBeInTheDocument();
    expect(screen.getByTestId("developer-card-Square")).toBeInTheDocument();
    expect(screen.getByTestId("developer-card-Capcom")).toBeInTheDocument();
    expect(screen.getByText("Square")).toBeInTheDocument();
    expect(screen.getByText("Capcom")).toBeInTheDocument();
  });

  it("renders developer cards as links to developer detail", () => {
    renderPage();
    const squareLink = screen.getByTestId("developer-card-Square");
    expect(squareLink.tagName).toBe("A");
    expect(squareLink).toHaveAttribute(
      "href",
      "/explore/developers/Square",
    );
  });

  it("renders recently played when present", () => {
    renderPage();
    expect(
      screen.getByTestId("recently-played-section"),
    ).toBeInTheDocument();
    expect(screen.getByText("Final Fantasy VI")).toBeInTheDocument();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
  });

  it("hides recently played when empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        recentlyPlayed: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("recently-played-section"),
    ).not.toBeInTheDocument();
  });

  it("back button calls navigate(-1)", async () => {
    const user = userEvent.setup();
    renderPage();
    const backButton = screen.getByRole("button", {
      name: /back to explore/i,
    });
    await user.click(backButton);
    expect(mockNavigate).toHaveBeenCalledWith(-1);
  });

  it("shows not found when console does not exist", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage("unknown");
    expect(screen.getByText("Console not found")).toBeInTheDocument();
  });

  it("hides essentials shelf when empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        essentials: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("shelf-Essentials")).not.toBeInTheDocument();
  });

  it("hides hidden gems shelf when empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        hiddenGems: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.queryByTestId("shelf-Hidden Gems"),
    ).not.toBeInTheDocument();
  });

  it("hides genre breakdown when empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        genreBreakdown: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("genre-breakdown")).not.toBeInTheDocument();
  });

  it("hides top developers when empty", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        topDevelopers: [],
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.queryByTestId("top-developers")).not.toBeInTheDocument();
  });

  it("renders singular game count correctly", () => {
    mockUseConsoleShowcase.mockReturnValue({
      data: {
        ...mockShowcase,
        console: makeConsole({ gameCount: 1 }),
      },
      isLoading: false,
    });
    renderPage();
    expect(screen.getByTestId("console-stats")).toHaveTextContent(
      "1 game in library",
    );
  });
});
