import { test, expect, resetServer } from "./fixtures";

const arrowsLeftPrefs = {
  showPerformanceOverlay: false,
  autoSaveEnabled: true,
  autoLoadSaveEnabled: true,
  selectedShader: "none",
  selectedTheme: "default-dark",
  consoleShaders: {},
  selectedKeyMapping: "arrows-left",
  customKeyMapping: {},
  consoleKeyMappings: {},
  raLinked: false,
  raUsername: "",
  raHardcoreEnabled: false,
};

async function mockPrefsStateful(page: import("@playwright/test").Page) {
  let currentPrefs = { ...arrowsLeftPrefs };
  await page.route("**/api/user/preferences", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({ status: 200, json: currentPrefs });
    } else if (route.request().method() === "PUT") {
      const body = route.request().postDataJSON();
      currentPrefs = { ...currentPrefs, ...body };
      route.fulfill({ status: 200, json: currentPrefs });
    } else {
      route.continue();
    }
  });
}

test.describe("Key Mapping Card", () => {
  test.beforeAll(async () => {
    await resetServer();
  });

  test("displays Key Mapping heading on preferences page", async ({ page }) => {
    await page.goto("/preferences");

    await expect(
      page.getByRole("heading", { name: "Key Mapping" }),
    ).toBeVisible();
  });

  test("renders all three mode buttons", async ({ page }) => {
    await page.goto("/preferences");

    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "WASD + Arrows" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Custom" })).toBeVisible();
  });

  test("Arrows + Left is selected by default", async ({ page }) => {
    await mockPrefsStateful(page);
    await page.goto("/preferences");

    const arrowsBtn = page.getByRole("button", { name: "Arrows + Left" });
    await expect(arrowsBtn).toBeVisible();

    // The active button gets ring-2 class for the highlight ring
    await expect(arrowsBtn).toHaveClass(/ring-2/);
  });

  test("clicking a mode button updates the active visual state", async ({
    page,
  }) => {
    await mockPrefsStateful(page);
    await page.goto("/preferences");

    const arrowsBtn = page.getByRole("button", { name: "Arrows + Left" });
    const wasdBtn = page.getByRole("button", { name: "WASD + Arrows" });
    const customBtn = page.getByRole("button", { name: "Custom" });

    // Wait for preferences to load — ring-2 only appears when data is rendered
    await expect(arrowsBtn).toHaveClass(/ring-2/);
    await expect(wasdBtn).not.toHaveClass(/ring-2/);

    // Click WASD + Arrows
    await wasdBtn.click();
    await expect(wasdBtn).toHaveClass(/ring-2/);
    await expect(arrowsBtn).not.toHaveClass(/ring-2/);

    // Click Custom
    await customBtn.click();
    await expect(customBtn).toHaveClass(/ring-2/);
    await expect(wasdBtn).not.toHaveClass(/ring-2/);

    // Click back to Arrows + Left
    await arrowsBtn.click();
    await expect(arrowsBtn).toHaveClass(/ring-2/);
    await expect(customBtn).not.toHaveClass(/ring-2/);
  });
});

test.describe("Controller Visual", () => {
  test("shows correct key badges for arrows-left preset", async ({ page }) => {
    await mockPrefsStateful(page);
    await page.goto("/preferences");

    // Ensure arrows-left is active (default)
    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).toHaveClass(/ring-2/);

    // D-pad uses arrow keys: Up=↑, Down=↓, Left=←, Right=→
    const visual = page.getByTestId("controller-visual");
    await expect(visual).toBeVisible();

    // Check arrow key symbols for D-pad
    await expect(visual.getByText("↑").first()).toBeVisible();
    await expect(visual.getByText("↓").first()).toBeVisible();
    await expect(visual.getByText("←").first()).toBeVisible();
    await expect(visual.getByText("→").first()).toBeVisible();

    // Face buttons: Z, X, A, S (exact match to avoid uppercase CSS labels like D-PAD, START, FACE)
    await expect(visual.getByText("Z")).toBeVisible();
    await expect(visual.getByText("X")).toBeVisible();
    await expect(visual.getByText("A", { exact: true })).toBeVisible();
    await expect(visual.getByText("S", { exact: true })).toBeVisible();
  });

  test("shows correct key badges for wasd-arrows preset", async ({ page }) => {
    await page.goto("/preferences");

    // Switch to WASD + Arrows mode
    await page.getByRole("button", { name: "WASD + Arrows" }).click();
    await expect(
      page.getByRole("button", { name: "WASD + Arrows" }),
    ).toHaveClass(/ring-2/);

    const visual = page.getByTestId("controller-visual");
    await expect(visual).toBeVisible();

    // D-pad uses WASD: W=up, S=down, A=left, D=right (exact match to avoid D-PAD label)
    await expect(visual.getByText("W")).toBeVisible();
    await expect(visual.getByText("D", { exact: true })).toBeVisible();

    // Face buttons use arrow keys
    // Face bottom=ArrowDown(↓), Face right=ArrowRight(→),
    // Face left=ArrowLeft(←), Face top=ArrowUp(↑)
    // Arrows are already present from d-pad in arrows-left mode, but here
    // they appear in the face button section
    await expect(visual.getByText("↑")).toBeVisible();
    await expect(visual.getByText("↓")).toBeVisible();
    await expect(visual.getByText("←")).toBeVisible();
    await expect(visual.getByText("→")).toBeVisible();
  });
});

test.describe("Custom Key Mapping Editor", () => {
  test.beforeAll(async () => {
    await resetServer();
  });

  test("switching to custom mode shows the key capture editor", async ({
    page,
  }) => {
    await page.goto("/preferences");

    // The controller visual should be visible initially
    const visual = page.getByTestId("controller-visual");
    await expect(visual).toBeVisible();

    // Switch to custom mode
    await page.getByRole("button", { name: "Custom" }).click();

    // Controller visual should be gone, editor groups should appear
    await expect(visual).not.toBeVisible();
    await expect(page.getByText("D-Pad", { exact: true })).toBeVisible();
    await expect(page.getByText("Face Buttons")).toBeVisible();
  });

  test("editor has all four button groups", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("button", { name: "Custom" }).click();

    await expect(page.getByText("D-Pad", { exact: true })).toBeVisible();
    await expect(page.getByText("Face Buttons")).toBeVisible();
    await expect(page.getByText("Shoulders & Triggers")).toBeVisible();
    await expect(page.getByText("Meta", { exact: true })).toBeVisible();
  });

  test("editor has all 14 assignable buttons", async ({ page }) => {
    await page.goto("/preferences");
    await page.getByRole("button", { name: "Custom" }).click();

    // D-Pad (4)
    await expect(page.getByText("D-pad Up")).toBeVisible();
    await expect(page.getByText("D-pad Down")).toBeVisible();
    await expect(page.getByText("D-pad Left")).toBeVisible();
    await expect(page.getByText("D-pad Right")).toBeVisible();

    // Face Buttons (4)
    await expect(page.getByText("Face Bottom")).toBeVisible();
    await expect(page.getByText("Face Right")).toBeVisible();
    await expect(page.getByText("Face Left")).toBeVisible();
    await expect(page.getByText("Face Top")).toBeVisible();

    // Shoulders & Triggers (4)
    await expect(page.getByText("L1")).toBeVisible();
    await expect(page.getByText("R1")).toBeVisible();
    await expect(page.getByText("L2")).toBeVisible();
    await expect(page.getByText("R2")).toBeVisible();

    // Meta (2)
    await expect(page.getByText("Select", { exact: true })).toBeVisible();
    await expect(page.getByText("Start", { exact: true })).toBeVisible();
  });

  test("key capture: click button shows listening state, press key captures it", async ({
    page,
  }) => {
    // Mock preferences to ensure known initial state
    await mockPrefsStateful(page);
    await page.goto("/preferences");
    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).toHaveClass(/ring-2/);
    await page.getByRole("button", { name: "Custom" }).click();

    // Find the first key capture button (next to a label like "D-pad Up")
    const dpadUpRow = page
      .locator("div")
      .filter({ hasText: /^D-pad Up$/ })
      .locator("..");
    const captureBtn = dpadUpRow.locator("button");
    await expect(captureBtn).toBeVisible();

    // Before clicking, button shows a formatted key name (not "...")
    await expect(captureBtn).not.toHaveText("...");

    // Click to enter listening mode
    await captureBtn.click();
    await expect(captureBtn).toHaveText("...");

    // Press a key to capture it
    await page.keyboard.press("p");

    // Button should now show "P" (formatted via formatKeyName)
    await expect(captureBtn).toHaveText("P");
    await expect(captureBtn).not.toHaveText("...");
  });

  test("key capture: pressing Escape cancels without changing the key", async ({
    page,
  }) => {
    await page.goto("/preferences");
    await page.getByRole("button", { name: "Custom" }).click();

    const dpadUpRow = page
      .locator("div")
      .filter({ hasText: /^D-pad Up$/ })
      .locator("..");
    const captureBtn = dpadUpRow.locator("button");
    await expect(captureBtn).toBeVisible();

    // Note the current value
    const valueBefore = await captureBtn.textContent();

    // Enter listening mode
    await captureBtn.click();
    await expect(captureBtn).toHaveText("...");

    // Press Escape to cancel
    await page.keyboard.press("Escape");

    // Should revert to the original value
    await expect(captureBtn).not.toHaveText("...");
    await expect(captureBtn).toHaveText(valueBefore!);
  });
});

test.describe("Per-Console Key Mapping Overrides", () => {
  test.beforeAll(async () => {
    await resetServer();
  });

  test("override table renders with console names and dropdowns", async ({
    page,
  }) => {
    await page.goto("/preferences");

    // Find the key mapping table by its unique "Key Mapping" column header (th)
    const table = page
      .locator("table")
      .filter({ has: page.locator("th", { hasText: "Key Mapping" }) });
    await expect(table).toBeVisible();
    await expect(table.getByText("Console")).toBeVisible();
    await expect(table.getByText("Key Mapping")).toBeVisible();

    // At least one console row should appear
    const rows = table.locator("tbody tr");
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);

    // Each row has a console name and a select dropdown
    const firstRow = rows.first();
    const consoleName = await firstRow.locator("td").first().textContent();
    expect(consoleName).toBeTruthy();
    await expect(firstRow.locator("select")).toBeVisible();
  });

  test("per-console dropdown has correct options", async ({ page }) => {
    await page.goto("/preferences");

    const table = page
      .locator("table")
      .filter({ has: page.locator("th", { hasText: "Key Mapping" }) });
    const firstSelect = table.locator("select").first();
    await expect(firstSelect).toBeVisible();

    const options = await firstSelect.locator("option").allTextContents();
    expect(options).toContain("Use global default");
    expect(options).toContain("Arrows + Left");
    expect(options).toContain("WASD + Arrows");
    expect(options).toContain("Custom");
  });

  test("per-console override persists after page reload", async ({ page }) => {
    await page.goto("/preferences");

    // Wait for preferences data to render (longer timeout for full-suite load)
    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).toHaveClass(/ring-2/, { timeout: 30_000 });

    const table = page
      .locator("table")
      .filter({ has: page.locator("th", { hasText: "Key Mapping" }) });
    const firstSelect = table.locator("select").first();
    await expect(firstSelect).toBeVisible();

    // Change to "WASD + Arrows" and wait for the save to complete
    const saveResponse = page.waitForResponse(
      (resp) =>
        resp.url().includes("/user/preferences") &&
        resp.request().method() === "PUT" &&
        resp.status() === 200,
    );
    await firstSelect.selectOption("wasd-arrows");
    await expect(firstSelect).toHaveValue("wasd-arrows");
    await saveResponse;

    // Reload the page
    await page.reload();

    // Verify the value persisted
    const reloadedTable = page
      .locator("table")
      .filter({ has: page.locator("th", { hasText: "Key Mapping" }) });
    const reloadedSelect = reloadedTable.locator("select").first();
    await expect(reloadedSelect).toBeVisible();
    await expect(reloadedSelect).toHaveValue("wasd-arrows");
  });
});

test.describe("Key Mapping Persistence", () => {
  test.beforeAll(async () => {
    await resetServer();
  });

  test("global mode change persists after page reload", async ({ page }) => {
    await page.goto("/preferences");

    // Wait for preferences data to render (longer timeout for full-suite load)
    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).toHaveClass(/ring-2/, { timeout: 30_000 });

    // Switch to WASD + Arrows and wait for the PUT to complete
    const wasdBtn = page.getByRole("button", { name: "WASD + Arrows" });
    const saveResponse = page.waitForResponse(
      (resp) =>
        resp.url().includes("/user/preferences") &&
        resp.request().method() === "PUT" &&
        resp.status() === 200,
    );
    await wasdBtn.click();
    await saveResponse;
    await expect(wasdBtn).toHaveClass(/ring-2/);

    // Reload
    await page.reload();

    // Verify WASD + Arrows is still selected
    await expect(
      page.getByRole("button", { name: "WASD + Arrows" }),
    ).toHaveClass(/ring-2/);
    await expect(
      page.getByRole("button", { name: "Arrows + Left" }),
    ).not.toHaveClass(/ring-2/);
  });

  test("custom key assignment persists after page reload", async ({ page }) => {
    await page.goto("/preferences");

    // Switch to custom mode
    await page.getByRole("button", { name: "Custom" }).click();
    await expect(page.getByRole("button", { name: "Custom" })).toHaveClass(
      /ring-2/,
    );

    // Find and click the D-pad Up capture button
    const dpadUpRow = page
      .locator("div")
      .filter({ hasText: /^D-pad Up$/ })
      .locator("..");
    const captureBtn = dpadUpRow.locator("button");
    await expect(captureBtn).toBeVisible();

    // Assign "m" to D-pad Up
    await captureBtn.click();
    await expect(captureBtn).toHaveText("...");
    await page.keyboard.press("m");
    await expect(captureBtn).toHaveText("M");

    // Wait for the debounced API save
    await page.waitForTimeout(1000);

    // Reload
    await page.reload();

    // Should still be in custom mode
    await expect(page.getByRole("button", { name: "Custom" })).toHaveClass(
      /ring-2/,
    );

    // Verify the custom key assignment persisted
    const reloadedRow = page
      .locator("div")
      .filter({ hasText: /^D-pad Up$/ })
      .locator("..");
    const reloadedBtn = reloadedRow.locator("button");
    await expect(reloadedBtn).toHaveText("M");
  });
});
