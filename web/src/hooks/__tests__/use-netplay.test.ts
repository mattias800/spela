import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useNetplaySessions,
  useNetplaySession,
  useCreateNetplaySession,
  useJoinNetplaySession,
  useLeaveNetplaySession,
  useUpdateNetplaySettings,
  useDeleteNetplaySession,
} from "../use-netplay";

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

const mockSessionsResponse = {
  data: [
    {
      id: "s1",
      hostId: "u1",
      hostUsername: "alice",
      hostAvatarUrl: null,
      clientId: null,
      clientUsername: null,
      clientAvatarUrl: null,
      gameId: "g1",
      gameTitle: "Super Mario World",
      gameCoverUrl: "https://example.com/cover.png",
      consoleName: "SNES",
      status: "waiting",
      endReason: null,
      inputDelay: 3,
      inviteCode: "ABC123",
      createdAt: "2026-02-13T10:00:00Z",
      startedAt: null,
      endedAt: null,
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockSession = mockSessionsResponse.data[0];

describe("useNetplaySessions", () => {
  it("fetches sessions with default pagination", async () => {
    mockApi.get.mockResolvedValue(mockSessionsResponse);

    const { result } = renderHook(() => useNetplaySessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/netplay/sessions?page=1&pageSize=20",
    );
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].gameTitle).toBe("Super Mario World");
  });

  it("fetches sessions with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockSessionsResponse, page: 2 });

    const { result } = renderHook(() => useNetplaySessions(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith(
      "/netplay/sessions?page=2&pageSize=10",
    );
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useNetplaySessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useNetplaySession", () => {
  it("fetches session detail", async () => {
    mockApi.get.mockResolvedValue(mockSession);

    const { result } = renderHook(() => useNetplaySession("s1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/netplay/sessions/s1");
    expect(result.current.data?.hostUsername).toBe("alice");
  });

  it("does not fetch when id is empty", () => {
    mockApi.get.mockResolvedValue(mockSession);

    renderHook(() => useNetplaySession(""), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });
});

describe("useCreateNetplaySession", () => {
  it("creates a session", async () => {
    mockApi.post.mockResolvedValue(mockSession);

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      gameId: "g1",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/netplay/sessions", {
      gameId: "g1",
    });
  });

  it("creates a session with custom input delay", async () => {
    mockApi.post.mockResolvedValue(mockSession);

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      gameId: "g1",
      inputDelay: 5,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/netplay/sessions", {
      gameId: "g1",
      inputDelay: 5,
    });
  });

  it("handles create error", async () => {
    mockApi.post.mockRejectedValue(new Error("Failed"));

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useJoinNetplaySession", () => {
  it("joins a session by invite code", async () => {
    mockApi.post.mockResolvedValue(mockSession);

    const { result } = renderHook(() => useJoinNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ABC123");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/netplay/sessions/join", {
      inviteCode: "ABC123",
    });
  });

  it("handles join error for invalid code", async () => {
    mockApi.post.mockRejectedValue(new Error("Session not found"));

    const { result } = renderHook(() => useJoinNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("INVALID");

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useLeaveNetplaySession", () => {
  it("leaves a session", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useLeaveNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("s1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/netplay/sessions/s1/leave");
  });
});

describe("useUpdateNetplaySettings", () => {
  it("updates session settings", async () => {
    mockApi.put.mockResolvedValue({ ...mockSession, inputDelay: 5 });

    const { result } = renderHook(() => useUpdateNetplaySettings(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "s1", inputDelay: 5 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.put).toHaveBeenCalledWith(
      "/netplay/sessions/s1/settings",
      { inputDelay: 5 },
    );
  });
});

describe("useDeleteNetplaySession", () => {
  it("deletes a session", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("s1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/netplay/sessions/s1");
  });
});
