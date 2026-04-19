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
  typedApi: {
    GET: vi.fn(),
    POST: vi.fn(),
    DELETE: vi.fn(),
  },
  unwrap: vi.fn(<T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
    p.then((r) => {
      if (r.error !== undefined) throw r.error;
      return r.data;
    }),
  ),
}));

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(),
}));

import { typedApi } from "@/lib/api-client";

const mockTypedApi = typedApi as unknown as {
  GET: ReturnType<typeof vi.fn>;
  POST: ReturnType<typeof vi.fn>;
  DELETE: ReturnType<typeof vi.fn>;
};

function ok(data: unknown) {
  return Promise.resolve({
    data,
    response: new Response(null, { status: 200 }),
  });
}

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

describe("useChallenges", () => {
  it("fetches paginated challenges list", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockChallengesResponse));
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockChallengesResponse);
  });

  it("passes filter parameters (gameId, consoleId, difficulty, sort)", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockChallengesResponse));
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
    const call = mockTypedApi.GET.mock.calls[0];
    expect(call[0]).toBe("/api/challenges");
    const query = call[1].params.query;
    expect(query).toMatchObject({
      gameId: "g1",
      consoleId: "c1",
      difficulty: "hard",
      sort: "popular",
    });
  });

  it("returns loading state initially", () => {
    mockTypedApi.GET.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    expect(result.current.isLoading).toBe(true);
  });

  it("returns error state on network failure", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));
    const { result } = renderHook(() => useChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useChallenge", () => {
  it("fetches single challenge by ID", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockChallengeDetail));
    const { result } = renderHook(() => useChallenge("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockChallengeDetail);
    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/challenges/{id}", {
      params: { path: { id: "1" } },
    });
  });

  it("returns error for non-existent challenge", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Not found")));
    const { result } = renderHook(() => useChallenge("999"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useChallengeLeaderboard", () => {
  it("fetches leaderboard for a challenge", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockLeaderboardResponse));
    const { result } = renderHook(() => useChallengeLeaderboard("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(mockLeaderboardResponse);
    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/challenges/{id}/leaderboard",
      { params: { path: { id: "1" }, query: { page: 1, pageSize: 50 } } },
    );
  });

  it("returns empty leaderboard for new challenge", async () => {
    mockTypedApi.GET.mockReturnValue(
      ok({ data: [], total: 0, page: 1, pageSize: 50 }),
    );
    const { result } = renderHook(() => useChallengeLeaderboard("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toEqual([]);
  });
});

describe("useGameChallenges", () => {
  it("fetches challenges for a specific game", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockChallengesResponse));
    const { result } = renderHook(() => useGameChallenges("g1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/challenges",
      { params: { path: { id: "g1" }, query: { page: 1, pageSize: 5 } } },
    );
  });

  it("returns empty list when game has no challenges", async () => {
    mockTypedApi.GET.mockReturnValue(
      ok({ data: [], total: 0, page: 1, pageSize: 5 }),
    );
    const { result } = renderHook(() => useGameChallenges("g1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data).toEqual([]);
  });
});

describe("useMyChallenges", () => {
  it("fetches challenges created by current user", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockChallengesResponse));
    const { result } = renderHook(() => useMyChallenges(), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/challenges", {
      params: { query: { page: 1, pageSize: 20 } },
    });
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
    mockTypedApi.GET.mockReturnValue(ok(mockAttempts));
    const { result } = renderHook(() => useMyAttempts("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/challenges/{id}/attempts/mine",
      { params: { path: { id: "1" } } },
    );
  });

  it("returns empty array when no attempts exist", async () => {
    mockTypedApi.GET.mockReturnValue(ok([]));
    const { result } = renderHook(() => useMyAttempts("1"), {
      wrapper: createWrapper(),
    });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual([]);
  });
});

describe("useDeleteChallenge", () => {
  it("sends DELETE request for challenge", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));
    const { result } = renderHook(() => useDeleteChallenge(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith("/api/challenges/{id}", {
      params: { path: { id: "1" } },
    });
  });

  it("invalidates challenges query cache on success", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));
    const { result } = renderHook(() => useDeleteChallenge(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("returns error for unauthorized deletion", async () => {
    mockTypedApi.DELETE.mockReturnValue(Promise.reject(new Error("Forbidden")));
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
    mockTypedApi.POST.mockReturnValue(ok(mockResponse));
    const { result } = renderHook(() => useStartAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate("1");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/challenges/{id}/attempts/start",
      { params: { path: { id: "1" } } },
    );
  });

  it("returns error when rate limited", async () => {
    mockTypedApi.POST.mockReturnValue(
      Promise.reject(new Error("Too many requests")),
    );
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
    mockTypedApi.POST.mockReturnValue(ok(mockResponse));
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/challenges/{id}/attempts/{aid}/complete",
      { params: { path: { id: "1", aid: "attempt-1" } } },
    );
  });

  it("invalidates leaderboard cache on success", async () => {
    mockTypedApi.POST.mockReturnValue(ok({}));
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });

  it("invalidates my attempts cache on success", async () => {
    mockTypedApi.POST.mockReturnValue(ok({}));
    const { result } = renderHook(() => useCompleteAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});

describe("useAbandonAttempt", () => {
  it("sends POST to abandon attempt", async () => {
    mockTypedApi.POST.mockReturnValue(ok({}));
    const { result } = renderHook(() => useAbandonAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/challenges/{id}/attempts/{aid}/abandon",
      { params: { path: { id: "1", aid: "attempt-1" } } },
    );
  });

  it("invalidates my attempts cache on success", async () => {
    mockTypedApi.POST.mockReturnValue(ok({}));
    const { result } = renderHook(() => useAbandonAttempt(), {
      wrapper: createWrapper(),
    });
    result.current.mutate({ challengeId: "1", attemptId: "attempt-1" });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
  });
});
