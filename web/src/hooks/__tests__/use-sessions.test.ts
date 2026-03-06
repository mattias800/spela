import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useGameSessions,
  useCreateSession,
  useRenameSession,
  useDeleteSession,
  useDuplicateSession,
} from "../use-sessions";

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

const mockSessions = [
  {
    id: "ses-1",
    gameId: "g1",
    name: "My Session",
    lastPlayedAt: "2026-03-01T10:00:00Z",
    lastPlayedByUsername: "alice",
    totalPlayTime: 3600,
    screenshotUrl: null,
    cheatsEnabled: false,
    isSharedSession: false,
    memberCount: 1,
    memberUsernames: [],
    memberAvatars: [],
    createdAt: "2026-02-28T10:00:00Z",
    updatedAt: "2026-03-01T10:00:00Z",
  },
];

describe("useGameSessions", () => {
  it("fetches sessions for a game", async () => {
    mockApi.get.mockResolvedValue(mockSessions);

    const { result } = renderHook(() => useGameSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/g1/sessions");
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].name).toBe("My Session");
  });

  it("does not fetch when gameId is empty", () => {
    mockApi.get.mockResolvedValue([]);

    renderHook(() => useGameSessions(""), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useGameSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useCreateSession", () => {
  it("creates a session", async () => {
    mockApi.post.mockResolvedValue(mockSessions[0]);

    const { result } = renderHook(() => useCreateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", name: "New Session" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/games/g1/sessions", {
      name: "New Session",
    });
  });

  it("handles create error", async () => {
    mockApi.post.mockRejectedValue(new Error("Failed"));

    const { result } = renderHook(() => useCreateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", name: "Test" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRenameSession", () => {
  it("renames a session", async () => {
    mockApi.put.mockResolvedValue({ ...mockSessions[0], name: "Renamed" });

    const { result } = renderHook(() => useRenameSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", name: "Renamed" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.put).toHaveBeenCalledWith("/sessions/ses-1", {
      name: "Renamed",
    });
  });
});

describe("useDeleteSession", () => {
  it("deletes a session", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/sessions/ses-1");
  });
});

describe("useDuplicateSession", () => {
  it("duplicates a session", async () => {
    mockApi.post.mockResolvedValue({ ...mockSessions[0], id: "ses-2", name: "My Session (Copy)" });

    const { result } = renderHook(() => useDuplicateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/sessions/ses-1/duplicate", {});
  });

  it("duplicates a session with custom name", async () => {
    mockApi.post.mockResolvedValue({ ...mockSessions[0], id: "ses-2", name: "Custom" });

    const { result } = renderHook(() => useDuplicateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", name: "Custom" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/sessions/ses-1/duplicate", { name: "Custom" });
  });
});
