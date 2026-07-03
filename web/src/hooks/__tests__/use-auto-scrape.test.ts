import { renderHook } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useAutoScrape } from "../use-auto-scrape";
import type { } from "@/types/api";

vi.mock("@/hooks/use-in-view", () => ({
  useInView: vi.fn(),
}));

vi.mock("@/lib/scrape-queue", () => ({
  enqueueScrape: vi.fn(),
}));

import { useInView } from "@/hooks/use-in-view";
import { enqueueScrape } from "@/lib/scrape-queue";
import { makeGame } from "@/test-utils/fixtures";

const mockUseInView = useInView as ReturnType<typeof vi.fn>;
const mockEnqueueScrape = enqueueScrape as ReturnType<typeof vi.fn>;


beforeEach(() => {
  vi.clearAllMocks();
  mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: false });
});

describe("useAutoScrape", () => {
  it("enqueues scrape when game needs scraping and is in view", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: true });

    renderHook(() => useAutoScrape(makeGame()));

    expect(mockEnqueueScrape).toHaveBeenCalledWith("game-1");
  });

  it("does not scrape when game has coverUrl", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: true });

    renderHook(() =>
      useAutoScrape(makeGame({ coverUrl: "https://example.com/cover.jpg" })),
    );

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("does not scrape when scrapeAttempts > 0", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: true });

    renderHook(() => useAutoScrape(makeGame({ scrapeAttempts: 1 })));

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("does not scrape when not in view", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: false });

    renderHook(() => useAutoScrape(makeGame()));

    expect(mockEnqueueScrape).not.toHaveBeenCalled();
  });

  it("returns a ref for attaching to the element", () => {
    const mockRef = vi.fn();
    mockUseInView.mockReturnValue({ observe: mockRef, isInView: false });

    const { result } = renderHook(() => useAutoScrape(makeGame()));

    expect(result.current.ref).toBe(mockRef);
  });

  it("returns isScraping=true when needs scrape and in view", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: true });

    const { result } = renderHook(() => useAutoScrape(makeGame()));

    expect(result.current.isScraping).toBe(true);
  });

  it("returns isScraping=false when game has cover", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: true });

    const { result } = renderHook(() =>
      useAutoScrape(makeGame({ coverUrl: "https://example.com/cover.jpg" })),
    );

    expect(result.current.isScraping).toBe(false);
  });

  it("returns isScraping=false when not in view", () => {
    mockUseInView.mockReturnValue({ observe: vi.fn(), isInView: false });

    const { result } = renderHook(() => useAutoScrape(makeGame()));

    expect(result.current.isScraping).toBe(false);
  });
});
