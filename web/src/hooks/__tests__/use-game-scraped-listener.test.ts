import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { useGameScrapedListener } from "../use-game-scraped-listener";

// Capture the event handlers
const wsHandlers: Record<string, (payload: unknown) => void> = {};

vi.mock("@/hooks/use-websocket", () => ({
  useWebSocketEvent: vi.fn(
    (type: string, callback: (payload: unknown) => void) => {
      wsHandlers[type] = callback;
    },
  ),
}));

let queryClient: QueryClient;

function wrapper({ children }: { children: React.ReactNode }) {
  return createElement(
    QueryClientProvider,
    { client: queryClient },
    children,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  for (const key of Object.keys(wsHandlers)) {
    delete wsHandlers[key];
  }
  queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
});

describe("useGameScrapedListener", () => {
  it("invalidates game and list queries on game_scrape_status idle", () => {
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGameScrapedListener(), { wrapper });

    act(() => {
      wsHandlers["game_scrape_status"]({ gameId: 42, status: "idle" });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["game", "42"],
      exact: true,
    });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["games"] });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["consoles"] });
  });

  it("does not invalidate on game_scrape_status scraping", () => {
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGameScrapedListener(), { wrapper });

    act(() => {
      wsHandlers["game_scrape_status"]({ gameId: 42, status: "scraping" });
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
  });

  it("invalidates game query on scrape_progress during batch scrape", () => {
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGameScrapedListener(), { wrapper });

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 5,
        total: 100,
        gameId: 42,
        gameName: "Test Game",
        consoleName: "NES",
        consoleAbbr: "nes",
        successes: 4,
        failures: 0,
        verified: 0,
      });
    });

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ["game", "42"],
      exact: true,
    });
  });

  it("ignores scrape_progress without gameId", () => {
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    renderHook(() => useGameScrapedListener(), { wrapper });

    act(() => {
      wsHandlers["scrape_progress"]({
        current: 0,
        total: 100,
      });
    });

    expect(invalidateSpy).not.toHaveBeenCalled();
  });
});
