import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useSharedSaves, useDeleteSharedSave } from "../use-shared-saves";

vi.mock("@/lib/api-client", () => ({
  api: {
    get: vi.fn(),
    upload: vi.fn(),
    delete: vi.fn(),
    getAccessToken: vi.fn(() => "test-token"),
  },
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  get: ReturnType<typeof vi.fn>;
  upload: ReturnType<typeof vi.fn>;
  delete: ReturnType<typeof vi.fn>;
  getAccessToken: ReturnType<typeof vi.fn>;
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

const mockSharedSaves = {
  data: [
    {
      id: "ss1",
      userId: "u1",
      username: "alice",
      gameId: "g1",
      name: "Final Boss",
      description: "Right before the final boss",
      fileSize: 32768,
      downloadCount: 5,
      createdAt: "2026-02-13T10:00:00Z",
    },
    {
      id: "ss2",
      userId: "u2",
      username: "bob",
      gameId: "g1",
      name: "100% Complete",
      fileSize: 65536,
      downloadCount: 12,
      createdAt: "2026-02-12T08:00:00Z",
    },
  ],
  total: 2,
  page: 1,
  pageSize: 20,
};

describe("useSharedSaves", () => {
  it("fetches shared saves for a game", async () => {
    mockApi.get.mockResolvedValue(mockSharedSaves);

    const { result } = renderHook(() => useSharedSaves("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/games/g1/shared-saves?page=1&pageSize=20",
    );
    expect(result.current.data?.data).toHaveLength(2);
    expect(result.current.data?.data[0].name).toBe("Final Boss");
  });

  it("fetches with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockSharedSaves, page: 2 });

    const { result } = renderHook(() => useSharedSaves("g1", 2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/games/g1/shared-saves?page=2&pageSize=10",
    );
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useSharedSaves("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteSharedSave", () => {
  it("deletes a shared save", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "ss1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/games/g1/shared-saves/ss1");
  });

  it("handles delete error", async () => {
    mockApi.delete.mockRejectedValue(new Error("Forbidden"));

    const { result } = renderHook(() => useDeleteSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "ss1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
