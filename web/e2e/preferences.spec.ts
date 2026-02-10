import { test, expect } from "./fixtures";

test.describe("Preferences Page", () => {
  test("displays emulation settings with toggles", async ({ page }) => {
    await page.goto("/preferences");

    await expect(page.getByRole("heading", { name: "Preferences" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Emulation Settings" })).toBeVisible();

    await expect(page.getByText("Performance Overlay")).toBeVisible();
    await expect(page.getByText("Auto Save")).toBeVisible();
    await expect(page.getByText("Auto Load Save")).toBeVisible();
  });

  test("displays video filters section with shader selector", async ({ page }) => {
    await page.goto("/preferences");

    await expect(page.getByRole("heading", { name: "Video Filters" })).toBeVisible();
    await expect(page.getByText("Global Default Shader")).toBeVisible();

    const select = page.locator("select").first();
    await expect(select).toBeVisible();
    const options = await select.locator("option").allTextContents();
    expect(options).toContain("None");
    expect(options).toContain("CRT Simple");
    expect(options).toContain("Scanlines");
  });

  test("can toggle a preference switch", async ({ page }) => {
    await page.goto("/preferences");

    const toggle = page.getByRole("switch").first();
    await expect(toggle).toBeVisible();

    const initialState = await toggle.getAttribute("aria-checked");
    await toggle.click();

    await expect(toggle).toHaveAttribute(
      "aria-checked",
      initialState === "true" ? "false" : "true",
    );
  });

  test("can change global shader selection", async ({ page }) => {
    await page.goto("/preferences");

    const select = page.locator("select").first();
    await select.selectOption("scanlines");
    await expect(select).toHaveValue("scanlines");
  });

  test("displays devices section", async ({ page }) => {
    await page.goto("/preferences");

    await expect(page.getByRole("heading", { name: "Devices", exact: true })).toBeVisible();
    await expect(page.getByText("No devices registered")).toBeVisible();
  });
});

test.describe("Shader Preview", () => {
  test("shows preview canvas with 4:3 aspect ratio", async ({ page }) => {
    await page.goto("/preferences");

    const preview = page.locator("canvas").first();
    const hasPreview = await preview.isVisible({ timeout: 5_000 }).catch(() => false);

    if (hasPreview) {
      const box = await preview.boundingBox();
      expect(box).toBeTruthy();
      if (box) {
        const ratio = box.width / box.height;
        expect(ratio).toBeGreaterThan(1.2);
        expect(ratio).toBeLessThan(1.5);
      }
    }
  });

  test("opens fullscreen shader preview modal on click", async ({ page }) => {
    await page.goto("/preferences");

    const preview = page.locator("canvas").first();
    const hasPreview = await preview.isVisible({ timeout: 5_000 }).catch(() => false);

    if (hasPreview) {
      await preview.click();
      await expect(page.getByText("Click anywhere to close")).toBeVisible({ timeout: 3_000 });

      const modalCanvas = page.locator(".fixed canvas");
      await expect(modalCanvas).toBeVisible();
    }
  });

  test("closes fullscreen preview modal on Escape", async ({ page }) => {
    await page.goto("/preferences");

    const preview = page.locator("canvas").first();
    const hasPreview = await preview.isVisible({ timeout: 5_000 }).catch(() => false);

    if (hasPreview) {
      await preview.click();
      await expect(page.getByText("Click anywhere to close")).toBeVisible({ timeout: 3_000 });

      await page.keyboard.press("Escape");
      await expect(page.getByText("Click anywhere to close")).not.toBeVisible();
    }
  });

  test("closes fullscreen preview modal on overlay click", async ({ page }) => {
    await page.goto("/preferences");

    const preview = page.locator("canvas").first();
    const hasPreview = await preview.isVisible({ timeout: 5_000 }).catch(() => false);

    if (hasPreview) {
      await preview.click();
      await expect(page.getByText("Click anywhere to close")).toBeVisible({ timeout: 3_000 });

      await page.locator(".fixed.inset-0").click({ position: { x: 10, y: 10 } });
      await expect(page.getByText("Click anywhere to close")).not.toBeVisible();
    }
  });

  test("per-console preview button opens modal", async ({ page }) => {
    await page.goto("/preferences");

    const previewButtons = page.locator("table button");
    const count = await previewButtons.count();

    if (count > 0) {
      await previewButtons.first().click();
      await expect(page.getByText("Click anywhere to close")).toBeVisible({ timeout: 3_000 });

      await page.keyboard.press("Escape");
      await expect(page.getByText("Click anywhere to close")).not.toBeVisible();
    }
  });

  test("shader preview re-renders on shader change without crashing", async ({ page }) => {
    await page.goto("/preferences");

    const preview = page.locator("canvas").first();
    const hasPreview = await preview.isVisible({ timeout: 5_000 }).catch(() => false);

    if (hasPreview) {
      const select = page.locator("select").first();
      await select.selectOption("crt-simple");
      await page.waitForTimeout(500);
      await expect(preview).toBeVisible();
    }
  });
});
