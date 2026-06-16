import { test, expect, resetServer } from "./fixtures";

const SERVER_URL = "http://localhost:8080";

// Seeds a connected-server catalog entry directly in the real backend (no second
// server needed) via the test-only endpoint, then verifies the web ⌘K command
// palette surfaces it in the read-only "On connected servers" section — i.e. the
// federated search wiring works against a live backend end to end.
test.describe("Federation — connected-server search", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("⌘K surfaces a game available on a connected server", async ({ page }) => {
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

    await page.goto("/");

    // Open the command palette via the sidebar search button. Clicking it
    // auto-waits for the app shell to mount, avoiding a race with the palette's
    // event listener.
    await page.getByRole("button", { name: "Open search" }).click();
    await expect(page.getByTestId("search-palette")).toBeVisible();

    await page.getByLabel("Search input").fill("Chrono");

    // The connected-servers section and the seeded game appear — served by the
    // real backend's federated catalog, rendered read-only in the palette.
    await expect(page.getByTestId("federated-search-section")).toBeVisible();
    const row = page.getByTestId("federated-result-igdb:1022");
    await expect(row).toBeVisible();
    await expect(row).toContainText("Chrono Trigger");
    await expect(row).toContainText("on 1 connected server");
  });
});
