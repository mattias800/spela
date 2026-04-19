import { renderHook, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement, type ReactNode } from "react";
import {
  useBiosStatus,
  useBiosFiles,
  useUploadBiosFile,
  useDeleteBiosFile,
} from "../use-bios";

vi.mock("@/lib/api-client", () => ({
  api: {
    upload: vi.fn(),
  },
  typedApi: {
    GET: vi.fn(),
    DELETE: vi.fn(),
    POST: vi.fn(),
  },
  unwrap: vi.fn(<T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
    p.then((r) => {
      if (r.error !== undefined) throw r.error;
      return r.data;
    }),
  ),
}));

import { api, typedApi } from "@/lib/api-client";

const mockApi = api as unknown as {
  upload: ReturnType<typeof vi.fn>;
};

const mockTypedApi = typedApi as unknown as {
  GET: ReturnType<typeof vi.fn>;
  DELETE: ReturnType<typeof vi.fn>;
  POST: ReturnType<typeof vi.fn>;
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

const mockBiosResponse = {
  files: [
    {
      name: "scph5501.bin",
      size: 524288,
      md5: "924e392ed05558ffdb115408c263dccf",
      consoleId: "psx",
      consoleName: "PlayStation",
      description: "PlayStation BIOS (North America)",
      required: true,
      status: "valid" as const,
    },
  ],
  consoles: [
    {
      consoleId: "psx",
      consoleName: "PlayStation",
      biosRequired: true,
      status: "ready" as const,
      requiredPresent: 1,
      requiredTotal: 1,
      optionalPresent: 0,
      optionalTotal: 2,
      files: [
        {
          fileName: "scph5501.bin",
          description: "PlayStation BIOS (North America)",
          required: true,
          md5: "924e392ed05558ffdb115408c263dccf",
          status: "valid" as const,
        },
      ],
    },
  ],
};

describe("useBiosStatus", () => {
  it("fetches enriched BIOS data", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockBiosResponse));

    const { result } = renderHook(() => useBiosStatus(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockTypedApi.GET).toHaveBeenCalledWith("/api/bios");
    expect(result.current.data).toEqual(mockBiosResponse);
    expect(result.current.data?.consoles).toHaveLength(1);
    expect(result.current.data?.consoles?.[0].status).toBe("ready");
  });

  it("handles fetch error", async () => {
    mockTypedApi.GET.mockReturnValue(Promise.reject(new Error("Server error")));

    const { result } = renderHook(() => useBiosStatus(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Server error");
  });
});

describe("useBiosFiles", () => {
  it("returns only the files array for backward compatibility", async () => {
    mockTypedApi.GET.mockReturnValue(ok(mockBiosResponse));

    const { result } = renderHook(() => useBiosFiles(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockBiosResponse.files);
    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].name).toBe("scph5501.bin");
  });
});

describe("useUploadBiosFile", () => {
  it("uploads a file via FormData", async () => {
    const uploadResult = {
      name: "scph5501.bin",
      size: 524288,
      md5: "924e392ed05558ffdb115408c263dccf",
      consoleId: "psx",
      consoleName: "PlayStation",
      description: "PlayStation BIOS (North America)",
      required: true,
      status: "valid" as const,
    };
    mockApi.upload.mockResolvedValue(uploadResult);

    const { result } = renderHook(() => useUploadBiosFile(), {
      wrapper: createWrapper(),
    });

    const testFile = new File(["test"], "scph5501.bin", {
      type: "application/octet-stream",
    });
    result.current.mutate(testFile);

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockApi.upload).toHaveBeenCalledWith(
      "/admin/bios",
      expect.any(FormData),
    );
    expect(result.current.data).toEqual(uploadResult);
  });

  it("handles upload error", async () => {
    mockApi.upload.mockRejectedValue(new Error("413 Payload Too Large"));

    const { result } = renderHook(() => useUploadBiosFile(), {
      wrapper: createWrapper(),
    });

    const testFile = new File(["test"], "big-file.bin", {
      type: "application/octet-stream",
    });
    result.current.mutate(testFile);

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("413 Payload Too Large");
  });
});

describe("useDeleteBiosFile", () => {
  it("deletes a BIOS file", async () => {
    mockTypedApi.DELETE.mockReturnValue(ok(undefined));

    const { result } = renderHook(() => useDeleteBiosFile(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("scph5501.bin");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockTypedApi.DELETE).toHaveBeenCalledWith(
      "/api/admin/bios/{filename}",
      { params: { path: { filename: "scph5501.bin" } } },
    );
  });

  it("handles delete error", async () => {
    mockTypedApi.DELETE.mockReturnValue(Promise.reject(new Error("Not found")));

    const { result } = renderHook(() => useDeleteBiosFile(), {
      wrapper: createWrapper(),
    });

    result.current.mutate("nonexistent.bin");

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe("Not found");
  });
});
