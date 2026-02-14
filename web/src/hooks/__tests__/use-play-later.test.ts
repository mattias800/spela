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
    isInPlayLater: true,
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
    isInPlayLater: true,
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
    mockApi.get.mockResolvedValue(mockPlayLaterGames);

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/user/play-later");
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data![0].title).toBe("Super Mario Bros.");
    expect(result.current.data![1].title).toBe("Zelda");
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  it("returns empty array when queue is empty", async () => {
    mockApi.get.mockResolvedValue([]);

    const { result } = renderHook(() => usePlayLaterGames(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toHaveLength(0);
  });
});

describe("useAddToPlayLater", () => {
  it("posts to add a game to the play later queue", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useAddToPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate("g1");
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/user/play-later/g1");
  });
});

describe("useRemoveFromPlayLater", () => {
  it("deletes a game from the play later queue", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRemoveFromPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate("g1");
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/user/play-later/g1");
  });
});

describe("useReorderPlayLater", () => {
  it("puts new game order to the server", async () => {
    mockApi.put.mockResolvedValue(undefined);

    const { result } = renderHook(() => useReorderPlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate(["g2", "g1"]);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.put).toHaveBeenCalledWith("/user/play-later/reorder", {
      gameIds: ["g2", "g1"],
    });
  });
});

describe("useTogglePlayLater", () => {
  it("adds game to play later when not in queue", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useTogglePlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ gameId: "g1", isInPlayLater: false });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/user/play-later/g1");
  });

  it("removes game from play later when already in queue", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useTogglePlayLater(), {
      wrapper: createWrapper(),
    });

    act(() => {
      result.current.mutate({ gameId: "g1", isInPlayLater: true });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/user/play-later/g1");
  });

  it("provides a toggle helper that reads isInPlayLater from game", async () => {
    mockApi.post.mockResolvedValue(undefined);

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
    expect(mockApi.post).toHaveBeenCalledWith("/user/play-later/g1");
  });
});
