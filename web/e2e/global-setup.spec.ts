import { test as setup, expect } from "@playwright/test";

/**
 * Authenticates against the server (started via docker-compose.e2e.yml)
 * using the seeded admin credentials and saves storage state for subsequent tests.
 */
setup("authenticate", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").fill("admin");
  await page.getByLabel("Password").fill("admin123");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL("/", { timeout: 15_000 });

  await page.context().storageState({ path: "./e2e/.auth/storage-state.json" });
});
