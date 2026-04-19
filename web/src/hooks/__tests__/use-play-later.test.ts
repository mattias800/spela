import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  usePlayLaterGames,
  useAddToPlayLater,
  useRemoveFromPlayLater,
  useReorderPlayLater,
  useTogglePlayLater,
} from "../use-play-later";

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

const mockPlayLaterGames = [
  {
    id: "g1",
    title: "Super Mario Bros.",
    consoleId: "1",
    consoleName: "NES",
    fileName: "smb.nes",
    fileSize: 40976,
    discCount: 1,
    isInPlayLater: true,
    coverAspectRatio: 0.75,
    playable: true,
    isFavorite: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    scrapeAttempts: 1,
    screenshotUrls: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
  {
    id: "g2",
    title: "Zelda",
    consoleId: "1",
    consoleName: "NES",
    fileName: "zelda.nes",
    fileSize: 131072,
    discCount: 1,
    isInPlayLater: true,
    coverAspectRatio: 0.75,
    playable: true,
    isFavorite: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    scrapeAttempts: 1,
    screenshotUrls: [],
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
  },
];

describe("usePlayLaterGames", () => {
  it("fetches play later games list", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockPlayLaterGames));

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/play-later");
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data![0].title).toBe("Super Mario Bros.");
    expect(result.current.data![1].title).toBe("Zelda");
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it("returns empty array when queue is empty", async () => {
    mockTypedApi.GET.mockReturnValue(ok([]));

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

describe("useAddToPlayLater", () => {
  it("posts to add a game to the play later queue", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useAddToPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate("g1");
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/user/play-later/{gameId}",
      { params: { path: { gameId: "g1" } } },
    );
  });
});

describe("useRemoveFromPlayLater", () => {
  it("deletes a game from the play later queue", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useRemoveFromPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate("g1");
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/user/play-later/{gameId}",
      { params: { path: { gameId: "g1" } } },
    );
  });
});

describe("useReorderPlayLater", () => {
  it("puts new game order to the server", async () => {
    mockTypedApi.PUT.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useReorderPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate(["g2", "g1"]);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith(
      "/api/user/play-later/reorder",
      { body: { gameIds: ["g2", "g1"] } },
    );
  });
});

describe("useTogglePlayLater", () => {
  it("adds game to play later when not in queue", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useTogglePlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ gameId: "g1", isInPlayLater: false });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/user/play-later/{gameId}",
      { params: { path: { gameId: "g1" } } },
    );
  });

  it("removes game from play later when already in queue", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useTogglePlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ gameId: "g1", isInPlayLater: true });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/user/play-later/{gameId}",
      { params: { path: { gameId: "g1" } } },
    );
  });

  it("provides a toggle helper that reads isInPlayLater from game", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useTogglePlayLater(), {
      wrapper: createWrapper(),
    });

    const game = {
      ...mockPlayLaterGames[0],
      isInPlayLater: false,
    };

    act(() => {
      result.current.toggle(
        game as Parameters<typeof result.current.toggle>[0],
      );
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/user/play-later/{gameId}",
      { params: { path: { gameId: "g1" } } },
    );
  });
});
