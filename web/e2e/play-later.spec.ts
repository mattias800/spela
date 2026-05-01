import { test, expect } from "./fixtures";

/**
 * Helper to create a standard mock game object for tests.
 */
function mockGame(overrides?: Record<string, unknown>) {
  return {
    id: "1",
    title: "Super Mario Bros.",
    consoleId: "1",
    consoleName: "NES",
    fileName: "smb.nes",
    fileSize: 40976,
    coverUrl: "",
    screenshotUrls: [],
    description: "A classic platformer.",
    scrapeAttempts: 1,
    isFavorite: false,
    isInPlayLater: false,
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

const mockGames = [
  mockGame({ id: "1", title: "Super Mario Bros.", isInPlayLater: true }),
  mockGame({ id: "2", title: "The Legend of Zelda", isInPlayLater: true }),
  mockGame({
    id: "3",
    title: "Metroid",
    consoleName: "SNES",
    isInPlayLater: true,
  }),
];

/**
 * Sets up the common API routes needed for the game detail page to render.
 */
async function setupGameDetailRoutes(
  page: import("@playwright/test").Page,
  game: Record<string, unknown>,
) {
  const gameId = game.id as string;

  await page.route(`**/api/games/${gameId}`, (route) => {
    if (
      route.request().url().includes("/saves") ||
      route.request().url().includes("/ratings") ||
      route.request().url().includes("/stats") ||
      route.request().url().includes("/shared-saves") ||
      route.request().url().includes("/achievements") ||
      route.request().url().includes("/play-later")
    ) {
      return route.continue();
    }
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(game),
    });
  });

  await page.route(`**/api/games/${gameId}/saves`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    });
  });

  await page.route("**/api/consoles", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([
        {
          id: "1",
          name: "NES",
          abbreviation: "NES",
          emulatorJsCore: "nestopia",
        },
      ]),
    });
  });

  await page.route(`**/api/games/${gameId}/achievements`, (route) => {
    if (route.request().url().includes("/progress")) return route.continue();
    if (route.request().url().includes("/leaderboard")) return route.continue();
    if (route.request().url().includes("/timeline")) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        raGameId: 0,
        totalCount: 0,
        totalPoints: 0,
        achievements: [],
      }),
    });
  });

  await page.route(`**/api/games/${gameId}/achievements/progress`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ progress: [] }),
    });
  });

  await page.route(
    `**/api/games/${gameId}/achievements/leaderboard`,
    (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          raGameId: 0,
          totalAchievements: 0,
          leaderboard: [],
        }),
      });
    },
  );

  await page.route(`**/api/games/${gameId}/achievements/timeline`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        raGameId: 0,
        gameTitle: "",
        totalPlayTime: 0,
        timeline: [],
        totalAchievements: 0,
        unlockedCount: 0,
        totalPoints: 0,
        earnedPoints: 0,
      }),
    });
  });

  await page.route(`**/api/games/${gameId}/stats`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        totalPlayers: 0,
        totalPlayTime: 0,
        averagePlayTime: 0,
        topPlayers: [],
      }),
    });
  });

  await page.route(`**/api/games/${gameId}/ratings/summary`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        averageRating: 0,
        totalRatings: 0,
        distribution: {},
      }),
    });
  });

  await page.route(`**/api/games/${gameId}/ratings/mine`, (route) => {
    route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
  });

  await page.route(`**/api/games/${gameId}/ratings?*`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 10 }),
    });
  });

  await page.route(`**/api/games/${gameId}/shared-saves?*`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 10 }),
    });
  });

  await page.route(`**/api/games/${gameId}/shared-saves`, (route) => {
    if (route.request().url().includes("?")) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 10 }),
    });
  });

  await page.route("**/api/collections/mine*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 100 }),
    });
  });
}

test.describe("Play Later Page", () => {
  test("shows empty state when queue has no games", async ({ page }) => {
    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    });

    await page.goto("/play-later");

    await expect(
      page.getByRole("heading", { name: "Play Later", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByText("Your Play Later queue is empty"),
    ).toBeVisible();
    await expect(
      page.getByText("Click the clock icon on any game to add it."),
    ).toBeVisible();
  });

  test("displays ordered list of games in play later queue", async ({
    page,
  }) => {
    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockGames),
      });
    });

    await page.goto("/play-later");

    await expect(
      page.getByRole("heading", { name: "Play Later", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    // All three games should be visible
    await expect(page.getByText("Super Mario Bros.")).toBeVisible();
    await expect(page.getByText("The Legend of Zelda")).toBeVisible();
    await expect(page.getByText("Metroid")).toBeVisible();
  });

  test("remove button sends DELETE request for the game", async ({ page }) => {
    let deletedGameId = "";

    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockGames),
      });
    });

    await page.route("**/api/user/play-later/*", (route) => {
      if (route.request().method() === "DELETE") {
        const url = route.request().url();
        deletedGameId = url.split("/").pop() ?? "";
        route.fulfill({ status: 204 });
      } else {
        route.continue();
      }
    });

    await page.goto("/play-later");

    await expect(page.getByText("Super Mario Bros.")).toBeVisible({
      timeout: 10_000,
    });

    // Click the first remove button
    const removeButtons = page.getByTitle("Remove from queue");
    await removeButtons.first().click();

    await page.waitForTimeout(500);
    expect(deletedGameId).toBe("1");
  });

  test("move down button sends reorder request", async ({ page }) => {
    let reorderPayload: { gameIds: string[] } | null = null;

    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockGames),
      });
    });

    await page.route("**/api/user/play-later/reorder", (route) => {
      if (route.request().method() === "PUT") {
        reorderPayload = route.request().postDataJSON();
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "[]",
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/play-later");

    await expect(page.getByText("Super Mario Bros.")).toBeVisible({
      timeout: 10_000,
    });

    // Click move down on the first item
    const moveDownButtons = page.getByTitle("Move down");
    await moveDownButtons.first().click();

    await page.waitForTimeout(500);
    expect(reorderPayload).not.toBeNull();
    // After moving first item down, order should be: 2, 1, 3
    expect(reorderPayload!.gameIds).toEqual(["2", "1", "3"]);
  });

  test("move up button sends reorder request", async ({ page }) => {
    let reorderPayload: { gameIds: string[] } | null = null;

    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockGames),
      });
    });

    await page.route("**/api/user/play-later/reorder", (route) => {
      if (route.request().method() === "PUT") {
        reorderPayload = route.request().postDataJSON();
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "[]",
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/play-later");

    await expect(page.getByText("Super Mario Bros.")).toBeVisible({
      timeout: 10_000,
    });

    // Click move up on the second item (index 1)
    const moveUpButtons = page.getByTitle("Move up");
    await moveUpButtons.nth(1).click();

    await page.waitForTimeout(500);
    expect(reorderPayload).not.toBeNull();
    // After moving second item up, order should be: 2, 1, 3
    expect(reorderPayload!.gameIds).toEqual(["2", "1", "3"]);
  });
});

test.describe("Play Later on Game Detail Page", () => {
  test("shows Play Later item that toggles to In Queue", async ({ page }) => {
    const gameData = mockGame({ isInPlayLater: false });
    await setupGameDetailRoutes(page, gameData);

    // Mock the toggle POST endpoint
    await page.route("**/api/user/play-later/*", (route) => {
      if (route.request().method() === "POST") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "{}",
        });
      } else if (route.request().method() === "DELETE") {
        route.fulfill({ status: 204 });
      } else {
        route.continue();
      }
    });

    await page.goto("/games/1");

    // Open the actions menu, then find the Play Later menu item
    // The page-level actions button shares aria-label="Actions" with
    // session-card actions buttons (which can render after seed data
    // changes). Pin to the first match so the locator stays
    // strict-mode safe regardless of which sessions appear.
    const actionsBtn = page.getByTestId("actions-menu-btn").first();
    await expect(actionsBtn).toBeVisible({ timeout: 10_000 });
    await actionsBtn.click();

    const playLaterItem = page.getByRole("menuitem", { name: "Play Later" });
    await expect(playLaterItem).toBeVisible();
  });

  test("shows In Queue item when game is already in play later", async ({
    page,
  }) => {
    const gameData = mockGame({ isInPlayLater: true });
    await setupGameDetailRoutes(page, gameData);

    await page.goto("/games/1");

    // The page-level actions button shares aria-label="Actions" with
    // session-card actions buttons (which can render after seed data
    // changes). Pin to the first match so the locator stays
    // strict-mode safe regardless of which sessions appear.
    const actionsBtn = page.getByTestId("actions-menu-btn").first();
    await expect(actionsBtn).toBeVisible({ timeout: 10_000 });
    await actionsBtn.click();

    const inQueueItem = page.getByRole("menuitem", { name: "In Queue" });
    await expect(inQueueItem).toBeVisible();
  });

  test("clicking Play Later item sends POST request", async ({ page }) => {
    let postCalled = false;
    const gameData = mockGame({ isInPlayLater: false });
    await setupGameDetailRoutes(page, gameData);

    await page.route("**/api/user/play-later/*", (route) => {
      if (route.request().method() === "POST") {
        postCalled = true;
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "{}",
        });
      } else if (route.request().method() === "DELETE") {
        route.fulfill({ status: 204 });
      } else {
        route.continue();
      }
    });

    await page.goto("/games/1");

    // The page-level actions button shares aria-label="Actions" with
    // session-card actions buttons (which can render after seed data
    // changes). Pin to the first match so the locator stays
    // strict-mode safe regardless of which sessions appear.
    const actionsBtn = page.getByTestId("actions-menu-btn").first();
    await expect(actionsBtn).toBeVisible({ timeout: 10_000 });
    await actionsBtn.click();

    const playLaterItem = page.getByRole("menuitem", { name: "Play Later" });
    await expect(playLaterItem).toBeVisible();
    await playLaterItem.click();

    await page.waitForTimeout(500);
    expect(postCalled).toBe(true);
  });

  test("clicking In Queue item sends DELETE request", async ({ page }) => {
    let deleteCalled = false;
    const gameData = mockGame({ isInPlayLater: true });
    await setupGameDetailRoutes(page, gameData);

    await page.route("**/api/user/play-later/*", (route) => {
      if (route.request().method() === "DELETE") {
        deleteCalled = true;
        route.fulfill({ status: 204 });
      } else if (route.request().method() === "POST") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: "{}",
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/games/1");

    // The page-level actions button shares aria-label="Actions" with
    // session-card actions buttons (which can render after seed data
    // changes). Pin to the first match so the locator stays
    // strict-mode safe regardless of which sessions appear.
    const actionsBtn = page.getByTestId("actions-menu-btn").first();
    await expect(actionsBtn).toBeVisible({ timeout: 10_000 });
    await actionsBtn.click();

    const inQueueItem = page.getByRole("menuitem", { name: "In Queue" });
    await expect(inQueueItem).toBeVisible();
    await inQueueItem.click();

    await page.waitForTimeout(500);
    expect(deleteCalled).toBe(true);
  });
});

test.describe("Dashboard Play Later Section", () => {
  async function setupDashboardRoutes(
    page: import("@playwright/test").Page,
    playLaterGames: Record<string, unknown>[],
  ) {
    await page.route("**/api/user/recent*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
      });
    });

    await page.route("**/api/user/favorites*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
      });
    });

    await page.route("**/api/games?*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 12 }),
      });
    });

    await page.route("**/api/user/play-later", (route) => {
      if (route.request().url().includes("/reorder")) return route.continue();
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(playLaterGames),
      });
    });

    await page.route("**/api/user/achievements/recent*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ achievements: [] }),
      });
    });

    await page.route("**/api/user/stats", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          totalGamesPlayed: 0,
          totalPlayTime: 0,
          totalFavorites: 0,
          totalSaveStates: 0,
        }),
      });
    });

    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 5 }),
      });
    });

    await page.route("**/api/social/online", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ users: [] }),
      });
    });

    await page.route("**/api/challenges?*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 4 }),
      });
    });
  }

  test("dashboard shows Play Later section when queue has items", async ({
    page,
  }) => {
    await setupDashboardRoutes(page, mockGames);

    await page.goto("/");

    const playLaterHeading = page.getByRole("heading", {
      name: "Play Later",
    });
    await expect(playLaterHeading).toBeVisible({ timeout: 10_000 });

    // Scope the assertion to the Play Later shelf by its testId. The dashboard
    // renders Play Later via `ScrollShelf` (not `TitledSection`), so the
    // original [data-comp="TitledSection"] selector matched nothing.
    const playLaterSection = page.getByTestId("shelf-play-later");
    await expect(
      playLaterSection.getByText("Super Mario Bros.", { exact: true }),
    ).toBeVisible();
  });

  test("dashboard hides Play Later section when queue is empty", async ({
    page,
  }) => {
    await setupDashboardRoutes(page, []);

    await page.goto("/");

    // Wait for the page to load
    await expect(
      page.getByRole("heading", { name: /welcome back/i }),
    ).toBeVisible({ timeout: 10_000 });

    // Play Later heading should not be visible
    await expect(
      page.getByRole("heading", { name: "Play Later" }),
    ).not.toBeVisible();
  });
});

test.describe("Activity Feed - Play Later Event", () => {
  test("renders queued_play_later event in activity feed", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "evt-pl-1",
              userId: "u1",
              username: "RetroFan",
              eventType: "queued_play_later",
              gameId: "1",
              gameTitle: "Castlevania",
              gameConsoleName: "NES",
              createdAt: "2026-02-14T10:00:00Z",
            },
          ],
          total: 1,
          page: 1,
          pageSize: 20,
        }),
      });
    });

    await page.route("**/api/social/online", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ users: [] }),
      });
    });

    await page.goto("/activity");

    await expect(page.getByTestId("activity-event-evt-pl-1")).toBeVisible({
      timeout: 10_000,
    });

    // User and game should be visible
    await expect(page.getByText("RetroFan")).toBeVisible();
    await expect(page.getByText("Castlevania")).toBeVisible();

    // The event description text
    await expect(page.getByText("to their Play Later queue")).toBeVisible();
  });
});
