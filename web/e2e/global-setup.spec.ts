import { test as setup, expect } from "@playwright/test";

const SERVER_URL = "http://localhost:8080";

/**
 * Verify the server is healthy and has scanned games before running any tests.
 * This catches issues like: server not started, scan not complete, stale state.
 */
setup("verify server readiness", async () => {
  // 1. Health check
  const health = await fetch(`${SERVER_URL}/api/health`);
  expect(health.ok, "Server health check failed").toBe(true);

  // 2. Verify test mode is enabled (reset endpoint available)
  const reset = await fetch(`${SERVER_URL}/api/test/reset`, { method: "POST" });
  expect(
    reset.ok,
    "Test reset endpoint not available — is SPELA_TEST_MODE=true?",
  ).toBe(true);

  // 3. Verify games have been scanned
  const loginRes = await fetch(`${SERVER_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "admin", password: "admin123" }),
  });
  expect(loginRes.ok, "Admin login failed").toBe(true);
  const { accessToken } = await loginRes.json();

  const gamesRes = await fetch(`${SERVER_URL}/api/games?limit=1`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(gamesRes.ok, "Games endpoint failed").toBe(true);
  const games = await gamesRes.json();
  expect(
    games.total,
    "No games found — game scan may not have completed",
  ).toBeGreaterThan(0);
});

/**
 * Authenticates against the server using the seeded admin credentials
 * and saves storage state for subsequent tests.
 */
setup("authenticate", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").fill("admin");
  await page.getByLabel("Password").fill("admin123");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page).toHaveURL("/", { timeout: 15_000 });

  await page.context().storageState({ path: "./e2e/.auth/storage-state.json" });
});

setup("login by pressing Enter after typing credentials", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("Username").click();
  await page.keyboard.type("admin");
  await page.keyboard.press("Tab");
  await page.keyboard.type("admin123");
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL("/", { timeout: 15_000 });
});
