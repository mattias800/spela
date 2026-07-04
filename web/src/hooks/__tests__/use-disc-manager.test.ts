import { renderHook, waitFor, act } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useDiscManager } from "../use-disc-manager";
import type { EmulatorStatus } from "../use-emulator-iframe";
import type { Game, GameDisc } from "@/types/api";

vi.mock("@/lib/api-client", () => ({
  api: {
    getAccessToken: vi.fn(() => "test-token"),
  },
}));

function disc(discNumber: number, fileName: string): GameDisc {
  return {
    discNumber,
    fileName,
    fileSize: 1024,
  };
}

function createGame(overrides: Partial<Game> = {}): Game {
  const discs = overrides.discs ?? [
    disc(1, "Disc 1.chd"),
    disc(2, "Disc 2.chd"),
  ];

  return {
    achievementsWarning: "",
    ageRatings: [],
    averageRating: 0,
    biosStatus: "",
    consoleId: "psx",
    consoleName: "PlayStation",
    consoleSaveStatePolicy: "",
    coreOverride: "",
    coverAspectRatio: 0,
    coverUrl: "",
    createdAt: "2026-01-01T00:00:00Z",
    description: "",
    developer: "",
    discCount: discs.length,
    discs,
    fileName: "",
    fileSize: 0,
    gameModes: "",
    genre: "",
    groupKey: "",
    heroUrl: "",
    id: "game-1",
    igdbCriticsRating: 0,
    igdbUserRating: 0,
    igdbUserRatingCount: 0,
    isFavorite: false,
    isInPlayLater: false,
    isPreRelease: false,
    languageSupports: [],
    lastPlayedAt: null,
    logoUrl: "",
    partyInfo: "",
    platforms: [
      {
        gameId: "game-1",
        consoleId: "psx",
        consoleName: "PlayStation",
        isPreferred: true,
      },
    ],
    playable: true,
    players: 1,
    publisher: "",
    ratingCount: 0,
    region: "",
    releaseDate: "",
    releaseDates: [],
    revision: "",
    romHacks: [],
    scrapeAttempts: 0,
    scraperId: "",
    screenshotUrls: [],
    storyline: "",
    tags: "",
    timeToBeatCompletely: 0,
    timeToBeatHastily: 0,
    timeToBeatNormally: 0,
    title: "Multi Disc Game",
    totalPlayTime: 0,
    totalRating: 0,
    totalRatingCount: 0,
    updatedAt: "2026-01-01T00:00:00Z",
    userRating: null,
    variantCount: 0,
    variants: [],
    verificationStatus: "",
    verificationTag: "",
    videos: [],
    ...overrides,
  };
}

function renderUseDiscManager({
  game,
  emulatorStatus = "ready",
}: {
  game: Game | undefined;
  emulatorStatus?: EmulatorStatus;
}) {
  return renderHook(
    ({ game, emulatorStatus }) =>
      useDiscManager({
        game,
        emulatorStatus,
      }),
    {
      initialProps: {
        game,
        emulatorStatus,
      },
    },
  );
}

function createDownloadResponse() {
  return new Response(new Uint8Array([1, 2, 3]).buffer, {
    status: 200,
  });
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn(() => Promise.resolve(createDownloadResponse()));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("useDiscManager", () => {
  it("initializes disc states and refreshes them when the game discs change", async () => {
    const initialGame = createGame();
    const { result, rerender } = renderUseDiscManager({ game: initialGame });

    await waitFor(() => expect(result.current.discStates).toHaveLength(2));
    expect(result.current.discStates.map((state) => state.fileName)).toEqual([
      "Disc 1.chd",
      "Disc 2.chd",
    ]);

    const updatedGame = createGame({
      discs: [
        disc(1, "Disc 1.chd"),
        disc(2, "Bonus Disc.chd"),
        disc(3, "Disc 3.chd"),
      ],
    });

    rerender({ game: updatedGame, emulatorStatus: "ready" });

    await waitFor(() => expect(result.current.discStates).toHaveLength(3));
    expect(result.current.discStates.map((state) => state.fileName)).toEqual([
      "Disc 1.chd",
      "Bonus Disc.chd",
      "Disc 3.chd",
    ]);
  });

  it("does not reset ready discs when the same discs arrive in a new array", async () => {
    const game = createGame();
    const { result, rerender } = renderUseDiscManager({
      game,
      emulatorStatus: "playing",
    });

    await waitFor(() => expect(result.current.allDiscsReady).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(2);

    const sameGameWithNewDiscArray = createGame({
      discs: game.discs.map((gameDisc) => ({ ...gameDisc })),
    });

    rerender({
      game: sameGameWithNewDiscArray,
      emulatorStatus: "playing",
    });

    await waitFor(() => expect(result.current.allDiscsReady).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(result.current.discStates.every((state) => state.data)).toBe(true);
  });

  it("restarts downloads when the disc list changes while playing", async () => {
    const game = createGame();
    const { result, rerender } = renderUseDiscManager({
      game,
      emulatorStatus: "playing",
    });

    await waitFor(() => expect(result.current.allDiscsReady).toBe(true));
    expect(fetchMock).toHaveBeenCalledTimes(2);

    rerender({
      game: createGame({
        discs: [disc(1, "Disc 1.chd"), disc(2, "Bonus Disc.chd")],
      }),
      emulatorStatus: "playing",
    });

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(3));
    await waitFor(() => expect(result.current.allDiscsReady).toBe(true));
    expect(result.current.discStates.map((state) => state.fileName)).toEqual([
      "Disc 1.chd",
      "Bonus Disc.chd",
    ]);
  });

  it("retries a disc download with the latest game id", async () => {
    const initialGame = createGame({ id: "game-1" });
    const updatedGame = createGame({ id: "game-2" });
    const { result, rerender } = renderUseDiscManager({ game: initialGame });

    await waitFor(() => expect(result.current.discStates).toHaveLength(2));

    rerender({ game: updatedGame, emulatorStatus: "ready" });

    act(() => {
      result.current.retryDisc(1);
    });

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        "/api/games/game-2/discs/1/download?format=zip&token=test-token",
        { signal: undefined },
      ),
    );
  });
});
