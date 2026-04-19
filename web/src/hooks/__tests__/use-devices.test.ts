import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useDevices,
  useUpdateDevice,
  useDeleteDevice,
  useUpdateDevicePreferences,
} from "../use-devices";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
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

const mockDevices = [
  {
    id: 1,
    userId: 10,
    deviceUuid: "uuid-abc",
    name: "My Phone",
    platform: "android",
    lastSeenAt: "2025-01-15T10:00:00Z",
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-15T10:00:00Z",
    consoleShaders: { "1": "crt-simple" },
  },
  {
    id: 2,
    userId: 10,
    deviceUuid: "uuid-def",
    name: "Desktop",
    platform: "linux",
    lastSeenAt: "2025-01-14T08:00:00Z",
    createdAt: "2025-01-02T00:00:00Z",
    updatedAt: "2025-01-14T08:00:00Z",
    consoleShaders: {},
  },
];

describe("useDevices", () => {
  it("fetches the user's devices", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockDevices));

    const { result } = renderHook(() => useDevices(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/devices");
    expect(result.current.data).toHaveLength(2);
    expect(result.current.data?.[0].name).toBe("My Phone");
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useDevices(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useUpdateDevice", () => {
  it("sends rename request", async () => {
    mockTypedApi.PUT.mockReturnValue(ok({ id: 1, name: "New Name" }));

    const { result } = renderHook(() => useUpdateDevice(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: 1, name: "New Name" });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith("/api/user/devices/{id}", {
      params: { path: { id: "1" } },
      body: { name: "New Name" },
    });
  });

  it("handles rename error", async () => {
    mockTypedApi.PUT.mockReturnValue(Promise.reject(new Error("Not found")));

    const { result } = renderHook(() => useUpdateDevice(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: 999, name: "Nope" });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useDeleteDevice", () => {
  it("sends delete request", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteDevice(), {
      wrapper: createWrapper(),
    });

    result.current.mutate(1);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/user/devices/{id}",
      { params: { path: { id: "1" } } },
    );
  });

  it("handles delete error", async () => {
    mockTypedApi.DELETE.mockReturnValue(Promise.reject(new Error("Forbidden")));

    const { result } = renderHook(() => useDeleteDevice(), {
      wrapper: createWrapper(),
    });

    result.current.mutate(1);

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useUpdateDevicePreferences", () => {
  it("sends shader preferences update", async () => {
    mockTypedApi.PUT.mockReturnValue(
      ok({ consoleShaders: { "1": "scanlines" } }),
    );

    const { result } = renderHook(() => useUpdateDevicePreferences(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: 1, consoleShaders: { "1": "scanlines" } });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith(
      "/api/user/devices/{id}/preferences",
      {
        params: { path: { id: "1" } },
        body: { consoleShaders: { "1": "scanlines" } },
      },
    );
  });

  it("handles preferences update error", async () => {
    mockTypedApi.PUT.mockReturnValue(Promise.reject(new Error("Bad request")));

    const { result } = renderHook(() => useUpdateDevicePreferences(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ id: 1, consoleShaders: { "1": "invalid" } });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
