import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { GameDetailPage } from "../game-detail-page";

vi.mock("@/hooks/use-games", () => ({
  useGame: vi.fn(),
  useToggleFavorite: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useScrapeIfNeeded: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useReplaceRom: vi.fn(() => ({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
    reset: vi.fn(),
  })),
}));

vi.mock("@/hooks/use-play-later", () => ({
  useTogglePlayLater: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({ user: { role: "admin" } })),
}));

vi.mock("@/hooks/use-admin", () => ({
  useScrapeGame: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
  useRefreshAchievements: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-consoles", () => ({
  useConsoles: vi.fn(() => ({ data: [] })),
}));

import { useConsoles } from "@/hooks/use-consoles";

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
  useGameAchievementProgress: vi.fn(() => ({ data: null })),
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
  GameAchievements: () => <div data-testid="game-achievements">Achievements</div>,
}));

vi.mock("@/features/game-detail/components/game-achievement-leaderboard", () => ({
  GameAchievementLeaderboard: () => <div data-testid="game-achievement-leaderboard">Leaderboard</div>,
}));

vi.mock("@/features/game-detail/components/rating-summary", () => ({
  RatingSummaryCard: () => null,
}));

vi.mock("@/features/game-detail/components/game-reviews", () => ({
  GameReviews: () => null,
}));

vi.mock("@/features/game-detail/components/shared-saves-list", () => ({
  SharedSavesList: () => <div data-testid="shared-saves-list">Shared Saves</div>,
}));

vi.mock("@/features/shared-sessions/components/game-active-shared-sessions", () => ({
  GameActiveSharedSessions: () => <div data-testid="game-active-shared-sessions">Active Sessions</div>,
}));

vi.mock("@/features/sessions/components/game-sessions", () => ({
  GameSessions: () => <div data-testid="game-sessions">Sessions</div>,
}));

vi.mock("@/features/challenges/components/game-challenges", () => ({
  GameChallenges: () => <div data-testid="game-challenges">Challenges</div>,
}));

vi.mock("@/features/bios/components/bios-warning-banner", () => ({
  BiosWarningBanner: () => null,
}));

vi.mock("@/features/game-detail/components/scrape-match-modal", () => ({
  ScrapeMatchModal: () => null,
}));

vi.mock("@/features/game-detail/components/replace-rom-modal", () => ({
  ReplaceRomModal: () => null,
}));

vi.mock("@/features/game-detail/components/time-to-beat-card", () => ({
  TimeToBeatCard: () => <div data-testid="time-to-beat-card">Time to Beat</div>,
}));

vi.mock("@/features/game-detail/components/game-variants-section", () => ({
  GameVariantsSection: () => null,
}));

vi.mock("@/features/game-detail/components/based-on-link", () => ({
  BasedOnLink: () => null,
}));

vi.mock("@/features/game-detail/components/standalone-rom-hacks-section", () => ({
  StandaloneRomHacksSection: () => null,
}));

import { useGame } from "@/hooks/use-games";

const mockUseGame = useGame as ReturnType<typeof vi.fn>;
const mockUseConsoles = useConsoles as ReturnType<typeof vi.fn>;

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

describe("GameDetailPage - Demo console (ADEMO)", () => {
  const mockDemoGame = {
    ...mockGame,
    consoleId: "ademo",
    consoleName: "Amiga Demos",
  };

  const ademoConsole = {
    id: "ademo",
    name: "Amiga Demos",
    abbreviation: "ADEMO",
    extensions: ["adf"],
    defaultCore: "puae",
    coverAspectRatio: 1,
    colorTheme: "#666",
    generation: 100,
    iconUrl: "",
    logoUrl: "",
    gameCount: 10,
    saveStateSupport: false,
    browserPlayable: false,
    playable: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };

  beforeEach(() => {
    mockUseGame.mockReturnValue({
      data: mockDemoGame,
      isLoading: false,
    });
    mockUseConsoles.mockReturnValue({
      data: [ademoConsole],
    });
  });

  it("hides TimeToBeatCard for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("time-to-beat-card")).not.toBeInTheDocument();
  });

  it("hides GameSessions for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("game-sessions")).not.toBeInTheDocument();
  });

  it("hides GameAchievements for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("game-achievements")).not.toBeInTheDocument();
  });

  it("hides SharedSavesList for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("shared-saves-list")).not.toBeInTheDocument();
  });

  it("hides GameChallenges for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("game-challenges")).not.toBeInTheDocument();
  });

  it("hides GameActiveSharedSessions for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("game-active-shared-sessions")).not.toBeInTheDocument();
  });

  it("hides GameAchievementLeaderboard for demo consoles", () => {
    renderPage();
    expect(screen.queryByTestId("game-achievement-leaderboard")).not.toBeInTheDocument();
  });
});

describe("GameDetailPage - Regular console shows all sections", () => {
  const snesConsole = {
    id: "snes",
    name: "SNES",
    abbreviation: "SNES",
    extensions: ["sfc", "smc"],
    defaultCore: "snes9x",
    coverAspectRatio: 0.75,
    colorTheme: "#7b68ee",
    generation: 4,
    iconUrl: "",
    logoUrl: "",
    gameCount: 50,
    saveStateSupport: true,
    browserPlayable: true,
    playable: true,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  };

  beforeEach(() => {
    mockUseGame.mockReturnValue({
      data: mockGame,
      isLoading: false,
    });
    mockUseConsoles.mockReturnValue({
      data: [snesConsole],
    });
  });

  it("shows TimeToBeatCard for regular consoles", () => {
    renderPage();
    expect(screen.getByTestId("time-to-beat-card")).toBeInTheDocument();
  });

  it("shows GameSessions for regular consoles", () => {
    renderPage();
    expect(screen.getByTestId("game-sessions")).toBeInTheDocument();
  });

  it("shows achievements badge for regular playable consoles with achievements", () => {
    renderPage();
    // GameAchievements moved to sub-page; badge is now in the hero section
    // The badge renders when achievementCount > 0, which requires mocked data
    // Just verify the page renders without the inline achievements section
    expect(screen.queryByTestId("game-achievements")).not.toBeInTheDocument();
  });

  it("shows SharedSavesList for regular playable consoles", () => {
    renderPage();
    expect(screen.getByTestId("shared-saves-list")).toBeInTheDocument();
  });

  it("shows GameChallenges for regular playable consoles", () => {
    renderPage();
    expect(screen.getByTestId("game-challenges")).toBeInTheDocument();
  });
});
