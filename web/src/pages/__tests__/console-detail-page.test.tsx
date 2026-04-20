import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ConsoleDetailPage } from "@/pages/console-detail-page";
import type { Console, Game, GamesResponse } from "@/types/api";

// Mock hooks
vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(),
}));

vi.mock("@/hooks/use-games", () => ({
  useGames: vi.fn(),
  useToggleFavorite: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: () => ({ toggle: vi.fn() }),
}));

vi.mock("@/hooks/use-bios", () => ({
  useBiosStatus: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({ isAdmin: false }),
}));

vi.mock("@/hooks/use-admin", () => ({
  useScanLibrary: () => ({ mutate: vi.fn(), isPending: false }),
}));

vi.mock("@/hooks/use-debounced-value", () => ({
  useDebouncedValue: (v: string) => v,
}));

vi.mock("@/components/ui", async () => {
  const actual = await vi.importActual("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

vi.mock("@/hooks/use-explore", () => ({
  useConsoleShowcase: () => ({ data: undefined }),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

import { useConsoles } from "@/hooks/use-consoles";
import { useGames } from "@/hooks/use-games";

const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;
const mockUseGames = useGames as ReturnType<typeof vi.fn>;

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

function makeConsole(overrides: Partial<Console> = {}): Console {
  return {
    id: "snes",
    code: "snes",
    name: "Super Nintendo",
    abbreviation: "snes",
    extensions: [".sfc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "#6366f1",
    generation: 4,
    iconUrl: "",
    logoUrl: "",
    gameCount: 100,
    saveStateSupport: true,
    browserPlayable: false,
    playable: true,
    maker: null,
    mediaType: null,
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "",
    updatedAt: "",
    ...overrides,
  };
}

function renderPage(consoleId = "snes") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/consoles/${consoleId}`]}>
        <Routes>
          <Route
            path="/consoles/:id"
            element={<ConsoleDetailPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe("ConsoleDetailPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseGames.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 48 },
      isLoading: false,
    });
  });

  describe("large library (> 24 games)", () => {
    beforeEach(() => {
      mockUseConsoles.mockReturnValue({
        data: [makeConsole({ gameCount: 100 })],
      });
    });

    it("renders console name in hero banner", () => {
      renderPage();
      expect(
        screen.getByRole("heading", { name: "Super Nintendo", level: 1 }),
      ).toBeInTheDocument();
    });

    it("renders back to consoles button", () => {
      renderPage();
      expect(
        screen.getByTestId("page-back-button"),
      ).toBeInTheDocument();
    });

    it("renders game count in hero banner", () => {
      renderPage();
      expect(screen.getByText("100 games")).toBeInTheDocument();
    });

    it("renders browse games link in the banner", () => {
      renderPage();
      const bannerLink = screen.getByTestId("banner-browse-games");
      expect(bannerLink).toBeInTheDocument();
      expect(bannerLink).toHaveTextContent("Browse 100 games");
      expect(bannerLink).toHaveAttribute("href", "/consoles/snes/games");
    });

    it("does not render inline search input", () => {
      renderPage();
      expect(
        screen.queryByPlaceholderText(/search super nintendo games/i),
      ).not.toBeInTheDocument();
    });
  });

  describe("small library (<= 24 games)", () => {
    const smallGames: GamesResponse = {
      data: [
        makeGame({ id: "1", title: "Super Mario World" }),
        makeGame({ id: "2", title: "Chrono Trigger" }),
      ],
      total: 2,
      page: 1,
      pageSize: 24,
    };

    beforeEach(() => {
      mockUseConsoles.mockReturnValue({
        data: [makeConsole({ gameCount: 10 })],
      });
      mockUseGames.mockReturnValue({
        data: smallGames,
        isLoading: false,
      });
    });

    it("renders inline search input", () => {
      renderPage();
      expect(
        screen.getByPlaceholderText(/search super nintendo games/i),
      ).toBeInTheDocument();
    });

    it("renders game cards inline", () => {
      renderPage();
      expect(screen.getByText("Super Mario World")).toBeInTheDocument();
      expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
    });

    it("hides console name and shows release year on game cards", () => {
      mockUseGames.mockReturnValue({
        data: {
          data: [
            makeGame({ id: "1", title: "Super Mario World", consoleName: "Super Nintendo", releaseDate: "1990-11-21" }),
            makeGame({ id: "2", title: "Chrono Trigger", consoleName: "Super Nintendo", releaseDate: "1995-03-11" }),
          ],
          total: 2,
          page: 1,
          pageSize: 24,
        },
        isLoading: false,
      });
      renderPage();

      // Game titles are visible
      expect(screen.getByText("Super Mario World")).toBeInTheDocument();
      expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();

      // Console name "Super Nintendo" should NOT appear below game cards
      // (it will appear in the hero banner heading, but not repeated on every card)
      const gameCardLinks = screen.getAllByRole("link").filter(
        (el) => el.getAttribute("href")?.startsWith("/games/"),
      );
      gameCardLinks.forEach((card) => {
        // No element within the card should display the console name as subtitle text
        const texts = Array.from(card.querySelectorAll("p")).map((p) => p.textContent);
        expect(texts).not.toContain("Super Nintendo");
      });

      // Instead, release year should be shown
      expect(screen.getByText("1990")).toBeInTheDocument();
      expect(screen.getByText("1995")).toBeInTheDocument();
    });

    it("does not render browse all games link", () => {
      renderPage();
      expect(screen.queryByTestId("banner-browse-games")).not.toBeInTheDocument();
    });

    it("shows loading skeletons while data is loading", () => {
      mockUseGames.mockReturnValue({
        data: undefined,
        isLoading: true,
      });
      renderPage();
      expect(
        screen.getByPlaceholderText(/search super nintendo games/i),
      ).toBeInTheDocument();
      // Should not show empty state or game cards
      expect(screen.queryByText("No games found")).not.toBeInTheDocument();
    });
  });

  describe("empty library", () => {
    beforeEach(() => {
      mockUseConsoles.mockReturnValue({
        data: [makeConsole({ gameCount: 0 })],
      });
    });

    it("renders empty state", () => {
      renderPage();
      expect(screen.getByText("No games found")).toBeInTheDocument();
    });

    it("does not render browse all games link", () => {
      renderPage();
      expect(screen.queryByTestId("banner-browse-games")).not.toBeInTheDocument();
    });
  });
});
