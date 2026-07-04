// Shared test fixtures for response-shaped types. Each factory returns a
// fully-populated object (every required field) and accepts a Partial<T>
// override so tests only spell out the fields they care about.
//
// Keep these in sync with the generated schemas — when openapi:gen adds a
// new required field, extend the default here instead of touching every
// test file.

import type { Game, Console, CompanyInfo } from "@/types/api";

export function makeGame(overrides: Partial<Game> = {}): Game {
  const id = overrides.id ?? "game-1";
  const consoleId = overrides.consoleId ?? "nes";
  const consoleName = overrides.consoleName ?? "NES";

  const game: Game = {
    id,
    title: "Test Game",
    consoleId,
    consoleName,
    consoleSaveStatePolicy: "small",
    fileName: "test.nes",
    fileSize: 1024,
    discCount: 1,
    discs: [],
    coverUrl: "",
    screenshotUrls: [],
    heroUrl: "",
    logoUrl: "",
    description: "",
    developer: "",
    publisher: "",
    releaseDate: "",
    genre: "",
    gameModes: "",
    storyline: "",
    totalRating: 0,
    totalRatingCount: 0,
    igdbUserRating: 0,
    igdbUserRatingCount: 0,
    timeToBeatHastily: 0,
    timeToBeatNormally: 0,
    timeToBeatCompletely: 0,
    releaseDates: [],
    videos: [],
    languageSupports: [],
    ageRatings: [],
    players: 1,
    igdbCriticsRating: 0,
    coreOverride: "",
    scraperId: "",
    scrapeAttempts: 0,
    achievementsWarning: "",
    verificationStatus: "",
    verificationTag: "",
    region: "",
    revision: "",
    tags: "",
    partyInfo: "",
    isPreRelease: false,
    variantCount: 0,
    groupKey: "",
    variants: [],
    parentGame: { id: "", title: "", coverUrl: "" },
    romHacks: [],
    platforms: overrides.platforms ?? [
      {
        gameId: id,
        consoleId,
        consoleName,
        isPreferred: true,
      },
    ],
    coverAspectRatio: 0.75,
    playable: true,
    biosStatus: "not_required",
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    userRating: 0,
    lastPlayedAt: null,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
  };

  return {
    ...game,
    ...overrides,
    id,
    consoleId,
    consoleName,
    platforms: overrides.platforms ?? game.platforms,
  };
}

export function makeCompanyInfo(overrides: Partial<CompanyInfo> = {}): CompanyInfo {
  return {
    logoUrl: "",
    description: "",
    foundedYear: 0,
    country: "",
    websiteUrl: "",
    wikipediaUrl: "",
    ...overrides,
  };
}

export function makeConsole(overrides: Partial<Console> = {}): Console {
  return {
    id: "nes",
    name: "NES",
    abbreviation: "NES",
    extensions: [],
    defaultCore: "",
    emulatorJsCore: "",
    coverAspectRatio: 0.75,
    logoAspectRatio: null,
    colorTheme: "",
    generation: 3,
    iconUrl: "",
    logoUrl: "",
    logoPngUrl: "",
    photoUrl: null,
    tag: null,
    gameCount: 0,
    saveStateSupport: true,
    saveStatePolicy: "small",
    browserPlayable: false,
    playable: true,
    code: "nes",
    maker: { code: "", name: "" },
    mediaType: { code: "", name: "", category: { code: "", name: "" } },
    releaseYear: null,
    unitsSold: null,
    summary: null,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}
