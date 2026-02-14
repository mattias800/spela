import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";
import { DashboardPage } from "../dashboard-page";

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({ user: { id: "u1", username: "testuser" } })),
}));

vi.mock("@/hooks/use-games", () => ({
  useRecentGames: vi.fn(() => ({ data: [], isLoading: false })),
  useFavoriteGames: vi.fn(() => ({ data: [], isLoading: false })),
  useGames: vi.fn(() => ({ data: { data: [] }, isLoading: false })),
  useToggleFavorite: vi.fn(() => ({ toggle: vi.fn() })),
}));

vi.mock("@/hooks/use-stats", () => ({
  useUserStats: vi.fn(() => ({ data: null, isLoading: false })),
}));

vi.mock("@/hooks/use-retroachievements", () => ({
  useRecentAchievements: vi.fn(),
}));

vi.mock("@/hooks/use-social", () => ({
  useOnlineUsers: vi.fn(() => ({ data: { users: [] }, isLoading: false })),
  useActivityFeed: vi.fn(() => ({ data: { data: [], total: 0, page: 1, pageSize: 20 }, isLoading: false })),
  useActivityRealtime: vi.fn(),
}));

import { useRecentAchievements } from "@/hooks/use-retroachievements";

const mockUseRecentAchievements = useRecentAchievements as ReturnType<typeof vi.fn>;

const mockRecentAchievements = [
  {
    achievementRaId: 101,
    title: "First Steps",
    description: "Complete the tutorial",
    points: 5,
    badgeUrl: "https://ra.org/badge/101.png",
    unlockedAt: "2025-06-15T10:00:00Z",
    isHardcore: false,
    playTimeAtUnlock: 600,
    gameId: "game-1",
    gameTitle: "Super Mario Bros",
    consoleName: "NES",
    coverUrl: "https://example.com/smb.png",
  },
  {
    achievementRaId: 102,
    title: "Boss Slayer",
    description: "Defeat the first boss",
    points: 10,
    badgeUrl: "https://ra.org/badge/102.png",
    unlockedAt: "2025-06-14T08:00:00Z",
    isHardcore: true,
    playTimeAtUnlock: 1800,
    gameId: "game-2",
    gameTitle: "Zelda",
    consoleName: "SNES",
    coverUrl: "https://example.com/zelda.png",
  },
];

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("DashboardPage - Recent Achievements", () => {
  it("renders recent achievements section when data is available", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    expect(screen.getByText("Recent Achievements")).toBeInTheDocument();
    expect(screen.getByText("First Steps")).toBeInTheDocument();
    expect(screen.getByText("Boss Slayer")).toBeInTheDocument();
  });

  it("shows game titles for each achievement", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    expect(screen.getByText("Super Mario Bros")).toBeInTheDocument();
    expect(screen.getByText("Zelda")).toBeInTheDocument();
  });

  it("shows points badge for each achievement", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    expect(screen.getByText("5 pts")).toBeInTheDocument();
    expect(screen.getByText("10 pts")).toBeInTheDocument();
  });

  it("shows Hardcore badge for hardcore achievements", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    expect(screen.getByText("Hardcore")).toBeInTheDocument();
  });

  it("renders badge images", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    const images = screen.getAllByRole("img");
    expect(images[0]).toHaveAttribute("src", "https://ra.org/badge/101.png");
  });

  it("links achievement cards to game detail pages", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: mockRecentAchievements,
      isLoading: false,
    });
    renderDashboard();

    const card = screen.getByTestId("recent-achievement-101");
    expect(card).toHaveAttribute("href", "/games/game-1");
  });

  it("does not render section when no achievements", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: [],
      isLoading: false,
    });
    renderDashboard();

    expect(screen.queryByText("Recent Achievements")).not.toBeInTheDocument();
  });

  it("does not render section when data is undefined", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    renderDashboard();

    expect(screen.queryByText("Recent Achievements")).not.toBeInTheDocument();
  });

  it("renders loading skeleton when data is loading", () => {
    mockUseRecentAchievements.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderDashboard();

    expect(screen.getByText("Recent Achievements")).toBeInTheDocument();
    expect(screen.getByTestId("recent-achievements-section")).toBeInTheDocument();
  });

  it("limits to 5 achievements maximum", () => {
    const manyAchievements = Array.from({ length: 8 }, (_, i) => ({
      achievementRaId: 200 + i,
      title: `Achievement ${i + 1}`,
      description: `Description ${i + 1}`,
      points: 5,
      badgeUrl: `https://ra.org/badge/${200 + i}.png`,
      unlockedAt: "2025-06-15T10:00:00Z",
      isHardcore: false,
      playTimeAtUnlock: 600,
      gameId: `game-${i}`,
      gameTitle: `Game ${i}`,
      consoleName: "NES",
      coverUrl: "",
    }));

    mockUseRecentAchievements.mockReturnValue({
      data: manyAchievements,
      isLoading: false,
    });
    renderDashboard();

    // Should only show 5
    expect(screen.getByText("Achievement 1")).toBeInTheDocument();
    expect(screen.getByText("Achievement 5")).toBeInTheDocument();
    expect(screen.queryByText("Achievement 6")).not.toBeInTheDocument();
  });
});
