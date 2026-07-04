import { describe, expect, it } from "vitest";
import {
  compactPlatformLabel,
  getGamePlatformTargets,
} from "@/lib/game-platforms";
import { makeGame } from "@/test-utils/fixtures";

describe("game-platforms", () => {
  it("falls back to the current game when no platform list is provided", () => {
    const game = makeGame({ platforms: [] });

    expect(getGamePlatformTargets(game)).toEqual([
      {
        gameId: "game-1",
        consoleId: "nes",
        consoleName: "NES",
        isPreferred: true,
        isCurrent: true,
      },
    ]);
  });

  it("falls back to the current game as preferred and dedupes targets", () => {
    const game = makeGame({
      id: "game-nes",
      platforms: [
        {
          gameId: "game-nes",
          consoleId: "nes",
          consoleName: "Nintendo Entertainment System",
          isPreferred: false,
        },
        {
          gameId: "game-snes",
          consoleId: "snes",
          consoleName: "Super Nintendo",
          isPreferred: false,
        },
        {
          gameId: "game-snes",
          consoleId: "snes",
          consoleName: "Super Nintendo",
          isPreferred: false,
        },
      ],
    });

    const targets = getGamePlatformTargets(game);

    expect(targets.map((target) => target.gameId)).toEqual([
      "game-nes",
      "game-snes",
    ]);
    expect(targets[0].isPreferred).toBe(true);
    expect(targets[0].isCurrent).toBe(true);
    expect(targets[1].isPreferred).toBe(false);
    expect(targets[1].isCurrent).toBe(false);
  });

  it("includes the current game when the backend target list omits it", () => {
    const game = makeGame({
      id: "game-nes",
      consoleId: "nes",
      consoleName: "NES",
      platforms: [
        {
          gameId: "game-snes",
          consoleId: "snes",
          consoleName: "Super Nintendo",
          isPreferred: true,
        },
      ],
    });

    const targets = getGamePlatformTargets(game);

    expect(targets.map((target) => target.gameId)).toEqual([
      "game-nes",
      "game-snes",
    ]);
    expect(targets[0]).toMatchObject({
      gameId: "game-nes",
      consoleName: "NES",
      isPreferred: false,
      isCurrent: true,
    });
    expect(targets[1].isPreferred).toBe(true);
    expect(targets[1].isCurrent).toBe(false);
  });

  it("uses compact console ids for long labels", () => {
    expect(
      compactPlatformLabel({
        gameId: "game-nes",
        consoleId: "nes",
        consoleName: "Nintendo Entertainment System",
        isPreferred: true,
      }),
    ).toBe("NES");
  });
});
