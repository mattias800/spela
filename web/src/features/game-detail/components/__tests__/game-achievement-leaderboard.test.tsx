import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GameAchievementLeaderboard } from "../game-achievement-leaderboard";

vi.mock("@/hooks/use-retroachievements", () => ({
  useAchievementLeaderboard: vi.fn(),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({ user: { id: "user-1", username: "testuser" } })),
}));

import { useAchievementLeaderboard } from "@/hooks/use-retroachievements";
import { useAuth } from "@/hooks/use-auth";

const mockUseAchievementLeaderboard = useAchievementLeaderboard as ReturnType<
  typeof vi.fn
>;
const mockUseAuth = useAuth as ReturnType<typeof vi.fn>;

const mockLeaderboard = {
  raGameId: 123,
  totalAchievements: 10,
  leaderboard: [
    {
      userId: "user-1",
      username: "player1",
      avatarUrl: "https://example.com/avatar1.png",
      unlockedCount: 8,
      earnedPoints: 50,
      lastUnlockedAt: "2025-06-15T10:00:00Z",
      firstUnlockedAt: "2025-06-01T10:00:00Z",
      isComplete: false,
    },
    {
      userId: "user-2",
      username: "player2",
      unlockedCount: 10,
      earnedPoints: 75,
      lastUnlockedAt: "2025-06-10T10:00:00Z",
      firstUnlockedAt: "2025-05-20T10:00:00Z",
      isComplete: true,
    },
    {
      userId: "user-3",
      username: "player3",
      unlockedCount: 3,
      earnedPoints: 15,
      lastUnlockedAt: "2025-06-05T10:00:00Z",
      firstUnlockedAt: "2025-06-04T10:00:00Z",
      isComplete: false,
    },
  ],
};

function renderComponent(gameId = "game-1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <GameAchievementLeaderboard gameId={gameId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseAuth.mockReturnValue({ user: { id: "user-1", username: "testuser" } });
});

describe("GameAchievementLeaderboard", () => {
  it("renders loading skeleton", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    renderComponent();

    // Skeleton should render inside a card
    expect(document.querySelector("[class*='rounded-2xl']")).toBeInTheDocument();
  });

  it("renders nothing when no leaderboard data", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: undefined,
      isLoading: false,
    });
    const { container } = renderComponent();

    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when leaderboard is empty", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: { raGameId: 123, totalAchievements: 10, leaderboard: [] },
      isLoading: false,
    });
    const { container } = renderComponent();

    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when totalAchievements is 0", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: { raGameId: 123, totalAchievements: 0, leaderboard: [] },
      isLoading: false,
    });
    const { container } = renderComponent();

    expect(container.innerHTML).toBe("");
  });

  it("renders leaderboard entries", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("Achievement Leaderboard")).toBeInTheDocument();
    expect(screen.getByText("player1")).toBeInTheDocument();
    expect(screen.getByText("player2")).toBeInTheDocument();
    expect(screen.getByText("player3")).toBeInTheDocument();
  });

  it("shows unlocked count and total for each entry", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("8 / 10")).toBeInTheDocument();
    expect(screen.getByText("10 / 10")).toBeInTheDocument();
    expect(screen.getByText("3 / 10")).toBeInTheDocument();
  });

  it("shows earned points for each entry", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    expect(screen.getByText("50 pts")).toBeInTheDocument();
    expect(screen.getByText("75 pts")).toBeInTheDocument();
    expect(screen.getByText("15 pts")).toBeInTheDocument();
  });

  it("shows Complete! badge for players who completed all achievements", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    const completeBadges = screen.getAllByText("Complete!");
    expect(completeBadges).toHaveLength(1);
  });

  it("highlights current user row", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    const currentUserRow = screen.getByTestId("leaderboard-entry-user-1");
    expect(currentUserRow.className).toContain("bg-brand-500/5");
    expect(currentUserRow.className).toContain("border-brand-400");
  });

  it("does not highlight other users", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    const otherUserRow = screen.getByTestId("leaderboard-entry-user-2");
    expect(otherUserRow.className).toContain("bg-surface-800/30");
    expect(otherUserRow.className).not.toContain("border-brand-400");
  });

  it("renders player avatars", () => {
    mockUseAchievementLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    renderComponent();

    const avatarImg = screen.getByAltText("player1");
    expect(avatarImg).toHaveAttribute("src", "https://example.com/avatar1.png");
  });
});
