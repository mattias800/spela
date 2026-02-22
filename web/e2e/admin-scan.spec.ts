import { test, expect } from "./fixtures";

test.describe("Admin Scan Page", () => {
  test("displays scan and scrape cards", async ({ page }) => {
    await page.goto("/admin/scan");

    await expect(page.getByRole("heading", { name: "Library Scan" })).toBeVisible();
    await expect(page.getByText("Scan for Games")).toBeVisible();
    await expect(
      page.getByText("Scrape Metadata", { exact: false }).first(),
    ).toBeVisible();
  });

  test("Start Scan button shows loading indicator during scan", async ({
    page,
  }) => {
    // Slow down the scan endpoint to keep loading indicator visible
    await page.route("**/api/games/scan", async (route) => {
      await new Promise((r) => setTimeout(r, 2_000));
      route.fulfill({
        status: 200,
        json: { totalGames: 3, newGames: 0, updatedGames: 0, removedGames: 0 },
      });
    });

    await page.goto("/admin/scan");

    await page.getByRole("button", { name: /Start Scan/ }).click();

    await expect(
      page.getByText("Scanning game directories..."),
    ).toBeVisible({ timeout: 3_000 });
  });

  test("shows scan results after scan completes", async ({ page }) => {
    await page.route("**/api/games/scan", (route) => {
      route.fulfill({
        status: 200,
        json: {
          totalGames: 10,
          newGames: 2,
          updatedGames: 1,
          removedGames: 0,
        },
      });
    });

    await page.goto("/admin/scan");

    await page.getByRole("button", { name: /Start Scan/ }).click();

    await expect(page.getByText("Scan complete")).toBeVisible({
      timeout: 5_000,
    });
    await expect(page.getByText("10")).toBeVisible();
    await expect(page.getByText("2")).toBeVisible();
  });

  test("Scrape Metadata button is clickable", async ({ page }) => {
    // Mock the scrape endpoint
    let scrapeRequested = false;
    await page.route("**/api/admin/scrape", (route) => {
      scrapeRequested = true;
      route.fulfill({ status: 200, json: {} });
    });

    // Mock the scrape status endpoint to return inactive
    await page.route("**/api/admin/scrape/status", (route) => {
      route.fulfill({
        status: 200,
        json: { active: false },
      });
    });

    await page.goto("/admin/scan");

    // Find the Scrape Metadata button (not the heading)
    const scrapeButtons = page.locator("button", {
      hasText: "Scrape Metadata",
    });
    await expect(scrapeButtons.first()).toBeVisible();
    await scrapeButtons.first().click();

    // Verify the scrape endpoint was called
    expect(scrapeRequested).toBe(true);
  });

  test("shows scrape progress when scrape is active on page load", async ({
    page,
  }) => {
    // Mock the scrape status endpoint to return active scrape
    await page.route("**/api/admin/scrape/status", (route) => {
      route.fulfill({
        status: 200,
        json: {
          active: true,
          current: 3,
          total: 10,
          gameName: "Super Mario Bros.",
          successes: 2,
          failures: 1,
        },
      });
    });

    await page.goto("/admin/scan");

    await expect(page.getByText(/Scraping game 3 of 10/)).toBeVisible({
      timeout: 5_000,
    });
    await expect(page.getByText("Super Mario Bros.")).toBeVisible();
    await expect(page.getByText("2 succeeded")).toBeVisible();
    await expect(page.getByText("1 failed")).toBeVisible();
  });
});
