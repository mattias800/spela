import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useMySharedSessions,
  useSharedSessionInvitations,
  usePendingInvitationCount,
  useSharedSession,
  useSharedSessionSaves,
  useGameSharedSessions,
  useCreateSharedSession,
  useDeleteSharedSession,
  useInviteToSharedSession,
  useAcceptSharedSessionInvitation,
  useRejectSharedSessionInvitation,
  useLeaveSharedSession,
  useRemoveSharedSessionMember,
  useDeleteSharedSessionSave,
} from "../use-shared-sessions";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
    POST: vi.fn(),
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

const mockSharedSessionsResponse = {
  data: [
    {
      id: "ss-1",
      name: "Friday Night SNES",
      gameId: "g1",
      gameTitle: "Super Mario World",
      gameCoverUrl: "https://example.com/cover.png",
      gameConsoleName: "SNES",
      ownerId: "u1",
      ownerUsername: "alice",
      status: "active",
      memberCount: 3,
      lastActivityAt: "2026-02-13T10:00:00Z",
      createdAt: "2026-02-01T10:00:00Z",
      updatedAt: "2026-02-13T10:00:00Z",
    },
  ],
  total: 1,
  page: 1,
  pageSize: 20,
};

const mockSharedSessionDetail = {
  id: "ss-1",
  name: "Friday Night SNES",
  gameId: "g1",
  gameTitle: "Super Mario World",
  gameCoverUrl: "https://example.com/cover.png",
  gameConsoleName: "SNES",
  ownerId: "u1",
  ownerUsername: "alice",
  status: "active",
  memberCount: 2,
  lastActivityAt: "2026-02-13T10:00:00Z",
  createdAt: "2026-02-01T10:00:00Z",
  updatedAt: "2026-02-13T10:00:00Z",
  members: [
    {
      userId: "u1",
      username: "alice",
      role: "owner",
      joinedAt: "2026-02-01T10:00:00Z",
      isOnline: true,
    },
    {
      userId: "u2",
      username: "bob",
      role: "member",
      joinedAt: "2026-02-02T10:00:00Z",
      isOnline: false,
    },
  ],
};

const mockInvitations = {
  data: [
    {
      id: "inv-1",
      sharedSessionId: "ss-1",
      sharedSessionName: "Friday Night SNES",
      gameId: "g1",
      gameTitle: "Super Mario World",
      gameConsoleName: "SNES",
      inviterUsername: "alice",
      createdAt: "2026-02-13T10:00:00Z",
    },
  ],
  total: 1,
};

const mockSaves = [
  {
    id: "1",
    sharedSessionId: "ss-1",
    gameId: "100",
    userId: "1",
    username: "alice",
    name: "World 3",
    fileSize: 32768,
    isAuto: false,
    createdAt: "2026-02-13T10:00:00Z",
    updatedAt: "2026-02-13T10:00:00Z",
  },
];

describe("useMySharedSessions", () => {
  it("fetches shared sessions", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSharedSessionsResponse));

    const { result } = renderHook(() => useMySharedSessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/shared-sessions");
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].name).toBe("Friday Night SNES");
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useMySharedSessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useSharedSessionInvitations", () => {
  it("fetches invitations", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockInvitations));

    const { result } = renderHook(() => useSharedSessionInvitations(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/user/shared-session-invites",
    );
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].sharedSessionName).toBe(
      "Friday Night SNES",
    );
  });
});

describe("usePendingInvitationCount", () => {
  it("fetches invitation count", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ count: 3 }));

    const { result } = renderHook(() => usePendingInvitationCount(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/user/shared-session-invites/count",
    );
    expect(result.current.data?.count).toBe(3);
  });
});

describe("useSharedSession", () => {
  it("fetches shared session detail", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSharedSessionDetail));

    const { result } = renderHook(() => useSharedSession("ss-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}",
      { params: { path: { id: "ss-1" } } },
    );
    expect(result.current.data?.members).toHaveLength(2);
    expect(result.current.data?.members?.[0].role).toBe("owner");
  });

  it("does not fetch when id is empty", () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSharedSessionDetail));

    renderHook(() => useSharedSession(""), {
      wrapper: createWrapper(),
    });

    expect(mockTypedApi.GET).not.toHaveBeenCalled();
  });
});

describe("useSharedSessionSaves", () => {
  it("fetches saves for a shared session", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockSaves));

    const { result } = renderHook(() => useSharedSessionSaves("ss-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}/saves",
      { params: { path: { id: "ss-1" } } },
    );
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].name).toBe("World 3");
  });
});

describe("useGameSharedSessions", () => {
  it("fetches shared sessions for a game", async () => {
    mockTypedApi.GET.mockReturnValue(
      ok([mockSharedSessionsResponse.data[0]]),
    );

    const { result } = renderHook(() => useGameSharedSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/games/{id}/shared-sessions",
      { params: { path: { id: "g1" } } },
    );
  });
});

describe("useCreateSharedSession", () => {
  it("creates a shared session", async () => {
    mockTypedApi.POST.mockReturnValue(ok(mockSharedSessionDetail));

    const { result } = renderHook(() => useCreateSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      name: "New Shared Session",
      gameId: "g1",
      description: "A test shared session",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/shared-sessions", {
      body: {
        name: "New Shared Session",
        gameId: "g1",
        description: "A test shared session",
      },
    });
  });

  it("handles create error", async () => {
    mockTypedApi.POST.mockReturnValue(Promise.reject(new Error("Conflict")));

    const { result } = renderHook(() => useCreateSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ name: "Dup", gameId: "g1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteSharedSession", () => {
  it("deletes a shared session", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ss-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}",
      { params: { path: { id: "ss-1" } } },
    );
  });
});

describe("useInviteToSharedSession", () => {
  it("sends an invitation", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useInviteToSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", username: "charlie" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}/invites",
      { params: { path: { id: "ss-1" } }, body: { username: "charlie" } },
    );
  });

  it("handles invite error", async () => {
    mockTypedApi.POST.mockReturnValue(
      Promise.reject(new Error("User not found")),
    );

    const { result } = renderHook(() => useInviteToSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", username: "nobody" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useAcceptSharedSessionInvitation", () => {
  it("accepts an invitation", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useAcceptSharedSessionInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/user/shared-session-invites/{id}/accept",
      { params: { path: { id: "inv-1" } } },
    );
  });
});

describe("useRejectSharedSessionInvitation", () => {
  it("rejects an invitation", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useRejectSharedSessionInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/user/shared-session-invites/{id}/decline",
      { params: { path: { id: "inv-1" } } },
    );
  });
});

describe("useLeaveSharedSession", () => {
  it("leaves a shared session", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useLeaveSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ss-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}/leave",
      { params: { path: { id: "ss-1" } } },
    );
  });
});

describe("useRemoveSharedSessionMember", () => {
  it("removes a member", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useRemoveSharedSessionMember(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", userId: "u2" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}/members/{userId}",
      { params: { path: { id: "ss-1", userId: "u2" } } },
    );
  });
});

describe("useDeleteSharedSessionSave", () => {
  it("deletes a shared session save", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteSharedSessionSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", saveId: "1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/shared-sessions/{id}/saves/{saveId}",
      { params: { path: { id: "ss-1", saveId: "1" } } },
    );
  });
});
