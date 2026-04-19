import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useOnlineUsers, useActivityFeed } from "../use-social";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
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
};

function ok(data: unknown) {
  return Promise.resolve({
    data,
    response: new Response(null, { status: 200 }),
  });
}

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
      avatarUrl: "https://example.com/avatar1.png",
      eventType: "started_playing",
      gameId: "game-1",
      gameTitle: "Super Mario Bros",
      gameCoverUrl: "https://example.com/cover.png",
      consoleName: "NES",
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
      consoleName: "NES",
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
    mockTypedApi.GET.mockReturnValue(ok(mockOnlineUsers));

    const { result } = renderHook(() => useOnlineUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/social/online");
    expect(result.current.data?.users).toHaveLength(2);
    expect(result.current.data?.users?.[0].username).toBe("alice");
    expect(result.current.data?.users?.[0].currentGame?.title).toBe(
      "Super Mario Bros",
    );
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));

    const { result } = renderHook(() => useOnlineUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useActivityFeed", () => {
  it("fetches activity feed with default pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockActivityFeed));

    const { result } = renderHook(() => useActivityFeed(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/social/activity", {
      params: { query: { page: 1, pageSize: 20 } },
    });
    expect(result.current.data?.data).toHaveLength(2);
    expect(result.current.data?.total).toBe(2);
  });

  it("fetches activity feed with custom pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ ...mockActivityFeed, page: 2 }));

    const { result } = renderHook(() => useActivityFeed(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/social/activity", {
      params: { query: { page: 2, pageSize: 10 } },
    });
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useActivityFeed(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
