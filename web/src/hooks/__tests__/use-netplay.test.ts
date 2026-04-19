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

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(),
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
    mockTypedApi.GET.mockReturnValue(ok(mockSessionsResponse));

    const { result } = renderHook(() => useNetplaySessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/netplay/sessions", {
      params: { query: { page: 1, pageSize: 20 } },
    });
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].gameTitle).toBe("Super Mario World");
  });

  it("fetches sessions with custom pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ ...mockSessionsResponse, page: 2 }));

    const { result } = renderHook(() => useNetplaySessions(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/netplay/sessions", {
      params: { query: { page: 2, pageSize: 10 } },
    });
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useNetplaySessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useNetplaySession", () => {
  it("fetches session detail", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSession));

    const { result } = renderHook(() => useNetplaySession("s1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/netplay/sessions/{id}",
      { params: { path: { id: "s1" } } },
    );
    expect(result.current.data?.hostUsername).toBe("alice");
  });

  it("does not fetch when id is empty", () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSession));

    renderHook(() => useNetplaySession(""), {
      wrapper: createWrapper(),
    });

    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });
});

describe("useCreateNetplaySession", () => {
  it("creates a session", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSession));

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/netplay/sessions", {
      body: { gameId: "g1" },
    });
  });

  it("creates a session with custom input delay", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSession));

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", inputDelay: 5 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/netplay/sessions", {
      body: { gameId: "g1", inputDelay: 5 },
    });
  });

  it("handles create error", async () => {
    mockTypedApi.POST.mockReturnValue(Promise.reject(new Error("Failed")));

    const { result } = renderHook(() => useCreateNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useJoinNetplaySession", () => {
  it("joins a session by invite code", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSession));

    const { result } = renderHook(() => useJoinNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ABC123");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/netplay/sessions/join",
      { body: { inviteCode: "ABC123" } },
    );
  });

  it("handles join error for invalid code", async () => {
    mockTypedApi.POST.mockReturnValue(
      Promise.reject(new Error("Session not found")),
    );

    const { result } = renderHook(() => useJoinNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("INVALID");

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useLeaveNetplaySession", () => {
  it("leaves a session", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useLeaveNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("s1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/netplay/sessions/{id}/leave",
      { params: { path: { id: "s1" } } },
    );
  });
});

describe("useUpdateNetplaySettings", () => {
  it("updates session settings", async () => {
    mockTypedApi.PUT.mockReturnValue(ok({ ...mockSession, inputDelay: 5 }));

    const { result } = renderHook(() => useUpdateNetplaySettings(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "s1", inputDelay: 5 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith(
      "/api/netplay/sessions/{id}/settings",
      { params: { path: { id: "s1" } }, body: { inputDelay: 5 } },
    );
  });
});

describe("useDeleteNetplaySession", () => {
  it("deletes a session", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteNetplaySession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("s1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/netplay/sessions/{id}",
      { params: { path: { id: "s1" } } },
    );
  });
});
