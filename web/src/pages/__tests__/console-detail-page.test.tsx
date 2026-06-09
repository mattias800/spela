import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ConsoleDetailPage } from "@/pages/console-detail-page";
import type { GamesResponse } from "@/types/api";

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
  useConsoleShowcase: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/hooks/use-auto-scrape", () => ({
  useAutoScrape: () => ({ ref: { current: null }, isScraping: false }),
}));

import { useConsoles } from "@/hooks/use-consoles";
import { useGames } from "@/hooks/use-games";
import { useConsoleShowcase } from "@/hooks/use-explore";
import { makeGame, makeConsole } from "@/test-utils/fixtures";

const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;
const mockUseGames = useGames as ReturnType<typeof vi.fn>;
const mockUseConsoleShowcase = useConsoleShowcase as ReturnType<typeof vi.fn>;



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
        data: [makeConsole({ id: "snes", name: "Super Nintendo", abbreviation: "snes", gameCount: 100 })],
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

    it("renders the Browse-all CTA above the curated shelves, aligned with the player app (#1312)", () => {
      // #1095 moved the Browse link out of the hero banner into a
      // "Library" section. It originally lived at the *bottom*, below
      // the showcase shelves. #1312 moves it to the *top* — directly
      // under the hero banner, above the shelves — to match the player
      // app's console screen (ConsoleScreen.kt), where "Browse all" is
      // the first section under the banner.
      mockUseConsoleShowcase.mockReturnValue({
        data: {
          console: makeConsole({ id: "snes", name: "Super Nintendo" }),
          essentials: [],
          launchGames: [],
          hiddenGems: [],
          recentlyPlayed: [makeGame({ id: "rp1", title: "Half-Played Game" })],
          recentlyAdded: [],
          topDevelopers: [],
        },
      });
      renderPage();

      const cta = screen.getByTestId("browse-all-games-cta");
      expect(cta).toBeInTheDocument();
      expect(cta).toHaveTextContent("Browse all 100 Super Nintendo games");
      expect(cta).toHaveAttribute("href", "/consoles/snes/games");

      // The CTA must render before the first curated shelf in DOM order.
      const shelf = screen.getByTestId("recently-played-section");
      expect(
        cta.compareDocumentPosition(shelf) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
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
        data: [makeConsole({ id: "snes", name: "Super Nintendo", abbreviation: "snes", gameCount: 10 })],
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
      // #1095: banner is now pure identity, Browse-all CTA only renders
      // on libraries with >24 games (the small-library / empty branches
      // stay free of it). New test ID is `browse-all-games-cta`.
      expect(screen.queryByTestId("browse-all-games-cta")).not.toBeInTheDocument();
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

    // #633 — curated shelves should render above the inline grid on
    // small libraries when the server provides matching content. The
    // previous behaviour hid them entirely, which hurt small libraries
    // the most (a user with 6 Virtual Boy games never saw the Launch
    // Games shelf). Each shelf self-hides on empty, so no visual
    // noise for libraries where the curated lists are empty.

    it("renders Essentials, Launch Games, and Hidden Gems shelves when the showcase returns content", () => {
      mockUseConsoleShowcase.mockReturnValue({
        data: {
          console: makeConsole({ id: "snes", name: "Super Nintendo" }),
          essentials: [makeGame({ id: "10", title: "Mega Man X" })],
          launchGames: [makeGame({ id: "11", title: "Super Mario World" })],
          hiddenGems: [makeGame({ id: "12", title: "Terranigma" })],
          recentlyPlayed: [],
          recentlyAdded: [],
          topDevelopers: [],
        },
      });
      renderPage();

      expect(screen.getByText("Essentials")).toBeInTheDocument();
      expect(screen.getByText("Launch Games")).toBeInTheDocument();
      expect(screen.getByText("Hidden Gems")).toBeInTheDocument();
    });

    it("hides lower-priority curated shelves on small libraries even when populated", () => {
      // Top Developers / Recently Added are intentionally omitted from the
      // small-library branch — they lose signal when the game pool is small.
      mockUseConsoleShowcase.mockReturnValue({
        data: {
          console: makeConsole({ id: "snes", name: "Super Nintendo" }),
          essentials: [],
          launchGames: [],
          hiddenGems: [],
          recentlyPlayed: [],
          recentlyAdded: [makeGame({ id: "20", title: "Just Added" })],
          topDevelopers: [{ id: "dev1", name: "Nintendo EAD", gameCount: 5 }],
        },
      });
      renderPage();

      expect(screen.queryByText(/top developers/i)).not.toBeInTheDocument();
      expect(screen.queryByText("Just Added")).not.toBeInTheDocument();
    });
  });

  describe("empty library", () => {
    beforeEach(() => {
      mockUseConsoles.mockReturnValue({
        data: [makeConsole({ id: "snes", name: "Super Nintendo", abbreviation: "snes", gameCount: 0 })],
      });
    });

    it("renders empty state", () => {
      renderPage();
      expect(screen.getByText("No games found")).toBeInTheDocument();
    });

    it("does not render browse all games link", () => {
      renderPage();
      // #1095: banner is now pure identity, Browse-all CTA only renders
      // on libraries with >24 games (the small-library / empty branches
      // stay free of it). New test ID is `browse-all-games-cta`.
      expect(screen.queryByTestId("browse-all-games-cta")).not.toBeInTheDocument();
    });
  });
});
