import { test, expect, type Page } from "./fixtures";

// Clone-session E2E. Covers the three US entry points in #553:
//   US-2  session-detail     → `…` menu → Clone session
//   US-3  per-save row       → `…` menu → Clone from this save
//   US-1  shared-session     → `…` menu → Clone to my library
//
// The flow is exercised against mocked API responses rather than the
// full Docker stack because cloning is a pure UI-wiring change — the
// backend path is covered by Go table tests (see backend-dev's
// commits on this branch). We only care that the UI posts to the
// right endpoint with the right path/query/body and navigates to the
// created session's detail page.

interface MockGame {
  id: string;
  title: string;
  consoleId: string;
  consoleName: string;
  [key: string]: unknown;
}

function mockGame(overrides?: Partial<MockGame>): MockGame {
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
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

function mockSession(overrides?: Record<string, unknown>) {
  return {
    id: "10",
    ownerId: "1",
    ownerUsername: "admin",
    gameId: "1",
    name: "Main Playthrough",
    lastPlayedAt: "2026-03-01T10:00:00Z",
    lastPlayedBy: "1",
    lastPlayedByUsername: "admin",
    totalPlayTime: 36000,
    screenshotUrl: "",
    coreName: "nestopia",
    cheatsEnabled: false,
    saveCount: 2,
    isSharedSession: false,
    sharedSessionId: null,
    memberCount: 1,
    memberAvatars: [],
    memberUsernames: [],
    pinnedCoreSha256: "ab12cd34",
    createdAt: "2026-02-28T10:00:00Z",
    updatedAt: "2026-03-01T10:00:00Z",
    ...overrides,
  };
}

function mockSave(overrides?: Record<string, unknown>) {
  return {
    id: "101",
    sessionId: "10",
    userId: "1",
    username: "admin",
    name: "Checkpoint 1",
    fileSize: 32768,
    screenshotUrl: "",
    isAuto: false,
    isCurrent: true,
    coreName: "nestopia",
    coreMatch: true,
    currentCore: "nestopia",
    notes: "",
    slot: 0,
    createdAt: "2026-03-01T09:00:00Z",
    updatedAt: "2026-03-01T09:00:00Z",
    ...overrides,
  };
}

/**
 * Stub the session-detail page data + clone endpoint. Captures the
 * most recent clone request so assertions can inspect path, query,
 * and body in one place.
 */
async function setupSessionDetailRoutes(
  page: Page,
  opts: {
    session: ReturnType<typeof mockSession>;
    saves: ReturnType<typeof mockSave>[];
    cloneResponse: ReturnType<typeof mockSession>;
  },
) {
  const cloneCalls: {
    url: string;
    method: string;
    body: string;
  }[] = [];

  await page.route("**/api/sessions/10", (route) => {
    const method = route.request().method();
    if (method === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(opts.session),
      });
    }
    return route.continue();
  });

  await page.route("**/api/sessions/10/saves", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(opts.saves),
    });
  });

  await page.route("**/api/sessions/10/cheats", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ cheatsEnabled: false, enabledIndices: [] }),
    });
  });

  await page.route("**/api/games/1", (route) => {
    const url = route.request().url();
    // Don't intercept sub-paths like /games/1/cheats
    if (url.match(/\/api\/games\/1\/[^/?]+/)) return route.continue();
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(
        mockGame({ id: "1", title: "Super Mario Bros." }),
      ),
    });
  });

  await page.route("**/api/games/1/cheats", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    });
  });

  // Intercept the *new* clone endpoint specifically — if the UI
  // still hits the deprecated /duplicate path the request falls
  // through and the spec fails with an unresolved mock.
  await page.route("**/api/sessions/10/clone**", (route) => {
    const req = route.request();
    cloneCalls.push({
      url: req.url(),
      method: req.method(),
      body: req.postData() ?? "",
    });
    route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify(opts.cloneResponse),
    });
  });

  // Also stub the detail fetch for the newly-cloned session so the
  // navigation target page renders without falling through to the
  // real backend.
  const newId = opts.cloneResponse.id as string;
  await page.route(`**/api/sessions/${newId}`, (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(opts.cloneResponse),
      });
    }
    return route.continue();
  });
  await page.route(`**/api/sessions/${newId}/saves`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    });
  });
  await page.route(`**/api/sessions/${newId}/cheats`, (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ cheatsEnabled: false, enabledIndices: [] }),
    });
  });

  return cloneCalls;
}

test.describe("Clone session (#553)", () => {
  test("US-2: clone from session-detail header `…` menu → navigates to new session", async ({
    page,
  }) => {
    const cloneResponse = mockSession({
      id: "11",
      name: "Main Playthrough (Copy)",
      pinnedCoreSha256: "ab12cd34",
    });
    const cloneCalls = await setupSessionDetailRoutes(page, {
      session: mockSession(),
      saves: [
        mockSave({ id: "101", name: "Checkpoint 1", isCurrent: true }),
        mockSave({
          id: "102",
          name: "Auto Save",
          isAuto: true,
          isCurrent: false,
        }),
      ],
      cloneResponse,
    });

    await page.goto("/sessions/10");

    await expect(
      page.getByRole("heading", { name: "Main Playthrough" }),
    ).toBeVisible();

    // Open the header `…` menu and click Clone session.
    const headerActions = page.getByTestId("session-header-actions");
    await headerActions.getByTestId("actions-menu-btn").click();
    await page.getByRole("menuitem", { name: /clone session/i }).click();

    // Dialog pre-fills name with "{source} (Copy)".
    const dialog = page.getByTestId("clone-session-dialog");
    await expect(dialog).toBeVisible();
    await expect(page.getByTestId("clone-session-name-input")).toHaveValue(
      "Main Playthrough (Copy)",
    );

    await page.getByTestId("clone-session-confirm").click();

    // Expect navigation to the new session's detail page.
    await expect(page).toHaveURL(/\/sessions\/11$/, { timeout: 10_000 });

    // And the clone call hit the *new* endpoint with the *new* body shape.
    expect(cloneCalls.length).toBe(1);
    expect(cloneCalls[0].url).toMatch(/\/api\/sessions\/10\/clone(\?|$)/);
    // No saveId query param for US-2 (whole-session clone).
    expect(cloneCalls[0].url).not.toContain("saveId=");
    expect(cloneCalls[0].method).toBe("POST");
    expect(JSON.parse(cloneCalls[0].body)).toEqual({
      name: "Main Playthrough (Copy)",
    });
  });

  test("US-3: clone from a per-save `…` menu posts saveId as a query param", async ({
    page,
  }) => {
    const cloneResponse = mockSession({
      id: "12",
      name: "Main Playthrough (Copy)",
    });
    const cloneCalls = await setupSessionDetailRoutes(page, {
      session: mockSession(),
      saves: [
        mockSave({ id: "101", name: "Checkpoint 1", isCurrent: true }),
        mockSave({
          id: "102",
          name: "Second-most-recent",
          isAuto: false,
          isCurrent: false,
        }),
      ],
      cloneResponse,
    });

    await page.goto("/sessions/10");

    // Open the per-save actions menu for save 102 and click
    // "Clone from this save". Save 102 is the second-most-recent
    // save in the fixture, exercising the US-3 branch that seeds
    // the clone from a non-current save.
    const saveActions = page.getByTestId("save-actions-102");
    await saveActions.getByTestId("actions-menu-btn").click();
    await page
      .getByRole("menuitem", { name: /clone from this save/i })
      .click();

    const dialog = page.getByTestId("clone-session-dialog");
    await expect(dialog).toBeVisible();
    await expect(
      dialog.getByText(/cloning from save "second-most-recent"/i),
    ).toBeVisible();

    // User tweaks the name to something meaningful before confirming.
    const input = page.getByTestId("clone-session-name-input");
    await input.fill("Before the boss");
    await page.getByTestId("clone-session-confirm").click();

    await expect(page).toHaveURL(/\/sessions\/12$/, { timeout: 10_000 });

    expect(cloneCalls.length).toBe(1);
    // The UI must forward the save's numeric ID as ?saveId= so the
    // backend seeds the new session with *this* save, not the most
    // recent one.
    expect(cloneCalls[0].url).toContain("saveId=102");
    expect(JSON.parse(cloneCalls[0].body)).toEqual({
      name: "Before the boss",
    });
  });

  test("Clone entry points are `…`-menu only — no standalone CTAs (#553)", async ({
    page,
  }) => {
    await setupSessionDetailRoutes(page, {
      session: mockSession(),
      saves: [mockSave({ id: "101", name: "Checkpoint 1" })],
      cloneResponse: mockSession({ id: "13", name: "Main Playthrough (Copy)" }),
    });

    await page.goto("/sessions/10");
    await expect(
      page.getByRole("heading", { name: "Main Playthrough" }),
    ).toBeVisible();

    // PO guidance: cloning is secondary and must never be a top-level
    // button in any page chrome. Before anyone opens a `…` menu, no
    // clone affordance should be reachable by role/name.
    await expect(
      page.getByRole("button", { name: /^clone session$/i }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("button", { name: /^clone from this save$/i }),
    ).toHaveCount(0);
    await expect(
      page.getByRole("menuitem", { name: /clone session/i }),
    ).toHaveCount(0);
  });

  test("US-1: shared-session detail → `…` menu → Clone to my library", async ({
    page,
  }) => {
    // Fixture for a shared session whose backing personal session id
    // is "10". Clone to my library must target *that* id, not the
    // shared-session wrapper's id ("ss-1").
    const shared = {
      id: "ss-1",
      name: "Friday Night NES",
      gameId: "1",
      gameTitle: "Super Mario Bros.",
      gameCoverUrl: "",
      consoleName: "NES",
      ownerId: "2",
      ownerUsername: "bob",
      status: "active",
      memberCount: 2,
      sessionId: "10",
      createdAt: "2026-02-01T10:00:00Z",
      updatedAt: "2026-02-13T10:00:00Z",
      members: [
        {
          userId: "2",
          username: "bob",
          role: "owner",
          joinedAt: "2026-02-01T10:00:00Z",
        },
        {
          userId: "1",
          username: "admin",
          role: "member",
          joinedAt: "2026-02-02T10:00:00Z",
        },
      ],
    };

    const cloneResponse = mockSession({
      id: "14",
      name: "Friday Night NES (Copy)",
    });
    const cloneCalls = await setupSessionDetailRoutes(page, {
      session: mockSession({ id: "10", name: "Friday Night NES" }),
      saves: [],
      cloneResponse,
    });

    await page.route("**/api/shared-sessions/ss-1", (route) => {
      if (route.request().method() === "GET") {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify(shared),
        });
      }
      return route.continue();
    });
    await page.route("**/api/shared-sessions/ss-1/saves", (route) => {
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
            emulatorJsCore: "nes",
            shortName: "NES",
            extensions: [".nes"],
          },
        ]),
      });
    });

    await page.goto("/shared-sessions/ss-1");
    await expect(
      page.getByRole("heading", { name: "Friday Night NES" }),
    ).toBeVisible();

    const heroActions = page.getByTestId("shared-session-hero-actions");
    await heroActions.getByTestId("actions-menu-btn").click();
    await page
      .getByRole("menuitem", { name: /clone to my library/i })
      .click();

    const dialog = page.getByTestId("clone-session-dialog");
    await expect(dialog).toBeVisible();
    await expect(page.getByTestId("clone-session-name-input")).toHaveValue(
      "Friday Night NES (Copy)",
    );

    await page.getByTestId("clone-session-confirm").click();

    // Navigates to the new personal session's detail page — the user
    // is now out of the shared-session wrapper.
    await expect(page).toHaveURL(/\/sessions\/14$/, { timeout: 10_000 });

    expect(cloneCalls.length).toBe(1);
    // Key assertion: the request is keyed on the *backing session
    // ID* (10), not the shared-session wrapper ID (ss-1).
    expect(cloneCalls[0].url).toMatch(/\/api\/sessions\/10\/clone(\?|$)/);
    expect(JSON.parse(cloneCalls[0].body)).toEqual({
      name: "Friday Night NES (Copy)",
    });
  });
});
