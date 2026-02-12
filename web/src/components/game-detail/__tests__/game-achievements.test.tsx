import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { GameAchievements } from "../game-achievements";

vi.mock("@/hooks/use-retroachievements", () => ({
  useGameAchievements: vi.fn(),
  useGameAchievementProgress: vi.fn(),
}));

import { useGameAchievements, useGameAchievementProgress } from "@/hooks/use-retroachievements";

const mockUseGameAchievements = useGameAchievements as ReturnType<typeof vi.fn>;
const mockUseGameAchievementProgress = useGameAchievementProgress as ReturnType<typeof vi.fn>;

const mockAchievements = {
  raGameId: 123,
  totalCount: 3,
  totalPoints: 25,
  achievements: [
    { id: 1, title: "First Blood", description: "Beat level 1", points: 5, badgeUrl: "https://ra.org/badge/1.png", type: "core" },
    { id: 2, title: "Speed Run", description: "Beat game in 30 min", points: 10, badgeUrl: "https://ra.org/badge/2.png", type: "core" },
    { id: 3, title: "Completionist", description: "Find all secrets", points: 10, badgeUrl: "https://ra.org/badge/3.png", type: "core" },
  ],
};

const mockProgress = [
  { achievementId: 1, unlockedAt: "2025-06-01T12:00:00Z", isHardcore: false },
];

function renderComponent(gameId = "game-1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <GameAchievements gameId={gameId} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("GameAchievements", () => {
  it("renders loading state", () => {
    mockUseGameAchievements.mockReturnValue({ data: undefined, isLoading: true });
    mockUseGameAchievementProgress.mockReturnValue({ data: undefined, isLoading: true });
    renderComponent();

    expect(screen.getByText("Achievements")).toBeInTheDocument();
  });

  it("renders nothing when no achievements", () => {
    mockUseGameAchievements.mockReturnValue({
      data: { raGameId: 0, totalCount: 0, totalPoints: 0, achievements: [] },
      isLoading: false,
    });
    mockUseGameAchievementProgress.mockReturnValue({ data: [], isLoading: false });
    const { container } = renderComponent();

    expect(container.innerHTML).toBe("");
  });

  it("renders nothing when achievements data is undefined", () => {
    mockUseGameAchievements.mockReturnValue({ data: undefined, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: undefined, isLoading: false });
    const { container } = renderComponent();

    expect(container.innerHTML).toBe("");
  });

  it("renders achievement grid with all achievements", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    expect(screen.getByText("First Blood")).toBeInTheDocument();
    expect(screen.getByText("Speed Run")).toBeInTheDocument();
    expect(screen.getByText("Completionist")).toBeInTheDocument();
  });

  it("shows browser warning banner", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    const banner = screen.getByTestId("browser-warning-banner");
    expect(banner).toBeInTheDocument();
    expect(screen.getByText(/require the Spela Player app/)).toBeInTheDocument();
  });

  it("displays progress summary", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    const summary = screen.getByTestId("achievement-progress-summary");
    expect(summary).toHaveTextContent("1 of 3 achievements unlocked (5 points)");
  });

  it("highlights unlocked achievements", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    const unlockedCard = screen.getByTestId("achievement-1");
    const lockedCard = screen.getByTestId("achievement-2");

    expect(unlockedCard.className).toContain("bg-surface-800");
    expect(lockedCard.className).toContain("opacity-50");
  });

  it("shows locked achievements with dimmed styling", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    const lockedCard = screen.getByTestId("achievement-3");
    expect(lockedCard.className).toContain("opacity-50");
    expect(lockedCard.className).toContain("bg-surface-900");
  });

  it("renders achievement badge images", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    const images = screen.getAllByRole("img");
    expect(images).toHaveLength(3);
    expect(images[0]).toHaveAttribute("src", "https://ra.org/badge/1.png");
  });

  it("displays point values for each achievement", () => {
    mockUseGameAchievements.mockReturnValue({ data: mockAchievements, isLoading: false });
    mockUseGameAchievementProgress.mockReturnValue({ data: mockProgress, isLoading: false });
    renderComponent();

    expect(screen.getByText("5 pts")).toBeInTheDocument();
    expect(screen.getAllByText("10 pts")).toHaveLength(2);
  });
});
