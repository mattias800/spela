import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAutoScrape } from "../use-auto-scrape";
import type { Game } from "@/types/api";

vi.mock("@/hooks/use-in-view", () => ({
  useInView: vi.fn(),
}));

vi.mock("@/lib/scrape-queue", () => ({
  enqueueScrape: vi.fn(),
}));

import { useInView } from "@/hooks/use-in-view";
import { enqueueScrape } from "@/lib/scrape-queue";

const mockUseInView = useInView as ReturnType<typeof vi.fn>;
const mockEnqueueScrape = enqueueScrape as ReturnType<typeof vi.fn>;

function makeGame(overrides: Partial<Game> = {}): Game {
  return {
    id: "game-1",
    title: "Test Game",
    consoleId: "c1",
    consoleName: "NES",
    fileName: "test.nes",
    fileSize: 1024,
    screenshotUrls: [],
    scrapeAttempts: 0,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUseInView.mockReturnValue({ ref: vi.fn(), isInView: false });
});

describe("useAutoScrape", () => {
  it("enqueues scrape when game needs scraping and is in view", () => {
    mockUseInView.mockReturnValue({ ref: vi.fn(), isInView: true });

    renderHook(() => useAutoScrape(makeGame()));

    expect(mockEnqueueScrape).toHaveBeenCalledWith("game-1");
  });

  it("does not scrape when game has coverUrl", () => {
    mockUseInView.mockReturnValue({ ref: vi.fn(), isInView: true });

    renderHook(() =>
      useAutoScrape(makeGame({ coverUrl: "https://example.com/cover.jpg" })),
    );

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("does not scrape when scrapeAttempts > 0", () => {
    mockUseInView.mockReturnValue({ ref: vi.fn(), isInView: true });

    renderHook(() => useAutoScrape(makeGame({ scrapeAttempts: 1 })));

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("does not scrape when not in view", () => {
    mockUseInView.mockReturnValue({ ref: vi.fn(), isInView: false });

    renderHook(() => useAutoScrape(makeGame()));

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("returns a ref for attaching to the element", () => {
    const mockRef = vi.fn();
    mockUseInView.mockReturnValue({ ref: mockRef, isInView: false });

    const { result } = renderHook(() => useAutoScrape(makeGame()));

    expect(result.current.ref).toBe(mockRef);
  });
});
