import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";

import { ChallengeLeaderboard } from "../challenge-leaderboard";

vi.mock("@/hooks/use-challenges", () => ({
  useChallengeLeaderboard: vi.fn(),
  useChallengeLeaderboardRealtime: vi.fn(),
}));

vi.mock("@/hooks/use-auth", () => ({
  useAuth: vi.fn(() => ({
    user: { id: "u-viewer", username: "viewer", role: "user" },
  })),
}));

import { useChallengeLeaderboard } from "@/hooks/use-challenges";
import { useAuth } from "@/hooks/use-auth";

const mockUseChallengeLeaderboard =
  useChallengeLeaderboard as ReturnType<typeof vi.fn>;
const mockUseAuth = useAuth as ReturnType<typeof vi.fn>;

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

function mockEntry(overrides?: Record<string, unknown>) {
  return {
    rank: 1,
    userId: "u1",
    username: "alice",
    durationMs: 45230,
    attemptId: "a1",
    completedAt: "2026-02-10T10:00:45Z",
    ...overrides,
  };
}

const mockLeaderboard = {
  data: [
    mockEntry(),
    mockEntry({
      attemptId: "a2",
      userId: "u2",
      username: "bob",
      durationMs: 52100,
      rank: 2,
    }),
    mockEntry({
      attemptId: "a3",
      userId: "u3",
      username: "charlie",
      durationMs: 61500,
      rank: 3,
    }),
  ],
  total: 3,
  page: 1,
  pageSize: 50,
};

function renderLeaderboard() {
  return render(
    <MemoryRouter>
      <ChallengeLeaderboard challengeId="1" />
    </MemoryRouter>,
  );
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("ChallengeLeaderboard", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseChallengeLeaderboard.mockReturnValue({
      data: mockLeaderboard,
      isLoading: false,
    });
    mockUseAuth.mockReturnValue({
      user: { id: "u-viewer", username: "viewer", role: "user" },
    });
  });

  it("renders Leaderboard heading", () => {
    renderLeaderboard();
    expect(
      screen.getByRole("heading", { name: /Leaderboard/i }),
    ).toBeInTheDocument();
  });

  it("displays entries with correct usernames", () => {
    renderLeaderboard();
    expect(screen.getByText("alice")).toBeInTheDocument();
    expect(screen.getByText("bob")).toBeInTheDocument();
    expect(screen.getByText("charlie")).toBeInTheDocument();
  });

  it("shows gold/silver/bronze badges for top 3", () => {
    renderLeaderboard();
    expect(screen.getByTestId("rank-badge-1")).toBeInTheDocument();
    expect(screen.getByTestId("rank-badge-2")).toBeInTheDocument();
    expect(screen.getByTestId("rank-badge-3")).toBeInTheDocument();
  });

  it("formats time as M:SS.s", () => {
    renderLeaderboard();
    expect(screen.getByText("0:45.2")).toBeInTheDocument(); // 45230ms
    expect(screen.getByText("0:52.1")).toBeInTheDocument(); // 52100ms
    expect(screen.getByText("1:01.5")).toBeInTheDocument(); // 61500ms
  });

  it("uses monospace font for time display", () => {
    renderLeaderboard();
    const timeCell = screen.getByText("0:45.2");
    expect(timeCell.className).toContain("font-mono");
  });

  it("highlights current user's entry", () => {
    mockUseAuth.mockReturnValue({
      user: { id: "u1", username: "alice", role: "user" },
    });
    renderLeaderboard();
    const entry = screen.getByTestId("leaderboard-entry-u1");
    expect(entry.className).toContain("border-brand");
  });

  it("shows empty state when no entries", () => {
    mockUseChallengeLeaderboard.mockReturnValue({
      data: { data: [], total: 0, page: 1, pageSize: 50 },
      isLoading: false,
    });
    renderLeaderboard();
    expect(
      screen.getByText("No attempts yet. Be the first!"),
    ).toBeInTheDocument();
  });

  it("renders loading skeletons when loading", () => {
    mockUseChallengeLeaderboard.mockReturnValue({
      data: undefined,
      isLoading: true,
    });
    const { container } = renderLeaderboard();
    const skeletons = container.querySelectorAll("[class*='animate-pulse']");
    expect(skeletons.length).toBeGreaterThan(0);
  });

  it("shows entry count in heading", () => {
    renderLeaderboard();
    expect(screen.getByText("(3)")).toBeInTheDocument();
  });
});
