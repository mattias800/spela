import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import { useUserPreferences, useUpdatePreferences } from "../use-preferences";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
    PUT: vi.fn(),
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

describe("useUserPreferences", () => {
  it("fetches user preferences", async () => {
    const prefs = {
      showPerformanceOverlay: true,
      autoSaveEnabled: false,
      autoLoadSaveEnabled: true,
      selectedShader: "crt-simple",
      consoleShaders: { "1": "bilinear" },
    };
    mockTypedApi.GET.mockReturnValue(ok(prefs));

    const { result } = renderHook(() => useUserPreferences(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/preferences");
    expect(result.current.data).toEqual(prefs);
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Network error")));

    const { result } = renderHook(() => useUserPreferences(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Network error");
  });
});

describe("useUpdatePreferences", () => {
  it("sends partial update to the server", async () => {
    mockTypedApi.PUT.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useUpdatePreferences(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ autoSaveEnabled: true });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.PUT).toHaveBeenCalledWith("/api/user/preferences", {
      body: { autoSaveEnabled: true },
    });
  });

  it("handles update error", async () => {
    mockTypedApi.PUT.mockReturnValue(Promise.reject(new Error("Forbidden")));

    const { result } = renderHook(() => useUpdatePreferences(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({ selectedShader: "scanlines" });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Forbidden");
  });
});
