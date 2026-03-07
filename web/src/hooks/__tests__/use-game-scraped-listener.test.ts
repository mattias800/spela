import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createElement } from "react";
import { useGameScrapedListener } from "../use-game-scraped-listener";

// Capture the game_scraped handler
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

    // Game query should be updated with scraped data
    const updatedGame = queryClient.getQueryData(["game", "42"]);
    expect(updatedGame).toMatchObject({
      coverUrl: "https://example.com/cover.jpg",
      description: "A great game",
      scrapeAttempts: 1,
    });

    // Cheats query must still be an array — this was the bug
    const updatedCheats = queryClient.getQueryData(["game", "42", "cheats"]);
    expect(Array.isArray(updatedCheats)).toBe(true);
    expect(updatedCheats).toEqual(cheats);
  });
});
