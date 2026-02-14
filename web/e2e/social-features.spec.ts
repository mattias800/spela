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
    averageRating: 0,
    ratingCount: 0,
    totalPlayTime: 0,
    createdAt: "2025-01-01T00:00:00Z",
    updatedAt: "2025-01-01T00:00:00Z",
    ...overrides,
  };
}

/**
 * Sets up the common API routes needed for the game detail page to render.
 */
async function setupGameDetailRoutes(
  page: import("@playwright/test").Page,
  {
    game,
    saves,
    consoles,
    achievements,
    achievementsProgress,
    stats,
    ratingSummary,
    myRating,
    ratings,
    sharedSaves,
  }: {
    game?: Record<string, unknown>;
    saves?: unknown[];
    consoles?: unknown[];
    achievements?: Record<string, unknown>;
    achievementsProgress?: unknown[];
    stats?: Record<string, unknown>;
    ratingSummary?: Record<string, unknown>;
    myRating?: Record<string, unknown> | null;
    ratings?: Record<string, unknown>;
    sharedSaves?: Record<string, unknown>;
  } = {},
) {
  const gameData = game ?? mockGame();
  const gameId = gameData.id as string;

  await page.route(`**/api/games/${gameId}`, (route) => {
    if (route.request().url().includes("/saves") || route.request().url().includes("/ratings") || route.request().url().includes("/stats") || route.request().url().includes("/shared-saves") || route.request().url().includes("/achievements")) {
      return route.continue();
    }
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(gameData),
    });
  });

  await page.route(`**/api/games/${gameId}/saves`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(saves ?? []),
    });
  });

  await page.route("**/api/consoles", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        consoles ?? [
          { id: "1", name: "NES", abbreviation: "NES", emulatorJsCore: "nestopia" },
        ],
      ),
    });
  });

  await page.route(`**/api/games/${gameId}/achievements`, (route) => {
    if (route.request().url().includes("/progress")) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        achievements ?? { raGameId: 0, totalCount: 0, totalPoints: 0, achievements: [] },
      ),
    });
  });

  await page.route(`**/api/games/${gameId}/achievements/progress`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(achievementsProgress ?? { progress: [] }),
    });
  });

  await page.route(`**/api/games/${gameId}/stats`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        stats ?? { totalPlayers: 0, totalPlayTime: 0, averagePlayTime: 0, topPlayers: [] },
      ),
    });
  });

  await page.route(`**/api/games/${gameId}/ratings/summary`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        ratingSummary ?? { averageRating: 0, totalRatings: 0, distribution: {} },
      ),
    });
  });

  await page.route(`**/api/games/${gameId}/ratings/mine`, (route) => {
    if (myRating === null) {
      route.fulfill({ status: 404, contentType: "application/json", body: "{}" });
    } else {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          myRating ?? { id: "", userId: "", username: "", gameId, rating: 0, createdAt: "", updatedAt: "" },
        ),
      });
    }
  });

  await page.route(`**/api/games/${gameId}/ratings?*`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        ratings ?? { data: [], total: 0, page: 1, pageSize: 10 },
      ),
    });
  });

  // Match shared-saves with query params
  await page.route(`**/api/games/${gameId}/shared-saves?*`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        sharedSaves ?? { data: [], total: 0, page: 1, pageSize: 10 },
      ),
    });
  });

  // Also match shared-saves without query params
  await page.route(`**/api/games/${gameId}/shared-saves`, (route) => {
    if (route.request().url().includes("?")) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        sharedSaves ?? { data: [], total: 0, page: 1, pageSize: 10 },
      ),
    });
  });

  // Mock the achievement leaderboard endpoint
  await page.route(`**/api/games/${gameId}/achievements/leaderboard`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ raGameId: 0, totalAchievements: 0, leaderboard: [] }),
    });
  });

  // Mock the achievement timeline endpoint
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
}

test.describe("Activity Feed Page", () => {
  test("renders activity page with heading and events", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "evt-1",
              userId: "u1",
              username: "PlayerOne",
              eventType: "started_playing",
              gameId: "1",
              gameTitle: "Zelda",
              gameConsoleName: "NES",
              createdAt: "2026-02-14T10:00:00Z",
            },
            {
              id: "evt-2",
              userId: "u2",
              username: "PlayerTwo",
              eventType: "favorited_game",
              gameId: "2",
              gameTitle: "Metroid",
              gameConsoleName: "SNES",
              createdAt: "2026-02-14T09:00:00Z",
            },
          ],
          total: 2,
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

    await expect(
      page.getByRole("heading", { name: "Activity", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByText("See what everyone has been up to."),
    ).toBeVisible();

    // Activity events should be visible
    await expect(page.getByTestId("activity-event-evt-1")).toBeVisible();
    await expect(page.getByTestId("activity-event-evt-2")).toBeVisible();

    // User info should be present
    await expect(page.getByText("PlayerOne")).toBeVisible();
    await expect(page.getByText("PlayerTwo")).toBeVisible();

    // Game links should be present
    await expect(page.getByText("Zelda")).toBeVisible();
    await expect(page.getByText("Metroid")).toBeVisible();
  });

  test("shows empty state when no activity", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 20 }),
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

    await expect(page.getByText("No activity yet")).toBeVisible({
      timeout: 10_000,
    });
  });

  test("shows online users sidebar on activity page", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 20 }),
      });
    });

    await page.route("**/api/social/online", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          users: [
            {
              id: "u1",
              username: "ActivePlayer",
              currentGame: { id: "1", title: "Mario", consoleName: "NES" },
            },
          ],
        }),
      });
    });

    await page.goto("/activity");

    await expect(page.getByTestId("online-users-widget")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByRole("heading", { name: "Online Now" }),
    ).toBeVisible();
    await expect(page.getByText("ActivePlayer")).toBeVisible();
  });

  test("pagination controls are visible for multi-page feeds", async ({
    page,
  }) => {
    const events = Array.from({ length: 20 }, (_, i) => ({
      id: `evt-${i}`,
      userId: "u1",
      username: "Player",
      eventType: "started_playing" as const,
      gameId: "1",
      gameTitle: "Game",
      gameConsoleName: "NES",
      createdAt: "2026-02-14T10:00:00Z",
    }));

    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: events,
          total: 40,
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

    await expect(page.getByTestId("activity-event-evt-0")).toBeVisible({
      timeout: 10_000,
    });

    // Pagination should be visible
    await expect(
      page.getByRole("button", { name: "Previous" }),
    ).toBeVisible();
    await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

    // Previous should be disabled on first page
    await expect(
      page.getByRole("button", { name: "Previous" }),
    ).toBeDisabled();

    // Next should be enabled since there are more pages
    await expect(page.getByRole("button", { name: "Next" })).toBeEnabled();

    // Page info should be shown
    await expect(page.getByText("Page 1 of 2")).toBeVisible();
  });
});

test.describe("Dashboard Social Widgets", () => {
  test("dashboard shows Recent Activity and Online Now sections", async ({
    page,
  }) => {
    // Mock all dashboard APIs
    await page.route("**/api/games/recent*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
      });
    });

    await page.route("**/api/games/favorites*", (route) => {
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

    await page.route("**/api/user/achievements/recent*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
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
        body: JSON.stringify({
          data: [
            {
              id: "evt-dash-1",
              userId: "u1",
              username: "DashPlayer",
              eventType: "rated_game",
              gameId: "1",
              gameTitle: "Contra",
              gameConsoleName: "NES",
              metadata: { rating: 4 },
              createdAt: "2026-02-14T08:00:00Z",
            },
          ],
          total: 1,
          page: 1,
          pageSize: 5,
        }),
      });
    });

    await page.route("**/api/social/online", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          users: [
            {
              id: "u2",
              username: "OnlineGamer",
              currentGame: { id: "3", title: "Tetris", consoleName: "GB" },
            },
          ],
        }),
      });
    });

    await page.goto("/");

    // Activity feed widget should be visible
    await expect(page.getByTestId("activity-feed")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByRole("heading", { name: "Recent Activity" }),
    ).toBeVisible();

    // Online users widget should be visible
    await expect(page.getByTestId("online-users-widget")).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "Online Now" }),
    ).toBeVisible();

    // Actual content should render
    await expect(page.getByText("DashPlayer")).toBeVisible();
    await expect(page.getByText("OnlineGamer")).toBeVisible();
    await expect(page.getByText("Tetris")).toBeVisible();
  });
});

test.describe("Game Ratings", () => {
  test("renders star rating component on game detail page", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, { myRating: null });

    await page.goto("/games/1");

    await expect(page.getByTestId("user-rating")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("star-rating").first()).toBeVisible();

    // All 5 star buttons should be present
    for (let i = 1; i <= 5; i++) {
      await expect(page.getByTestId(`star-${i}`).first()).toBeVisible();
    }
  });

  test("clicking a star submits a rating", async ({ page }) => {
    let submittedRating = 0;

    await setupGameDetailRoutes(page, { myRating: null });

    // Intercept rating POST
    await page.route("**/api/games/1/ratings", (route) => {
      if (route.request().method() === "POST") {
        const body = route.request().postDataJSON();
        submittedRating = body.rating;
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "rating-1",
            userId: "admin-id",
            username: "admin",
            gameId: "1",
            rating: body.rating,
            createdAt: "2026-02-14T10:00:00Z",
            updatedAt: "2026-02-14T10:00:00Z",
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("user-rating")).toBeVisible({
      timeout: 10_000,
    });

    // Click the 4th star
    await page.getByTestId("user-rating").getByTestId("star-4").click();

    // Verify the POST was made with rating 4
    await page.waitForTimeout(500);
    expect(submittedRating).toBe(4);
  });

  test("clicking a different star changes the rating", async ({ page }) => {
    const submittedRatings: number[] = [];

    await setupGameDetailRoutes(page, {
      myRating: {
        id: "rating-1",
        userId: "admin-id",
        username: "admin",
        gameId: "1",
        rating: 3,
        createdAt: "2026-02-14T10:00:00Z",
        updatedAt: "2026-02-14T10:00:00Z",
      },
    });

    await page.route("**/api/games/1/ratings", (route) => {
      if (route.request().method() === "POST") {
        const body = route.request().postDataJSON();
        submittedRatings.push(body.rating);
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            id: "rating-1",
            userId: "admin-id",
            username: "admin",
            gameId: "1",
            rating: body.rating,
            createdAt: "2026-02-14T10:00:00Z",
            updatedAt: "2026-02-14T10:00:00Z",
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("user-rating")).toBeVisible({
      timeout: 10_000,
    });

    // Change from 3 to 5
    await page.getByTestId("user-rating").getByTestId("star-5").click();

    await page.waitForTimeout(500);
    expect(submittedRatings).toContain(5);
  });

  test("displays rating summary with average and distribution", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, {
      game: mockGame({ averageRating: 4.2, ratingCount: 15 }),
      ratingSummary: {
        averageRating: 4.2,
        totalRatings: 15,
        distribution: { "1": 1, "2": 0, "3": 2, "4": 5, "5": 7 },
      },
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("rating-summary")).toBeVisible({
      timeout: 10_000,
    });

    // Average rating value should be visible
    await expect(
      page.getByTestId("rating-summary").getByText("4.2"),
    ).toBeVisible();

    // Total ratings count
    await expect(page.getByText("15 ratings")).toBeVisible();
  });

  test("rating summary shows empty state when no ratings", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, {
      ratingSummary: {
        averageRating: 0,
        totalRatings: 0,
        distribution: {},
      },
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("rating-summary")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("No ratings yet")).toBeVisible();
  });
});

test.describe("Shared Saves (Community Saves)", () => {
  test("renders Community Saves section on game detail page", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page);

    await page.goto("/games/1");

    await expect(page.getByTestId("shared-saves-list")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByRole("heading", { name: "Community Saves" }),
    ).toBeVisible();
  });

  test("shows empty state when no shared saves exist", async ({ page }) => {
    await setupGameDetailRoutes(page, {
      sharedSaves: { data: [], total: 0, page: 1, pageSize: 10 },
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("shared-saves-list")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("No shared saves")).toBeVisible();
    await expect(
      page.getByText("Share your save states so others can enjoy them too."),
    ).toBeVisible();
  });

  test("displays shared saves with user info and metadata", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, {
      sharedSaves: {
        data: [
          {
            id: "ss-1",
            userId: "u1",
            username: "SpeedRunner",
            gameId: "1",
            name: "World 8 Warpless",
            description: "All worlds completed without warps",
            fileSize: 8192,
            downloadCount: 42,
            createdAt: "2026-02-13T10:00:00Z",
          },
          {
            id: "ss-2",
            userId: "u2",
            username: "CasualGamer",
            gameId: "1",
            name: "World 4 Start",
            fileSize: 4096,
            downloadCount: 5,
            createdAt: "2026-02-12T15:30:00Z",
          },
        ],
        total: 2,
        page: 1,
        pageSize: 10,
      },
    });

    await page.goto("/games/1");

    // Both shared saves should be visible
    await expect(page.getByTestId("shared-save-ss-1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByTestId("shared-save-ss-2")).toBeVisible();

    // Names should be visible
    await expect(page.getByText("World 8 Warpless")).toBeVisible();
    await expect(page.getByText("World 4 Start")).toBeVisible();

    // Usernames
    await expect(page.getByText("SpeedRunner")).toBeVisible();
    await expect(page.getByText("CasualGamer")).toBeVisible();

    // Description
    await expect(
      page.getByText("All worlds completed without warps"),
    ).toBeVisible();

    // Download counts
    await expect(page.getByText("42 downloads")).toBeVisible();
    await expect(page.getByText("5 downloads")).toBeVisible();

    // Total count in header
    await expect(page.getByText("(2)")).toBeVisible();
  });

  test("shared saves count label uses singular for 1 download", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, {
      sharedSaves: {
        data: [
          {
            id: "ss-1",
            userId: "u1",
            username: "Player",
            gameId: "1",
            name: "My Save",
            fileSize: 1024,
            downloadCount: 1,
            createdAt: "2026-02-14T10:00:00Z",
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
      },
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("shared-save-ss-1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("1 download")).toBeVisible();
  });
});

test.describe("Game Reviews", () => {
  test("displays reviews with user info, rating, and text", async ({
    page,
  }) => {
    await setupGameDetailRoutes(page, {
      ratings: {
        data: [
          {
            id: "r-1",
            userId: "u1",
            username: "Reviewer1",
            gameId: "1",
            rating: 5,
            review: "Absolute masterpiece! Every gamer should play this.",
            createdAt: "2026-02-14T10:00:00Z",
            updatedAt: "2026-02-14T10:00:00Z",
          },
          {
            id: "r-2",
            userId: "u2",
            username: "Reviewer2",
            gameId: "1",
            rating: 3,
            createdAt: "2026-02-13T10:00:00Z",
            updatedAt: "2026-02-13T10:00:00Z",
          },
        ],
        total: 2,
        page: 1,
        pageSize: 10,
      },
    });

    await page.goto("/games/1");

    await expect(page.getByTestId("game-reviews")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByRole("heading", { name: "Reviews" }),
    ).toBeVisible();

    // Reviews should be visible
    await expect(page.getByTestId("review-r-1")).toBeVisible();
    await expect(page.getByTestId("review-r-2")).toBeVisible();

    // Review text
    await expect(
      page.getByText("Absolute masterpiece! Every gamer should play this."),
    ).toBeVisible();

    // Usernames
    await expect(page.getByText("Reviewer1")).toBeVisible();
    await expect(page.getByText("Reviewer2")).toBeVisible();
  });

  test("shows empty state when there are no reviews", async ({ page }) => {
    await setupGameDetailRoutes(page);

    await page.goto("/games/1");

    await expect(page.getByTestId("game-reviews")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("No reviews yet")).toBeVisible();
  });
});

test.describe("Online Users Widget", () => {
  test("shows online user with current game info", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 20 }),
      });
    });

    await page.route("**/api/social/online", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          users: [
            {
              id: "u1",
              username: "GamerPro",
              currentGame: {
                id: "5",
                title: "Castlevania",
                consoleName: "NES",
              },
            },
            {
              id: "u2",
              username: "IdleUser",
            },
          ],
        }),
      });
    });

    await page.goto("/activity");

    await expect(page.getByTestId("online-users-widget")).toBeVisible({
      timeout: 10_000,
    });

    // Users should be listed
    await expect(page.getByTestId("online-user-u1")).toBeVisible();
    await expect(page.getByTestId("online-user-u2")).toBeVisible();

    // User playing a game should show game title
    await expect(page.getByText("GamerPro")).toBeVisible();
    await expect(page.getByText("Castlevania")).toBeVisible();

    // Idle user
    await expect(page.getByText("IdleUser")).toBeVisible();
    await expect(page.getByText("Idle", { exact: true })).toBeVisible();
  });

  test("shows empty state when no users are online", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 20 }),
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

    await expect(page.getByTestId("online-users-widget")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("No one online")).toBeVisible();
  });
});
