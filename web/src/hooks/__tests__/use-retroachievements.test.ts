import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useRAStatus,
  useLinkRA,
  useUnlinkRA,
  useRASettings,
  useGameAchievements,
  useGameAchievementProgress,
  useRecentAchievements,
} from "../use-retroachievements";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
    POST: vi.fn(),
    PUT: vi.fn(),
    DELETE: vi.fn(),
  },
  unwrap: vi.fn(<T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
    p.then((r) => {
      if (r.error !== undefined) throw r.error;
      return r.data;
    }),
  ),
}));

import { typedApi } from "@/lib/api-client";

const mockTypedApi = typedApi as unknown as {
  GET: ReturnType<typeof vi.fn>;
  POST: ReturnType<typeof vi.fn>;
  PUT: ReturnType<typeof vi.fn>;
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

const mockStatus = {
  linked: true,
  username: "player1",
  hardcoreEnabled: false,
};

const mockAchievements = {
  raGameId: 123,
  totalCount: 2,
  totalPoints: 15,
  achievements: [
    {
      id: 1,
      title: "First Blood",
      description: "Beat level 1",
      points: 5,
      badgeUrl: "https://ra.org/badge/1.png",
      type: "core",
    },
    {
      id: 2,
      title: "Speed Run",
      description: "Beat game in 30 min",
      points: 10,
      badgeUrl: "https://ra.org/badge/2.png",
      type: "core",
    },
  ],
};

const mockProgress = [
  {
    achievementId: 1,
    unlockedAt: "2025-06-01T12:00:00Z",
    isHardcore: false,
    playTimeAtUnlock: 1200,
  },
];

describe("useRAStatus", () => {
  it("fetches RA status", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockStatus));

    const { result } = renderHook(() => useRAStatus(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/ra/status");
    expect(result.current.data).toEqual(mockStatus);
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));

    const { result } = renderHook(() => useRAStatus(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Network error");
  });
});

describe("useLinkRA", () => {
  it("sends link request", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockStatus));

    const { result } = renderHook(() => useLinkRA(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ username: "player1", password: "secret" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/user/ra/link", {
      body: { username: "player1", password: "secret" },
    });
  });

  it("handles link error", async () => {
    mockTypedApi.POST.mockReturnValue(
      Promise.reject(new Error("Invalid credentials")),
    );

    const { result } = renderHook(() => useLinkRA(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ username: "bad", password: "creds" });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Invalid credentials");
  });
});

describe("useUnlinkRA", () => {
  it("sends unlink request", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useUnlinkRA(), {
      wrapper: createWrapper(),
    });

    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith("/api/user/ra/link");
  });

  it("handles unlink error", async () => {
    mockTypedApi.DELETE.mockReturnValue(Promise.reject(new Error("Forbidden")));

    const { result } = renderHook(() => useUnlinkRA(), {
      wrapper: createWrapper(),
    });

    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRASettings", () => {
  it("sends settings update", async () => {
    mockTypedApi.PUT.mockReturnValue(ok({ ...mockStatus, hardcoreEnabled: true }));

    const { result } = renderHook(() => useRASettings(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ hardcoreEnabled: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith("/api/user/ra/settings", {
      body: { hardcoreEnabled: true },
    });
  });

  it("handles settings error", async () => {
    mockTypedApi.PUT.mockReturnValue(Promise.reject(new Error("Bad request")));

    const { result } = renderHook(() => useRASettings(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ hardcoreEnabled: true });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useGameAchievements", () => {
  it("fetches achievements for a game", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockAchievements));

    const { result } = renderHook(() => useGameAchievements("game-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/achievements",
      { params: { path: { id: "game-1" } } },
    );
    expect(result.current.data?.totalCount).toBe(2);
  });

  it("does not fetch when gameId is undefined", () => {
    renderHook(() => useGameAchievements(undefined), {
      wrapper: createWrapper(),
    });

    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Not found")));

    const { result } = renderHook(() => useGameAchievements("game-bad"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it("polls when status is pending and stops when data arrives", async () => {
    const pendingResponse = { status: "pending" as const };
    mockTypedApi.GET
      .mockReturnValueOnce(ok(pendingResponse))
      .mockReturnValueOnce(ok(pendingResponse))
      .mockReturnValueOnce(ok(mockAchievements));

    const { result } = renderHook(() => useGameAchievements("game-1"), {
      wrapper: createWrapper(),
    });

    // First call returns pending
    await waitFor(() => expect(result.current.data).toBeDefined());
    expect(result.current.data?.status).toBe("pending");

    // Should eventually get real data via refetch
    await waitFor(
      () => {
        expect(result.current.data?.achievements).toBeDefined();
        expect(result.current.data?.achievements?.length).toBeGreaterThan(0);
      },
      { timeout: 10000 },
    );

    expect(result.current.data?.totalCount).toBe(2);
  });
});

describe("useGameAchievementProgress", () => {
  it("fetches achievement progress for a game", async () => {
    mockTypedApi.GET.mockReturnValue(
      ok({ raGameId: 123, progress: mockProgress }),
    );

    const { result } = renderHook(() => useGameAchievementProgress("game-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/achievements/progress",
      { params: { path: { id: "game-1" } } },
    );
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].playTimeAtUnlock).toBe(1200);
  });

  it("does not fetch when gameId is undefined", () => {
    renderHook(() => useGameAchievementProgress(undefined), {
      wrapper: createWrapper(),
    });

    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Forbidden")));

    const { result } = renderHook(
      () => useGameAchievementProgress("game-bad"),
      {
        wrapper: createWrapper(),
      },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRecentAchievements", () => {
  it("fetches and unwraps recent achievements", async () => {
    const mockRecent = [
      {
        achievementRaId: 101,
        title: "First Steps",
        description: "Complete tutorial",
        points: 5,
        badgeUrl: "https://ra.org/badge/101.png",
        unlockedAt: "2025-06-15T10:00:00Z",
        isHardcore: false,
        playTimeAtUnlock: 600,
        gameId: "game-1",
        gameTitle: "Super Mario",
        consoleName: "NES",
        coverUrl: "https://example.com/smb.png",
      },
    ];
    mockTypedApi.GET.mockReturnValue(ok({ achievements: mockRecent }));

    const { result } = renderHook(() => useRecentAchievements(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/user/achievements/recent",
    );
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].title).toBe("First Steps");
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Unauthorized")));

    const { result } = renderHook(() => useRecentAchievements(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
