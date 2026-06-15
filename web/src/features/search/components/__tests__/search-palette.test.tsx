import { render, screen, waitFor, act, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SearchPalette } from "../search-palette";
import type { SearchResults } from "@/hooks/use-search";

const mockNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

// mockGet retains the legacy signature ((path) => data) so the test bodies
// don't need to change. The wrapper around typedApi.GET reshapes its output
// into the { data, response } tuple openapi-fetch returns; unwrap then
// strips the wrapper inside the hook.
const mockGet = vi.fn();
vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: async (...args: unknown[]) => {
      const data = await mockGet(...args);
      return { data, response: new Response(null, { status: 200 }) };
    },
  },
  unwrap: <T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
    p.then((r) => {
      if (r.error !== undefined) throw r.error;
      return r.data;
    }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

/** Simulate Cmd+K (metaKey) via document keydown. */
function pressMetaK() {
  fireEvent.keyDown(document, { key: "k", metaKey: true });
}

/** Simulate Ctrl+K (ctrlKey) via document keydown. */
function pressCtrlK() {
  fireEvent.keyDown(document, { key: "k", ctrlKey: true });
}

/** Simulate Escape via document keydown. */
function pressEscape() {
  fireEvent.keyDown(document, { key: "Escape" });
}

function emptyResults(): SearchResults {
  return {
    games: { results: [], total: 0 },
    consoles: { results: [], total: 0 },
    developers: { results: [], total: 0 },
    publishers: { results: [], total: 0 },
    collections: { results: [], total: 0 },
    series: { results: [], total: 0 },
    franchises: { results: [], total: 0 },
  };
}

function resultsWithGames(): SearchResults {
  return {
    ...emptyResults(),
    games: {
      results: [
        {
          id: "game-1",
          title: "Super Mario World",
          coverUrl: "/covers/smw.jpg",
          coverAspectRatio: 0.75,
          genre: "Platformer",
          consoleId: "SNES",
          consoleName: "Super Nintendo",
          developer: "Nintendo",
        },
        {
          id: "game-2",
          title: "Mario Kart 64",
          coverUrl: "",
          coverAspectRatio: 0.75,
          genre: "Racing",
          consoleId: "N64",
          consoleName: "Nintendo 64",
          developer: "Nintendo",
        },
      ],
      total: 5,
    },
    developers: {
      results: [
        { name: "Nintendo", gameCount: 42, avgRating: 85.3 },
      ],
      total: 1,
    },
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  document.body.style.overflow = "";
});

afterEach(() => {
  document.body.style.overflow = "";
});

describe("SearchPalette", () => {
  describe("opening and closing", () => {
    it("is not visible by default", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      expect(screen.queryByTestId("search-palette")).not.toBeInTheDocument();
    });

    it("opens with Cmd+K", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      expect(screen.getByTestId("search-palette")).toBeInTheDocument();
    });

    it("opens with Ctrl+K", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressCtrlK());
      expect(screen.getByTestId("search-palette")).toBeInTheDocument();
    });

    it("opens via custom event (sidebar button)", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => {
        window.dispatchEvent(new Event("open-search-palette"));
      });
      expect(screen.getByTestId("search-palette")).toBeInTheDocument();
    });

    it("closes on Escape", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      expect(screen.getByTestId("search-palette")).toBeInTheDocument();
      act(() => pressEscape());
      expect(screen.queryByTestId("search-palette")).not.toBeInTheDocument();
    });

    it("closes on backdrop click", async () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      const overlay = screen.getByTestId("search-palette");
      // Click the overlay itself (not the dialog panel inside)
      await userEvent.click(overlay);
      expect(screen.queryByTestId("search-palette")).not.toBeInTheDocument();
    });
  });

  describe("empty and short query states", () => {
    it("shows prompt when query is empty and no recent searches", () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      expect(screen.getByText("Start typing to search...")).toBeInTheDocument();
    });

    it("shows hint when query is less than 2 characters", async () => {
      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "a");
      expect(screen.getByText("Type at least 2 characters")).toBeInTheDocument();
    });
  });

  describe("search results", () => {
    it("displays results grouped by type after typing", async () => {
      mockGet.mockResolvedValue(resultsWithGames());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "mario");

      await waitFor(() => {
        expect(screen.getByText("Games")).toBeInTheDocument();
      });

      expect(screen.getByText("Super Mario World")).toBeInTheDocument();
      expect(screen.getByText("Mario Kart 64")).toBeInTheDocument();
      expect(screen.getByText("SNES")).toBeInTheDocument();
      expect(screen.getByText("N64")).toBeInTheDocument();

      // Developer section
      expect(screen.getByText("Developers")).toBeInTheDocument();
    });

    it("shows 'total' count when there are more results than shown", async () => {
      mockGet.mockResolvedValue(resultsWithGames());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "mario");

      await waitFor(() => {
        expect(screen.getByText("5 total")).toBeInTheDocument();
      });
    });

    it("shows no results message when search returns empty", async () => {
      mockGet.mockResolvedValue(emptyResults());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "zzzzz");

      await waitFor(() => {
        expect(screen.getByText(/No results for/)).toBeInTheDocument();
      });
    });

    it("shows connected-server games in a read-only section, even while local search is still loading", async () => {
      // Path-aware: local search never resolves (stays loading), federated
      // catalog resolves with a match — the federated section must still show.
      mockGet.mockImplementation((path: string) => {
        if (
          typeof path === "string" &&
          path.includes("/api/federation/catalog/available")
        ) {
          return Promise.resolve({
            games: [
              {
                key: "igdb:9",
                title: "Chrono Trigger",
                console: "SNES",
                cover:
                  "https://images.igdb.com/igdb/image/upload/t_cover_big/ct.jpg",
                originCount: 2,
                local: false,
              },
            ],
          });
        }
        return new Promise(() => {}); // local search hangs
      });

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "chrono");

      await waitFor(() => {
        expect(
          screen.getByTestId("federated-search-section"),
        ).toBeInTheDocument();
      });

      const row = screen.getByTestId("federated-result-igdb:9");
      expect(row).toHaveTextContent("Chrono Trigger");
      expect(row).toHaveTextContent("on 2 connected servers");
      // Box art: the origin's IGDB cover URL is rendered.
      const img = row.querySelector("img");
      expect(img).toHaveAttribute(
        "src",
        "https://images.igdb.com/igdb/image/upload/t_cover_big/ct.jpg",
      );
      // Read-only: no "No results" message despite empty local results.
      expect(screen.queryByText(/No results for/)).not.toBeInTheDocument();
    });

    it("navigates and closes when clicking a result", async () => {
      mockGet.mockResolvedValue(resultsWithGames());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "mario");

      await waitFor(() => {
        expect(screen.getByText("Super Mario World")).toBeInTheDocument();
      });

      await userEvent.click(screen.getByTestId("search-result-game-game-1"));

      expect(mockNavigate).toHaveBeenCalledWith("/games/game-1");
      // Palette should close
      expect(screen.queryByTestId("search-palette")).not.toBeInTheDocument();
    });
  });

  describe("loading state", () => {
    it("shows loading spinner while fetching", async () => {
      // Never resolve the promise so we stay in loading
      mockGet.mockReturnValue(new Promise(() => {}));

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "mario");

      await waitFor(() => {
        expect(screen.getAllByTestId("search-loading").length).toBeGreaterThanOrEqual(1);
      });
    });
  });

  describe("recent searches", () => {
    it("shows recent searches when palette opens with empty query", () => {
      localStorage.setItem(
        "spela-recent-searches",
        JSON.stringify(["zelda", "metroid"]),
      );

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());

      expect(screen.getByText("Recent searches")).toBeInTheDocument();
      expect(screen.getByText("zelda")).toBeInTheDocument();
      expect(screen.getByText("metroid")).toBeInTheDocument();
    });

    it("removes a recent search when clicking X", async () => {
      localStorage.setItem(
        "spela-recent-searches",
        JSON.stringify(["zelda", "metroid"]),
      );

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());

      await userEvent.click(screen.getByLabelText("Remove zelda"));
      expect(screen.queryByText("zelda")).not.toBeInTheDocument();
      expect(screen.getByText("metroid")).toBeInTheDocument();
    });

    it("clears all recent searches", async () => {
      localStorage.setItem(
        "spela-recent-searches",
        JSON.stringify(["zelda", "metroid"]),
      );

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());

      await userEvent.click(screen.getByText("Clear all"));
      expect(screen.queryByText("zelda")).not.toBeInTheDocument();
      expect(screen.queryByText("metroid")).not.toBeInTheDocument();
      expect(screen.getByText("Start typing to search...")).toBeInTheDocument();
    });

    it("fills search input when clicking a recent search", async () => {
      localStorage.setItem(
        "spela-recent-searches",
        JSON.stringify(["zelda"]),
      );

      mockGet.mockResolvedValue(emptyResults());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());

      await userEvent.click(screen.getByText("zelda"));

      const input = screen.getByLabelText("Search input") as HTMLInputElement;
      expect(input.value).toBe("zelda");
    });

    it("saves search term when navigating to a result", async () => {
      mockGet.mockResolvedValue(resultsWithGames());

      render(<SearchPalette />, { wrapper: createWrapper() });
      act(() => pressMetaK());
      await userEvent.type(screen.getByLabelText("Search input"), "mario");

      await waitFor(() => {
        expect(screen.getByText("Super Mario World")).toBeInTheDocument();
      });

      await userEvent.click(screen.getByTestId("search-result-game-game-1"));

      const stored = JSON.parse(
        localStorage.getItem("spela-recent-searches") ?? "[]",
      );
      expect(stored).toContain("mario");
    });
  });
});
