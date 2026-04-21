import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useGameSessions,
  useCreateSession,
  useRenameSession,
  useDeleteSession,
  useCloneSession,
} from "../use-sessions";

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
    mockTypedApi.GET.mockReturnValue(ok(mockSessions));

    const { result } = renderHook(() => useGameSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/games/{id}/sessions", {
      params: { path: { id: "g1" } },
    });
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].name).toBe("My Session");
  });

  it("does not fetch when gameId is empty", () => {
    mockTypedApi.GET.mockReturnValue(ok([]));

    renderHook(() => useGameSessions(""), {
      wrapper: createWrapper(),
    });

    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useGameSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useCreateSession", () => {
  it("creates a session", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSessions[0]));

    const { result } = renderHook(() => useCreateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", name: "New Session" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/games/{id}/sessions",
      {
        params: { path: { id: "g1" } },
        body: { name: "New Session" },
      },
    );
  });

  it("handles create error", async () => {
    mockTypedApi.POST.mockReturnValue(Promise.reject(new Error("Failed")));

    const { result } = renderHook(() => useCreateSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", name: "Test" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRenameSession", () => {
  it("renames a session", async () => {
    mockTypedApi.PUT.mockReturnValue(ok({ ...mockSessions[0], name: "Renamed" }));

    const { result } = renderHook(() => useRenameSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", name: "Renamed" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith("/api/sessions/{id}", {
      params: { path: { id: "ses-1" } },
      body: { name: "Renamed" },
    });
  });
});

describe("useDeleteSession", () => {
  it("deletes a session", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith("/api/sessions/{id}", {
      params: { path: { id: "ses-1" } },
    });
  });
});

describe("useCloneSession", () => {
  it("posts to the clone endpoint with no body when name is omitted", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ ...mockSessions[0], id: "ses-2", name: "My Session (Copy)" }),
    );

    const { result } = renderHook(() => useCloneSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/sessions/{id}/clone",
      {
        params: { path: { id: "ses-1" }, query: undefined },
        body: {},
      },
    );
  });

  it("sends a custom name in the body when provided", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ ...mockSessions[0], id: "ses-2", name: "Custom" }),
    );

    const { result } = renderHook(() => useCloneSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", name: "Custom" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/sessions/{id}/clone",
      {
        params: { path: { id: "ses-1" }, query: undefined },
        body: { name: "Custom" },
      },
    );
  });

  it("passes saveId as a query param when cloning from a specific save", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ ...mockSessions[0], id: "ses-2", name: "My Session (Copy)" }),
    );

    const { result } = renderHook(() => useCloneSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", saveId: 42 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/sessions/{id}/clone",
      {
        params: { path: { id: "ses-1" }, query: { saveId: 42 } },
        body: {},
      },
    );
  });

  it("omits the saveId query param when saveId is zero", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ ...mockSessions[0], id: "ses-2", name: "My Session (Copy)" }),
    );

    const { result } = renderHook(() => useCloneSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1", gameId: "g1", saveId: 0 });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/sessions/{id}/clone",
      {
        params: { path: { id: "ses-1" }, query: undefined },
        body: {},
      },
    );
  });

  it("works without gameId (shared-session clone case)", async () => {
    mockTypedApi.POST.mockReturnValue(
      ok({ ...mockSessions[0], id: "ses-2", name: "My Session (Copy)" }),
    );

    const { result } = renderHook(() => useCloneSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: "ses-1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalled();
  });
});
