import { test, expect } from "@playwright/test";

/**
 * E2E tests for the registration approval workflow.
 * New accounts require admin approval before they can log in.
 *
 * These tests run without the default auth storage state so that
 * we can test the unauthenticated registration flow directly.
 */

// Each test run uses a unique suffix to avoid username conflicts across runs
const suffix = Date.now();

test.describe("Registration Approval Flow", () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  test("registration shows pending approval message instead of logging in", async ({
    page,
  }) => {
    const username = `pendinguser${suffix}`;

    await page.goto("/register");
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Email").fill(`${username}@test.com`);
    await page.getByLabel("Password", { exact: true }).fill("password123");
    await page.getByLabel("Confirm Password", { exact: true }).fill("password123");
    await page.getByRole("button", { name: /create account/i }).click();

    // Should NOT navigate to "/" — should show a pending message
    await expect(page).not.toHaveURL("/", { timeout: 5_000 });
    await expect(
      page.getByText(/pending.*approval|waiting.*admin|admin.*approve/i).first(),
    ).toBeVisible({ timeout: 5_000 });
  });

  test("pending user cannot log in", async ({ page }) => {
    const username = `pendinglogin${suffix}`;

    // Register via API (bypass the UI to avoid waiting for the UI message test)
    await page.goto("/");
    const regStatus = await page.evaluate(
      async ([user, suffix]) => {
        const res = await fetch("/api/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: `${user}${suffix}`,
            email: `${user}${suffix}@test.com`,
            password: "password123",
          }),
        });
        return res.status;
      },
      [username, ""] as [string, string],
    );
    // Should return 202 (pending), not 201 (auto-login)
    expect(regStatus).toBe(202);

    // Try to log in as the pending user
    await page.goto("/login");
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Password").fill("password123");
    await page.getByRole("button", { name: /sign in/i }).click();

    // Should see "pending approval" error, NOT navigate to "/"
    await expect(page).not.toHaveURL("/", { timeout: 5_000 });
    await expect(
      page.getByText(/pending.*approval|account.*pending|awaiting.*approval/i),
    ).toBeVisible({ timeout: 5_000 });
  });

  test("admin can approve a pending user and the user can then log in", async ({
    browser,
  }) => {
    const username = `approvaltest${suffix}`;

    // Step 1: Register a new user (unauthenticated context)
    const userCtx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
      baseURL: "http://localhost:5173",
    });
    const userPage = await userCtx.newPage();

    // Navigate first so the page has a URL context for relative fetch calls
    await userPage.goto("/login");

    const regStatus = await userPage.evaluate(
      async (uname: string) => {
        const res = await fetch("/api/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: uname,
            email: `${uname}@test.com`,
            password: "password123",
          }),
        });
        return res.status;
      },
      username,
    );
    expect(regStatus).toBe(202);

    // Step 2: Log in as admin and approve the user
    const adminCtx = await browser.newContext();
    const adminPage = await adminCtx.newPage();

    await adminPage.goto("/login");
    await adminPage.getByLabel("Username").fill("admin");
    await adminPage.getByLabel("Password").fill("admin123");
    await adminPage.getByRole("button", { name: /sign in/i }).click();
    await expect(adminPage).toHaveURL("/", { timeout: 15_000 });

    await adminPage.goto("/admin/users");
    await expect(adminPage.getByText("User Management")).toBeVisible();

    // Find the pending user row — it should have a "pending" badge
    const userRow = adminPage.locator("tr").filter({ hasText: username });
    await expect(userRow).toBeVisible();
    await expect(userRow.getByText(/pending/i)).toBeVisible();

    // Click "Approve" for this user
    await userRow.getByRole("button", { name: /approve/i }).click();

    // Wait for the row to update — "pending" badge should be gone
    await expect(userRow.getByText(/pending/i)).not.toBeVisible({
      timeout: 5_000,
    });

    await adminCtx.close();

    // Step 3: Now the user should be able to log in
    await userPage.goto("/login");
    await userPage.getByLabel("Username").fill(username);
    await userPage.getByLabel("Password").fill("password123");
    await userPage.getByRole("button", { name: /sign in/i }).click();
    await expect(userPage).toHaveURL("/", { timeout: 10_000 });

    await userCtx.close();
  });
});
