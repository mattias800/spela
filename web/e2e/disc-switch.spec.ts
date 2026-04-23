import { test, expect } from "./fixtures";
import type { Page } from "@playwright/test";
import { createZipStore } from "../src/lib/zip-utils";

/**
 * Build a tiny STORE-mode zip with a single file, using the same
 * builder the app uses when it repackages the multi-disc bundle.
 * Guarantees `extractZipStore` in production code round-trips
 * cleanly — we're not shipping a subtly wrong zip header.
 */
function buildSingleFileZip(fileName: string, payload: string): Buffer {
  const files = new Map<string, Uint8Array>([
    [fileName, new TextEncoder().encode(payload)],
  ]);
  const ab = createZipStore(files);
  return Buffer.from(ab);
}

/**
 * E2E test for the multi-disc switch flow on the play page.
 *
 * Uses two layers of test fixtures so it can run without any real
 * multi-disc ROMs in `testdata/roms`:
 *
 *   1. **API mocking** (`page.route`) — synthesises a 2-disc PSX
 *      game on the wire so `useGame`, `useConsoles`, etc. see what
 *      they need.
 *   2. **Mock emulator iframe** — `?mockEmulator=true` swaps in
 *      `/emulator-mock.html`, a deterministic stub that records
 *      every `init`/`request-save-state` it gets and replies
 *      synchronously with a known save-state payload.
 *
 * The combination lets us exercise the full
 *   click switch-disc → pause → request save → save arrives →
 *   iframe reloads → init re-runs with combined bundle + targetDisc
 * sequence without ROMs, BIOS, or EmulatorJS in the loop.
 *
 * The expected init sequence:
 *
 *   inits[0]  the initial boot — disc 1 download URL, no targetDisc,
 *             no saveStateData
 *   inits[1]  the post-switch boot — romData (combined bundle),
 *             targetDisc = 1 (0-indexed for disc 2),
 *             saveStateData = the mock state from the previous request
 */

const GAME_ID = "999001";
const SESSION_ID = "777001";
const CONSOLE_ID = "psx";

function multiDiscGame(): Record<string, unknown> {
  return {
    achievementsWarning: "",
    ageRatings: [],
    averageRating: 0,
    biosStatus: "ready",
    consoleId: CONSOLE_ID,
    consoleName: "PlayStation",
    coreOverride: "",
    coverAspectRatio: 1.5,
    coverUrl: "",
    createdAt: new Date().toISOString(),
    description: "",
    developer: "Spela QA",
    discCount: 2,
    discs: [
      {
        id: "d1",
        gameId: GAME_ID,
        discNumber: 1,
        fileName: "Mock Game (Disc 1).bin",
        fileSize: 256,
      },
      {
        id: "d2",
        gameId: GAME_ID,
        discNumber: 2,
        fileName: "Mock Game (Disc 2).bin",
        fileSize: 256,
      },
    ],
    fileName: "Mock Game.m3u",
    fileSize: 64,
    gameModes: "",
    genre: "",
    groupKey: "",
    heroUrl: "",
    id: GAME_ID,
    igdbCriticsRating: 0,
    igdbUserRating: 0,
    igdbUserRatingCount: 0,
    isFavorite: false,
    isInPlayLater: false,
    isPreRelease: false,
    languageSupports: [],
    lastPlayedAt: null,
    logoUrl: "",
    parentGame: null,
    partyInfo: "",
    playable: true,
    players: 1,
    publisher: "",
    ratingCount: 0,
    region: "USA",
    releaseDate: "",
    releaseDates: [],
    revision: "",
    romHacks: [],
    series: null,
    seriesPosition: null,
    title: "Mock Multi-Disc Game",
    updatedAt: new Date().toISOString(),
    verificationStatus: "verified",
  };
}

function psxConsole(): Record<string, unknown> {
  return {
    abbreviation: "psx",
    browserPlayable: true,
    code: "psx",
    colorTheme: "blue",
    coverAspectRatio: 1.0,
    createdAt: new Date().toISOString(),
    defaultCore: "pcsx_rearmed",
    emulatorJsCore: "psx",
    extensions: ["bin", "cue", "m3u"],
    gameCount: 1,
    generation: 5,
    iconUrl: "",
    id: CONSOLE_ID,
    logoPngUrl: "",
    logoUrl: "",
    maker: { id: "sony", name: "Sony" },
    mediaType: { id: "disc", name: "Disc" },
    name: "PlayStation",
    playable: true,
    releaseYear: 1994,
    sortKey: "psx",
    type: "console",
  };
}

async function mockGameApi(page: Page): Promise<void> {
  // Game detail
  await page.route(`**/api/games/${GAME_ID}`, (route) =>
    route.fulfill({ status: 200, json: multiDiscGame() }),
  );
  // Game saves (no auto-saves)
  await page.route(`**/api/games/${GAME_ID}/saves`, (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  // Session creation when sessionIdParam === "new"
  await page.route(`**/api/games/${GAME_ID}/sessions`, (route) => {
    if (route.request().method() === "POST") {
      route.fulfill({
        status: 200,
        json: { id: SESSION_ID, gameId: GAME_ID, name: "Default" },
      });
    } else {
      route.fulfill({ status: 200, json: [] });
    }
  });
  // Session saves (no auto-save)
  await page.route(`**/api/sessions/${SESSION_ID}/saves`, (route) =>
    route.fulfill({ status: 200, json: [] }),
  );
  // Disc downloads — synthesise a tiny but real STORE-method zip per
  // disc so use-disc-manager's `extractZipStore` round-trips cleanly.
  await page.route(`**/api/games/${GAME_ID}/discs/*/download*`, (route) => {
    const url = route.request().url();
    const match = url.match(/\/discs\/(\d+)\/download/);
    const discNum = match ? match[1] : "0";
    const body = buildSingleFileZip(
      `Mock Game (Disc ${discNum}).bin`,
      `DISC_${discNum}_PAYLOAD`,
    );
    route.fulfill({
      status: 200,
      contentType: "application/zip",
      body,
    });
  });
}

test.describe("Multi-disc switch flow", () => {
  test("re-inits emulator with combined bundle, target disc, and save state", async ({
    page,
  }) => {
    await mockGameApi(page);
    await page.goto(
      `/games/${GAME_ID}/play/new?mockEmulator=true`,
    );

    type MockStore = {
      inits: Array<Record<string, unknown>>;
      saveRequests: number;
      pauseCount: number;
      resumeCount: number;
    };
    const readStore = () =>
      page.evaluate(() => {
        const w = window as Window & {
          __spelaEmulatorMockParent__?: MockStore;
        };
        const s = w.__spelaEmulatorMockParent__;
        return {
          initCount: s?.inits.length ?? 0,
          saveRequests: s?.saveRequests ?? 0,
          pauseCount: s?.pauseCount ?? 0,
          firstInit: s?.inits[0] ?? null,
          secondInit: s?.inits[1] ?? null,
        };
      });

    // Wait for the first init (initial boot) to reach the mock.
    await expect
      .poll(async () => (await readStore()).initCount, {
        timeout: 15_000,
        message: "mock never received initial init",
      })
      .toBeGreaterThanOrEqual(1);

    const firstInit = (await readStore()).firstInit;

    expect(firstInit, "initial init not recorded").not.toBeNull();
    expect(firstInit).toMatchObject({
      gameName: "Mock Multi-Disc Game",
      targetDisc: null,
      saveStateData: null,
    });
    expect(
      String(firstInit?.romUrl ?? ""),
      "first boot should download disc 1",
    ).toContain(`/api/games/${GAME_ID}/discs/1/download`);

    // Wait for disc switch dropdown to be available
    const discSwitch = page.getByTestId("disc-switch-btn");
    await expect(discSwitch).toBeVisible({ timeout: 15_000 });

    // Open disc menu and wait for disc 2 to become enabled (downloads done)
    await discSwitch.click();
    const disc2 = page.getByTestId("disc-option-2");
    await expect(disc2).toBeVisible({ timeout: 15_000 });
    await expect(disc2).toBeEnabled({ timeout: 15_000 });
    await disc2.click();

    // Sanity-check: the switch handler pauses the emulator and requests a
    // save state. Readings come from the parent-window store which
    // survives the iframe reload that happens next.
    await expect
      .poll(async () => {
        const s = await readStore();
        return { pauseCount: s.pauseCount, saveRequests: s.saveRequests };
      }, { timeout: 5_000, message: "pause / save-state never reached iframe" })
      .toMatchObject({ pauseCount: 1, saveRequests: 1 });

    // Wait until the parent-window store records the second init — the
    // iframe's own store is wiped on the mid-flow reload, but the
    // parent-level one persists.
    await expect
      .poll(async () => (await readStore()).initCount, {
        timeout: 15_000,
        message: "second init never fired",
      })
      .toBeGreaterThanOrEqual(2);

    const final = await readStore();
    expect(final.saveRequests, "save state should have been requested").toBe(1);
    expect(final.pauseCount, "emulator should have been paused").toBe(1);
    expect(final.secondInit).toMatchObject({
      gameName: "Mock Multi-Disc Game",
      targetDisc: 1, // 0-indexed disc 2
      saveStateData: "TU9DS19TQVZFX1NUQVRFX1YxAA==",
    });
    expect(
      (final.secondInit as { romDataByteLength?: number } | null)
        ?.romDataByteLength,
      "second init should carry combined disc bundle",
    ).toBeGreaterThan(0);
  });
});
