import { test, expect, resetServer } from "./fixtures";
import type { BrowserContext, Page } from "@playwright/test";

const API = "http://localhost:8080/api";

// ── Helpers ─────────────────────────────────────────────────────────────────

/** Log in via the API and return an access token. */
async function apiLogin(
  username: string,
  password: string,
): Promise<string> {
  const res = await fetch(`${API}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) throw new Error(`Login failed for ${username}: ${res.status}`);
  const data = await res.json();
  return data.accessToken;
}

/** Get the first game from a netplay-supported console. */
async function getNetplayGame(
  token: string,
): Promise<{ id: string; title: string }> {
  const res = await fetch(`${API}/games?pageSize=100`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw new Error(`Games list failed: ${res.status}`);
  const data = await res.json();
  const supportedConsoles = [
    "Nintendo Entertainment System",
    "Super Nintendo",
    "Game Boy",
    "Game Boy Color",
    "Game Boy Advance",
    "Sega Genesis",
  ];
  const game = data.data?.find((g: { consoleName: string }) =>
    supportedConsoles.includes(g.consoleName),
  );
  if (!game) throw new Error("No netplay-supported game found after scan");
  return { id: String(game.id), title: game.title };
}

/** Log in via the browser UI. Sets auth token in localStorage. */
async function browserLogin(
  page: Page,
  username: string,
  password: string,
): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Username").fill(username);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  // Wait for redirect to dashboard
  await page.waitForURL("**/", { timeout: 10_000 });
}

/** Create a netplay session via API and return the session ID and invite code. */
async function apiCreateSession(
  token: string,
  gameId: string,
): Promise<{ id: string; inviteCode: string }> {
  const res = await fetch(`${API}/netplay/sessions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ gameId }),
  });
  if (!res.ok) throw new Error(`Create session failed: ${res.status}`);
  const session = await res.json();
  return { id: session.id, inviteCode: session.inviteCode };
}

// ── Test ────────────────────────────────────────────────────────────────────

test.describe("Netplay invite flow (two browsers)", () => {
  let adminToken: string;
  let gameId: string;

  test.beforeAll(async () => {
    await resetServer();

    // Get tokens
    adminToken = await apiLogin("admin", "admin123");

    // Games are already scanned by the E2E startup script — just find one
    const game = await getNetplayGame(adminToken);
    gameId = game.id;
  });

  test("admin creates session, invites player, player accepts", async ({
    browser,
  }) => {
    // ── Set up two isolated browser contexts ──
    const adminContext: BrowserContext = await browser.newContext();
    const playerContext: BrowserContext = await browser.newContext();
    const adminPage: Page = await adminContext.newPage();
    const playerPage: Page = await playerContext.newPage();

    try {
      // ── Step 1: Both users log in ──
      await Promise.all([
        browserLogin(adminPage, "admin", "admin123"),
        browserLogin(playerPage, "player", "player123"),
      ]);

      // ── Step 2: Admin creates a netplay session via API ──
      // (Using API for speed — the create modal requires game picker interaction)
      const session = await apiCreateSession(adminToken, gameId);

      // ── Step 3: Admin navigates to session and invites player ──
      await adminPage.goto(`/netplay/${session.id}`);
      await expect(
        adminPage.getByRole("heading", { level: 1 }),
      ).toBeVisible();

      // Click "Invite Player" button
      await adminPage.getByRole("button", { name: /invite player/i }).click();

      // Type "player" in the search input (aria-label is "Search users")
      const searchInput = adminPage.getByLabel(/search users/i);
      await searchInput.fill("player");

      // Wait for search results to appear (debounced 300ms + API call)
      // Then click the "Invite" button next to the "player" user row.
      // Use the dialog scope to avoid matching the "Invite Player" button behind the modal.
      const modal = adminPage.getByRole("dialog");
      const inviteButton = modal.getByRole("button", { name: "Invite", exact: true });
      await expect(inviteButton).toBeVisible({ timeout: 5_000 });
      await inviteButton.click();

      // Wait for success feedback — the modal shows "Invited this session" chip
      await expect(
        modal.getByText(/invited this session/i),
      ).toBeVisible({ timeout: 5_000 });

      // Close the modal
      await modal
        .getByRole("button", { name: "Close", exact: true })
        .click();

      // ── Step 4: Player navigates to /netplay and sees the invite ──
      await playerPage.goto("/netplay");

      // Look for the accept button on the invite card (use first() in case of stale invites)
      const acceptButton = playerPage
        .getByRole("button", { name: /accept/i })
        .first();
      await expect(acceptButton).toBeVisible({ timeout: 10_000 });

      // ── Step 5: Player accepts the invite ──
      await acceptButton.click();

      // After accepting, the invite should disappear or show "accepted"
      // and the player should be navigated to the session page
      await playerPage.waitForURL(`**/netplay/${session.id}`, {
        timeout: 10_000,
      });

      // Verify the session page shows both players
      await expect(playerPage.getByText(/admin/i).first()).toBeVisible();

      // ── Step 6: Admin verifies player joined (reload to get fresh state) ──
      await adminPage.reload();
      await expect(
        adminPage.getByText(/accepted/i).first(),
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await adminContext.close();
      await playerContext.close();
    }
  });

  test("player declines invite", async ({ browser }) => {
    const adminContext = await browser.newContext();
    const playerContext = await browser.newContext();
    const adminPage = await adminContext.newPage();
    const playerPage = await playerContext.newPage();

    try {
      // Both log in
      await Promise.all([
        browserLogin(adminPage, "admin", "admin123"),
        browserLogin(playerPage, "player", "player123"),
      ]);

      // Admin creates session and sends invite via API
      const session = await apiCreateSession(adminToken, gameId);
      const inviteRes = await fetch(
        `${API}/netplay/sessions/${session.id}/invites`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${adminToken}`,
          },
          body: JSON.stringify({ username: "player" }),
        },
      );
      expect(inviteRes.ok).toBe(true);

      // Player goes to /netplay and declines the first invite
      await playerPage.goto("/netplay");
      const declineButton = playerPage
        .getByRole("button", { name: /decline/i })
        .first();
      await expect(declineButton).toBeVisible({ timeout: 10_000 });
      await declineButton.click();

      // Wait briefly for the decline to process
      await playerPage.waitForTimeout(1_000);

      // Admin sees "declined" status
      await adminPage.goto(`/netplay/${session.id}`);
      await expect(
        adminPage.getByText(/declined/i).first(),
      ).toBeVisible({ timeout: 10_000 });
    } finally {
      await adminContext.close();
      await playerContext.close();
    }
  });
});
