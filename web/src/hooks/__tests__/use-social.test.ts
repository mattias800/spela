import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useOnlineUsers, useActivityFeed } from "../use-social";

vi.mock("@/lib/api-client", () => ({
  api: {
    get: vi.fn(),
  },
}));

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(),
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
};

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
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

const mockOnlineUsers = {
  users: [
    {
      id: "user-1",
      username: "alice",
      avatarUrl: "https://example.com/avatar1.png",
      currentGame: {
        id: "game-1",
        title: "Super Mario Bros",
        coverUrl: "https://example.com/cover.png",
        consoleName: "NES",
      },
    },
    {
      id: "user-2",
      username: "bob",
    },
  ],
};

const mockActivityFeed = {
  data: [
    {
      id: "evt-1",
      userId: "user-1",
      username: "alice",
      userAvatarUrl: "https://example.com/avatar1.png",
      eventType: "started_playing",
      gameId: "game-1",
      gameTitle: "Super Mario Bros",
      gameCoverUrl: "https://example.com/cover.png",
      gameConsoleName: "NES",
      metadata: {},
      createdAt: "2026-02-13T10:00:00Z",
    },
    {
      id: "evt-2",
      userId: "user-2",
      username: "bob",
      eventType: "favorited_game",
      gameId: "game-2",
      gameTitle: "Zelda",
      gameConsoleName: "NES",
      metadata: {},
      createdAt: "2026-02-13T09:00:00Z",
    },
  ],
  total: 2,
  page: 1,
  pageSize: 20,
};

describe("useOnlineUsers", () => {
  it("fetches online users", async () => {
    mockApi.get.mockResolvedValue(mockOnlineUsers);

    const { result } = renderHook(() => useOnlineUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/social/online");
    expect(result.current.data?.users).toHaveLength(2);
    expect(result.current.data?.users[0].username).toBe("alice");
    expect(result.current.data?.users[0].currentGame?.title).toBe(
      "Super Mario Bros",
    );
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => useOnlineUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useActivityFeed", () => {
  it("fetches activity feed with default pagination", async () => {
    mockApi.get.mockResolvedValue(mockActivityFeed);

    const { result } = renderHook(() => useActivityFeed(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/social/activity?page=1&pageSize=20",
    );
    expect(result.current.data?.data).toHaveLength(2);
    expect(result.current.data?.total).toBe(2);
  });

  it("fetches activity feed with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockActivityFeed, page: 2 });

    const { result } = renderHook(() => useActivityFeed(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/social/activity?page=2&pageSize=10",
    );
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useActivityFeed(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
