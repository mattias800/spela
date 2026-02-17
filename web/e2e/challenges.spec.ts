import { test, expect } from "./fixtures";

/**
 * Helper to create a standard mock challenge object for tests.
 */
function mockChallenge(overrides?: Record<string, unknown>) {
  return {
    id: "1",
    creatorId: "u1",
    creatorUsername: "alice",
    gameId: "g1",
    gameTitle: "Super Mario Bros.",
    gameCoverUrl: "",
    gameConsoleName: "NES",
    name: "Speed Run World 1",
    description: "Complete World 1 as fast as possible!",
    type: "speedrun",
    difficulty: "medium",
    status: "active",
    screenshotUrl: "/api/challenges/1/screenshot",
    coreName: "fceumm",
    saveFileSize: 1024,
    attemptCount: 12,
    completionCount: 8,
    expiresAt: null,
    createdAt: "2026-02-01T10:00:00Z",
    updatedAt: "2026-02-01T10:00:00Z",
    ...overrides,
  };
}

/**
 * Helper to create a mock leaderboard entry.
 */
function mockLeaderboardEntry(overrides?: Record<string, unknown>) {
  return {
    rank: 1,
    userId: "u1",
    username: "alice",
    durationMs: 45230,
    attemptId: "a1",
    completedAt: "2026-02-10T10:00:45Z",
    ...overrides,
  };
}

/**
 * Sets up the common API routes needed for the challenges list page.
 */
async function setupChallengesListRoutes(
  page: import("@playwright/test").Page,
  {
    challenges,
    consoles,
  }: {
    challenges?: Record<string, unknown>;
    consoles?: unknown[];
  } = {},
) {
  await page.route("**/api/challenges?*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        challenges ?? {
          data: [],
          total: 0,
          page: 1,
          pageSize: 20,
        },
      ),
    });
  });

  // Also match without query params
  await page.route("**/api/challenges", (route) => {
    if (route.request().url().includes("?")) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        challenges ?? {
          data: [],
          total: 0,
          page: 1,
          pageSize: 20,
        },
      ),
    });
  });

  await page.route("**/api/consoles", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        consoles ?? [
          {
            id: "1",
            name: "NES",
            abbreviation: "NES",
            emulatorJsCore: "nestopia",
          },
          {
            id: "2",
            name: "SNES",
            abbreviation: "SNES",
            emulatorJsCore: "snes9x",
          },
        ],
      ),
    });
  });
}

/**
 * Sets up the common API routes needed for the challenge detail page.
 */
async function setupChallengeDetailRoutes(
  page: import("@playwright/test").Page,
  {
    challenge,
    leaderboard,
    myAttempts,
  }: {
    challenge?: Record<string, unknown>;
    leaderboard?: Record<string, unknown>;
    myAttempts?: unknown[];
  } = {},
) {
  const challengeData = challenge ?? mockChallenge();
  const challengeId = challengeData.id as string;

  await page.route(`**/api/challenges/${challengeId}`, (route) => {
    if (
      route.request().url().includes("/leaderboard") ||
      route.request().url().includes("/attempts") ||
      route.request().url().includes("/screenshot") ||
      route.request().url().includes("/save")
    ) {
      return route.continue();
    }
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(challengeData),
    });
  });

  await page.route(
    `**/api/challenges/${challengeId}/leaderboard*`,
    (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(
          leaderboard ?? {
            data: [],
            total: 0,
            page: 1,
            pageSize: 50,
          },
        ),
      });
    },
  );

  await page.route(
    `**/api/challenges/${challengeId}/attempts/mine`,
    (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(myAttempts ?? []),
      });
    },
  );
}

// ---------------------------------------------------------------------------
// Challenges List Page
// ---------------------------------------------------------------------------

test.describe("Challenges List Page", () => {
  test("renders page heading and description", async ({ page }) => {
    await setupChallengesListRoutes(page);

    await page.goto("/challenges");

    await expect(
      page.getByRole("heading", { name: "Challenges", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("displays challenge cards with metadata", async ({ page }) => {
    await setupChallengesListRoutes(page, {
      challenges: {
        data: [
          mockChallenge(),
          mockChallenge({
            id: "2",
            name: "Survival Run",
            type: "survival",
            difficulty: "hard",
            creatorUsername: "bob",
            gameTitle: "Castlevania",
            gameConsoleName: "NES",
            attemptCount: 5,
            completionCount: 2,
          }),
        ],
        total: 2,
        page: 1,
        pageSize: 20,
      },
    });

    await page.goto("/challenges");

    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("Survival Run")).toBeVisible();

    // Difficulty badges should be visible (scope to cards to avoid matching filter <option>)
    await expect(
      page.locator("[data-testid^='challenge-card']").getByText("Medium").first(),
    ).toBeVisible();
    await expect(
      page.locator("[data-testid^='challenge-card']").getByText("Hard").first(),
    ).toBeVisible();

    // Creator usernames
    await expect(page.getByText("alice").first()).toBeVisible();
    await expect(page.getByText("bob").first()).toBeVisible();
  });

  test("shows empty state when no challenges exist", async ({ page }) => {
    await setupChallengesListRoutes(page);

    await page.goto("/challenges");

    await expect(page.getByText("No challenges yet")).toBeVisible({
      timeout: 10_000,
    });
  });

  test("renders loading skeletons when loading", async ({ page }) => {
    // Delay the API response to observe loading state
    await page.route("**/api/challenges*", (route) => {
      setTimeout(() => {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            data: [],
            total: 0,
            page: 1,
            pageSize: 20,
          }),
        });
      }, 3000);
    });

    await page.route("**/api/consoles", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      });
    });

    await page.goto("/challenges");

    // Should show skeletons while loading
    const skeletons = page.locator("[class*='animate-pulse']");
    await expect(skeletons.first()).toBeVisible({ timeout: 5_000 });
  });

  test("tabs switch between Popular, Recent, and My Challenges", async ({
    page,
  }) => {
    await setupChallengesListRoutes(page, {
      challenges: {
        data: [mockChallenge()],
        total: 1,
        page: 1,
        pageSize: 20,
      },
    });

    await page.goto("/challenges");

    // Default tab should show content
    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });

    // Tabs should be visible
    await expect(page.getByText("Popular")).toBeVisible();
    await expect(page.getByText("Recent")).toBeVisible();
    await expect(page.getByText("My Challenges")).toBeVisible();
  });

  test("pagination controls are visible for multi-page results", async ({
    page,
  }) => {
    const challenges = Array.from({ length: 20 }, (_, i) =>
      mockChallenge({
        id: String(i + 1),
        name: `Challenge ${i + 1}`,
      }),
    );

    await setupChallengesListRoutes(page, {
      challenges: {
        data: challenges,
        total: 40,
        page: 1,
        pageSize: 20,
      },
    });

    await page.goto("/challenges");

    await expect(
      page.getByText("Challenge 1", { exact: true }),
    ).toBeVisible({
      timeout: 10_000,
    });

    // Pagination should be visible
    await expect(page.getByRole("button", { name: "Previous" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Next" })).toBeVisible();

    // Previous should be disabled on first page
    await expect(
      page.getByRole("button", { name: "Previous" }),
    ).toBeDisabled();

    // Next should be enabled
    await expect(page.getByRole("button", { name: "Next" })).toBeEnabled();

    // Page info
    await expect(page.getByText("Page 1 of 2")).toBeVisible();
  });

  test("challenge card links to detail page", async ({ page }) => {
    await setupChallengesListRoutes(page, {
      challenges: {
        data: [mockChallenge()],
        total: 1,
        page: 1,
        pageSize: 20,
      },
    });

    // Pre-mock the detail page routes for navigation target
    await setupChallengeDetailRoutes(page);

    await page.goto("/challenges");

    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });

    // Click the challenge card/link
    await page.getByText("Speed Run World 1").click();

    // Should navigate to detail page
    await page.waitForURL("**/challenges/1", { timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// Challenge Detail Page
// ---------------------------------------------------------------------------

test.describe("Challenge Detail Page", () => {
  test("renders challenge metadata", async ({ page }) => {
    await setupChallengeDetailRoutes(page);

    await page.goto("/challenges/1");

    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByText("Complete World 1 as fast as possible!"),
    ).toBeVisible();

    // Game info
    await expect(page.getByText("Super Mario Bros.")).toBeVisible();

    // Creator
    await expect(page.getByText("alice")).toBeVisible();

    // Difficulty and type badges
    await expect(page.getByText("Medium").first()).toBeVisible();
    await expect(page.getByText("Speedrun").first()).toBeVisible();
  });

  test("renders leaderboard with entries", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      leaderboard: {
        data: [
          mockLeaderboardEntry(),
          mockLeaderboardEntry({
            attemptId: "a2",
            userId: "u2",
            username: "bob",
            durationMs: 52100,
            rank: 2,
          }),
          mockLeaderboardEntry({
            attemptId: "a3",
            userId: "u3",
            username: "charlie",
            durationMs: 61500,
            rank: 3,
          }),
        ],
        total: 3,
        page: 1,
        pageSize: 50,
      },
    });

    await page.goto("/challenges/1");

    // Leaderboard heading
    await expect(
      page.getByRole("heading", { name: /Leaderboard/i }),
    ).toBeVisible({ timeout: 10_000 });

    // Entries should be visible (scoped to leaderboard to avoid creator username match)
    const leaderboard = page.getByTestId("challenge-leaderboard");
    await expect(leaderboard.getByText("alice")).toBeVisible();
    await expect(leaderboard.getByText("bob")).toBeVisible();
    await expect(leaderboard.getByText("charlie")).toBeVisible();
  });

  test("leaderboard shows empty state when no attempts", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      leaderboard: {
        data: [],
        total: 0,
        page: 1,
        pageSize: 50,
      },
    });

    await page.goto("/challenges/1");

    await expect(
      page.getByText("No attempts yet. Be the first!"),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("shows Download Save State button", async ({ page }) => {
    await setupChallengeDetailRoutes(page);

    await page.goto("/challenges/1");

    await expect(
      page.getByRole("button", { name: /Download Save State/i }),
    ).toBeVisible({ timeout: 10_000 });
  });

  test("shows 404 for invalid challenge ID", async ({ page }) => {
    await page.route("**/api/challenges/invalid", (route) => {
      if (
        route.request().url().includes("/leaderboard") ||
        route.request().url().includes("/attempts")
      ) {
        return route.continue();
      }
      route.fulfill({
        status: 404,
        contentType: "application/json",
        body: JSON.stringify({ error: "Challenge not found" }),
      });
    });

    await page.route("**/api/challenges/invalid/leaderboard*", (route) => {
      route.fulfill({
        status: 404,
        contentType: "application/json",
        body: JSON.stringify({ error: "Challenge not found" }),
      });
    });

    await page.route("**/api/challenges/invalid/attempts/mine", (route) => {
      route.fulfill({
        status: 404,
        contentType: "application/json",
        body: JSON.stringify({ error: "Challenge not found" }),
      });
    });

    await page.goto("/challenges/invalid");

    await expect(page.getByText(/not found/i)).toBeVisible({
      timeout: 10_000,
    });
  });

  test("shows attempt and completion counts", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      challenge: mockChallenge({ attemptCount: 12, completionCount: 8 }),
    });

    await page.goto("/challenges/1");

    await expect(page.getByText("12")).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText("8")).toBeVisible();
  });

  test("leaderboard shows gold/silver/bronze for top 3", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      leaderboard: {
        data: [
          mockLeaderboardEntry({ rank: 1, username: "gold_player" }),
          mockLeaderboardEntry({
            attemptId: "a2",
            rank: 2,
            userId: "u2",
            username: "silver_player",
            durationMs: 52100,
          }),
          mockLeaderboardEntry({
            attemptId: "a3",
            rank: 3,
            userId: "u3",
            username: "bronze_player",
            durationMs: 61500,
          }),
        ],
        total: 3,
        page: 1,
        pageSize: 50,
      },
    });

    await page.goto("/challenges/1");

    await expect(page.getByText("gold_player")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("silver_player")).toBeVisible();
    await expect(page.getByText("bronze_player")).toBeVisible();

    // Rank badges (gold/silver/bronze indicators)
    await expect(page.getByTestId("rank-badge-1")).toBeVisible();
    await expect(page.getByTestId("rank-badge-2")).toBeVisible();
    await expect(page.getByTestId("rank-badge-3")).toBeVisible();
  });

  test("displays time in M:SS.s format in monospace", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      leaderboard: {
        data: [
          mockLeaderboardEntry({ durationMs: 45230 }), // 0:45.2
        ],
        total: 1,
        page: 1,
        pageSize: 50,
      },
    });

    await page.goto("/challenges/1");

    // Time should be formatted as M:SS.s
    await expect(page.getByText("0:45.2")).toBeVisible({ timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// Challenge Creation from Web (US-2)
// ---------------------------------------------------------------------------

test.describe("Challenge Creation (Web)", () => {
  test("Create Challenge button visible on game detail page", async ({
    page,
  }) => {
    // Set up game detail routes (reuse pattern from social-features)
    await page.route("**/api/games/1", (route) => {
      if (
        route.request().url().includes("/saves") ||
        route.request().url().includes("/ratings") ||
        route.request().url().includes("/stats") ||
        route.request().url().includes("/shared-saves") ||
        route.request().url().includes("/achievements") ||
        route.request().url().includes("/challenges")
      ) {
        return route.continue();
      }
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
        }),
      });
    });

    // Mock all sub-endpoints with defaults
    // Note: order matters — Playwright routes are LIFO, so more specific
    // paths (ratings/summary) must come AFTER the generic (ratings) so they
    // take priority during request matching.
    for (const subpath of [
      "saves",
      "achievements",
      "achievements/progress",
      "achievements/leaderboard",
      "achievements/timeline",
      "stats",
      "ratings",
      "ratings/summary",
      "ratings/mine",
      "shared-saves",
      "challenges",
    ]) {
      const path = subpath;
      await page.route(`**/api/games/1/${path}*`, (route) => {
        route.fulfill({
          status: path === "ratings/mine" ? 404 : 200,
          contentType: "application/json",
          body: JSON.stringify(
            path === "saves"
              ? [
                  {
                    id: "save-1",
                    gameId: "1",
                    userId: "1",
                    slot: 0,
                    name: "World 1-1",
                    fileSize: 8192,
                    createdAt: "2025-01-01T00:00:00Z",
                    updatedAt: "2025-01-01T00:00:00Z",
                  },
                ]
              : path.includes("ratings/summary")
                ? { averageRating: 0, totalRatings: 0, distribution: {} }
                : path === "stats"
                  ? {
                      totalPlayers: 0,
                      totalPlayTime: 0,
                      averagePlayTime: 0,
                      topPlayers: [],
                    }
                  : path.includes("achievements/leaderboard")
                    ? {
                        raGameId: 0,
                        totalAchievements: 0,
                        leaderboard: [],
                      }
                    : path.includes("achievements/timeline")
                      ? {
                          raGameId: 0,
                          gameTitle: "",
                          totalPlayTime: 0,
                          timeline: [],
                          totalAchievements: 0,
                          unlockedCount: 0,
                          totalPoints: 0,
                          earnedPoints: 0,
                        }
                      : path === "achievements/progress"
                        ? { progress: [] }
                        : path.includes("achievements")
                          ? {
                              raGameId: 0,
                              totalCount: 0,
                              totalPoints: 0,
                              achievements: [],
                            }
                          : { data: [], total: 0, page: 1, pageSize: 10 },
          ),
        });
      });
    }

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

    await page.goto("/games/1");

    await expect(
      page.getByRole("button", { name: /Create Challenge/i }).first(),
    ).toBeVisible({ timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// Activity Feed Integration
// ---------------------------------------------------------------------------

test.describe("Challenge Activity Feed Events", () => {
  test("activity feed shows challenge_created events", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "evt-ch-1",
              userId: "u1",
              username: "alice",
              eventType: "challenge_created",
              gameId: "1",
              gameTitle: "Super Mario Bros.",
              gameConsoleName: "NES",
              metadata: { challengeId: "1", challengeName: "Speed Run World 1" },
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

    await expect(page.getByTestId("activity-event-evt-ch-1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("alice")).toBeVisible();
    await expect(page.getByText("Speed Run World 1")).toBeVisible();
  });

  test("activity feed shows challenge_completed events", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "evt-ch-2",
              userId: "u2",
              username: "bob",
              eventType: "challenge_completed",
              gameId: "1",
              gameTitle: "Super Mario Bros.",
              gameConsoleName: "NES",
              metadata: {
                challengeId: "1",
                challengeName: "Speed Run World 1",
                durationMs: 45230,
              },
              createdAt: "2026-02-14T11:00:00Z",
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

    await expect(page.getByTestId("activity-event-evt-ch-2")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("bob")).toBeVisible();
  });

  test("activity feed shows challenge_record events", async ({ page }) => {
    await page.route("**/api/social/activity*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "evt-ch-3",
              userId: "u1",
              username: "alice",
              eventType: "challenge_record",
              gameId: "1",
              gameTitle: "Super Mario Bros.",
              gameConsoleName: "NES",
              metadata: {
                challengeId: "1",
                challengeName: "Speed Run World 1",
                durationMs: 38000,
              },
              createdAt: "2026-02-14T12:00:00Z",
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

    await expect(page.getByTestId("activity-event-evt-ch-3")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("alice")).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Game Detail Page — Challenges Section
// ---------------------------------------------------------------------------

test.describe("Game Detail Page — Challenges Section", () => {
  test("shows challenges section with challenge cards", async ({ page }) => {
    // Use simplified game detail setup with challenges
    await page.route("**/api/games/1", (route) => {
      if (route.request().url().includes("/")) {
        const url = route.request().url();
        if (
          url.includes("/saves") ||
          url.includes("/ratings") ||
          url.includes("/stats") ||
          url.includes("/shared-saves") ||
          url.includes("/achievements") ||
          url.includes("/challenges")
        ) {
          return route.continue();
        }
      }
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
        }),
      });
    });

    await page.route("**/api/games/1/challenges*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            mockChallenge(),
            mockChallenge({
              id: "2",
              name: "No Deaths Run",
              type: "survival",
              difficulty: "hard",
            }),
          ],
          total: 2,
          page: 1,
          pageSize: 10,
        }),
      });
    });

    // Mock other game detail sub-endpoints with defaults
    for (const path of [
      "saves",
      "achievements",
      "achievements/progress",
      "achievements/leaderboard",
      "achievements/timeline",
      "stats",
      "ratings",
      "ratings/summary",
      "ratings/mine",
      "shared-saves",
    ]) {
      await page.route(`**/api/games/1/${path}*`, (route) => {
        route.fulfill({
          status: path === "ratings/mine" ? 404 : 200,
          contentType: "application/json",
          body: JSON.stringify(
            path === "saves"
              ? []
              : path.includes("ratings/summary")
                ? { averageRating: 0, totalRatings: 0, distribution: {} }
                : path === "stats"
                  ? {
                      totalPlayers: 0,
                      totalPlayTime: 0,
                      averagePlayTime: 0,
                      topPlayers: [],
                    }
                  : path.includes("achievements/leaderboard")
                    ? {
                        raGameId: 0,
                        totalAchievements: 0,
                        leaderboard: [],
                      }
                    : path.includes("achievements/timeline")
                      ? {
                          raGameId: 0,
                          gameTitle: "",
                          totalPlayTime: 0,
                          timeline: [],
                          totalAchievements: 0,
                          unlockedCount: 0,
                          totalPoints: 0,
                          earnedPoints: 0,
                        }
                      : path === "achievements/progress"
                        ? { progress: [] }
                        : path.includes("achievements")
                          ? {
                              raGameId: 0,
                              totalCount: 0,
                              totalPoints: 0,
                              achievements: [],
                            }
                          : { data: [], total: 0, page: 1, pageSize: 10 },
          ),
        });
      });
    }

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

    await page.goto("/games/1");

    // Challenges section should be visible
    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText("No Deaths Run")).toBeVisible();
  });

  test("shows empty state when game has no challenges", async ({ page }) => {
    await page.route("**/api/games/1", (route) => {
      if (route.request().url().includes("/")) {
        const url = route.request().url();
        if (
          url.includes("/saves") ||
          url.includes("/ratings") ||
          url.includes("/stats") ||
          url.includes("/shared-saves") ||
          url.includes("/achievements") ||
          url.includes("/challenges")
        ) {
          return route.continue();
        }
      }
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
        }),
      });
    });

    await page.route("**/api/games/1/challenges*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [],
          total: 0,
          page: 1,
          pageSize: 10,
        }),
      });
    });

    for (const path of [
      "saves",
      "achievements",
      "achievements/progress",
      "achievements/leaderboard",
      "achievements/timeline",
      "stats",
      "ratings",
      "ratings/summary",
      "ratings/mine",
      "shared-saves",
    ]) {
      await page.route(`**/api/games/1/${path}*`, (route) => {
        route.fulfill({
          status: path === "ratings/mine" ? 404 : 200,
          contentType: "application/json",
          body: JSON.stringify(
            path === "saves"
              ? []
              : path.includes("ratings/summary")
                ? { averageRating: 0, totalRatings: 0, distribution: {} }
                : path === "stats"
                  ? {
                      totalPlayers: 0,
                      totalPlayTime: 0,
                      averagePlayTime: 0,
                      topPlayers: [],
                    }
                  : path.includes("achievements/leaderboard")
                    ? {
                        raGameId: 0,
                        totalAchievements: 0,
                        leaderboard: [],
                      }
                    : path.includes("achievements/timeline")
                      ? {
                          raGameId: 0,
                          gameTitle: "",
                          totalPlayTime: 0,
                          timeline: [],
                          totalAchievements: 0,
                          unlockedCount: 0,
                          totalPoints: 0,
                          earnedPoints: 0,
                        }
                      : path === "achievements/progress"
                        ? { progress: [] }
                        : path.includes("achievements")
                          ? {
                              raGameId: 0,
                              totalCount: 0,
                              totalPoints: 0,
                              achievements: [],
                            }
                          : { data: [], total: 0, page: 1, pageSize: 10 },
          ),
        });
      });
    }

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

    await page.goto("/games/1");

    await expect(
      page.getByText(/No challenges/i),
    ).toBeVisible({ timeout: 10_000 });
  });
});

// ---------------------------------------------------------------------------
// Challenge Delete (US-7)
// ---------------------------------------------------------------------------

test.describe("Challenge Deletion", () => {
  test("delete button shows confirmation dialog", async ({ page }) => {
    await setupChallengeDetailRoutes(page, {
      // Current user is the creator
      challenge: mockChallenge({ creatorId: "admin-id" }),
    });

    await page.goto("/challenges/1");

    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });

    // Click delete button
    const deleteBtn = page.getByRole("button", { name: /Delete/i });
    await expect(deleteBtn).toBeVisible();
    await deleteBtn.click();

    // Confirmation dialog should appear
    await expect(page.getByText(/Are you sure/i)).toBeVisible();
  });

  test("confirming delete sends DELETE request", async ({ page }) => {
    let deleteRequested = false;

    await setupChallengeDetailRoutes(page, {
      challenge: mockChallenge({ creatorId: "admin-id" }),
    });

    await page.route("**/api/challenges/1", (route) => {
      if (route.request().method() === "DELETE") {
        deleteRequested = true;
        route.fulfill({
          status: 204,
          contentType: "application/json",
          body: "",
        });
      } else {
        route.fallback();
      }
    });

    await page.goto("/challenges/1");

    await expect(page.getByText("Speed Run World 1")).toBeVisible({
      timeout: 10_000,
    });

    // Click "Delete Challenge" button, then confirm in the modal
    await page.getByRole("button", { name: /Delete Challenge/i }).click();
    await page.getByText(/Are you sure/i).waitFor({ state: "visible" });
    // Modal has "Cancel" and "Delete" buttons — use exact match to avoid matching "Delete Challenge"
    await page.getByRole("button", { name: "Delete", exact: true }).click();

    await page.waitForTimeout(500);
    expect(deleteRequested).toBe(true);
  });
});
