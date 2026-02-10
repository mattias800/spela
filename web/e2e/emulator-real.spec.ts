import { test, expect } from "./fixtures";

test.describe("EmulatorJS Real Integration", () => {
  test.setTimeout(120_000);

  test("EmulatorJS loads and starts a game inside the iframe", async ({
    page,
  }) => {
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
    const ejsLoadingText = frame.locator(
      'text="Loading..."',
    );
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
  });

  test("EmulatorJS assets are served at the correct path", async ({
    page,
  }) => {
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
  });

  test("save and load buttons become enabled after game starts", async ({
    page,
  }) => {
    await page.goto("/games");
    await page.getByPlaceholder(/search/i).fill("Castlevania");
    await page.keyboard.press("Enter");

    await page.getByText("Castlevania", { exact: false }).first().click();
    const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];
    expect(gameId).toBeTruthy();

    await page.goto(`/games/${gameId}/play`);

    // Wait for iframe
    await expect(
      page.locator('iframe[src="/emulator.html"]'),
    ).toBeVisible({ timeout: 15_000 });

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
  });
});
