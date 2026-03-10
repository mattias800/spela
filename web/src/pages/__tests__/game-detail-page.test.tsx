import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { GameDetailPage } from "../game-detail-page";

vi.mock("@/hooks/use-games", () => ({
  useGame: vi.fn(),
  useToggleFavorite: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useScrapeIfNeeded: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({ user: { role: "admin" } })),
}));

vi.mock("@/hooks/use-admin", () => ({
  useScrapeGame: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(() => ({ data: [] })),
}));

vi.mock("@/hooks/use-collections", () => ({
  useMyCollections: vi.fn(() => ({ data: { data: [] } })),
  useAddGameToCollection: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
  })),
}));

vi.mock("@/hooks/use-sessions", () => ({
  useGameSessions: vi.fn(() => ({ data: [] })),
}));

vi.mock("@/hooks/use-retroachievements", () => ({
  useGameAchievements: vi.fn(() => ({ data: null })),
}));

vi.mock("@/hooks/use-explore", () => ({
  useGameSeries: vi.fn(() => ({ data: null })),
  useGameFranchises: vi.fn(() => ({ data: null })),
}));

vi.mock("@/hooks/use-bios", () => ({
  useBiosStatus: vi.fn(() => ({ data: null })),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

vi.mock("@/lib/api-client", () => ({
  api: { getAccessToken: vi.fn(() => "test-token") },
}));

vi.mock("@/features/game-detail/components/game-hero", () => ({
  GameHero: () => <div data-testid="game-hero">Game Hero</div>,
}));

vi.mock("@/features/game-detail/components/game-screenshots", () => ({
  GameScreenshots: () => null,
}));

vi.mock("@/features/game-detail/components/game-community-stats", () => ({
  GameCommunityStats: () => null,
}));

vi.mock("@/features/game-detail/components/game-achievements", () => ({
  GameAchievements: () => null,
}));

vi.mock("@/features/game-detail/components/game-achievement-leaderboard", () => ({
  GameAchievementLeaderboard: () => null,
}));

vi.mock("@/features/game-detail/components/rating-summary", () => ({
  RatingSummaryCard: () => null,
}));

vi.mock("@/features/game-detail/components/game-reviews", () => ({
  GameReviews: () => null,
}));

vi.mock("@/features/game-detail/components/shared-saves-list", () => ({
  SharedSavesList: () => null,
}));

vi.mock("@/features/shared-sessions/components/game-active-shared-sessions", () => ({
  GameActiveSharedSessions: () => null,
}));

vi.mock("@/features/sessions/components/game-sessions", () => ({
  GameSessions: () => null,
}));

vi.mock("@/features/challenges/components/game-challenges", () => ({
  GameChallenges: () => null,
}));

vi.mock("@/features/bios/components/bios-warning-banner", () => ({
  BiosWarningBanner: () => null,
}));

vi.mock("@/features/game-detail/components/scrape-match-modal", () => ({
  ScrapeMatchModal: () => null,
}));

import { useGame } from "@/hooks/use-games";

const mockUseGame = useGame as ReturnType<typeof vi.fn>;

const mockGame = {
  id: "g1",
  title: "Super Mario World",
  consoleId: "snes",
  consoleName: "SNES",
  coverUrl: "",
  fileName: "smw.sfc",
  scraperId: "igdb",
  scrapeAttempts: 1,
  playable: true,
  isFavorite: false,
  isInPlayLater: false,
  screenshotUrls: [],
};

function renderPage(gameId = "g1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/games/${gameId}`]}>
        <Routes>
          <Route path="games/:id" element={<GameDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseGame.mockReturnValue({
    data: mockGame,
    isLoading: false,
  });
});

describe("GameDetailPage - Cheats", () => {
  it("does not render a GameCheats/cheat codes section on the game detail page", () => {
    renderPage();
    expect(screen.queryByText("Cheat Codes")).not.toBeInTheDocument();
  });
});
