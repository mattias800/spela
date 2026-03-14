import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { TopListsPage } from "../top-lists-page";

vi.mock("@/hooks/use-top-lists", () => ({
  useTopRated: vi.fn(),
  useLongestGames: vi.fn(),
}));

vi.mock("@/hooks/use-play-stats", () => ({
  usePlayStats: vi.fn(),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import { useTopRated, useLongestGames } from "@/hooks/use-top-lists";
import { usePlayStats } from "@/hooks/use-play-stats";

const mockUseTopRated = useTopRated as ReturnType<typeof vi.fn>;
const mockUseLongestGames = useLongestGames as ReturnType<typeof vi.fn>;
const mockUsePlayStats = usePlayStats as ReturnType<typeof vi.fn>;

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

const mockTopRatedGames = [
  {
    rank: 1,
    gameId: "42",
    name: "Super Mario World",
    coverUrl: "/api/images/covers/smw.jpg",
    consoleName: "SNES",
    consoleId: "2",
    rating: 92.5,
  },
  {
    rank: 2,
    gameId: "99",
    name: "The Legend of Zelda",
    coverUrl: "/api/images/covers/zelda.jpg",
    consoleName: "NES",
    consoleId: "1",
    rating: 91.0,
  },
  {
    rank: 3,
    gameId: "55",
    name: "Chrono Trigger",
    coverUrl: "/api/images/covers/ct.jpg",
    consoleName: "SNES",
    consoleId: "2",
    rating: 90.3,
  },
];

// TTB values from IGDB API are in seconds
const mockLongestGames = [
  {
    rank: 1,
    gameId: "10",
    name: "Dragon Quest VII",
    coverUrl: "/api/images/covers/dq7.jpg",
    consoleName: "PS1",
    consoleId: "ps1",
    timeToBeatNormally: 100 * 3600,
    timeToBeatHastily: 80 * 3600,
    timeToBeatCompletely: 150 * 3600,
  },
  {
    rank: 2,
    gameId: "20",
    name: "Final Fantasy Tactics",
    coverUrl: "/api/images/covers/fft.jpg",
    consoleName: "PS1",
    consoleId: "ps1",
    timeToBeatNormally: 54 * 3600,
    timeToBeatHastily: 40 * 3600,
    timeToBeatCompletely: 80 * 3600,
  },
  {
    rank: 3,
    gameId: "30",
    name: "Tactics Ogre",
    coverUrl: "",
    consoleName: "SNES",
    consoleId: "snes",
    timeToBeatNormally: 48 * 3600,
    timeToBeatHastily: 0,
    timeToBeatCompletely: 0,
  },
];

const mockPlayStats = [
  { gameId: 42, playTime: 3600, lastPlayedAt: "2026-03-01T12:00:00Z" },
  { gameId: 55, playTime: 7200, lastPlayedAt: "2026-02-28T08:00:00Z" },
];

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <TopListsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseTopRated.mockReturnValue({
    data: mockTopRatedGames,
    isLoading: false,
  });
  mockUseLongestGames.mockReturnValue({
    data: mockLongestGames,
    isLoading: false,
  });
  mockUsePlayStats.mockReturnValue({
    data: mockPlayStats,
    isLoading: false,
  });
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("TopListsPage", () => {
  it("renders page heading", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Top Lists/i, level: 1 }),
    ).toBeInTheDocument();
  });

  it("renders updated subtitle", () => {
    renderPage();
    expect(
      screen.getByText("Discover the best and biggest games in your library."),
    ).toBeInTheDocument();
  });

  it("renders tab bar with both tabs", () => {
    renderPage();
    const tablist = screen.getByRole("tablist");
    expect(tablist).toBeInTheDocument();

    const tabs = screen.getAllByRole("tab");
    expect(tabs).toHaveLength(2);
    expect(tabs[0]).toHaveTextContent("Top Rated");
    expect(tabs[1]).toHaveTextContent("Longest Games");
  });

  it("shows Top Rated tab as active by default", () => {
    renderPage();
    const tabs = screen.getAllByRole("tab");
    expect(tabs[0]).toHaveAttribute("aria-selected", "true");
    expect(tabs[1]).toHaveAttribute("aria-selected", "false");
  });

  it("renders section heading for Top Rated", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Top Rated Games/i, level: 2 }),
    ).toBeInTheDocument();
  });

  it("renders game list when data is loaded", () => {
    renderPage();
    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
    expect(screen.getByText("The Legend of Zelda")).toBeInTheDocument();
    expect(screen.getByText("Chrono Trigger")).toBeInTheDocument();
  });

  it("renders console badges", () => {
    renderPage();
    expect(screen.getAllByText("SNES")).toHaveLength(2);
    expect(screen.getByText("NES")).toBeInTheDocument();
  });

  it("renders star ratings", () => {
    renderPage();
    expect(screen.getByText("92.5")).toBeInTheDocument();
    expect(screen.getByText("91.0")).toBeInTheDocument();
    expect(screen.getByText("90.3")).toBeInTheDocument();
  });

  it("renders game links pointing to game detail", () => {
    renderPage();
    const links = screen.getAllByRole("link");
    expect(links[0]).toHaveAttribute("href", "/games/42");
    expect(links[1]).toHaveAttribute("href", "/games/99");
    expect(links[2]).toHaveAttribute("href", "/games/55");
  });

  it("shows empty state when no top rated games", () => {
    mockUseTopRated.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No top rated games yet")).toBeInTheDocument();
  });

  it("shows empty state when top rated data is undefined", () => {
    mockUseTopRated.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No top rated games yet")).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseTopRated.mockReturnValue({ data: undefined, isLoading: true });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders rank badges for top 3", () => {
    renderPage();
    // Rank numbers 1, 2, 3 should be visible
    const rankBadges = screen.getAllByText(/^[123]$/);
    expect(rankBadges).toHaveLength(3);
  });

  it("renders play info for games with play history", () => {
    renderPage();
    // Game 42 has 3600s = 1h 0m
    expect(screen.getByText("1h 0m")).toBeInTheDocument();
    // Game 55 has 7200s = 2h 0m
    expect(screen.getByText("2h 0m")).toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // Tab switching
  // ---------------------------------------------------------------------------

  it("switches to Longest Games tab on click", async () => {
    const user = userEvent.setup();
    renderPage();

    const longestTab = screen.getByRole("tab", { name: /Longest Games/i });
    await user.click(longestTab);

    expect(longestTab).toHaveAttribute("aria-selected", "true");
    expect(
      screen.getByRole("heading", { name: /Longest Games/i, level: 2 }),
    ).toBeInTheDocument();
  });

  it("hides Top Rated content when Longest Games tab is active", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(screen.queryByText("Super Mario World")).not.toBeInTheDocument();
    expect(screen.queryByText("92.5")).not.toBeInTheDocument();
  });

  it("can switch back to Top Rated tab", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));
    await user.click(screen.getByRole("tab", { name: /Top Rated/i }));

    expect(screen.getByText("Super Mario World")).toBeInTheDocument();
  });

  // ---------------------------------------------------------------------------
  // Longest Games tab content
  // ---------------------------------------------------------------------------

  it("renders longest games list", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(screen.getByText("Dragon Quest VII")).toBeInTheDocument();
    expect(screen.getByText("Final Fantasy Tactics")).toBeInTheDocument();
    expect(screen.getByText("Tactics Ogre")).toBeInTheDocument();
  });

  it("renders time-to-beat normally as primary metric", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(screen.getByText("100 hrs")).toBeInTheDocument();
    expect(screen.getByText("54 hrs")).toBeInTheDocument();
    expect(screen.getByText("48 hrs")).toBeInTheDocument();
  });

  it("renders hastily and completely as secondary metrics", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    // Dragon Quest VII: hastily 80, completely 150
    expect(screen.getByText(/Main 80 hrs/)).toBeInTheDocument();
    expect(screen.getByText(/100% 150 hrs/)).toBeInTheDocument();
  });

  it("hides zero hastily/completely values", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    // Tactics Ogre has 0 hastily and 0 completely, so no secondary metrics
    // The "Tactics Ogre" row should not have "Main" or "100%" text
    const tacticsOgreLink = screen.getByText("Tactics Ogre").closest("a");
    expect(tacticsOgreLink).toBeInTheDocument();
    // Look within the link for secondary metrics
    const linkText = tacticsOgreLink!.textContent ?? "";
    expect(linkText).not.toContain("Main");
    expect(linkText).not.toContain("100%");
  });

  it("shows empty state when no longest games", async () => {
    mockUseLongestGames.mockReturnValue({
      data: [],
      isLoading: false,
    });
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(screen.getByText("No longest games yet")).toBeInTheDocument();
  });

  it("shows loading skeleton for longest games tab", async () => {
    mockUseLongestGames.mockReturnValue({ data: undefined, isLoading: true });
    const user = userEvent.setup();
    const { container } = renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("renders console badges on longest games", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(screen.getAllByText("PS1")).toHaveLength(2);
    expect(screen.getByText("SNES")).toBeInTheDocument();
  });

  it("renders description for longest games section", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("tab", { name: /Longest Games/i }));

    expect(
      screen.getByText(
        /biggest time investments/i,
      ),
    ).toBeInTheDocument();
  });
});
