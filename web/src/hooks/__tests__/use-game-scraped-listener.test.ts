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

const makeGame = (overrides = {}) => ({
  id: "42",
  title: "Test Game",
  consoleId: "nes",
  consoleName: "NES",
  coverUrl: "",
  description: "",
  developer: "",
  publisher: "",
  releaseDate: "",
  genre: "",
  players: 1,
  rating: 0,
  scraperId: "",
  scrapeAttempts: 0,
  screenshotUrls: [],
  isFavorite: false,
  isInPlayLater: false,
  playable: true,
  ...overrides,
});

describe("useGameScrapedListener", () => {
  it("does not clobber sub-queries like cheats when game is scraped", () => {
    // Pre-populate the cheats query for game 42
    const cheats = [
      { id: 1, index: 0, description: "Infinite lives", code: "AAEAULPA" },
    ];
    queryClient.setQueryData(["game", "42", "cheats"], cheats);

    // Pre-populate the game query
    const game = makeGame({ scrapeAttempts: 0 });
    queryClient.setQueryData(["game", "42"], game);

    renderHook(() => useGameScrapedListener(), { wrapper });

    // Simulate the server broadcasting game_scraped after scrape completes
    const scraped = makeGame({
      scrapeAttempts: 1,
      coverUrl: "https://example.com/cover.jpg",
      description: "A great game",
    });

    act(() => {
      wsHandlers["game_scraped"](scraped);
    });

    // Cheats query must still be an array — this was the original bug
    const updatedCheats = queryClient.getQueryData(["game", "42", "cheats"]);
    expect(Array.isArray(updatedCheats)).toBe(true);
    expect(updatedCheats).toEqual(cheats);
  });

  it("invalidates game query on scrape_progress during batch scrape", () => {
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries");

    // Pre-populate the game query
    queryClient.setQueryData(["game", "42"], makeGame());

    renderHook(() => useGameScrapedListener(), { wrapper });

    // Simulate batch scrape progress event
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
