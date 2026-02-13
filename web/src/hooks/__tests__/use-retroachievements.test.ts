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
  api: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
  put: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return function Wrapper({ children }: { children: ReactNode }) {
    return createElement(QueryClientProvider, { client: queryClient }, children);
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
    { id: 1, title: "First Blood", description: "Beat level 1", points: 5, badgeUrl: "https://ra.org/badge/1.png", type: "core" },
    { id: 2, title: "Speed Run", description: "Beat game in 30 min", points: 10, badgeUrl: "https://ra.org/badge/2.png", type: "core" },
  ],
};

const mockProgress = [
  { achievementId: 1, unlockedAt: "2025-06-01T12:00:00Z", isHardcore: false, playTimeAtUnlock: 1200 },
];

describe("useRAStatus", () => {
  it("fetches RA status", async () => {
    mockApi.get.mockResolvedValue(mockStatus);

    const { result } = renderHook(() => useRAStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/user/ra/status");
    expect(result.current.data).toEqual(mockStatus);
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useRAStatus(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Network error");
  });
});

describe("useLinkRA", () => {
  it("sends link request", async () => {
    mockApi.post.mockResolvedValue(mockStatus);

    const { result } = renderHook(() => useLinkRA(), { wrapper: createWrapper() });

    result.current.mutate({ username: "player1", password: "secret" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/user/ra/link", {
      username: "player1",
      password: "secret",
    });
  });

  it("handles link error", async () => {
    mockApi.post.mockRejectedValue(new Error("Invalid credentials"));

    const { result } = renderHook(() => useLinkRA(), { wrapper: createWrapper() });

    result.current.mutate({ username: "bad", password: "creds" });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Invalid credentials");
  });
});

describe("useUnlinkRA", () => {
  it("sends unlink request", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useUnlinkRA(), { wrapper: createWrapper() });

    result.current.mutate();

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/user/ra/link");
  });

  it("handles unlink error", async () => {
    mockApi.delete.mockRejectedValue(new Error("Forbidden"));

    const { result } = renderHook(() => useUnlinkRA(), { wrapper: createWrapper() });

    result.current.mutate();

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRASettings", () => {
  it("sends settings update", async () => {
    mockApi.put.mockResolvedValue({ ...mockStatus, hardcoreEnabled: true });

    const { result } = renderHook(() => useRASettings(), { wrapper: createWrapper() });

    result.current.mutate({ hardcoreEnabled: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.put).toHaveBeenCalledWith("/user/ra/settings", {
      hardcoreEnabled: true,
    });
  });

  it("handles settings error", async () => {
    mockApi.put.mockRejectedValue(new Error("Bad request"));

    const { result } = renderHook(() => useRASettings(), { wrapper: createWrapper() });

    result.current.mutate({ hardcoreEnabled: true });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useGameAchievements", () => {
  it("fetches achievements for a game", async () => {
    mockApi.get.mockResolvedValue(mockAchievements);

    const { result } = renderHook(() => useGameAchievements("game-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/game-1/achievements");
    expect(result.current.data?.totalCount).toBe(2);
  });

  it("does not fetch when gameId is undefined", () => {
    renderHook(() => useGameAchievements(undefined), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Not found"));

    const { result } = renderHook(() => useGameAchievements("game-bad"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useGameAchievementProgress", () => {
  it("fetches achievement progress for a game", async () => {
    mockApi.get.mockResolvedValue({ raGameId: 123, progress: mockProgress });

    const { result } = renderHook(() => useGameAchievementProgress("game-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/game-1/achievements/progress");
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].playTimeAtUnlock).toBe(1200);
  });

  it("does not fetch when gameId is undefined", () => {
    renderHook(() => useGameAchievementProgress(undefined), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Forbidden"));

    const { result } = renderHook(() => useGameAchievementProgress("game-bad"), {
      wrapper: createWrapper(),
    });

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
    mockApi.get.mockResolvedValue({ achievements: mockRecent });

    const { result } = renderHook(() => useRecentAchievements(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/user/achievements/recent");
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].title).toBe("First Steps");
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Unauthorized"));

    const { result } = renderHook(() => useRecentAchievements(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
