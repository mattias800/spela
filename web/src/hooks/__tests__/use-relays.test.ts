import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useMyRelays,
  useRelayInvitations,
  usePendingInvitationCount,
  useRelay,
  useRelaySaves,
  useGameRelays,
  useCreateRelay,
  useDeleteRelay,
  useInviteToRelay,
  useAcceptRelayInvitation,
  useRejectRelayInvitation,
  useLeaveRelay,
  useRemoveRelayMember,
  useDeleteRelaySave,
} from "../use-relays";

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

const mockRelaysResponse = {
  data: [
    {
      id: "relay-1",
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

const mockRelayDetail = {
  id: "relay-1",
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
      relayId: "relay-1",
      relayName: "Friday Night SNES",
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
    relayId: "relay-1",
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

describe("useMyRelays", () => {
  it("fetches relays with default pagination", async () => {
    mockApi.get.mockResolvedValue(mockRelaysResponse);

    const { result } = renderHook(() => useMyRelays(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays?page=1&pageSize=20");
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].name).toBe("Friday Night SNES");
  });

  it("fetches relays with custom pagination", async () => {
    mockApi.get.mockResolvedValue({ ...mockRelaysResponse, page: 2 });

    const { result } = renderHook(() => useMyRelays(2, 10), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays?page=2&pageSize=10");
  });

  it("handles fetch error", async () => {
    mockApi.get.mockRejectedValue(new Error("Server error"));

    const { result } = renderHook(() => useMyRelays(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRelayInvitations", () => {
  it("fetches invitations", async () => {
    mockApi.get.mockResolvedValue(mockInvitations);

    const { result } = renderHook(() => useRelayInvitations(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays/invitations");
    expect(result.current.data?.data).toHaveLength(1);
    expect(result.current.data?.data[0].relayName).toBe("Friday Night SNES");
  });
});

describe("usePendingInvitationCount", () => {
  it("fetches invitation count", async () => {
    mockApi.get.mockResolvedValue({ count: 3 });

    const { result } = renderHook(() => usePendingInvitationCount(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays/invitations/count");
    expect(result.current.data?.count).toBe(3);
  });
});

describe("useRelay", () => {
  it("fetches relay detail", async () => {
    mockApi.get.mockResolvedValue(mockRelayDetail);

    const { result } = renderHook(() => useRelay("relay-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays/relay-1");
    expect(result.current.data?.members).toHaveLength(2);
    expect(result.current.data?.members[0].role).toBe("owner");
  });

  it("does not fetch when id is empty", () => {
    mockApi.get.mockResolvedValue(mockRelayDetail);

    renderHook(() => useRelay(""), {
      wrapper: createWrapper(),
    });

    expect(mockApi.get).not.toHaveBeenCalled();
  });
});

describe("useRelaySaves", () => {
  it("fetches saves for a relay", async () => {
    mockApi.get.mockResolvedValue(mockSaves);

    const { result } = renderHook(() => useRelaySaves("relay-1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/relays/relay-1/saves");
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data?.[0].name).toBe("World 3");
  });
});

describe("useGameRelays", () => {
  it("fetches relays for a game", async () => {
    mockApi.get.mockResolvedValue([mockRelaysResponse.data[0]]);

    const { result } = renderHook(() => useGameRelays("g1"), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.get).toHaveBeenCalledWith("/games/g1/relays");
  });
});

describe("useCreateRelay", () => {
  it("creates a relay", async () => {
    mockApi.post.mockResolvedValue(mockRelayDetail);

    const { result } = renderHook(() => useCreateRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      name: "New Relay",
      gameId: "g1",
      description: "A test relay",
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/relays", {
      name: "New Relay",
      gameId: "g1",
      description: "A test relay",
    });
  });

  it("handles create error", async () => {
    mockApi.post.mockRejectedValue(new Error("Conflict"));

    const { result } = renderHook(() => useCreateRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ name: "Dup", gameId: "g1" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteRelay", () => {
  it("deletes a relay", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("relay-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/relays/relay-1");
  });
});

describe("useInviteToRelay", () => {
  it("sends an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useInviteToRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ relayId: "relay-1", username: "charlie" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith("/relays/relay-1/invitations", {
      username: "charlie",
    });
  });

  it("handles invite error", async () => {
    mockApi.post.mockRejectedValue(new Error("User not found"));

    const { result } = renderHook(() => useInviteToRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ relayId: "relay-1", username: "nobody" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useAcceptRelayInvitation", () => {
  it("accepts an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useAcceptRelayInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/relays/invitations/inv-1/accept",
    );
  });
});

describe("useRejectRelayInvitation", () => {
  it("rejects an invitation", async () => {
    mockApi.post.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRejectRelayInvitation(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("inv-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.post).toHaveBeenCalledWith(
      "/relays/invitations/inv-1/reject",
    );
  });
});

describe("useLeaveRelay", () => {
  it("leaves a relay", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useLeaveRelay(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("relay-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/relays/relay-1/members/me");
  });
});

describe("useRemoveRelayMember", () => {
  it("removes a member", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useRemoveRelayMember(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ relayId: "relay-1", userId: "u2" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/relays/relay-1/members/u2");
  });
});

describe("useDeleteRelaySave", () => {
  it("deletes a relay save", async () => {
    mockApi.delete.mockResolvedValue(undefined);

    const { result } = renderHook(() => useDeleteRelaySave(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ relayId: "relay-1", saveId: "1" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApi.delete).toHaveBeenCalledWith("/relays/relay-1/saves/1");
  });
});
