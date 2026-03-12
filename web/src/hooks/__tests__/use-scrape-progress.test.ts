import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { useScrapeProgress } from "../use-scrape-progress";

// Capture WebSocket event handlers registered by the hook
const wsHandlers: Record<string, (payload: unknown) => void> = {};

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(
    (type: string, callback: (payload: unknown) => void) => {
      wsHandlers[type] = callback;
    },
  ),
}));

vi.mock("@/hooks/use-admin", () => ({
  useScrapeStatus: vi.fn(),
}));

import { useScrapeStatus } from "@/hooks/use-admin";

const mockUseScrapeStatus = useScrapeStatus as ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.clearAllMocks();
  for (const key of Object.keys(wsHandlers)) {
    delete wsHandlers[key];
  }
  mockUseScrapeStatus.mockReturnValue({ data: undefined });
});

describe("useScrapeProgress", () => {
  it("starts with idle phase", () => {
    const { result } = renderHook(() => useScrapeProgress());

    expect(result.current.phase).toBe("idle");
    expect(result.current.current).toBe(0);
    expect(result.current.total).toBe(0);
    expect(result.current.gameName).toBe("");
    expect(result.current.successes).toBe(0);
    expect(result.current.failures).toBe(0);
    expect(result.current.verified).toBe(0);
    expect(result.current.error).toBeNull();
  });

  it("transitions to active on scrape_started event", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_started"]({});
    });

    expect(result.current.phase).toBe("active");
    expect(result.current.current).toBe(0);
    expect(result.current.total).toBe(0);
    expect(result.current.gameName).toBe("");
    expect(result.current.successes).toBe(0);
    expect(result.current.failures).toBe(0);
    expect(result.current.verified).toBe(0);
    expect(result.current.error).toBeNull();
  });

  it("updates progress on scrape_progress event", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 3,
        total: 10,
        gameName: "Super Mario Bros.",
        successes: 2,
        failures: 1,
        verified: 1,
      });
    });

    expect(result.current.phase).toBe("active");
    expect(result.current.current).toBe(3);
    expect(result.current.total).toBe(10);
    expect(result.current.gameName).toBe("Super Mario Bros.");
    expect(result.current.successes).toBe(2);
    expect(result.current.failures).toBe(1);
    expect(result.current.verified).toBe(1);
  });

  it("transitions to complete on scrape_complete event", () => {
    const { result } = renderHook(() => useScrapeProgress());

    // First set active state
    act(() => {
      wsHandlers["scrape_progress"]({
        current: 5,
        total: 10,
        gameName: "Zelda",
        successes: 4,
        failures: 1,
      });
    });

    act(() => {
      wsHandlers["scrape_complete"]({ scraped: 10, total: 10 });
    });

    expect(result.current.phase).toBe("complete");
    expect(result.current.successes).toBe(10);
    expect(result.current.total).toBe(10);
  });

  it("handles scrape_complete with 0 scraped and 0 total", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_started"]({});
    });

    act(() => {
      wsHandlers["scrape_complete"]({ scraped: 0, total: 0 });
    });

    expect(result.current.phase).toBe("complete");
    expect(result.current.successes).toBe(0);
    expect(result.current.total).toBe(0);
  });

  it("transitions to error on scrape_error event", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_error"]({ error: "IGDB rate limit exceeded" });
    });

    expect(result.current.phase).toBe("error");
    expect(result.current.error).toBe("IGDB rate limit exceeded");
  });

  it("resets to idle on dismiss()", () => {
    const { result } = renderHook(() => useScrapeProgress());

    // First transition to complete
    act(() => {
      wsHandlers["scrape_complete"]({ scraped: 5, total: 5 });
    });

    expect(result.current.phase).toBe("complete");

    act(() => {
      result.current.dismiss();
    });

    expect(result.current.phase).toBe("idle");
    expect(result.current.current).toBe(0);
    expect(result.current.total).toBe(0);
    expect(result.current.gameName).toBe("");
    expect(result.current.successes).toBe(0);
    expect(result.current.failures).toBe(0);
    expect(result.current.verified).toBe(0);
    expect(result.current.error).toBeNull();
  });

  it("dismiss() resets from error state", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_error"]({ error: "Something went wrong" });
    });

    expect(result.current.phase).toBe("error");

    act(() => {
      result.current.dismiss();
    });

    expect(result.current.phase).toBe("idle");
    expect(result.current.error).toBeNull();
  });

  it("scrape_started clears previous error state", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_error"]({ error: "Previous error" });
    });

    expect(result.current.phase).toBe("error");

    act(() => {
      wsHandlers["scrape_started"]({});
    });

    expect(result.current.phase).toBe("active");
    expect(result.current.error).toBeNull();
  });

  it("initializes from active scrape status on mount", () => {
    mockUseScrapeStatus.mockReturnValue({
      data: {
        active: true,
        current: 7,
        total: 20,
        gameName: "Mega Man",
        successes: 5,
        failures: 2,
        verified: 3,
      },
    });

    const { result } = renderHook(() => useScrapeProgress());

    expect(result.current.phase).toBe("active");
    expect(result.current.current).toBe(7);
    expect(result.current.total).toBe(20);
    expect(result.current.gameName).toBe("Mega Man");
    expect(result.current.successes).toBe(5);
    expect(result.current.failures).toBe(2);
    expect(result.current.verified).toBe(3);
  });

  it("does not reinitialize from status after already initialized", () => {
    mockUseScrapeStatus.mockReturnValue({
      data: {
        active: true,
        current: 7,
        total: 20,
        gameName: "Mega Man",
        successes: 5,
        failures: 2,
        verified: 3,
      },
    });

    const { result, rerender } = renderHook(() => useScrapeProgress());

    expect(result.current.phase).toBe("active");
    expect(result.current.current).toBe(7);

    // Progress via WebSocket
    act(() => {
      wsHandlers["scrape_progress"]({
        current: 15,
        total: 20,
        gameName: "Contra",
        successes: 13,
        failures: 2,
      });
    });

    expect(result.current.current).toBe(15);

    // Re-render should not reset to initial status values
    rerender();

    expect(result.current.current).toBe(15);
    expect(result.current.gameName).toBe("Contra");
  });

  it("stays idle when initial status is not active", () => {
    mockUseScrapeStatus.mockReturnValue({
      data: { active: false },
    });

    const { result } = renderHook(() => useScrapeProgress());

    expect(result.current.phase).toBe("idle");
  });

  it("handles multiple progress updates correctly", () => {
    const { result } = renderHook(() => useScrapeProgress());

    act(() => {
      wsHandlers["scrape_started"]({});
    });

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 1,
        total: 5,
        gameName: "Game 1",
        successes: 1,
        failures: 0,
      });
    });

    expect(result.current.current).toBe(1);
    expect(result.current.gameName).toBe("Game 1");

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 2,
        total: 5,
        gameName: "Game 2",
        successes: 1,
        failures: 1,
      });
    });

    expect(result.current.current).toBe(2);
    expect(result.current.gameName).toBe("Game 2");
    expect(result.current.failures).toBe(1);

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 3,
        total: 5,
        gameName: "Game 3",
        successes: 2,
        failures: 1,
      });
    });

    expect(result.current.current).toBe(3);
    expect(result.current.gameName).toBe("Game 3");
    expect(result.current.successes).toBe(2);
  });

  it("registers handlers for all four WebSocket event types", () => {
    renderHook(() => useScrapeProgress());

    expect(wsHandlers["scrape_started"]).toBeDefined();
    expect(wsHandlers["scrape_progress"]).toBeDefined();
    expect(wsHandlers["scrape_complete"]).toBeDefined();
    expect(wsHandlers["scrape_error"]).toBeDefined();
  });
});
