import { test, expect } from "./fixtures";

test.describe("Admin System Events Page", () => {
  test("displays heading and description", async ({ page }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/system-events");

    await expect(
      page.getByRole("heading", { name: /System Events/ }),
    ).toBeVisible();
    await expect(page.getByText(/Audit log of system events/)).toBeVisible();
  });

  test("renders events from the API", async ({ page }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: 1,
              createdAt: "2026-04-10T09:00:00Z",
              categoryCode: "security",
              categoryName: "Security",
              eventType: "login_failed",
              reason: "bad_password",
              username: "alice",
              ip: "10.0.0.1",
              metadata: { failedCount: 3 },
            },
            {
              id: 2,
              createdAt: "2026-04-10T08:55:00Z",
              categoryCode: "security",
              categoryName: "Security",
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

    await page.goto("/admin/system-events");

    await expect(page.getByText("2 events")).toBeVisible();
    await expect(page.getByText("alice").first()).toBeVisible();
    await expect(page.getByText("10.0.0.1").first()).toBeVisible();
  });

  test("shows empty state when no events match", async ({ page }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/system-events");

    await expect(page.getByText("No system events")).toBeVisible();
  });

  test("filter chip updates URL query string", async ({ page }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/system-events");

    await page.getByRole("button", { name: "Login failed" }).first().click();

    await expect(page).toHaveURL(/eventType=login_failed/);
  });

  test("federation category filter updates URL query string", async ({
    page,
  }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith("/categories")) {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify([
            { code: "security", name: "Security" },
            { code: "operational", name: "Operational" },
            { code: "federation", name: "Federation" },
          ]),
        });
        return;
      }
      if (path.endsWith("/types")) {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            types: [
              { type: "login_failed", category: "security" },
              {
                type: "federation_peer_unreachable",
                category: "federation",
              },
            ],
          }),
        });
        return;
      }
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 50 }),
      });
    });

    await page.goto("/admin/system-events");
    await page.getByRole("button", { name: "Login failed" }).first().click();
    await expect(page).toHaveURL(/eventType=login_failed/);

    await page.getByRole("button", { name: "Federation", exact: true }).click();

    await expect(page).toHaveURL(/category=federation/);
    await expect(page).not.toHaveURL(/eventType=/);
    await expect(
      page.getByRole("button", { name: "Federation peer unreachable" }),
    ).toBeVisible();
  });

  test("clicking a row opens the detail modal", async ({ page }) => {
    await page.route("**/api/admin/system-events*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: 42,
              createdAt: "2026-04-10T09:00:00Z",
              categoryCode: "security",
              categoryName: "Security",
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

    await page.goto("/admin/system-events");

    await page.getByRole("row").nth(1).click();

    await expect(page.getByRole("dialog")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "System event" }),
    ).toBeVisible();
    await expect(page.getByText(/failedCount/).first()).toBeVisible();
    await expect(page.getByText("/api/auth/login")).toBeVisible();
  });

  test("non-admin user cannot access the page", async ({ browser }) => {
    const context = await browser.newContext({ storageState: undefined });
    const page = await context.newPage();
    await page.goto("/admin/system-events");
    await expect(
      page.getByRole("heading", { name: /System Events/ }),
    ).not.toBeVisible({ timeout: 2_000 });
    await context.close();
  });

  test("admin nav link is visible in sidebar", async ({ page }) => {
    await page.goto("/admin/system-events");
    await expect(
      page.getByRole("link", { name: /System Events/ }).first(),
    ).toBeVisible();
  });
});
