import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { enqueueScrape, _resetScrapeQueue } from "../scrape-queue";

vi.mock("@/lib/api-client", () => ({
  typedApi: {
    POST: vi.fn(),
  },
  unwrap: vi.fn(<T,>(p: Promise<{ data?: T; error?: unknown; response: Response }>) =>
    p.then((r) => {
      if (r.error !== undefined) throw r.error;
      return r.data;
    }),
  ),
}));

vi.mock("@/lib/query-client", () => ({
  queryClient: {
    invalidateQueries: vi.fn(),
  },
}));

import { typedApi } from "@/lib/api-client";

const mockTypedApi = typedApi as unknown as {
  POST: ReturnType<typeof vi.fn>;
};

function ok(data: unknown) {
  return Promise.resolve({
    data,
    response: new Response(null, { status: 200 }),
  });
}

beforeEach(() => {
  vi.clearAllMocks();
  _resetScrapeQueue();
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

async function flushPromises() {
  await vi.runAllTimersAsync();
}

describe("scrape-queue", () => {
  it("calls typedApi.POST for an enqueued game", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    enqueueScrape("game-1");
    await flushPromises();

    expect(mockTypedApi.POST).toHaveBeenCalledWith(
      "/api/games/{id}/scrape-if-needed",
      { params: { path: { id: "game-1" } } },
    );
    expect(mockTypedApi.POST).toHaveBeenCalledTimes(1);
  });

  it("deduplicates the same game id", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    enqueueScrape("game-1");
    enqueueScrape("game-1");
    enqueueScrape("game-1");
    await flushPromises();

    expect(mockTypedApi.POST).toHaveBeenCalledTimes(1);
  });

  it("processes multiple games sequentially", async () => {
    const callOrder: string[] = [];
    mockTypedApi.POST.mockImplementation(
      (_url: string, opts: { params: { path: { id: string } } }) => {
        callOrder.push(opts.params.path.id);
        return ok(undefined);
      },
    );

    enqueueScrape("game-1");
    enqueueScrape("game-2");
    enqueueScrape("game-3");
    await flushPromises();

    expect(callOrder).toEqual(["game-1", "game-2", "game-3"]);
  });

  it("silently handles API errors", async () => {
    mockTypedApi.POST
      .mockReturnValueOnce(Promise.reject(new Error("Network error")))
      .mockReturnValueOnce(ok(undefined));

    enqueueScrape("game-1");
    enqueueScrape("game-2");
    await flushPromises();

    expect(mockTypedApi.POST).toHaveBeenCalledTimes(2);
    expect(mockTypedApi.POST).toHaveBeenLastCalledWith(
      "/api/games/{id}/scrape-if-needed",
      { params: { path: { id: "game-2" } } },
    );
  });

  it("does not re-enqueue after reset and re-add", async () => {
    mockTypedApi.POST.mockReturnValue(ok(undefined));

    enqueueScrape("game-1");
    await flushPromises();
    expect(mockTypedApi.POST).toHaveBeenCalledTimes(1);

    // Same ID should be deduped even after processing
    enqueueScrape("game-1");
    await flushPromises();
    expect(mockTypedApi.POST).toHaveBeenCalledTimes(1);
  });
});
