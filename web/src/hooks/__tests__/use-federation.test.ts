import { createElement, type ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useFederationExchanges } from "../use-federation";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    GET: vi.fn(),
    POST: vi.fn(),
    PUT: vi.fn(),
    DELETE: vi.fn(),
  },
  unwrap: vi.fn(
    <T>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
      p.then((r) => {
        if (r.error !== undefined) throw r.error;
        return r.data;
      }),
  ),
}));

import { typedApi } from "@/lib/api-client";

const mockTypedApi = typedApi as unknown as {
  GET: ReturnType<typeof vi.fn>;
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

describe("useFederationExchanges", () => {
  it("fetches recent exchanges with the default limit", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ exchanges: [] }));

    const { result } = renderHook(() => useFederationExchanges(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/admin/federation/exchanges",
      { params: { query: { limit: 50 } } },
    );
  });

  it("sends exchange ledger filters to the API", async () => {
    mockTypedApi.GET.mockReturnValue(ok({ exchanges: [] }));

    const filters = {
      peer: "abcdef1234567890xyz",
      direction: "outbound",
      operation: "stats_pull",
      status: "ok",
      startedAfter: "2026-07-04T10:00:00.000Z",
      startedBefore: "2026-07-04T11:00:00.000Z",
      limit: 25,
    };
    const { result } = renderHook(() => useFederationExchanges(filters), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith(
      "/api/admin/federation/exchanges",
      { params: { query: filters } },
    );
  });
});
