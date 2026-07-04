import { test, expect, resetServer } from "./fixtures";

test.describe("Admin Settings — Allow Registration", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("disabling Allow Registration persists after page reload", async ({
    page,
  }) => {
    await page.goto("/admin/settings");
    await expect(page.getByText("Allow Registration")).toBeVisible();

    // Find the Allow Registration switch (first switch in the General card)
    const registrationToggle = page
      .locator("div")
      .filter({ hasText: /^Allow Registration/ })
      .getByRole("switch")
      .first();

    // Ensure it starts enabled
    await expect(registrationToggle).toHaveAttribute("aria-checked", "true");

    // Disable it
    await registrationToggle.click();
    await expect(registrationToggle).toHaveAttribute("aria-checked", "false");

    // Save
    await page.getByRole("button", { name: /save settings/i }).click();
    await expect(page.getByText("Settings saved")).toBeVisible();

    // Reload and re-check
    await page.goto("/admin/settings");
    await expect(page.getByText("Allow Registration")).toBeVisible();

    const toggleAfterReload = page
      .locator("div")
      .filter({ hasText: /^Allow Registration/ })
      .getByRole("switch")
      .first();
    await expect(toggleAfterReload).toHaveAttribute("aria-checked", "false");
  });

  test("registration endpoint returns 403 when registration is disabled", async ({
    page,
  }) => {
    await page.goto("/admin/settings");
    // Disable registration via API (direct fetch as admin)
    const response = await page.evaluate(async () => {
      const token = localStorage.getItem("accessToken");
      const res = await fetch("/api/admin/settings", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({ registration_enabled: "false" }),
      });
      return res.ok;
    });
    expect(response).toBe(true);

    // Try to register a new user — should be forbidden
    const regResult = await page.evaluate(async () => {
      const res = await fetch("/api/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          username: "blockeduser",
          password: "BlockedUser1531!",
        }),
      });
      return { status: res.status };
    });
    expect(regResult.status).toBe(403);
  });
});
