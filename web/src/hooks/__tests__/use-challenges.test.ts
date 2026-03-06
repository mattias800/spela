import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";

import {
  useChallenges,
  useChallenge,
  useChallengeLeaderboard,
  useGameChallenges,
  useMyChallenges,
  useMyAttempts,
  useDeleteChallenge,
  useStartAttempt,
  useCompleteAttempt,
  useAbandonAttempt,
} from "../use-challenges";

vi.mock("@/lib/api-client", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    upload: vi.fn(),
    getAccessToken: vi.fn(() => "test-token"),
  },
}));

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(),
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
  upload: ReturnType<typeof vi.fn>;
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(
      QueryClientProvider,
      { client: queryClient },
      children,
    );
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

// ---------------------------------------------------------------------------
// Mock Data
// ---------------------------------------------------------------------------

const mockChallengesResponse = {
  data: [
    {
      id: "1",
      creatorId: "u1",
      creatorUsername: "alice",
      gameId: "g1",
      gameTitle: "Super Mario Bros.",
      gameCoverUrl: "",
      gameConsoleName: "NES",
      name: "Speed Run World 1",
      description: "Complete World 1 as fast as possible!",
      type: "speedrun",
      difficulty: "medium",
      status: "active",
      screenshotUrl: "/api/challenges/1/screenshot",
      coreName: "fceumm",
      saveFileSize: 1024,
      attemptCount: 12,
      completionCount: 8,
      expiresAt: null,
      createdAt: "2026-02-01T10:00:00Z",
      updatedAt: "2026-02-01T10:00:00Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockChallengeDetail = {
  id: "1",
  creatorId: "u1",
  creatorUsername: "alice",
  gameId: "g1",
  gameTitle: "Super Mario Bros.",
  gameCoverUrl: "",
  gameConsoleName: "NES",
  name: "Speed Run World 1",
  description: "Complete World 1 as fast as possible!",
  type: "speedrun",
  difficulty: "medium",
  status: "active",
  screenshotUrl: "/api/challenges/1/screenshot",
  coreName: "fceumm",
  saveFileSize: 1024,
  attemptCount: 12,
  completionCount: 8,
  expiresAt: null,
  createdAt: "2026-02-01T10:00:00Z",
  updatedAt: "2026-02-01T10:00:00Z",
};

const mockLeaderboardResponse = {
  data: [
    {
      rank: 1,
      userId: "u1",
      username: "alice",
      durationMs: 45230,
      attemptId: "a1",
      completedAt: "2026-02-10T10:00:45Z",
    },
    {
      rank: 2,
      userId: "u2",
      username: "bob",
      durationMs: 52100,
      attemptId: "a2",
      completedAt: "2026-02-10T11:00:52Z",
    },
  ],
  total: 2,
  page: 1,
  pageSize: 50,
};

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("useChallenges", () => {
  it("fetches paginated challenges list", async () => {
    mockApi.get.mockResolvedValue(mockChallengesResponse);
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockChallengesResponse);
  });

  it("passes filter parameters (gameId, consoleId, difficulty, sort)", async () => {
    mockApi.get.mockResolvedValue(mockChallengesResponse);
    const { result } = renderHook(
      () =>
        useChallenges({
          gameId: "g1",
          consoleId: "c1",
          difficulty: "hard",
          sortBy: "most_attempted",
        }),
      { wrapper: createWrapper() },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    const callUrl = mockApi.get.mock.calls[0][0] as string;
    expect(callUrl).toContain("gameId=g1");
    expect(callUrl).toContain("consoleId=c1");
    expect(callUrl).toContain("difficulty=hard");
    expect(callUrl).toContain("sort=popular");
  });

  it("returns loading state initially", () => {
    mockApi.get.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    expect(result.current.isLoading).toBe(true);
  });

  it("returns error state on network failure", async () => {
    mockApi.get.mockRejectedValue(new Error("Network error"));
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useChallenge", () => {
  it("fetches single challenge by ID", async () => {
    mockApi.get.mockResolvedValue(mockChallengeDetail);
    const { result } = renderHook(() => useChallenge("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockChallengeDetail);
    expect(mockApi.get).toHaveBeenCalledWith("/challenges/1");
  });

  it("returns error for non-existent challenge", async () => {
    mockApi.get.mockRejectedValue(new Error("Not found"));
    const { result } = renderHook(() => useChallenge("999"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useChallengeLeaderboard", () => {
  it("fetches leaderboard for a challenge", async () => {
    mockApi.get.mockResolvedValue(mockLeaderboardResponse);
    const { result } = renderHook(() => useChallengeLeaderboard("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockLeaderboardResponse);
    expect(mockApi.get).toHaveBeenCalledWith(
      "/challenges/1/leaderboard?page=1&pageSize=50",
    );
  });

  it("returns empty leaderboard for new challenge", async () => {
    mockApi.get.mockResolvedValue({
      data: [],
      total: 0,
      page: 1,
      pageSize: 50,
    });
    const { result } = renderHook(() => useChallengeLeaderboard("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toEqual([]);
  });
});

describe("useGameChallenges", () => {
  it("fetches challenges for a specific game", async () => {
    mockApi.get.mockResolvedValue(mockChallengesResponse);
    const { result } = renderHook(() => useGameChallenges("g1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.get).toHaveBeenCalledWith(
      "/games/g1/challenges?page=1&pageSize=5",
    );
  });

  it("returns empty list when game has no challenges", async () => {
    mockApi.get.mockResolvedValue({
      data: [],
      total: 0,
      page: 1,
      pageSize: 5,
    });
    const { result } = renderHook(() => useGameChallenges("g1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toEqual([]);
  });
});

describe("useMyChallenges", () => {
  it("fetches challenges created by current user", async () => {
    mockApi.get.mockResolvedValue(mockChallengesResponse);
    const { result } = renderHook(() => useMyChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.get).toHaveBeenCalledWith(
      "/user/challenges?page=1&pageSize=20",
    );
  });
});

describe("useMyAttempts", () => {
  it("fetches current user attempts for a challenge", async () => {
    const mockAttempts = [
      {
        id: "a1",
        challengeId: "1",
        userId: "u1",
        username: "alice",
        status: "completed",
        startedAt: "2026-02-10T10:00:00Z",
        completedAt: "2026-02-10T10:00:45Z",
        durationMs: 45230,
        isBest: true,
      },
    ];
    mockApi.get.mockResolvedValue(mockAttempts);
    const { result } = renderHook(() => useMyAttempts("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.get).toHaveBeenCalledWith("/challenges/1/attempts/mine");
  });

  it("returns empty array when no attempts exist", async () => {
    mockApi.get.mockResolvedValue([]);
    const { result } = renderHook(() => useMyAttempts("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });
});

describe("useDeleteChallenge", () => {
  it("sends DELETE request for challenge", async () => {
    mockApi.delete.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDeleteChallenge(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/challenges/1");
  });

  it("invalidates challenges query cache on success", async () => {
    mockApi.delete.mockResolvedValue(undefined);
    const { result } = renderHook(() => useDeleteChallenge(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("returns error for unauthorized deletion", async () => {
    mockApi.delete.mockRejectedValue(new Error("Forbidden"));
    const { result } = renderHook(() => useDeleteChallenge(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useStartAttempt", () => {
  it("sends POST to start attempt and returns attemptId", async () => {
    const mockResponse = {
      id: "attempt-1",
      challengeId: "1",
      userId: "u1",
      username: "alice",
      status: "in_progress",
      startedAt: "2026-02-10T10:00:00Z",
      durationMs: 0,
      isBest: false,
    };
    mockApi.post.mockResolvedValue(mockResponse);
    const { result } = renderHook(() => useStartAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/challenges/1/attempts/start",
    );
  });

  it("returns error when rate limited", async () => {
    mockApi.post.mockRejectedValue(new Error("Too many requests"));
    const { result } = renderHook(() => useStartAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useCompleteAttempt", () => {
  it("sends POST to complete attempt and returns result", async () => {
    const mockResponse = {
      id: "attempt-1",
      challengeId: "1",
      userId: "u1",
      username: "alice",
      status: "completed",
      startedAt: "2026-02-10T10:00:00Z",
      completedAt: "2026-02-10T10:00:45Z",
      durationMs: 45230,
      isBest: true,
    };
    mockApi.post.mockResolvedValue(mockResponse);
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/challenges/1/attempts/attempt-1/complete",
    );
  });

  it("invalidates leaderboard cache on success", async () => {
    mockApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("invalidates my attempts cache on success", async () => {
    mockApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useAbandonAttempt", () => {
  it("sends POST to abandon attempt", async () => {
    mockApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useAbandonAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/challenges/1/attempts/attempt-1/abandon",
    );
  });

  it("invalidates my attempts cache on success", async () => {
    mockApi.post.mockResolvedValue({});
    const { result } = renderHook(() => useAbandonAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
