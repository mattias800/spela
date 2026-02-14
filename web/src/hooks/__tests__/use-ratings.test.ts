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

vi.mock("@/lib/api-client", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  post: ReturnType<typeof vi.fn>;
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
    mockApi.get.mockResolvedValue(mockRatingsResponse);

    const { result } = renderHook(() => useGameRatings("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/games/g1/ratings?page=1&pageSize=20",
    );
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].rating).toBe(4);
  });

  it("fetches with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockRatingsResponse, page: 2 });

    const { result } = renderHook(() => useGameRatings("g1", 2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/games/g1/ratings?page=2&pageSize=10",
    );
  });
});

describe("useGameRatingSummary", () => {
  it("fetches rating summary", async () => {
    mockApi.get.mockResolvedValue(mockSummary);

    const { result } = renderHook(() => useGameRatingSummary("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/g1/ratings/summary");
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
    mockApi.get.mockResolvedValue(myRating);

    const { result } = renderHook(() => useMyRating("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/g1/ratings/mine");
    expect(result.current.data?.rating).toBe(5);
  });

  it("handles no rating (404)", async () => {
    mockApi.get.mockRejectedValue(new Error("no rating found"));

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
    mockApi.post.mockResolvedValue(createdRating);

    const { result } = renderHook(() => useRateGame(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", rating: 4, review: "Nice" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/games/g1/ratings", {
      rating: 4,
      review: "Nice",
    });
  });
});

describe("useDeleteRating", () => {
  it("deletes a rating", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteRating(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("g1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/games/g1/ratings");
  });
});
