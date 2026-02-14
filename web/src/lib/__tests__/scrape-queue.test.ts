import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { enqueueScrape, _resetScrapeQueue } from "../scrape-queue";

vi.mock("@/lib/api-client", () => ({
  api: {
    post: vi.fn(),
  },
}));

import { api } from "@/lib/api-client";

const mockApi = api as unknown as {
  post: ReturnType<typeof vi.fn>;
};

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
  it("calls api.post for an enqueued game", async () => {
    mockApi.post.mockResolvedValue(undefined);

    enqueueScrape("game-1");
    await flushPromises();

    expect(mockApi.post).toHaveBeenCalledWith("/games/game-1/scrape-if-needed");
    expect(mockApi.post).toHaveBeenCalledTimes(1);
  });

  it("deduplicates the same game id", async () => {
    mockApi.post.mockResolvedValue(undefined);

    enqueueScrape("game-1");
    enqueueScrape("game-1");
    enqueueScrape("game-1");
    await flushPromises();

    expect(mockApi.post).toHaveBeenCalledTimes(1);
  });

  it("processes multiple games sequentially", async () => {
    const callOrder: string[] = [];
    mockApi.post.mockImplementation((url: string) => {
      callOrder.push(url);
      return Promise.resolve(undefined);
    });

    enqueueScrape("game-1");
    enqueueScrape("game-2");
    enqueueScrape("game-3");
    await flushPromises();

    expect(callOrder).toEqual([
      "/games/game-1/scrape-if-needed",
      "/games/game-2/scrape-if-needed",
      "/games/game-3/scrape-if-needed",
    ]);
  });

  it("silently handles API errors", async () => {
    mockApi.post
      .mockRejectedValueOnce(new Error("Network error"))
      .mockResolvedValueOnce(undefined);

    enqueueScrape("game-1");
    enqueueScrape("game-2");
    await flushPromises();

    expect(mockApi.post).toHaveBeenCalledTimes(2);
    expect(mockApi.post).toHaveBeenCalledWith("/games/game-2/scrape-if-needed");
  });

  it("does not re-enqueue after reset and re-add", async () => {
    mockApi.post.mockResolvedValue(undefined);

    enqueueScrape("game-1");
    await flushPromises();
    expect(mockApi.post).toHaveBeenCalledTimes(1);

    // Same ID should be deduped even after processing
    enqueueScrape("game-1");
    await flushPromises();
    expect(mockApi.post).toHaveBeenCalledTimes(1);
  });
});
