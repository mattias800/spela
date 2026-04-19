import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useSharedSaves,
  useDeleteSharedSave,
  useCreateSessionFromSharedSave,
} from "../use-shared-saves";

// Mock the typedApi client + unwrap helper. typedApi methods return
// { data, error, response } in production; unwrap throws on error and returns
// data on success. The mocks below let tests resolve/reject mutation/query
// calls directly, mimicking the previous api.get/post/delete pattern.
vi.mock("@/lib/api-client", () => {
  const respond = (data: unknown) =>
    Promise.resolve({ data, response: new Response(null, { status: 200 }) });
  const reject = (err: Error) => Promise.reject(err);
  return {
    typedApi: {
      GET: vi.fn(),
      POST: vi.fn(),
      PUT: vi.fn(),
      DELETE: vi.fn(),
    },
    unwrap: vi.fn(<T>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
      p.then((r) => {
        if (r.error !== undefined) throw r.error;
        return r.data;
      }),
    ),
    api: {
      get: vi.fn(),
      post: vi.fn(),
      upload: vi.fn(),
      delete: vi.fn(),
      getAccessToken: vi.fn(() => "test-token"),
    },
    __helpers: { respond, reject },
  };
});

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
    mockTypedApi.GET.mockReturnValue(ok(mockSharedSaves));

    const { result } = renderHook(() => useSharedSaves("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/shared-saves",
      { params: { path: { id: "g1" }, query: { page: 1, pageSize: 20 } } },
    );
    expect(result.current.data?.data).toHaveLength(2);
    expect(result.current.data?.data?.[0].name).toBe("Final Boss");
  });

  it("fetches with custom pagination", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ ...mockSharedSaves, page: 2 }));

    const { result } = renderHook(() => useSharedSaves("g1", 2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/shared-saves",
      { params: { path: { id: "g1" }, query: { page: 2, pageSize: 10 } } },
    );
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useSharedSaves("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteSharedSave", () => {
  it("deletes a shared save", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "ss1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/games/{id}/shared-saves/{saveId}",
      { params: { path: { id: "g1", saveId: "ss1" } } },
    );
  });

  it("handles delete error", async () => {
    mockTypedApi.DELETE.mockReturnValue(Promise.reject(new Error("Forbidden")));

    const { result } = renderHook(() => useDeleteSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "ss1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useCreateSessionFromSharedSave", () => {
  const mockSession = {
    id: "ses-new",
    gameId: "g1",
    name: "From shared save",
    lastPlayedAt: "2026-03-06T10:00:00Z",
    lastPlayedByUsername: "alice",
    totalPlayTime: 0,
    screenshotUrl: null,
    cheatsEnabled: false,
    isSharedSession: false,
    memberCount: 1,
    memberUsernames: [],
    memberAvatars: [],
    createdAt: "2026-03-06T10:00:00Z",
    updatedAt: "2026-03-06T10:00:00Z",
  };

  it("creates a session from a shared save", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSession));

    const { result } = renderHook(() => useCreateSessionFromSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "ss1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/games/{id}/sessions/from-shared-save/{saveId}",
      { params: { path: { id: "g1", saveId: "ss1" } } },
    );
    expect(result.current.data).toEqual(mockSession);
  });

  it("handles error when creating session from shared save", async () => {
    mockTypedApi.POST.mockReturnValue(Promise.reject(new Error("Save not found")));

    const { result } = renderHook(() => useCreateSessionFromSharedSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ gameId: "g1", saveId: "bad-id" });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error).toBeInstanceOf(Error);
  });
});
