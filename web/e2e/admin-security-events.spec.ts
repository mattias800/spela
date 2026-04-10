import { test, expect } from "./fixtures";

test.describe("Admin Security Events Page", () => {
  test("displays heading and description", async ({ page }) => {
    await page.route("**/api/admin/security-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/security-events");

    await expect(
      page.getByRole("heading", { name: /Security Events/ }),
    ).toBeVisible();
    await expect(
      page.getByText(/Audit log of authentication events/),
    ).toBeVisible();
  });

  test("renders events from the API", async ({ page }) => {
    await page.route("**/api/admin/security-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: 1,
              createdAt: "2026-04-10T09:00:00Z",
              eventType: "login_failed",
              reason: "bad_password",
              username: "alice",
              ip: "10.0.0.1",
              metadata: { failedCount: 3 },
            },
            {
              id: 2,
              createdAt: "2026-04-10T08:55:00Z",
              eventType: "account_locked",
              username: "alice",
              ip: "10.0.0.1",
            },
          ],
          total: 2,
          page: 1,
          pageSize: 50,
        }),
      });
    });

    await page.goto("/admin/security-events");

    await expect(page.getByText("2 events")).toBeVisible();
    // alice appears in both rows.
    await expect(page.getByText("alice").first()).toBeVisible();
    await expect(page.getByText("10.0.0.1").first()).toBeVisible();
  });

  test("shows empty state when no events match", async ({ page }) => {
    await page.route("**/api/admin/security-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/security-events");

    await expect(page.getByText("No security events")).toBeVisible();
  });

  test("filter chip updates URL query string", async ({ page }) => {
    await page.route("**/api/admin/security-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/security-events");

    // Filter chips have role=button.
    await page
      .getByRole("button", { name: "Login failed" })
      .first()
      .click();

    await expect(page).toHaveURL(/eventType=login_failed/);
  });

  test("clicking a row opens the detail modal", async ({ page }) => {
    await page.route("**/api/admin/security-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: 42,
              createdAt: "2026-04-10T09:00:00Z",
              eventType: "login_failed",
              reason: "bad_password",
              username: "victim",
              ip: "10.0.0.99",
              path: "/api/auth/login",
              metadata: { failedCount: 4 },
            },
          ],
          total: 1,
          page: 1,
          pageSize: 50,
        }),
      });
    });

    await page.goto("/admin/security-events");

    // Click the row (the username cell is in the data row, not the header).
    await page.getByRole("row").nth(1).click();

    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Security event" }),
    ).toBeVisible();
    // Metadata block with the JSON payload.
    await expect(page.getByText(/failedCount/).first()).toBeVisible();
    await expect(page.getByText("/api/auth/login")).toBeVisible();
  });

  test("non-admin user cannot access the page", async ({ browser }) => {
    // Use a fresh context without the admin auth storage.
    const context = await browser.newContext({ storageState: undefined });
    const page = await context.newPage();
    await page.goto("/admin/security-events");
    // Should be redirected to login, not render the page.
    await expect(
      page.getByRole("heading", { name: /Security Events/ }),
    ).not.toBeVisible({ timeout: 2_000 });
    await context.close();
  });

  test("admin nav link is visible in sidebar", async ({ page }) => {
    await page.goto("/admin/security-events");
    // The nav link renders in the sidebar as a link with the label text.
    await expect(
      page.getByRole("link", { name: /Security Events/ }).first(),
    ).toBeVisible();
  });
});
