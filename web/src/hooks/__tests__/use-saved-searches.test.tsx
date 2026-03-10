import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useSavedSearches, useCreateSavedSearch, useDeleteSavedSearch } from "../use-saved-searches";

const mockGet = vi.fn();
const mockPost = vi.fn();
const mockDelete = vi.fn();

vi.mock("@/lib/api-client", () => ({
  api: {
    get: (...args: unknown[]) => mockGet(...args),
    post: (...args: unknown[]) => mockPost(...args),
    delete: (...args: unknown[]) => mockDelete(...args),
  },
}));

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
    mockGet.mockResolvedValueOnce(data);
    const { result } = renderHook(() => useSavedSearches(), { wrapper: createWrapper() });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockGet).toHaveBeenCalledWith("/user/saved-searches");
    expect(result.current.data).toEqual(data);
  });
});

describe("useCreateSavedSearch", () => {
  it("creates a saved search", async () => {
    const created = { id: "2", name: "Test", filters: { genres: "Action" }, createdAt: "2026-01-01T00:00:00Z" };
    mockPost.mockResolvedValueOnce(created);
    const { result } = renderHook(() => useCreateSavedSearch(), { wrapper: createWrapper() });
    result.current.mutate({ name: "Test", filters: { genres: "Action" } });
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockPost).toHaveBeenCalledWith("/user/saved-searches", { name: "Test", filters: { genres: "Action" } });
  });
});

describe("useDeleteSavedSearch", () => {
  it("deletes a saved search", async () => {
    mockDelete.mockResolvedValueOnce({ status: "deleted" });
    const { result } = renderHook(() => useDeleteSavedSearch(), { wrapper: createWrapper() });
    result.current.mutate("123");
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockDelete).toHaveBeenCalledWith("/user/saved-searches/123");
  });
});
