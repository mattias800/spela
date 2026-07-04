import { test, expect, resetServer } from "./fixtures";

const SERVER_URL = "http://localhost:8080";

async function enableRegistration(): Promise<void> {
  const loginRes = await fetch(`${SERVER_URL}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "admin", password: "admin123" }),
  });
  expect(loginRes.ok).toBe(true);
  const { accessToken } = await loginRes.json();

  const settingsRes = await fetch(`${SERVER_URL}/api/admin/settings`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({ registration_enabled: "true" }),
  });
  expect(settingsRes.ok).toBe(true);
}

/**
 * E2E tests for the registration approval workflow.
 * New accounts require admin approval before they can log in.
 *
 * These tests run without the default auth storage state so that
 * we can test the unauthenticated registration flow directly.
 */

test.describe("Registration Approval Flow", () => {
  test.use({ storageState: { cookies: [], origins: [] } });

  const testPassword = "ApprovalUser1531!";

  test.beforeEach(async () => {
    await resetServer();
    await enableRegistration();
  });

  test("registration shows pending approval message instead of logging in", async ({
    page,
  }) => {
    const username = "pendinguser";

    await page.goto("/register");
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Password", { exact: true }).fill(testPassword);
    await page.getByLabel("Confirm Password", { exact: true }).fill(testPassword);
    await page.getByRole("button", { name: /create account/i }).click();

    // Should NOT navigate to "/" — should show a pending message
    await expect(page).not.toHaveURL("/", { timeout: 5_000 });
    await expect(
      page.getByText(/pending.*approval|waiting.*admin|admin.*approve/i).first(),
    ).toBeVisible({ timeout: 5_000 });
  });

  test("pending user cannot log in", async ({ page }) => {
    const username = "pendinglogin";

    // Register via API (bypass the UI to avoid waiting for the UI message test)
    await page.goto("/");
    const regStatus = await page.evaluate(async ({ user, password }) => {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: user,
          password,
        }),
      });
      return res.status;
    }, { user: username, password: testPassword });
    // Should return 202 (pending), not 201 (auto-login)
    expect(regStatus).toBe(202);

    // Try to log in as the pending user
    await page.goto("/login");
    await page.getByLabel("Username").fill(username);
    await page.getByLabel("Password").fill(testPassword);
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
    const username = "approvaltest";

    // Step 1: Register a new user (unauthenticated context)
    const userCtx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
      baseURL: "http://localhost:5173",
    });
    const userPage = await userCtx.newPage();

    // Navigate first so the page has a URL context for relative fetch calls
    await userPage.goto("/login");

    const regStatus = await userPage.evaluate(
      async ({ uname, password }) => {
        const res = await fetch("/api/auth/register", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            username: uname,
            password,
          }),
        });
        return res.status;
      },
      { uname: username, password: testPassword },
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

    // Click "Approve" for this user and wait for the server response
    const approveResponse = adminPage.waitForResponse(
      (resp) =>
        resp.url().includes("/api/admin/users/") &&
        resp.request().method() === "PUT" &&
        resp.status() === 200,
    );
    await userRow.getByRole("button", { name: /approve/i }).click();
    await approveResponse;

    // Wait for the row to update — "pending" badge should be gone
    await expect(userRow.getByText(/pending/i)).not.toBeVisible({
      timeout: 5_000,
    });

    await adminCtx.close();

    // Step 3: Now the user should be able to log in
    await userPage.goto("/login");
    await userPage.getByLabel("Username").fill(username);
    await userPage.getByLabel("Password").fill(testPassword);
    await userPage.getByRole("button", { name: /sign in/i }).click();
    await expect(userPage).toHaveURL("/", { timeout: 10_000 });

    await userCtx.close();
  });
});
