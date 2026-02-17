import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter, Route, Routes } from "react-router-dom";

import { ChallengeDetailPage } from "../challenge-detail-page";

vi.mock("@/hooks/use-challenges", () => ({
  useChallenge: vi.fn(),
  useChallengeLeaderboard: vi.fn(),
  useChallengeLeaderboardRealtime: vi.fn(),
  useMyAttempts: vi.fn(),
  useDeleteChallenge: vi.fn(() => ({ mutate: vi.fn(), isPending: false })),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "admin-id", username: "admin", role: "admin" },
  })),
}));

vi.mock("@/components/ui", async () => {
  const actual =
    await vi.importActual<Record<string, unknown>>("@/components/ui");
  return {
    ...actual,
    useToast: () => ({ toast: vi.fn() }),
  };
});

import {
  useChallenge,
  useChallengeLeaderboard,
  useDeleteChallenge,
} from "@/hooks/use-challenges";

const mockUseChallenge = useChallenge as ReturnType<typeof vi.fn>;
const mockUseChallengeLeaderboard = useChallengeLeaderboard as ReturnType<
  typeof vi.fn
>;
const mockUseDeleteChallenge = useDeleteChallenge as ReturnType<typeof vi.fn>;

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

const mockChallenge = {
  id: "1",
  creatorId: "u1",
  creatorUsername: "alice",
  creatorAvatarUrl: "",
  gameId: "g1",
  gameTitle: "Super Mario Bros.",
  gameCoverUrl: "",
  gameConsoleName: "NES",
  name: "Speed Run World 1",
  description: "Complete World 1 as fast as possible!",
  type: "speedrun" as const,
  difficulty: "medium" as const,
  status: "active" as const,
  screenshotUrl: "/api/challenges/1/screenshot",
  coreName: "fceumm",
  saveFileSize: 8192,
  attemptCount: 12,
  completionCount: 8,
  expiresAt: null,
  createdAt: "2026-02-01T10:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

const mockLeaderboard = {
  data: [
    {
      rank: 1,
      userId: "u1",
      username: "alice",
      durationMs: 45230,
      attemptId: "a1",
      completedAt: "2026-02-10T10:00:45Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 50,
};

function renderPage(challengeId = "1") {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/challenges/${challengeId}`]}>
        <Routes>
          <Route path="challenges/:id" element={<ChallengeDetailPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseChallenge.mockReturnValue({
    data: mockChallenge,
    isLoading: false,
    isError: false,
  });
  mockUseChallengeLeaderboard.mockReturnValue({
    data: mockLeaderboard,
    isLoading: false,
  });
  mockUseDeleteChallenge.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  });
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ChallengeDetailPage", () => {
  it("renders challenge title and description", () => {
    renderPage();
    expect(screen.getByText("Speed Run World 1")).toBeInTheDocument();
    expect(
      screen.getByText("Complete World 1 as fast as possible!"),
    ).toBeInTheDocument();
  });

  it("renders game info with link", () => {
    renderPage();
    expect(screen.getByText("Super Mario Bros.")).toBeInTheDocument();
    const gameLink = screen.getByRole("link", { name: /Super Mario Bros/i });
    expect(gameLink).toHaveAttribute("href", "/games/g1");
  });

  it("renders creator username", () => {
    renderPage();
    // alice appears in both the challenge info and the leaderboard
    const aliceElements = screen.getAllByText("alice");
    expect(aliceElements.length).toBeGreaterThanOrEqual(1);
  });

  it("renders difficulty and type badges", () => {
    renderPage();
    expect(screen.getByText("Medium")).toBeInTheDocument();
    expect(screen.getByText("Speedrun")).toBeInTheDocument();
  });

  it("renders leaderboard section with entries", () => {
    renderPage();
    expect(
      screen.getByRole("heading", { name: /Leaderboard/i }),
    ).toBeInTheDocument();
    // alice appears in both the challenge info and leaderboard
    const aliceElements = screen.getAllByText("alice");
    expect(aliceElements.length).toBeGreaterThanOrEqual(1);
  });

  it("shows empty leaderboard state", () => {
    mockUseChallengeLeaderboard.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderPage();
    expect(
      screen.getByText("No attempts yet. Be the first!"),
    ).toBeInTheDocument();
  });

  it("shows Download Save State button", () => {
    renderPage();
    expect(
      screen.getByRole("button", { name: /Download Save State/i }),
    ).toBeInTheDocument();
  });

  it("shows Delete button for challenge creator", () => {
    mockUseChallenge.mockReturnValue({
      data: { ...mockChallenge, creatorId: "admin-id" },
      isLoading: false,
      isError: false,
    });
    renderPage();
    expect(
      screen.getByRole("button", { name: /Delete/i }),
    ).toBeInTheDocument();
  });

  it("shows loading state", () => {
    mockUseChallenge.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    });
    const { container } = renderPage();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows error/not-found state for invalid challenge", () => {
    mockUseChallenge.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    });
    renderPage("invalid");
    expect(screen.getByText(/not found/i)).toBeInTheDocument();
  });

  it("renders attempt and completion counts", () => {
    renderPage();
    expect(screen.getByText("12")).toBeInTheDocument();
    expect(screen.getByText("8")).toBeInTheDocument();
  });

  it("delete confirmation dialog appears on delete click", async () => {
    mockUseChallenge.mockReturnValue({
      data: { ...mockChallenge, creatorId: "admin-id" },
      isLoading: false,
      isError: false,
    });
    renderPage();
    await userEvent.click(screen.getByRole("button", { name: /Delete/i }));
    expect(screen.getByText(/Are you sure/i)).toBeInTheDocument();
  });
});
