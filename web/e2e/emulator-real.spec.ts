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

  test("EmulatorJS loads and starts a game inside the iframe", async ({
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

    // Listen for the game-started postMessage from the iframe
    const gameStartedPromise = page.evaluate(() => {
      return new Promise<boolean>((resolve) => {
        const timeout = setTimeout(() => resolve(false), 90_000);
        window.addEventListener("message", (event) => {
          if (
            event.data &&
            typeof event.data === "object" &&
            event.data.type === "game-started"
          ) {
            clearTimeout(timeout);
            resolve(true);
          }
        });
      });
    });

    // Verify EmulatorJS loader.js is actually accessible (not a 404)
    const loaderResponse = await page.request.get(
      "http://localhost:5173/emulatorjs/data/loader.js",
    );
    expect(loaderResponse.ok()).toBe(true);
    expect(loaderResponse.headers()["content-type"]).toContain("javascript");

    // Inspect the iframe for EmulatorJS-specific elements
    const frame = page.frameLocator('iframe[title*="Playing"]');

    // EmulatorJS creates a canvas and UI elements inside #game.
    // Wait for the #game div (always present) then wait for EmulatorJS to
    // populate it with a canvas or its loading UI.
    await expect(frame.locator("#game")).toBeVisible({ timeout: 30_000 });

    // Wait for EmulatorJS to create its UI — it adds elements like
    // canvas, .ejs--css-btn, or the loading overlay with progress bar.
    // Use a generous timeout since it downloads WASM cores at runtime.
    const ejsCanvas = frame.locator("#game canvas");
    const ejsLoadingText = frame.locator('text="Loading..."');
    const ejsStartButton = frame.locator('[class*="ejs"]');

    // At least one of these should appear, confirming EmulatorJS initialized
    await expect(
      ejsCanvas.or(ejsLoadingText).or(ejsStartButton).first(),
    ).toBeVisible({ timeout: 60_000 });

    // Wait for the game-started event from the iframe
    const gameStarted = await gameStartedPromise;
    expect(gameStarted).toBe(true);

    // After game-started, verify the canvas is rendering (has non-zero size)
    await expect(ejsCanvas.first()).toBeVisible({ timeout: 30_000 });
    const canvasBox = await ejsCanvas.first().boundingBox();
    expect(canvasBox).toBeTruthy();
    expect(canvasBox!.width).toBeGreaterThan(0);
    expect(canvasBox!.height).toBeGreaterThan(0);

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

    // Wait for the game-started postMessage from the iframe
    const gameStarted = await page.evaluate(() => {
      return new Promise<boolean>((resolve) => {
        const timeout = setTimeout(() => resolve(false), 90_000);
        window.addEventListener("message", (event) => {
          if (
            event.data &&
            typeof event.data === "object" &&
            event.data.type === "game-started"
          ) {
            clearTimeout(timeout);
            resolve(true);
          }
        });
      });
    });
    expect(gameStarted).toBe(true);

    // Let the game run briefly so emulator has state to save
    await page.waitForTimeout(2_000);

    // Set up listener for auto-save POST before clicking Back
    const autoSavePromise = page.waitForResponse(
      (res) =>
        res.request().method() === "POST" && res.url().includes("/saves/auto"),
      { timeout: 10_000 },
    );

    // Click the Back button — this should trigger an exit auto-save
    await page.getByTestId("back-btn").click();

    // Verify the auto-save POST request was made and succeeded
    const autoSaveResponse = await autoSavePromise;
    expect(autoSaveResponse.ok()).toBe(true);

    // Should navigate back to game detail page
    await expect(page).toHaveURL(new RegExp(`/games/${gameId}$`), {
      timeout: 10_000,
    });

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

    // Wait for EmulatorJS to start the game (enables the buttons)
    // The game-started postMessage triggers status="playing" in React
    await expect(saveButton).toBeEnabled({ timeout: 90_000 });
    await expect(loadButton).toBeEnabled({ timeout: 5_000 });

    // Verify no unexpected errors occurred
    monitor.assertClean();
  });
});
