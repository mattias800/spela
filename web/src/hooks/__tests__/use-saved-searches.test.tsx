import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSavedSearches, useCreateSavedSearch, useDeleteSavedSearch } from "../use-saved-searches";

vi.mock("@/lib/api-client", () => ({
  typedApi: { GET: vi.fn(), POST: vi.fn(), PUT: vi.fn(), DELETE: vi.fn() },
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
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useSavedSearches", () => {
  it("fetches saved searches", async () => {
    const data = [
      { id: "1", name: "RPGs", filters: { genres: "RPG" }, createdAt: "2026-01-01T00:00:00Z" },
    ];
    mockTypedApi.GET.mockReturnValue(ok(data));
    const { result } = renderHook(() => useSavedSearches(), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/user/saved-searches");
    expect(result.current.data).toEqual(data);
  });
});

describe("useCreateSavedSearch", () => {
  it("creates a saved search", async () => {
    const created = { id: "2", name: "Test", filters: { genres: "Action" }, createdAt: "2026-01-01T00:00:00Z" };
    mockTypedApi.POST.mockReturnValue(ok(created));
    const { result } = renderHook(() => useCreateSavedSearch(), { wrapper: createWrapper() });
    result.current.mutate({ name: "Test", filters: { genres: "Action" } });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.POST).toHaveBeenCalledWith("/api/user/saved-searches", {
      body: { name: "Test", filters: { genres: "Action" } },
    });
  });
});

describe("useDeleteSavedSearch", () => {
  it("deletes a saved search", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok({ status: "deleted" }));
    const { result } = renderHook(() => useDeleteSavedSearch(), { wrapper: createWrapper() });
    result.current.mutate("123");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/user/saved-searches/{id}",
      { params: { path: { id: "123" } } },
    );
  });
});
