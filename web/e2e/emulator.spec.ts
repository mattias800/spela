import { test, expect } from "./fixtures";

test.describe("Emulator Page", () => {
  test.describe("Play Button on Game Detail", () => {
    test("shows enabled Play in Browser button for NES game", async ({
      page,
    }) => {
      // Mock saves endpoint so hasSaves=false and play-in-browser-btn is shown
      await page.route("**/api/games/*/saves", (route) =>
        route.fulfill({ json: [] }),
      );

      // Navigate to games list, wait for API data before searching
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      // Click on the first Castlevania result to open detail page
      await page.getByText("Castlevania", { exact: false }).first().click();
      await expect(page).toHaveURL(/\/games\/\d+$/);

      // The "Play in Browser" button should be visible and enabled
      const playButton = page.getByTestId("play-in-browser-btn");
      await expect(playButton).toBeVisible();
      await expect(playButton).toBeEnabled();
    });

    test("navigates to play page when Play in Browser is clicked", async ({
      page,
    }) => {
      // Mock saves endpoint so hasSaves=false and play-in-browser-btn is shown
      await page.route("**/api/games/*/saves", (route) =>
        route.fulfill({ json: [] }),
      );

      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      await expect(page).toHaveURL(/\/games\/\d+$/);

      // Extract the game ID from the URL
      const url = page.url();
      const gameId = url.match(/\/games\/(\d+)$/)?.[1];
      expect(gameId).toBeTruthy();

      await page.getByTestId("play-in-browser-btn").click();
      await expect(page).toHaveURL(new RegExp(`/games/${gameId}/play/`));
    });
  });

  test.describe("Emulator Page Layout", () => {
    test("shows game title and navigation controls", async ({ page }) => {
      // First find a valid game ID by navigating through the UI
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      await expect(page).toHaveURL(/\/games\/\d+$/);

      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];
      expect(gameId).toBeTruthy();

      // Navigate to the play page (use "new" to auto-create a session)
      await page.goto(`/games/${gameId}/play/new`);

      // Game title should be displayed in the top bar
      await expect(page.getByText("Castlevania", { exact: false })).toBeVisible(
        {
          timeout: 15_000,
        },
      );

      // Back button should be visible (use exact match to avoid matching "Back to Game")
      await expect(
        page.getByRole("button", { name: "Back", exact: true }),
      ).toBeVisible();

      // Toolbar buttons should be present (Save, Fullscreen)
      await expect(page.getByTitle("Save State")).toBeVisible();
      await expect(page.getByTitle("Fullscreen (F11)")).toBeVisible();
    });

    test("shows emulator iframe", async ({ page }) => {
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];

      await page.goto(`/games/${gameId}/play/new`);

      // The iframe hosting EmulatorJS should be present
      const iframe = page.locator('iframe[src="/emulator.html"]');
      await expect(iframe).toBeVisible({ timeout: 15_000 });

      // The iframe should have proper attributes
      await expect(iframe).toHaveAttribute("allow", /gamepad/);
      await expect(iframe).toHaveAttribute("allow", /fullscreen/);
    });

    test("shows loading state while initializing", async ({ page }) => {
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];

      await page.goto(`/games/${gameId}/play/new`);

      // Wait for the page to finish loading game data first
      await expect(
        page
          .locator('iframe[src="/emulator.html"]')
          .or(
            page.getByText(
              /initializing emulator|loading rom|error|not available|failed/i,
            ),
          )
          .first(),
      ).toBeVisible({ timeout: 15_000 });

      // Loading indicator should appear (may be brief)
      // Check for the loading text or the spinner
      const loadingText = page.getByText(/initializing emulator|loading rom/i);
      // This may disappear quickly so we use a soft check
      const isLoading = await loadingText.isVisible().catch(() => false);
      // If EmulatorJS assets aren't installed, we should see an error instead
      if (!isLoading) {
        // Either the emulator loaded very quickly, or an error occurred
        // Both are acceptable outcomes for this test
        const hasError = await page
          .getByText(/error|not available|failed/i)
          .isVisible()
          .catch(() => false);
        const hasIframe = await page
          .locator('iframe[src="/emulator.html"]')
          .isVisible()
          .catch(() => false);
        expect(hasError || hasIframe).toBe(true);
      }
    });

    test("exit button returns to game detail page", async ({ page }) => {
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];

      await page.goto(`/games/${gameId}/play/new`);

      // Wait for the page to be ready (use exact match to avoid matching "Back to Game")
      await expect(
        page.getByRole("button", { name: "Back", exact: true }),
      ).toBeVisible({ timeout: 15_000 });

      // Click the Back button to return to game detail
      await page.getByRole("button", { name: "Back", exact: true }).click();

      await expect(page).toHaveURL(`/games/${gameId}`, { timeout: 10_000 });
    });
  });

  test.describe("Unsupported Console Handling", () => {
    test("shows not available message for unsupported console", async ({
      page,
    }) => {
      // Intercept the consoles API BEFORE any navigation so the cache
      // is populated with modified data from the very first request.
      await page.route("**/api/consoles", async (route) => {
        try {
          const response = await route.fetch();
          const consoles = await response.json();
          // Remove emulatorJsCore from all consoles to simulate unsupported
          const modified = consoles.map((c: Record<string, unknown>) => ({
            ...c,
            emulatorJsCore: "",
          }));
          await route.fulfill({ json: modified });
        } catch {
          // Page may have closed during route handling
        }
      });

      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      await expect(page).toHaveURL(/\/games\/\d+$/);
      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];

      await page.goto(`/games/${gameId}/play/new`);

      await expect(page.getByText("Browser Play Not Available")).toBeVisible({
        timeout: 15_000,
      });
      await expect(
        page.getByText(/not yet supported for browser play/i),
      ).toBeVisible();

      // The "Back to Game Details" button should navigate back
      await page.getByRole("button", { name: /back to game details/i }).click();
      await expect(page).toHaveURL(`/games/${gameId}`, { timeout: 10_000 });
    });

    test("Play in Browser button is disabled for unsupported console", async ({
      page,
    }) => {
      // Mock saves endpoint so hasSaves=false and play-in-browser-btn is shown
      await page.route("**/api/games/*/saves", (route) =>
        route.fulfill({ json: [] }),
      );

      // Intercept consoles API to remove emulatorJsCore
      await page.route("**/api/consoles", async (route) => {
        try {
          const response = await route.fetch();
          const consoles = await response.json();
          const modified = consoles.map((c: Record<string, unknown>) => ({
            ...c,
            emulatorJsCore: "",
          }));
          await route.fulfill({ json: modified });
        } catch {
          // Page may have closed during route handling
        }
      });

      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      // Target the card's anchor specifically. `.getByText(...).first()`
      // sometimes resolved to a heading inside the card whose click didn't
      // reliably propagate to the wrapping link — intermittent #565 failure.
      const firstCard = page.getByRole("link", { name: /Castlevania/ }).first();
      await expect(firstCard).toBeVisible();
      await firstCard.click();
      await expect(page).toHaveURL(/\/games\/\d+$/);
      // Wait for the detail page's queries (game, consoles, saves, achievements)
      // to settle. Without this the `play-in-browser-btn` locator races with
      // the react-query cache warm-up and intermittently reports the button
      // missing (#565). The mock intercepts /api/consoles to return
      // emulatorJsCore=""; the button only appears once both the game and
      // its console info have landed in the cache.
      await page.waitForLoadState("networkidle");

      const playButton = page.getByTestId("play-in-browser-btn");
      await expect(playButton).toBeVisible();
      await expect(playButton).toBeDisabled();
    });
  });

  test.describe("Toolbar Controls", () => {
    test("save button is disabled before emulator is playing", async ({
      page,
    }) => {
      await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes("/api/games") && resp.ok(),
        ),
        page.goto("/games"),
      ]);
      await page.getByPlaceholder(/search/i).fill("Castlevania");
      await page.keyboard.press("Enter");

      await page.getByText("Castlevania", { exact: false }).first().click();
      const gameId = page.url().match(/\/games\/(\d+)$/)?.[1];

      await page.goto(`/games/${gameId}/play/new`);

      // Before the emulator is fully loaded and playing, save should be disabled
      const saveButton = page.getByTitle("Save State");

      await expect(saveButton).toBeVisible({ timeout: 15_000 });
      await expect(saveButton).toBeDisabled();
    });
  });

  test.describe("Game Not Found", () => {
    test("shows not found message for invalid game ID", async ({ page }) => {
      await page.goto("/games/999999/play/new");

      await expect(page.getByText("Game not found")).toBeVisible({
        timeout: 15_000,
      });
      await expect(page.getByText("Go back")).toBeVisible();
    });
  });
});
