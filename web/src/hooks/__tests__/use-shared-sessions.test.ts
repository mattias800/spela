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
  api: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(),
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
  it("fetches shared sessions with default pagination", async () => {
    mockApi.get.mockResolvedValue(mockSharedSessionsResponse);

    const { result } = renderHook(() => useMySharedSessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/shared-sessions?page=1&pageSize=20");
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].name).toBe("Friday Night SNES");
  });

  it("fetches shared sessions with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockSharedSessionsResponse, page: 2 });

    const { result } = renderHook(() => useMySharedSessions(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/shared-sessions?page=2&pageSize=10");
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useMySharedSessions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useSharedSessionInvitations", () => {
  it("fetches invitations", async () => {
    mockApi.get.mockResolvedValue(mockInvitations);

    const { result } = renderHook(() => useSharedSessionInvitations(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/user/shared-session-invites");
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].sharedSessionName).toBe("Friday Night SNES");
  });
});

describe("usePendingInvitationCount", () => {
  it("fetches invitation count", async () => {
    mockApi.get.mockResolvedValue({ count: 3 });

    const { result } = renderHook(() => usePendingInvitationCount(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/user/shared-session-invites/count");
    expect(result.current.data?.count).toBe(3);
  });
});

describe("useSharedSession", () => {
  it("fetches shared session detail", async () => {
    mockApi.get.mockResolvedValue(mockSharedSessionDetail);

    const { result } = renderHook(() => useSharedSession("ss-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/shared-sessions/ss-1");
    expect(result.current.data?.members).toHaveLength(2);
    expect(result.current.data?.members[0].role).toBe("owner");
  });

  it("does not fetch when id is empty", () => {
    mockApi.get.mockResolvedValue(mockSharedSessionDetail);

    renderHook(() => useSharedSession(""), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });
});

describe("useSharedSessionSaves", () => {
  it("fetches saves for a shared session", async () => {
    mockApi.get.mockResolvedValue(mockSaves);

    const { result } = renderHook(() => useSharedSessionSaves("ss-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/shared-sessions/ss-1/saves");
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].name).toBe("World 3");
  });
});

describe("useGameSharedSessions", () => {
  it("fetches shared sessions for a game", async () => {
    mockApi.get.mockResolvedValue([mockSharedSessionsResponse.data[0]]);

    const { result } = renderHook(() => useGameSharedSessions("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/g1/shared-sessions");
  });
});

describe("useCreateSharedSession", () => {
  it("creates a shared session", async () => {
    mockApi.post.mockResolvedValue(mockSharedSessionDetail);

    const { result } = renderHook(() => useCreateSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      name: "New Shared Session",
      gameId: "g1",
      description: "A test shared session",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/shared-sessions", {
      name: "New Shared Session",
      gameId: "g1",
      description: "A test shared session",
    });
  });

  it("handles create error", async () => {
    mockApi.post.mockRejectedValue(new Error("Conflict"));

    const { result } = renderHook(() => useCreateSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ name: "Dup", gameId: "g1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteSharedSession", () => {
  it("deletes a shared session", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ss-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/shared-sessions/ss-1");
  });
});

describe("useInviteToSharedSession", () => {
  it("sends an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useInviteToSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", username: "charlie" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/shared-sessions/ss-1/invites", {
      username: "charlie",
    });
  });

  it("handles invite error", async () => {
    mockApi.post.mockRejectedValue(new Error("User not found"));

    const { result } = renderHook(() => useInviteToSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", username: "nobody" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useAcceptSharedSessionInvitation", () => {
  it("accepts an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useAcceptSharedSessionInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/user/shared-session-invites/inv-1/accept",
    );
  });
});

describe("useRejectSharedSessionInvitation", () => {
  it("rejects an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRejectSharedSessionInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/user/shared-session-invites/inv-1/decline",
    );
  });
});

describe("useLeaveSharedSession", () => {
  it("leaves a shared session", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useLeaveSharedSession(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("ss-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/shared-sessions/ss-1/leave",
    );
  });
});

describe("useRemoveSharedSessionMember", () => {
  it("removes a member", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRemoveSharedSessionMember(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", userId: "u2" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/shared-sessions/ss-1/members/u2");
  });
});

describe("useDeleteSharedSessionSave", () => {
  it("deletes a shared session save", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteSharedSessionSave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ sharedSessionId: "ss-1", saveId: "1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/shared-sessions/ss-1/saves/1");
  });
});
