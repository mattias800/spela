import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

import { TopListsPage } from "../top-lists-page";

vi.mock("@/hooks/use-top-lists", () => ({
  useTopRated: vi.fn(),
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

import { useTopRated } from "@/hooks/use-top-lists";
import { usePlayStats } from "@/hooks/use-play-stats";

const mockUseTopRated = useTopRated as ReturnType<typeof vi.fn>;
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

  it("renders section heading", () => {
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

  it("shows empty state when no games", () => {
    mockUseTopRated.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderPage();
    expect(screen.getByText("No top rated games yet")).toBeInTheDocument();
  });

  it("shows empty state when data is undefined", () => {
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
});
