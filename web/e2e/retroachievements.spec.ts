import { test, expect } from "./fixtures";

/**
 * Sets up common API route mocks needed for the game detail page to load
 * the new social/rating/shared-saves components without errors.
 */
async function setupGameDetailSocialRoutes(
  page: import("@playwright/test").Page,
  gameId: string,
) {
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
    route.fulfill({
      status: 404,
      contentType: "application/json",
      body: "{}",
    });
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

  await page.route(
    `**/api/games/${gameId}/achievements/timeline`,
    (route) => {
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
    },
  );
}

test.describe("RetroAchievements - Preferences Page", () => {
  test("displays RetroAchievements card with link form when not linked", async ({
    page,
  }) => {
    // Mock RA status as not linked
    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: false,
          username: "",
          hardcoreEnabled: false,
        }),
      });
    });

    await page.goto("/preferences");

    await expect(
      page.getByRole("heading", { name: "RetroAchievements" }),
    ).toBeVisible();

    // Should show the link form
    await expect(page.getByTestId("ra-username-input")).toBeVisible();
    await expect(page.getByTestId("ra-password-input")).toBeVisible();
    await expect(page.getByTestId("link-ra-btn")).toBeVisible();

    // Privacy note should be visible
    await expect(
      page.getByText("Your password is only used to obtain a token"),
    ).toBeVisible();

    // Link button should be disabled when inputs are empty
    await expect(page.getByTestId("link-ra-btn")).toBeDisabled();
  });

  test("link button enables when username and password are filled", async ({
    page,
  }) => {
    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: false,
          username: "",
          hardcoreEnabled: false,
        }),
      });
    });

    await page.goto("/preferences");

    await page.getByTestId("ra-username-input").fill("testuser");
    await page.getByTestId("ra-password-input").fill("testpass");

    await expect(page.getByTestId("link-ra-btn")).toBeEnabled();
  });

  test("can link RA account and shows linked state", async ({ page }) => {
    let linkCalled = false;

    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          linkCalled
            ? { linked: true, username: "RetroUser", hardcoreEnabled: false }
            : { linked: false, username: "", hardcoreEnabled: false },
        ),
      });
    });

    await page.route("**/api/user/ra/link", (route) => {
      linkCalled = true;
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: true,
          username: "RetroUser",
          hardcoreEnabled: false,
        }),
      });
    });

    // Also mock preferences to include RA fields
    await page.route("**/api/user/preferences", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            showPerformanceOverlay: false,
            autoSaveEnabled: true,
            autoLoadSaveEnabled: true,
            selectedShader: "none",
            consoleShaders: {},
            selectedKeyMapping: "arrows-left",
            customKeyMapping: {},
            consoleKeyMappings: {},
            raLinked: linkCalled,
            raUsername: linkCalled ? "RetroUser" : "",
            raHardcoreEnabled: false,
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/preferences");

    // Fill in credentials and link
    await page.getByTestId("ra-username-input").fill("RetroUser");
    await page.getByTestId("ra-password-input").fill("secretpass");
    await page.getByTestId("link-ra-btn").click();

    // After linking, should show linked state
    await expect(page.getByText("RetroUser")).toBeVisible({ timeout: 5_000 });
    await expect(page.getByTestId("unlink-ra-btn")).toBeVisible();
    await expect(page.getByText("Hardcore Mode")).toBeVisible();
  });

  test("displays linked state with username and controls", async ({
    page,
  }) => {
    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: true,
          username: "AchievementHunter",
          hardcoreEnabled: false,
        }),
      });
    });

    await page.goto("/preferences");

    // Should show linked account info
    await expect(page.getByText("Linked Account")).toBeVisible();
    await expect(page.getByText("AchievementHunter")).toBeVisible();

    // Should show hardcore toggle and unlink button
    await expect(page.getByText("Hardcore Mode")).toBeVisible();
    await expect(page.getByTestId("unlink-ra-btn")).toBeVisible();
    await expect(page.getByTestId("hardcore-toggle")).toBeVisible();
  });

  test("can toggle hardcore mode", async ({ page }) => {
    let hardcoreEnabled = false;

    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: true,
          username: "TestUser",
          hardcoreEnabled,
        }),
      });
    });

    await page.route("**/api/user/ra/settings", (route) => {
      hardcoreEnabled = !hardcoreEnabled;
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked: true,
          username: "TestUser",
          hardcoreEnabled,
        }),
      });
    });

    await page.goto("/preferences");

    await expect(page.getByTestId("hardcore-toggle")).toBeVisible();

    // Toggle hardcore mode
    await page.getByTestId("hardcore-toggle").click();
  });

  test("can unlink RA account", async ({ page }) => {
    let linked = true;

    await page.route("**/api/user/ra/status", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          linked,
          username: linked ? "TestUser" : "",
          hardcoreEnabled: false,
        }),
      });
    });

    await page.route("**/api/user/ra/link", (route) => {
      if (route.request().method() === "DELETE") {
        linked = false;
        route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
      } else {
        route.continue();
      }
    });

    await page.route("**/api/user/preferences", (route) => {
      if (route.request().method() === "GET") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            showPerformanceOverlay: false,
            autoSaveEnabled: true,
            autoLoadSaveEnabled: true,
            selectedShader: "none",
            consoleShaders: {},
            selectedKeyMapping: "arrows-left",
            customKeyMapping: {},
            consoleKeyMappings: {},
            raLinked: linked,
            raUsername: linked ? "TestUser" : "",
            raHardcoreEnabled: false,
          }),
        });
      } else {
        route.continue();
      }
    });

    await page.goto("/preferences");

    await expect(page.getByTestId("unlink-ra-btn")).toBeVisible();
    await page.getByTestId("unlink-ra-btn").click();

    // After unlinking, should show the link form again
    await expect(page.getByTestId("ra-username-input")).toBeVisible({
      timeout: 5_000,
    });
    await expect(page.getByTestId("link-ra-btn")).toBeVisible();
  });
});

test.describe("RetroAchievements - Game Detail", () => {
  test("shows achievements section with browser warning banner", async ({
    page,
  }) => {
    // Mock a game
    await page.route("**/api/games/1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1",
          title: "Super Mario Bros.",
          consoleId: "1",
          consoleName: "NES",
          fileName: "smb.nes",
          fileSize: 40976,
          screenshotUrls: [],
          isFavorite: false,
        }),
      });
    });

    // Mock achievements
    await page.route("**/api/games/1/achievements", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          raGameId: 1446,
          totalCount: 3,
          totalPoints: 45,
          achievements: [
            {
              id: 101,
              title: "First Steps",
              description: "Complete World 1-1",
              points: 10,
              badgeUrl: "https://media.retroachievements.org/Badge/101.png",
              type: "core",
            },
            {
              id: 102,
              title: "Coin Collector",
              description: "Collect 100 coins",
              points: 15,
              badgeUrl: "https://media.retroachievements.org/Badge/102.png",
              type: "core",
            },
            {
              id: 103,
              title: "Speed Runner",
              description: "Complete the game in under 10 minutes",
              points: 20,
              badgeUrl: "https://media.retroachievements.org/Badge/103.png",
              type: "core",
            },
          ],
        }),
      });
    });

    // Mock user progress (one achievement unlocked)
    await page.route("**/api/games/1/achievements/progress", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          progress: [
            {
              achievementId: 101,
              unlockedAt: "2025-12-01T10:00:00Z",
              isHardcore: false,
            },
          ],
        }),
      });
    });

    // Mock saves and consoles for the page
    await page.route("**/api/games/1/saves", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: "[]",
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

    // Mock social/ratings/shared-saves endpoints for the game detail page
    await setupGameDetailSocialRoutes(page, "1");

    await page.goto("/games/1");

    // Achievements section should be visible
    await expect(
      page.getByRole("heading", { name: "Achievements" }),
    ).toBeVisible({ timeout: 10_000 });

    // Browser warning banner
    await expect(page.getByTestId("browser-warning-banner")).toBeVisible();
    await expect(
      page.getByText(
        "Achievements are available for this game but require the Spela Player app",
      ),
    ).toBeVisible();

    // Progress summary
    await expect(
      page.getByTestId("achievement-progress-summary"),
    ).toContainText("1 of 3 achievements unlocked");

    // Individual achievements visible
    await expect(page.getByText("First Steps")).toBeVisible();
    await expect(page.getByText("Coin Collector")).toBeVisible();
    await expect(page.getByText("Speed Runner")).toBeVisible();
  });

  test("unlocked achievements have different styling from locked ones", async ({
    page,
  }) => {
    await page.route("**/api/games/1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1",
          title: "Super Mario Bros.",
          consoleId: "1",
          consoleName: "NES",
          fileName: "smb.nes",
          fileSize: 40976,
          screenshotUrls: [],
          isFavorite: false,
        }),
      });
    });

    await page.route("**/api/games/1/achievements", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          raGameId: 1446,
          totalCount: 2,
          totalPoints: 25,
          achievements: [
            {
              id: 101,
              title: "Unlocked One",
              description: "You got this",
              points: 10,
              badgeUrl: "https://media.retroachievements.org/Badge/101.png",
              type: "core",
            },
            {
              id: 102,
              title: "Locked One",
              description: "Keep trying",
              points: 15,
              badgeUrl: "https://media.retroachievements.org/Badge/102.png",
              type: "core",
            },
          ],
        }),
      });
    });

    await page.route("**/api/games/1/achievements/progress", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          progress: [
            {
              achievementId: 101,
              unlockedAt: "2025-12-01T10:00:00Z",
              isHardcore: false,
            },
          ],
        }),
      });
    });

    await page.route("**/api/games/1/saves", (route) => {
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });

    await page.route("**/api/consoles", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: "1", name: "NES", abbreviation: "NES", emulatorJsCore: "nestopia" },
        ]),
      });
    });

    // Mock social/ratings/shared-saves endpoints for the game detail page
    await setupGameDetailSocialRoutes(page, "1");

    await page.goto("/games/1");

    await expect(page.getByTestId("achievement-101")).toBeVisible({
      timeout: 10_000,
    });

    // Unlocked achievement should NOT have opacity-50
    const unlocked = page.getByTestId("achievement-101");
    await expect(unlocked).not.toHaveClass(/opacity-50/);

    // Locked achievement SHOULD have opacity-50
    const locked = page.getByTestId("achievement-102");
    await expect(locked).toHaveClass(/opacity-50/);
  });

  test("hides achievements section when game has no achievements", async ({
    page,
  }) => {
    await page.route("**/api/games/1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "1",
          title: "Obscure Game",
          consoleId: "1",
          consoleName: "NES",
          fileName: "obscure.nes",
          fileSize: 12345,
          screenshotUrls: [],
          isFavorite: false,
        }),
      });
    });

    await page.route("**/api/games/1/achievements", (route) => {
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

    await page.route("**/api/games/1/achievements/progress", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ progress: [] }),
      });
    });

    await page.route("**/api/games/1/saves", (route) => {
      route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
    });

    await page.route("**/api/consoles", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([
          { id: "1", name: "NES", abbreviation: "NES", emulatorJsCore: "nestopia" },
        ]),
      });
    });

    // Mock social/ratings/shared-saves endpoints for the game detail page
    await setupGameDetailSocialRoutes(page, "1");

    await page.goto("/games/1");

    // Wait for game title to load
    await expect(page.getByText("Obscure Game")).toBeVisible({
      timeout: 10_000,
    });

    // Achievements section should NOT be visible (component returns null for empty)
    await expect(
      page.getByTestId("browser-warning-banner"),
    ).not.toBeVisible();
  });
});
