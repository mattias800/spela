import { test, expect, resetServer } from "./fixtures";

const SERVER_URL = "http://localhost:8080";

// Exercises the connected-servers browse area against the real backend: seed a
// catalog entry, then walk the overview → per-console grid → remote-game page.
test.describe("Federation — connected-servers browse", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("browse consoles → games → remote-game page", async ({ page }) => {
    const seed = await fetch(`${SERVER_URL}/api/test/federation/seed-catalog`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        originFingerprint: "e2e-remote-server-fingerprint",
        key: "igdb:1022",
        title: "Chrono Trigger",
        console: "SNES",
      }),
    });
    expect(seed.ok, "seed-catalog endpoint failed").toBe(true);

    // Overview: the SNES console appears with its connected-server game count.
    await page.goto("/connected-servers");
    const consoleCard = page.getByTestId("connected-console-SNES");
    await expect(consoleCard).toBeVisible();
    await expect(consoleCard).toContainText("1 game");
    await consoleCard.click();

    // Per-console grid: the seeded game card is shown.
    const gameCard = page.getByTestId("remote-game-card-igdb:1022");
    await expect(gameCard).toBeVisible();
    await expect(gameCard).toContainText("Chrono Trigger");
    await gameCard.click();

    // Lands on the remote-game page for that game.
    const detail = page.getByTestId("remote-game-detail");
    await expect(detail).toBeVisible();
    await expect(detail).toContainText("Chrono Trigger");
  });
});
