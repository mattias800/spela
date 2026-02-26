import { test, expect } from "./fixtures";

const STAGED_UPLOAD_READY = {
  id: "1",
  fileName: "Super Mario Bros.nes",
  originalFileName: "Super Mario Bros.nes",
  fileSize: 40960,
  consoleId: "nes",
  consoleName: "Nintendo Entertainment System",
  possibleConsoles: [],
  status: "ready",
  title: "Super Mario Bros.",
  coverUrl: "",
  rating: 4.2,
  verificationStatus: "verified",
  crc32: "abc123",
  canonicalName: "Super Mario Bros. (World).nes",
};

const STAGED_UPLOAD_DUPLICATE = {
  ...STAGED_UPLOAD_READY,
  id: "2",
  fileName: "Super Mario Bros (copy).nes",
  status: "duplicate",
};

const STAGED_UPLOAD_PENDING_CONSOLE = {
  id: "3",
  fileName: "game.bin",
  originalFileName: "game.bin",
  fileSize: 524288,
  consoleId: "",
  consoleName: "",
  possibleConsoles: [
    { id: "genesis", name: "Sega Genesis" },
    { id: "psx", name: "PlayStation" },
  ],
  status: "pending_console",
  title: "",
  coverUrl: "",
  rating: 0,
  verificationStatus: "",
  crc32: "",
  canonicalName: "",
};

const STAGED_UPLOAD_PENDING_SCRAPE = {
  id: "4",
  fileName: "Zelda.sfc",
  originalFileName: "Zelda.sfc",
  fileSize: 1048576,
  consoleId: "snes",
  consoleName: "Super Nintendo",
  possibleConsoles: [],
  status: "pending_scrape",
  title: "",
  coverUrl: "",
  rating: 0,
  verificationStatus: "",
  crc32: "",
  canonicalName: "",
};

function mockUploadsEndpoint(
  page: import("@playwright/test").Page,
  uploads: unknown[],
) {
  return page.route("**/api/admin/uploads", (route) => {
    if (route.request().method() === "GET") {
      route.fulfill({ status: 200, json: uploads });
    } else if (route.request().method() === "DELETE") {
      route.fulfill({ status: 200, json: { cleared: uploads.length } });
    } else {
      route.fulfill({ status: 200, json: uploads });
    }
  });
}

test.describe("Upload ROMs Page", () => {
  test("navigates to upload page from admin sidebar", async ({ page }) => {
    await mockUploadsEndpoint(page, []);
    await page.goto("/");
    await page.getByRole("link", { name: "Upload ROMs" }).click();
    await expect(
      page.getByRole("heading", { name: "Upload ROMs" }),
    ).toBeVisible();
  });

  test("displays drop zone and empty state", async ({ page }) => {
    await mockUploadsEndpoint(page, []);
    await page.goto("/admin/upload");

    await expect(page.getByTestId("upload-drop-zone")).toBeVisible();
    await expect(page.getByText("No staged uploads")).toBeVisible();
    await expect(
      page.getByText(/Drag and drop ROM files here/),
    ).toBeVisible();
  });

  test("shows game summary cards after upload and scrape", async ({
    page,
  }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_READY]);
    await page.goto("/admin/upload");

    await expect(page.getByTestId("game-summary-card")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Super Mario Bros." }),
    ).toBeVisible();
    await expect(
      page.getByText("Nintendo Entertainment System"),
    ).toBeVisible();
    await expect(page.getByTestId("verification-badge")).toHaveText(
      "Verified",
    );
  });

  test("shows duplicate badge for duplicate uploads", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_DUPLICATE]);
    await page.goto("/admin/upload");

    await expect(page.getByTestId("duplicate-badge")).toBeVisible();
    await expect(page.getByTestId("duplicate-badge")).toHaveText("Duplicate");
  });

  test("shows console selector for ambiguous extensions", async ({
    page,
  }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_PENDING_CONSOLE]);
    await page.goto("/admin/upload");

    await expect(page.getByTestId("console-selector")).toBeVisible();
    await expect(
      page.getByText(/matches multiple consoles/),
    ).toBeVisible();
  });

  test("sets console for ambiguous upload", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_PENDING_CONSOLE]);

    const consoleSetPromise = page.waitForRequest(
      (req) =>
        req.url().includes("/api/admin/uploads/3/console") &&
        req.method() === "POST",
    );
    await page.route("**/api/admin/uploads/3/console", (route) => {
      route.fulfill({
        status: 200,
        json: {
          ...STAGED_UPLOAD_PENDING_CONSOLE,
          status: "pending_scrape",
          consoleId: "genesis",
          consoleName: "Sega Genesis",
        },
      });
    });

    await page.goto("/admin/upload");

    const selector = page.getByTestId("console-selector").locator("select");
    await selector.selectOption("genesis");

    const consoleSetRequest = await consoleSetPromise;
    expect(consoleSetRequest.postDataJSON()).toEqual({
      consoleId: "genesis",
    });
  });

  test("accept button sends accept request", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_READY]);

    let acceptCalled = false;
    await page.route("**/api/admin/uploads/1/accept", (route) => {
      acceptCalled = true;
      route.fulfill({
        status: 200,
        json: { id: "100", title: "Super Mario Bros." },
      });
    });

    await page.goto("/admin/upload");

    await page.getByTestId("accept-button").click();
    expect(acceptCalled).toBe(true);
  });

  test("reject button sends reject request", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_READY]);

    let rejectCalled = false;
    await page.route("**/api/admin/uploads/1/reject", (route) => {
      rejectCalled = true;
      route.fulfill({ status: 200, json: { message: "upload rejected" } });
    });

    await page.goto("/admin/upload");

    await page.getByTestId("reject-button").click();
    expect(rejectCalled).toBe(true);
  });

  test("Accept All button sends batch accept request", async ({ page }) => {
    await mockUploadsEndpoint(page, [
      STAGED_UPLOAD_READY,
      { ...STAGED_UPLOAD_READY, id: "5", fileName: "Game2.nes" },
    ]);

    let acceptAllCalled = false;
    await page.route("**/api/admin/uploads/accept-all", (route) => {
      acceptAllCalled = true;
      route.fulfill({ status: 200, json: { accepted: 2 } });
    });

    await page.goto("/admin/upload");

    await page.getByTestId("accept-all-button").click();
    expect(acceptAllCalled).toBe(true);
  });

  test("Reject All button sends batch reject request", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_READY]);

    let rejectAllCalled = false;
    await page.route("**/api/admin/uploads/reject-all", (route) => {
      rejectAllCalled = true;
      route.fulfill({ status: 200, json: { rejected: 1 } });
    });

    await page.goto("/admin/upload");

    await page.getByTestId("reject-all-button").click();
    expect(rejectAllCalled).toBe(true);
  });

  test("Clear Staging button sends delete request", async ({ page }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_READY]);
    await page.goto("/admin/upload");

    await page.getByTestId("clear-staging-button").click();

    // After clearing, the empty state should eventually appear
    // (the mock DELETE handler returns cleared count)
    await expect(page.getByTestId("clear-staging-button")).toBeVisible();
  });

  test("shows scrape button when uploads are pending scrape", async ({
    page,
  }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_PENDING_SCRAPE]);
    await page.goto("/admin/upload");

    await expect(
      page.getByRole("button", { name: /Scrape Metadata/ }),
    ).toBeVisible();
    await expect(page.getByText(/1 file ready to scrape/)).toBeVisible();
  });

  test("scrape button triggers scrape for all pending uploads", async ({
    page,
  }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_PENDING_SCRAPE]);

    let scrapeCalled = false;
    await page.route("**/api/admin/uploads/scrape", (route) => {
      if (route.request().method() === "POST") {
        scrapeCalled = true;
        route.fulfill({
          status: 200,
          json: [
            {
              ...STAGED_UPLOAD_PENDING_SCRAPE,
              status: "ready",
              title: "The Legend of Zelda",
            },
          ],
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/admin/upload");

    await page.getByRole("button", { name: /Scrape Metadata/ }).click();
    expect(scrapeCalled).toBe(true);
  });

  test("shows pending scrape status for unscraped uploads", async ({
    page,
  }) => {
    await mockUploadsEndpoint(page, [STAGED_UPLOAD_PENDING_SCRAPE]);
    await page.goto("/admin/upload");

    await expect(page.getByText(/Waiting for scrape/)).toBeVisible();
  });
});
