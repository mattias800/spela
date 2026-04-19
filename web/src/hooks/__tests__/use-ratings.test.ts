import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useGameRatings,
  useGameRatingSummary,
  useMyRating,
  useRateGame,
  useDeleteRating,
} from "../use-ratings";

vi.mock("@/lib/api-client", () => {
  class MockApiError extends Error {
    status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = "ApiError";
      this.status = status;
    }
  }
  return {
    typedApi: {
      GET: vi.fn(),
      POST: vi.fn(),
      DELETE: vi.fn(),
    },
    unwrap: vi.fn(
      <T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
        p.then((r) => {
          if (r.error !== undefined) {
            throw new MockApiError(r.response.status, "error");
          }
          return r.data;
        }),
    ),
    ApiError: MockApiError,
  };
});

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

function err(status: number) {
  return Promise.resolve({
    error: { message: "error" },
    response: new Response(null, { status }),
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

const mockRatingsResponse = {
  data: [
    {
      id: "r1",
      userId: "u1",
      username: "alice",
      gameId: "g1",
      rating: 4,
      review: "Great game!",
      createdAt: "2026-02-13T10:00:00Z",
      updatedAt: "2026-02-13T10:00:00Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockSummary = {
  averageRating: 4.2,
  totalRatings: 10,
  distribution: { "1": 0, "2": 1, "3": 2, "4": 4, "5": 3 },
};

describe("useGameRatings", () => {
  it("fetches game ratings with default pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockRatingsResponse));

    const { result } = renderHook(() => useGameRatings("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/games/{id}/ratings", {
      params: { path: { id: "g1" }, query: { page: 1, pageSize: 20 } },
    });
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data?.[0].rating).toBe(4);
  });

  it("fetches with custom pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ ...mockRatingsResponse, page: 2 }));

    const { result } = renderHook(() => useGameRatings("g1", 2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/games/{id}/ratings", {
      params: { path: { id: "g1" }, query: { page: 2, pageSize: 10 } },
    });
  });
});

describe("useGameRatingSummary", () => {
  it("fetches rating summary", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSummary));

    const { result } = renderHook(() => useGameRatingSummary("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/ratings/summary",
      { params: { path: { id: "g1" } } },
    );
    expect(result.current.data?.averageRating).toBe(4.2);
    expect(result.current.data?.totalRatings).toBe(10);
    expect(result.current.data?.distribution["5"]).toBe(3);
  });
});

describe("useMyRating", () => {
  it("fetches current user rating", async () => {
    const myRating = {
      id: "r1",
      userId: "u1",
      username: "alice",
      gameId: "g1",
      rating: 5,
      review: "Love it",
      createdAt: "2026-02-13T10:00:00Z",
      updatedAt: "2026-02-13T10:00:00Z",
    };
    mockTypedApi.GET.mockReturnValue(ok(myRating));

    const { result } = renderHook(() => useMyRating("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/ratings/mine",
      { params: { path: { id: "g1" } } },
    );
    expect(result.current.data?.rating).toBe(5);
  });

  it("returns null on 404 (no rating)", async () => {
    mockTypedApi.GET.mockReturnValue(err(404));

    const { result } = renderHook(() => useMyRating("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toBeNull();
  });

  it("propagates non-404 errors", async () => {
    mockTypedApi.GET.mockReturnValue(err(500));

    const { result } = renderHook(() => useMyRating("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRateGame", () => {
  it("submits a rating", async () => {
    const createdRating = {
      id: "r2",
      userId: "u1",
      username: "alice",
      gameId: "g1",
      rating: 4,
      review: "Nice",
      createdAt: "2026-02-13T11:00:00Z",
      updatedAt: "2026-02-13T11:00:00Z",
    };
    mockTypedApi.POST.mockReturnValue(ok(createdRating));

    const { result } = renderHook(() => useRateGame(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", rating: 4, review: "Nice" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/games/{id}/ratings", {
      params: { path: { id: "g1" } },
      body: { rating: 4, review: "Nice" },
    });
  });
});

describe("useDeleteRating", () => {
  it("deletes a rating", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteRating(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("g1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/games/{id}/ratings",
      { params: { path: { id: "g1" } } },
    );
  });
});
