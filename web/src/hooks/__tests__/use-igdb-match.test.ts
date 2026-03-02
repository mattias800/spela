import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useIgdbSearch, useApplyIgdbMatch } from "../use-admin";

vi.mock("@/lib/api-client", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
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

const mockSearchResults = [
  {
    igdbId: 1234,
    name: "Super Mario Bros.",
    coverUrl: "https://images.igdb.com/cover.jpg",
    releaseYear: 1985,
    developer: "Nintendo",
    summary: "A classic platformer",
  },
  {
    igdbId: 5678,
    name: "Super Mario Bros. 2",
    coverUrl: "https://images.igdb.com/cover2.jpg",
    releaseYear: 1988,
    developer: "Nintendo",
    summary: "The sequel",
  },
];

describe("useIgdbSearch", () => {
  it("fetches IGDB search results for a game", async () => {
    mockApi.get.mockResolvedValue(mockSearchResults);

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Super Mario"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/admin/games/game-1/igdb-search?q=Super%20Mario",
    );
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data?.[0].igdbId).toBe(1234);
    expect(result.current.data?.[0].name).toBe("Super Mario Bros.");
  });

  it("does not fetch when query is less than 2 characters", () => {
    const { result } = renderHook(() => useIgdbSearch("game-1", "S"), {
      wrapper: createWrapper(),
    });

    expect(result.current.isFetching).toBe(false);
    expect(mockApi.get).not.toHaveBeenCalled();
  });

  it("fetches when query is exactly 2 characters", async () => {
    mockApi.get.mockResolvedValue([]);

    const { result } = renderHook(() => useIgdbSearch("game-1", "SM"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/admin/games/game-1/igdb-search?q=SM",
    );
  });

  it("encodes special characters in query", async () => {
    mockApi.get.mockResolvedValue([]);

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Crash & Burn"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/admin/games/game-1/igdb-search?q=Crash%20%26%20Burn",
    );
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Super Mario"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useApplyIgdbMatch", () => {
  it("posts IGDB match and succeeds", async () => {
    mockApi.post.mockResolvedValue({ id: "game-1", title: "Super Mario Bros." });

    const { result } = renderHook(() => useApplyIgdbMatch(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "game-1", igdbId: 1234 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/admin/games/game-1/igdb-match",
      { igdbId: 1234 },
    );
  });

  it("handles mutation error", async () => {
    mockApi.post.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useApplyIgdbMatch(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "game-1", igdbId: 9999 });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
