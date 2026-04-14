import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useGameScrapeStatus } from "../use-game-scrape-status";

const mockCallbacks = new Map<string, (payload: unknown) => void>();
vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: (type: string, callback: (payload: unknown) => void) => {
    mockCallbacks.set(type, callback);
  },
}));

describe("useGameScrapeStatus", () => {
  beforeEach(() => {
    mockCallbacks.clear();
  });

  it("returns isScraping false by default", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    expect(result.current.isScraping).toBe(false);
  });

  it("returns isScraping true when scraping event received for matching game", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 42, status: "scraping" }));

    expect(result.current.isScraping).toBe(true);
  });

  it("returns isScraping false when idle event received", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 42, status: "scraping" }));
    expect(result.current.isScraping).toBe(true);

    act(() => callback({ gameId: 42, status: "idle" }));
    expect(result.current.isScraping).toBe(false);
  });

  it("ignores events for other game IDs", () => {
    const { result } = renderHook(() => useGameScrapeStatus("42"));
    const callback = mockCallbacks.get("game_scrape_status")!;

    act(() => callback({ gameId: 99, status: "scraping" }));

    expect(result.current.isScraping).toBe(false);
  });
});
