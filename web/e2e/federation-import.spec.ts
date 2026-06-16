import { test, expect, resetServer } from "./fixtures";

const SERVER_URL = "http://localhost:8080";

// Drives the connected-server import flow against the real backend: seed a
// catalog entry (no second server needed), navigate from ⌘K to the remote-game
// page, and start an import. With no peer actually serving the ROM the job
// fails — but that still exercises the full enqueue → worker → status pipeline
// and the queue UI end to end. The happy path (a completed import) is covered
// by the server's Go unit + two-server integration tests.
test.describe("Federation — import a connected-server game", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("⌘K result opens the remote-game page and starts an import", async ({
    page,
  }) => {
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

    // Open ⌘K and surface the connected-server result, then click into it.
    await page.getByRole("button", { name: "Open search" }).click();
    await expect(page.getByTestId("search-palette")).toBeVisible();
    await page.getByLabel("Search input").fill("Chrono");
    const row = page.getByTestId("federated-result-igdb:1022");
    await expect(row).toBeVisible();
    await row.click();

    // The remote-game page renders the sparse connected-server detail.
    const detail = page.getByTestId("remote-game-detail");
    await expect(detail).toBeVisible();
    await expect(detail).toContainText("Chrono Trigger");
    await expect(detail).toContainText("SNES");
    await expect(detail).toContainText("Available on 1 connected server");

    // The default E2E user is an admin, so the import action is available.
    const importButton = page.getByTestId("import-game-button");
    await expect(importButton).toBeVisible();
    await importButton.click();

    // The job appears in the queue and reaches a terminal state.
    const jobRow = page.getByTestId(/^import-job-\d+$/).first();
    await expect(jobRow).toBeVisible();
    await expect(jobRow).toContainText("Chrono Trigger");
    await expect(page.getByTestId("import-job-status").first()).toContainText(
      "Failed",
      { timeout: 20000 },
    );
  });
});
