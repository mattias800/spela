import { describe, it, expect } from "vitest";
import { NETPLAY_SUPPORTED_CONSOLES } from "../netplay-consoles";
import type { Game } from "@/types/api";

function makeGame(overrides: Partial<Game> & { consoleName: string }): Game {
  const { consoleName, ...rest } = overrides;
  return {
    id: "test-id",
    title: "Test Game",
    consoleId: "test",
    consoleName,
    fileName: "test.rom",
    fileSize: 1024,
    discCount: 0,
    screenshotUrls: [],
    scrapeAttempts: 0,
    coverAspectRatio: 1,
    playable: true,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2024-01-01T00:00:00Z",
    updatedAt: "2024-01-01T00:00:00Z",
    coverUrl: "",
    description: "",
    developer: "",
    genre: "",
    igdbCriticsRating: 0,
    isPreRelease: false,
    lastPlayedAt: null,
    players: 0,
    publisher: "",
    releaseDate: "",
    ...rest,
  };
}

function filterNetplayGames(games: Game[]): Game[] {
  return games.filter((g) => NETPLAY_SUPPORTED_CONSOLES.includes(g.consoleName));
}

describe("NETPLAY_SUPPORTED_CONSOLES", () => {
  it("includes all expected console names matching database values", () => {
    expect(NETPLAY_SUPPORTED_CONSOLES).toEqual([
      "Nintendo Entertainment System",
      "Super Nintendo",
      "Game Boy",
      "Game Boy Color",
      "Game Boy Advance",
      "Sega Genesis",
    ]);
  });

  it("does not include abbreviations or short names", () => {
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("NES");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("SNES");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("Genesis");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("Mega Drive");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("GBA");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("GB");
    expect(NETPLAY_SUPPORTED_CONSOLES).not.toContain("GBC");
  });
});

describe("filterNetplayGames", () => {
  it("keeps games on supported consoles", () => {
    const games = [
      makeGame({ id: "1", title: "Zelda", consoleName: "Nintendo Entertainment System" }),
      makeGame({ id: "2", title: "Mario World", consoleName: "Super Nintendo" }),
      makeGame({ id: "3", title: "Pokemon Red", consoleName: "Game Boy" }),
      makeGame({ id: "4", title: "Pokemon Crystal", consoleName: "Game Boy Color" }),
      makeGame({ id: "5", title: "Metroid Fusion", consoleName: "Game Boy Advance" }),
      makeGame({ id: "6", title: "Sonic", consoleName: "Sega Genesis" }),
    ];

    const result = filterNetplayGames(games);
    expect(result).toHaveLength(6);
    expect(result.map((g) => g.id)).toEqual(["1", "2", "3", "4", "5", "6"]);
  });

  it("filters out games on unsupported consoles", () => {
    const games = [
      makeGame({ id: "1", title: "Crash", consoleName: "PlayStation" }),
      makeGame({ id: "2", title: "Mario 64", consoleName: "Nintendo 64" }),
      makeGame({ id: "3", title: "Pokemon DS", consoleName: "Nintendo DS" }),
    ];

    const result = filterNetplayGames(games);
    expect(result).toHaveLength(0);
  });

  it("filters a mixed list correctly", () => {
    const games = [
      makeGame({ id: "1", title: "Mario World", consoleName: "Super Nintendo" }),
      makeGame({ id: "2", title: "Crash", consoleName: "PlayStation" }),
      makeGame({ id: "3", title: "Sonic", consoleName: "Sega Genesis" }),
      makeGame({ id: "4", title: "Mario 64", consoleName: "Nintendo 64" }),
    ];

    const result = filterNetplayGames(games);
    expect(result).toHaveLength(2);
    expect(result.map((g) => g.title)).toEqual(["Mario World", "Sonic"]);
  });

  it("returns empty array for empty input", () => {
    expect(filterNetplayGames([])).toEqual([]);
  });
});
