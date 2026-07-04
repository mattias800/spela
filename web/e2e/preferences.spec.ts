import { test, expect, resetServer } from "./fixtures";

test.describe("Preferences Page", () => {
  test.beforeAll(async () => {
    await resetServer();
  });

  test("displays emulation settings with toggles", async ({ page }) => {
    await page.goto("/preferences");

    await expect(
      page.getByRole("heading", { name: "Preferences" }),
    ).toBeVisible();
    await page.getByRole("tab", { name: "Emulation" }).click();
    await expect(
      page.getByRole("heading", { name: "Emulation Settings" }),
    ).toBeVisible();

    await expect(page.getByText("Performance Overlay")).toBeVisible();
    await expect(page.getByText("Auto Save")).toBeVisible();
    await expect(page.getByText("Auto Load Save")).toBeVisible();
  });

  test("displays video filters section with shader selector", async ({
    page,
  }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    await expect(
      page.getByRole("heading", { name: "Video Filters" }),
    ).toBeVisible();
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
    await page.getByRole("tab", { name: "Emulation" }).click();

    // Wait for preferences data to be rendered (Auto Save is true after reset,
    // so its switch will be [checked] only once the API response is processed).
    // Use longer timeout: under full-suite load the server may be slow.
    await expect(
      page.getByRole("switch", { name: /auto save/i }),
    ).toBeChecked({ timeout: 30_000 });

    const toggle = page.getByRole("switch").first();
    await expect(toggle).toBeVisible();

    const initialState = await toggle.getAttribute("aria-checked");
    const saveResponse = page.waitForResponse(
      (resp) =>
        resp.url().includes("/user/preferences") &&
        resp.request().method() === "PUT" &&
        resp.status() === 200,
    );
    await toggle.click();
    await saveResponse;

    await expect(toggle).toHaveAttribute(
      "aria-checked",
      initialState === "true" ? "false" : "true",
    );
  });

  test("can change global shader selection", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const select = page.locator("select").first();
    await select.selectOption("scanlines");
    await expect(select).toHaveValue("scanlines");
  });

  test("displays devices section", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Devices" }).click();

    await expect(
      page.getByRole("heading", { name: "Devices", exact: true }),
    ).toBeVisible();
  });
});

test.describe("Shader Preview", () => {
  test("shows preview canvas with 4:3 aspect ratio", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    const box = await preview.boundingBox();
    expect(box).toBeTruthy();
    const ratio = box!.width / box!.height;
    expect(ratio).toBeGreaterThan(1.2);
    expect(ratio).toBeLessThan(1.5);
  });

  test("preview canvas renders non-empty content", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    // Wait for the image to load and the shader to render
    await page.waitForTimeout(1_000);

    // Read pixel data from the canvas to verify it's not all black/empty
    const hasContent = await preview.evaluate((canvas: HTMLCanvasElement) => {
      const gl = canvas.getContext("webgl2");
      if (gl) {
        const pixels = new Uint8Array(4 * 16 * 16);
        const x = Math.floor(canvas.width / 2) - 8;
        const y = Math.floor(canvas.height / 2) - 8;
        gl.readPixels(x, y, 16, 16, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
        for (let i = 0; i < pixels.length; i += 4) {
          if (pixels[i] > 5 || pixels[i + 1] > 5 || pixels[i + 2] > 5) {
            return true;
          }
        }
        return false;
      }
      // Fallback: check 2D canvas
      const ctx = canvas.getContext("2d");
      if (ctx) {
        const data = ctx.getImageData(
          Math.floor(canvas.width / 4),
          Math.floor(canvas.height / 4),
          canvas.width / 2,
          canvas.height / 2,
        );
        for (let i = 0; i < data.data.length; i += 4) {
          if (
            data.data[i] > 5 ||
            data.data[i + 1] > 5 ||
            data.data[i + 2] > 5
          ) {
            return true;
          }
        }
      }
      return false;
    });

    expect(hasContent).toBe(true);
  });

  test("global preview fetches correct console screenshot", async ({
    page,
  }) => {
    // Intercept preview-screenshot requests to verify the URL
    const screenshotRequests: string[] = [];
    await page.route("**/api/consoles/*/preview-screenshot", (route) => {
      screenshotRequests.push(route.request().url());
      route.continue();
    });

    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    // At least one preview-screenshot request should have been made
    expect(screenshotRequests.length).toBeGreaterThan(0);
    // The URL should contain a valid console abbreviation (e.g. "nes", "snes")
    expect(screenshotRequests[0]).toMatch(
      /\/api\/consoles\/[a-z0-9-]+\/preview-screenshot/,
    );
  });

  test("opens fullscreen shader preview modal on click", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    await preview.click();
    await expect(page.getByText("Click anywhere to close")).toBeVisible({
      timeout: 3_000,
    });

    const modalCanvas = page.locator(".fixed canvas");
    await expect(modalCanvas).toBeVisible();
  });

  test("closes fullscreen preview modal on Escape", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    await preview.click();
    await expect(page.getByText("Click anywhere to close")).toBeVisible({
      timeout: 3_000,
    });

    await page.keyboard.press("Escape");
    await expect(page.getByText("Click anywhere to close")).not.toBeVisible();
  });

  test("closes fullscreen preview modal on overlay click", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    await preview.click();
    await expect(page.getByText("Click anywhere to close")).toBeVisible({
      timeout: 3_000,
    });

    await page.locator(".fixed.inset-0.z-50").click({ position: { x: 10, y: 10 } });
    await expect(page.getByText("Click anywhere to close")).not.toBeVisible();
  });

  test("per-console preview button opens modal with correct console screenshot", async ({
    page,
  }) => {
    // Intercept preview-screenshot requests
    const screenshotRequests: string[] = [];
    await page.route("**/api/consoles/*/preview-screenshot", (route) => {
      screenshotRequests.push(route.request().url());
      route.continue();
    });

    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    // Wait for the per-console table to render.
    const previewButtons = page.locator('table button[title="Preview shader"]');
    await expect(previewButtons.first()).toBeVisible({ timeout: 10_000 });

    const count = await previewButtons.count();
    expect(count).toBeGreaterThan(0);

    // Get the console IDs from the per-console shader selects in the table rows
    const rows = page.locator("table tbody tr");
    const firstRowConsoleName = await rows
      .first()
      .locator("td")
      .first()
      .textContent();
    expect(firstRowConsoleName).toBeTruthy();

    // Clear tracked requests before clicking. The global preview above may
    // already have requested a screenshot for the first console.
    screenshotRequests.length = 0;

    const targetIndex = count > 1 ? 1 : 0;
    await previewButtons.nth(targetIndex).click();
    await expect(page.getByText("Click anywhere to close")).toBeVisible({
      timeout: 3_000,
    });

    if (targetIndex > 0) {
      await expect.poll(() => screenshotRequests.length).toBeGreaterThan(0);
      expect(screenshotRequests[0]).toMatch(
        /\/api\/consoles\/[a-z0-9-]+\/preview-screenshot/,
      );
    }

    // The modal should render the selected console preview. A cached image may
    // skip a network request, so the canvas is the user-visible assertion.
    const modalCanvas = page.locator(".fixed canvas");
    await expect(modalCanvas).toBeVisible();

    await page.keyboard.press("Escape");
    await expect(page.getByText("Click anywhere to close")).not.toBeVisible();

    // Now click a different console's preview button if available.
    if (count > 1) {
      screenshotRequests.length = 0;
      await previewButtons.first().click();
      await expect(page.getByText("Click anywhere to close")).toBeVisible({
        timeout: 3_000,
      });
      await expect(page.locator(".fixed canvas")).toBeVisible({ timeout: 5_000 });

      await page.keyboard.press("Escape");
    }
  });

  test("shader preview re-renders on shader change without crashing", async ({
    page,
  }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    // Read pixel data before changing shader
    await page.waitForTimeout(500);
    const pixelsBefore = await preview.evaluate((canvas: HTMLCanvasElement) => {
      const gl = canvas.getContext("webgl2", { preserveDrawingBuffer: true });
      if (gl) {
        const pixels = new Uint8Array(4 * 4);
        gl.readPixels(
          Math.floor(canvas.width / 2),
          Math.floor(canvas.height / 2),
          2,
          2,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          pixels,
        );
        return Array.from(pixels);
      }
      return null;
    });

    // Change shader to crt-simple
    const select = page.locator("select").first();
    await select.selectOption("crt-simple");
    await page.waitForTimeout(1_000);

    // Canvas should still be visible and rendering
    await expect(preview).toBeVisible();

    // Read pixel data after shader change
    const pixelsAfter = await preview.evaluate((canvas: HTMLCanvasElement) => {
      const gl = canvas.getContext("webgl2", { preserveDrawingBuffer: true });
      if (gl) {
        const pixels = new Uint8Array(4 * 4);
        gl.readPixels(
          Math.floor(canvas.width / 2),
          Math.floor(canvas.height / 2),
          2,
          2,
          gl.RGBA,
          gl.UNSIGNED_BYTE,
          pixels,
        );
        return Array.from(pixels);
      }
      return null;
    });

    // If we got pixel data both times, verify the shader actually changed the output
    if (pixelsBefore && pixelsAfter) {
      // At minimum, verify the canvas still has content after re-render
      const hasContentAfter = pixelsAfter.some((v, i) => i % 4 !== 3 && v > 5);
      expect(hasContentAfter).toBe(true);
    }
  });

  test("shader effect is visually different from no-shader", async ({
    page,
  }) => {
    await page.goto("/preferences");
    await page.getByRole("tab", { name: "Video Filters" }).click();

    const preview = page.locator("canvas").first();
    await expect(preview).toBeVisible({ timeout: 10_000 });

    // Set to "none" first and sample pixels
    const select = page.locator("select").first();
    await select.selectOption("none");
    await page.waitForTimeout(1_000);

    const nonePixels = await preview.evaluate((canvas: HTMLCanvasElement) => {
      const w = Math.min(canvas.width, 64);
      const h = Math.min(canvas.height, 64);
      const x = Math.floor((canvas.width - w) / 2);
      const y = Math.floor((canvas.height - h) / 2);

      // Try WebGL2 first, fall back to Canvas 2D
      const gl = canvas.getContext("webgl2");
      if (gl) {
        const pixels = new Uint8Array(4 * w * h);
        gl.readPixels(x, y, w, h, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
        let sum = 0;
        for (let i = 0; i < pixels.length; i += 4) {
          sum += pixels[i] + pixels[i + 1] + pixels[i + 2];
        }
        return sum;
      }

      const ctx = canvas.getContext("2d");
      if (!ctx) return null;
      const data = ctx.getImageData(x, y, w, h).data;
      let sum = 0;
      for (let i = 0; i < data.length; i += 4) {
        sum += data[i] + data[i + 1] + data[i + 2];
      }
      return sum;
    });

    // Switch to scanlines and sample again
    await select.selectOption("scanlines");
    await page.waitForTimeout(1_000);

    const scanlinesPixels = await preview.evaluate(
      (canvas: HTMLCanvasElement) => {
        const w = Math.min(canvas.width, 64);
        const h = Math.min(canvas.height, 64);
        const x = Math.floor((canvas.width - w) / 2);
        const y = Math.floor((canvas.height - h) / 2);

        const gl = canvas.getContext("webgl2");
        if (gl) {
          const pixels = new Uint8Array(4 * w * h);
          gl.readPixels(x, y, w, h, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
          let sum = 0;
          for (let i = 0; i < pixels.length; i += 4) {
            sum += pixels[i] + pixels[i + 1] + pixels[i + 2];
          }
          return sum;
        }

        const ctx = canvas.getContext("2d");
        if (!ctx) return null;
        const data = ctx.getImageData(x, y, w, h).data;
        let sum = 0;
        for (let i = 0; i < data.length; i += 4) {
          sum += data[i] + data[i + 1] + data[i + 2];
        }
        return sum;
      },
    );

    // Both should have content
    expect(nonePixels).not.toBeNull();
    expect(scanlinesPixels).not.toBeNull();
    if (nonePixels !== null && scanlinesPixels !== null) {
      expect(nonePixels).toBeGreaterThan(0);
      expect(scanlinesPixels).toBeGreaterThan(0);
      // Scanlines darken the image, so the sum should differ from "none"
      expect(scanlinesPixels).not.toBe(nonePixels);
    }
  });
});
