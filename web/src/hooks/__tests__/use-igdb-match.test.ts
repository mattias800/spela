import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useIgdbSearch, useApplyIgdbMatch } from "../use-admin";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
    POST: vi.fn(),
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
    mockTypedApi.GET.mockReturnValue(ok(mockSearchResults));

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Super Mario"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/admin/games/{id}/igdb-search",
      { params: { path: { id: "game-1" }, query: { q: "Super Mario" } } },
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
    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });

  it("fetches when query is exactly 2 characters", async () => {
    mockTypedApi.GET.mockReturnValue(ok([]));

    const { result } = renderHook(() => useIgdbSearch("game-1", "SM"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/admin/games/{id}/igdb-search",
      { params: { path: { id: "game-1" }, query: { q: "SM" } } },
    );
  });

  it("passes special characters through (openapi-fetch handles encoding)", async () => {
    mockTypedApi.GET.mockReturnValue(ok([]));

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Crash & Burn"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/admin/games/{id}/igdb-search",
      { params: { path: { id: "game-1" }, query: { q: "Crash & Burn" } } },
    );
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));

    const { result } = renderHook(
      () => useIgdbSearch("game-1", "Super Mario"),
      { wrapper: createWrapper() },
    );

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useApplyIgdbMatch", () => {
  it("posts IGDB match and succeeds", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ id: "game-1", title: "Super Mario Bros." }),
    );

    const { result } = renderHook(() => useApplyIgdbMatch(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "game-1", igdbId: 1234 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/admin/games/{id}/igdb-match",
      { params: { path: { id: "game-1" } }, body: { igdbId: 1234 } },
    );
  });

  it("handles mutation error", async () => {
    mockTypedApi.POST.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useApplyIgdbMatch(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "game-1", igdbId: 9999 });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
