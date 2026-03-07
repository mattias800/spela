import { test, expect } from "./fixtures";

function mockSession(overrides?: Record<string, unknown>) {
  return {
    id: "s1",
    name: "Super Mario World",
    hostId: "u1",
    hostUsername: "alice",
    hostAvatarUrl: null,
    clientId: null,
    clientUsername: null,
    clientAvatarUrl: null,
    gameId: "g1",
    gameTitle: "Super Mario World",
    gameCoverUrl: "https://example.com/cover.png",
    consoleName: "SNES",
    status: "waiting",
    endReason: null,
    inputDelay: 3,
    inviteCode: "ABC123",
    createdAt: "2026-02-13T10:00:00Z",
    startedAt: null,
    endedAt: null,
    ...overrides,
  };
}

const mockSessions = [
  mockSession(),
  mockSession({
    id: "s2",
    name: "Mega Man 2",
    hostId: "u2",
    hostUsername: "bob",
    gameId: "g2",
    gameTitle: "Mega Man 2",
    gameCoverUrl: null,
    consoleName: "NES",
    status: "in_progress",
    clientId: "u3",
    clientUsername: "charlie",
    inviteCode: "XYZ789",
    startedAt: "2026-02-13T10:05:00Z",
  }),
  mockSession({
    id: "s3",
    name: "Sonic the Hedgehog",
    hostId: "u1",
    hostUsername: "alice",
    gameId: "g3",
    gameTitle: "Sonic the Hedgehog",
    gameCoverUrl: null,
    consoleName: "Genesis",
    status: "ended",
    endReason: "completed",
    clientId: "u2",
    clientUsername: "bob",
    inviteCode: "END456",
    startedAt: "2026-02-13T09:00:00Z",
    endedAt: "2026-02-13T10:00:00Z",
  }),
];

async function setupNetplayRoutes(
  page: import("@playwright/test").Page,
  sessions: Record<string, unknown>[] = mockSessions,
) {
  await page.route("**/api/netplay/sessions?*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        data: sessions,
        total: sessions.length,
        page: 1,
        pageSize: 20,
      }),
    });
  });

  await page.route("**/api/netplay/invites", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 20 }),
    });
  });
}

test.describe("Netplay Page", () => {
  test("renders netplay page with heading and session list", async ({
    page,
  }) => {
    await setupNetplayRoutes(page);

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByText("Real-time multiplayer sessions."),
    ).toBeVisible();

    // Sessions should be visible
    await expect(page.getByText("Super Mario World")).toBeVisible();
    await expect(page.getByText("Mega Man 2")).toBeVisible();
    await expect(page.getByText("Sonic the Hedgehog")).toBeVisible();
  });

  test("shows status badges for each session", async ({ page }) => {
    await setupNetplayRoutes(page);

    await page.goto("/netplay");

    await expect(page.getByText("Super Mario World")).toBeVisible({
      timeout: 10_000,
    });

    await expect(page.getByText("Waiting for player")).toBeVisible();
    await expect(page.getByText("In Progress")).toBeVisible();
    await expect(page.getByText("Ended")).toBeVisible();
  });

  test("shows invite code for waiting sessions", async ({ page }) => {
    await setupNetplayRoutes(page);

    await page.goto("/netplay");

    await expect(page.getByText("Super Mario World")).toBeVisible({
      timeout: 10_000,
    });

    // Invite code should be visible for waiting session
    await expect(page.getByText("ABC123")).toBeVisible();
  });

  test("shows empty state when no sessions exist", async ({ page }) => {
    await setupNetplayRoutes(page, []);

    await page.goto("/netplay");

    await expect(page.getByText("No netplay sessions yet")).toBeVisible({
      timeout: 10_000,
    });
    await expect(
      page.getByText("Create one to play with a friend."),
    ).toBeVisible();
  });

  test("renders Create Session button", async ({ page }) => {
    await setupNetplayRoutes(page);

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });
    await expect(
      page.getByRole("button", { name: /Create Session/ }),
    ).toBeVisible();
  });

  test("opens create modal when clicking Create Session", async ({ page }) => {
    await setupNetplayRoutes(page);

    // Mock games API for the create modal
    await page.route("**/api/games?*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ data: [], total: 0, page: 1, pageSize: 8 }),
      });
    });

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    await page.getByRole("button", { name: /Create Session/ }).first().click();

    await expect(page.getByText("Create Netplay Session")).toBeVisible();
    await expect(
      page.getByPlaceholder("Search for a game..."),
    ).toBeVisible();
  });

  test("invite code input converts to uppercase", async ({ page }) => {
    await setupNetplayRoutes(page);

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    const input = page.getByPlaceholder("e.g. ABC123");
    await input.fill("abc");

    await expect(input).toHaveValue("ABC");
  });

  test("View Session button looks up invite code", async ({ page }) => {
    await setupNetplayRoutes(page);

    let joinCalled = false;
    await page.route("**/api/netplay/sessions/join", (route) => {
      if (route.request().method() === "POST") {
        joinCalled = true;
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(mockSession()),
        });
      } else {
        route.continue();
      }
    });

    // Mock session detail for navigation target
    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockSession()),
      });
    });

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    const input = page.getByPlaceholder("e.g. ABC123");
    await input.fill("ABC123");
    await page.getByRole("button", { name: "View Session" }).click();

    await page.waitForTimeout(500);
    expect(joinCalled).toBe(true);
  });
});

test.describe("Netplay Session Detail Page", () => {
  test("renders waiting session with invite code and players", async ({
    page,
  }) => {
    const session = mockSession();

    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(session),
      });
    });

    await page.goto("/netplay/s1");

    // Session name as heading
    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    // Console badge
    await expect(page.getByText("SNES")).toBeVisible();

    // Invite code prominently displayed
    await expect(page.getByText("ABC123")).toBeVisible();
    await expect(page.getByText("Invite Code")).toBeVisible();

    // Open in app banner
    await expect(
      page.getByText(
        "Open this session in the Spela player app to join and play.",
      ),
    ).toBeVisible();

    // Players section
    await expect(
      page.getByRole("heading", { name: "Players" }),
    ).toBeVisible();
    await expect(page.getByText("alice").first()).toBeVisible();
  });

  test("shows empty player slot for waiting session", async ({ page }) => {
    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockSession()),
      });
    });

    await page.goto("/netplay/s1");

    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    await expect(page.getByTestId("netplay-player-slot-empty")).toBeVisible();
  });

  test("renders in-progress session with both players", async ({ page }) => {
    const session = mockSession({
      status: "in_progress",
      clientId: "u2",
      clientUsername: "bob",
      startedAt: "2026-02-13T10:05:00Z",
    });

    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(session),
      });
    });

    await page.goto("/netplay/s1");

    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    await expect(page.getByText("In Progress", { exact: true })).toBeVisible();
    await expect(page.getByText("alice").first()).toBeVisible();
    await expect(page.getByText("bob").first()).toBeVisible();
  });

  test("renders ended session with end reason and create button", async ({
    page,
  }) => {
    const session = mockSession({
      status: "ended",
      endReason: "host_left",
      clientId: "u2",
      clientUsername: "bob",
      startedAt: "2026-02-13T10:00:00Z",
      endedAt: "2026-02-13T11:00:00Z",
    });

    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(session),
      });
    });

    await page.goto("/netplay/s1");

    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    await expect(page.getByText("Ended", { exact: true })).toBeVisible();
    await expect(page.getByText("Host left")).toBeVisible();
    await expect(
      page.getByRole("button", { name: /Create New Session/ }),
    ).toBeVisible();

    // No open in app banner for ended sessions
    await expect(
      page.getByText(
        "Open this session in the Spela player app to join and play.",
      ),
    ).not.toBeVisible();
  });

  test("shows session not found for invalid session", async ({ page }) => {
    await page.route("**/api/netplay/sessions/nonexistent", (route) => {
      route.fulfill({
        status: 404,
        contentType: "application/json",
        body: JSON.stringify({ error: "not found" }),
      });
    });

    await page.goto("/netplay/nonexistent");

    await expect(page.getByText("Session not found")).toBeVisible({
      timeout: 10_000,
    });
  });

  test("shows meta info with input delay and timestamps", async ({ page }) => {
    await page.route("**/api/netplay/sessions/s1", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(mockSession()),
      });
    });

    await page.goto("/netplay/s1");

    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    await expect(page.getByText("Input Delay:")).toBeVisible();
    await expect(page.getByText("3 frames")).toBeVisible();
    await expect(page.getByText("Host:")).toBeVisible();
  });
});

test.describe("Netplay Create Session Flow", () => {
  test("creates session and navigates to detail page", async ({ page }) => {
    await setupNetplayRoutes(page);

    const createdSession = mockSession({ id: "s-new", inviteCode: "NEW123" });

    // Mock games search — use full console name to pass NETPLAY_SUPPORTED_CONSOLES filter
    await page.route("**/api/games?*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "g1",
              title: "Super Mario World",
              consoleName: "Super Nintendo",
              coverUrl: "https://example.com/cover.png",
            },
          ],
          total: 1,
          page: 1,
          pageSize: 8,
        }),
      });
    });

    // Mock create session
    await page.route("**/api/netplay/sessions", (route) => {
      if (route.request().url().includes("?")) return route.continue();
      if (route.request().method() === "POST") {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(createdSession),
        });
      } else {
        route.continue();
      }
    });

    // Mock session detail for navigation target
    await page.route("**/api/netplay/sessions/s-new", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(createdSession),
      });
    });

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    // Open create modal
    await page.getByRole("button", { name: /Create Session/ }).first().click();
    await expect(page.getByText("Create Netplay Session")).toBeVisible();

    // Search for a game
    await page.getByPlaceholder("Search for a game...").fill("Mario");

    // Select the game from dropdown results
    await page
      .locator("button", { hasText: "Super Mario World" })
      .first()
      .click();

    // Should show selected game with Change button
    await expect(page.getByText("Change")).toBeVisible();

    // Submit
    const submitButtons = page.getByRole("button", { name: "Create Session" });
    await submitButtons.last().click();

    // Should navigate to the new session detail page
    await expect(page).toHaveURL(/\/netplay\/s-new/, { timeout: 10_000 });
  });

  test("create modal filters to supported consoles only", async ({
    page,
  }) => {
    await setupNetplayRoutes(page);

    // Mock games with mix of supported and unsupported consoles
    // Use full console names to match NETPLAY_SUPPORTED_CONSOLES filter
    await page.route("**/api/games?*", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          data: [
            {
              id: "g1",
              title: "Super Mario World",
              consoleName: "Super Nintendo",
              coverUrl: null,
            },
            {
              id: "g2",
              title: "Crash Bandicoot",
              consoleName: "PlayStation",
              coverUrl: null,
            },
          ],
          total: 2,
          page: 1,
          pageSize: 8,
        }),
      });
    });

    await page.goto("/netplay");

    await expect(
      page.getByRole("heading", { name: "Netplay", level: 1 }),
    ).toBeVisible({ timeout: 10_000 });

    await page.getByRole("button", { name: /Create Session/ }).first().click();
    await page.getByPlaceholder("Search for a game...").fill("game");

    // Scope assertions to the modal dropdown to avoid matching session list
    const dropdown = page.locator(".absolute.z-10");

    // Super Nintendo game should be visible (supported)
    await expect(dropdown.getByText("Super Mario World")).toBeVisible();

    // PlayStation game should NOT be visible (unsupported)
    await expect(dropdown.getByText("Crash Bandicoot")).not.toBeVisible();
  });
});

test.describe("Netplay Delete Session", () => {
  test("host can cancel a waiting session", async ({ page }) => {
    let deleteCalled = false;

    // Mock auth with user who is the host
    await page.route("**/api/user/profile", (route) => {
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "u1",
          username: "alice",
          role: "admin",
        }),
      });
    });

    const session = mockSession({ hostId: "u1" });

    await page.route("**/api/netplay/sessions/s1", (route) => {
      if (route.request().method() === "DELETE") {
        deleteCalled = true;
        route.fulfill({ status: 204 });
      } else {
        route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(session),
        });
      }
    });

    await page.goto("/netplay/s1");

    await expect(
      page.getByRole("heading", { name: "Super Mario World" }),
    ).toBeVisible({ timeout: 10_000 });

    // Click cancel button
    await page.getByRole("button", { name: /Cancel Session/ }).click();

    // Confirm in modal
    await expect(page.getByText("Are you sure you want to cancel")).toBeVisible();

    // Click the confirm "Cancel Session" button in the modal
    const modalButtons = page.getByRole("button", { name: "Cancel Session" });
    await modalButtons.last().click();

    await page.waitForTimeout(500);
    expect(deleteCalled).toBe(true);
  });
});
