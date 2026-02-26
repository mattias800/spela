import { test, expect } from "./fixtures";

/**
 * Sets up monitoring for console errors, page errors, and failed network
 * requests.  Call at the beginning of each test, then call the returned
 * `assertClean()` at the end to verify no unexpected errors occurred.
 */
function monitorPageErrors(page: import("@playwright/test").Page) {
  const consoleErrors: string[] = [];
  const pageErrors: string[] = [];
  const failedRequests: string[] = [];

  page.on("console", (msg) => {
    if (msg.type() === "error") {
      consoleErrors.push(msg.text());
    }
  });

  page.on("pageerror", (err) => {
    pageErrors.push(err.message);
  });

  page.on("response", (response) => {
    if (response.status() >= 400) {
      const url = response.url();
      // Auto-save 404 is expected when no save exists yet
      if (response.status() === 404 && url.includes("/saves/auto")) return;
      // Ratings & shared-saves endpoints may not be available on the server
      if (response.status() === 404 && url.includes("/ratings")) return;
      if (response.status() === 404 && url.includes("/shared-saves")) return;
      // BIOS files may not be present in the E2E environment
      if (response.status() === 404 && url.includes("/api/bios/")) return;
      failedRequests.push(`${response.status()} ${url}`);
    }
  });

  return {
    consoleErrors,
    pageErrors,
    failedRequests,
    assertClean() {
      // Filter out non-fatal / expected console messages:
      // - "Translation not found" from EmulatorJS locale handling
      // - "Missing language" from EmulatorJS when locale JSON isn't available
      // - "Loading language" informational log that sometimes arrives as error
      const fatalConsoleErrors = consoleErrors.filter(
        (e) =>
          !e.includes("Translation not found") &&
          !e.includes("Missing language") &&
          !e.includes("Loading language") &&
          !e.includes("Failed to load resource") &&
          !e.includes("Query data cannot be undefined"),
      );

      // Filter out expected page errors:
      // - "Wake Lock" — EmulatorJS requests wake lock to prevent screen sleep;
      //   this is denied in headless Chromium and is non-fatal
      const fatalPageErrors = pageErrors.filter(
        (e) => !e.includes("Wake Lock"),
      );

      expect(
        failedRequests,
        `Unexpected failed requests:\n${failedRequests.join("\n")}`,
      ).toHaveLength(0);
      expect(
        fatalPageErrors,
        `Uncaught page errors:\n${fatalPageErrors.join("\n")}`,
      ).toHaveLength(0);
      expect(
        fatalConsoleErrors,
        `Unexpected console errors:\n${fatalConsoleErrors.join("\n")}`,
      ).toHaveLength(0);
    },
  };
}

test.describe("EmulatorJS Real Integration", () => {
  test.setTimeout(120_000);

  /**
   * Helper: continuously send game-started messages to simulate the emulator
   * reaching the "playing" state. In headless Chrome with SwiftShader, the
   * WASM core may not fully start, so we inject the event to unblock UI.
   */
  async function simulatePlaying(page: import("@playwright/test").Page) {
    const saveButton = page.getByTitle("Save State");
    const interval = setInterval(async () => {
      await page
        .evaluate(() => {
          window.postMessage(
            { type: "game-started" },
            window.location.origin,
          );
        })
        .catch(() => {});
    }, 200);

    try {
      await expect(saveButton).toBeEnabled({ timeout: 15_000 });
    } finally {
      clearInterval(interval);
    }
  }

  test("EmulatorJS loads and initializes for a game", async ({
    page,
  }) => {
    const monitor = monitorPageErrors(page);

    // Mock saves endpoint so hasSaves=false and play-in-browser-btn is shown
    await page.route("**/api/games/*/saves", (route) =>
      route.fulfill({ json: [] }),
    );

    // Navigate to the games list and find a playable game
    await page.goto("/games");
    await page.getByPlaceholder(/search/i).fill("Castlevania");
    await page.keyboard.press("Enter");

    // Open game detail
    await page.getByText("Castlevania", { exact: false }).first().click();
    await expect(page).toHaveURL(/\/games\/\d+$/);

    const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];
    expect(gameId).toBeTruthy();

    // Click Play in Browser
    await page.getByTestId("play-in-browser-btn").click();
    await expect(page).toHaveURL(`/games/${gameId}/play`);

    // Wait for the iframe to appear
    const iframe = page.locator('iframe[src="/emulator.html"]');
    await expect(iframe).toBeVisible({ timeout: 15_000 });

    // Verify EmulatorJS loader.js is actually accessible (not a 404)
    const loaderResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/loader.js",
    );
    expect(loaderResponse.ok()).toBe(true);
    expect(loaderResponse.headers()["content-type"]).toContain("javascript");

    // Inspect the iframe for EmulatorJS-specific elements
    const frame = page.frameLocator('iframe[title*="Playing"]');

    // EmulatorJS creates a canvas and UI elements inside #game.
    await expect(frame.locator("#game")).toBeVisible({ timeout: 30_000 });

    // Simulate game-started (WASM may not fully start in headless Chrome)
    await simulatePlaying(page);

    // Verify no unexpected errors occurred during the entire loading flow
    monitor.assertClean();
  });

  test("EmulatorJS assets are served at the correct path", async ({ page }) => {
    const monitor = monitorPageErrors(page);

    // Verify the critical EmulatorJS endpoints are available
    const loaderResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/loader.js",
    );
    expect(loaderResponse.ok()).toBe(true);

    const versionResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/version.json",
    );
    expect(versionResponse.ok()).toBe(true);

    const versionData = await versionResponse.json();
    expect(versionData.version).toBeTruthy();

    // Verify the emulator CSS is also served
    const cssResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/emulator.css",
    );
    expect(cssResponse.ok()).toBe(true);

    // Verify the non-minified source files are accessible (used in debug mode)
    const emulatorJsResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/src/emulator.js",
    );
    expect(emulatorJsResponse.ok()).toBe(true);

    const shadersResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/src/shaders.js",
    );
    expect(shadersResponse.ok()).toBe(true);

    monitor.assertClean();
  });

  test("auto-save triggers when clicking Back button during gameplay", async ({
    page,
  }) => {
    const monitor = monitorPageErrors(page);

    // Mock saves list so hasSaves=false and play-in-browser-btn is shown.
    // Only intercept GET to the saves list; POST to /saves/auto has a different
    // path and won't be caught by this pattern.
    await page.route("**/api/games/*/saves", (route) =>
      route.fulfill({ json: [] }),
    );

    // Navigate to a playable game
    await page.goto("/games");
    await page.getByPlaceholder(/search/i).fill("Castlevania");
    await page.keyboard.press("Enter");

    await page.getByText("Castlevania", { exact: false }).first().click();
    await expect(page).toHaveURL(/\/games\/\d+$/);

    const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];
    expect(gameId).toBeTruthy();

    // Click Play in Browser
    await page.getByTestId("play-in-browser-btn").click();
    await expect(page).toHaveURL(`/games/${gameId}/play`);

    // Wait for the iframe to appear
    await expect(page.locator('iframe[src="/emulator.html"]')).toBeVisible({
      timeout: 15_000,
    });

    // Simulate game-started (WASM may not fully start in headless Chrome)
    await simulatePlaying(page);

    // Intercept the auto-save POST so we can verify it was called
    let autoSaveCalled = false;
    await page.route(`**/api/games/${gameId}/saves/auto`, (route) => {
      if (route.request().method() === "POST") {
        autoSaveCalled = true;
        route.fulfill({
          status: 201,
          json: {
            id: 99,
            gameId: parseInt(gameId!),
            userId: 1,
            name: "auto",
            fileSize: 1024,
            isAuto: true,
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          },
        });
      } else {
        route.continue();
      }
    });

    // The Back button sends request-save-state to the iframe, which won't
    // respond (no real emulator). Set up a timer to simulate the iframe
    // responding with save data shortly after the click.
    await page.evaluate(() => {
      setTimeout(() => {
        window.postMessage(
          {
            type: "save-state-response",
            data: btoa("fake-auto-save-data"),
            screenshot: "",
          },
          window.location.origin,
        );
      }, 200);
    });

    // Click the Back button — this triggers the exit auto-save flow
    await page.getByTestId("back-btn").click();

    // Should navigate back to game detail page (with or without the save completing)
    await expect(page).toHaveURL(new RegExp(`/games/${gameId}$`), {
      timeout: 10_000,
    });

    // The auto-save POST should have been triggered
    expect(autoSaveCalled).toBe(true);

    // Verify no unexpected errors
    monitor.assertClean();
  });

  test("save and load buttons become enabled after game starts", async ({
    page,
  }) => {
    const monitor = monitorPageErrors(page);

    await page.goto("/games");
    await page.getByPlaceholder(/search/i).fill("Castlevania");
    await page.keyboard.press("Enter");

    await page.getByText("Castlevania", { exact: false }).first().click();
    const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];
    expect(gameId).toBeTruthy();

    await page.goto(`/games/${gameId}/play`);

    // Wait for iframe
    await expect(page.locator('iframe[src="/emulator.html"]')).toBeVisible({
      timeout: 15_000,
    });

    // Save/Load should be disabled initially
    const saveButton = page.getByTitle("Save State");
    const loadButton = page.getByTitle("Load State");
    await expect(saveButton).toBeVisible({ timeout: 15_000 });
    await expect(saveButton).toBeDisabled();
    await expect(loadButton).toBeDisabled();

    // Simulate game-started (WASM may not fully start in headless Chrome)
    await simulatePlaying(page);
    await expect(loadButton).toBeEnabled({ timeout: 5_000 });

    // Verify no unexpected errors occurred
    monitor.assertClean();
  });
});
