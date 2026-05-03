# UX Proposal: IGDB Admin Configuration

## Overview

Replace the ScreenScraper credentials card and "Default Scraper Source" dropdown with a comprehensive IGDB/Twitch configuration experience. The design must guide the admin through the setup process with clear instructions, real-time feedback, and persistent status indicators.

---

## 1. IGDB Configuration Card (replaces ScreenScraper card)

This card replaces both the ScreenScraper Credentials card AND the "Default Scraper Source" Select from the General card. IGDB is now the only scraper, so the dropdown is unnecessary.

### State: Unconfigured (empty fields)

```
+----------------------------------------------------------+
|  IGDB Configuration                                      |
|  [Badge: "Not configured" (warning variant)]             |
|                                                          |
|  Spela uses IGDB (Internet Game Database) to fetch       |
|  game metadata, cover art, and descriptions. IGDB        |
|  requires Twitch developer credentials to authenticate.  |
|                                                          |
|  +----------------------------------------------------+  |
|  |  How to get your credentials                    [v] |  |
|  |                                                     |  |
|  |  1. Go to dev.twitch.tv/console and log in         |  |
|  |     (create a Twitch account if needed)             |  |
|  |  2. Click "Register Your Application"               |  |
|  |  3. Fill in:                                        |  |
|  |     - Name: anything (e.g. "Spela")                 |  |
|  |     - OAuth Redirect URL: http://localhost          |  |
|  |     - Category: "Application Integration"           |  |
|  |  4. Copy the Client ID from your app dashboard      |  |
|  |  5. Click "New Secret" to generate a Client Secret  |  |
|  |  6. Paste both values below                         |  |
|  +----------------------------------------------------+  |
|                                                          |
|  Client ID                                               |
|  [______________________________________]                |
|                                                          |
|  Client Secret                                           |
|  [______________________________________] (password)     |
|                                                          |
|  [Test Connection]                                       |
+----------------------------------------------------------+
```

**Design notes:**
- Card icon: `Globe` from lucide-react (represents external service / internet database)
- Title: "IGDB Configuration" with `Globe` icon in `text-brand-400`
- Status badge sits next to the title, right-aligned or inline after title text
- Unconfigured badge: `Badge variant="warning"` with text "Not configured"
- Description paragraph: `text-sm text-surface-400`, 2-3 lines max
- Setup instructions: Collapsible section using a button/disclosure pattern. Starts **expanded** when credentials are empty, **collapsed** when credentials are filled in. Uses a subtle `bg-surface-800/50 rounded-xl p-4` container.
- The `dev.twitch.tv/console` text is a clickable link (`text-brand-400 hover:text-brand-300 underline`), opens in new tab
- Input fields: Side by side in a `grid grid-cols-1 sm:grid-cols-2 gap-4` layout (same as current ScreenScraper card)
- Client Secret uses `type="password"`
- "Test Connection" button: `Button variant="secondary"`, disabled when either field is empty

### State: Testing (connection test in progress)

```
|  [Test Connection (loading spinner)]                     |
```

- Button shows loading spinner (uses existing `loading` prop on Button)
- Button is disabled during test
- No badge change yet -- wait for result

### State: Test Successful

```
|  [Test Connection]  [Badge: checkmark "Connected" (success)]  |
```

- Badge: `Badge variant="success"` with `CheckCircle2` icon + "Connected"
- Same pattern as current ScreenScraper "Authenticated" badge
- Status badge next to the title also changes: `Badge variant="success"` with "Connected"

### State: Test Failed

```
|  [Test Connection]  [Badge: alert "Invalid credentials" (danger)]  |
```

- Badge: `Badge variant="danger"` with `AlertCircle` icon + error message from server
- Common errors: "Invalid credentials", "Connection failed", "Rate limited"
- Status badge next to title remains "Not configured" (warning) since credentials aren't valid

### State: Configured and Saved (persistent status)

After saving settings with valid tested credentials:

```
+----------------------------------------------------------+
|  IGDB Configuration                                      |
|  [Badge: checkmark "Connected" (success)]                |
|                                                          |
|  Spela uses IGDB (Internet Game Database) to fetch       |
|  game metadata, cover art, and descriptions. IGDB        |
|  requires Twitch developer credentials to authenticate.  |
|                                                          |
|  [> How to get your credentials]  (collapsed)            |
|                                                          |
|  Client ID                                               |
|  [abc123xxxxxxxxxx_____________________]                 |
|                                                          |
|  Client Secret                                           |
|  [*************************************]                 |
|                                                          |
|  [Test Connection]  [Badge: checkmark "Connected"]       |
+----------------------------------------------------------+
```

- Setup instructions section is **collapsed** (since credentials are already filled)
- The card-level status badge shows "Connected" (success variant)
- If the admin edits either field, both the card-level badge and inline badge reset to reflect the untested state

### State: Credentials Become Invalid (token refresh failure)

If the server detects during a scrape that the IGDB token is expired or credentials are invalid:

```
+----------------------------------------------------------+
|  IGDB Configuration                                      |
|  [Badge: alert "Connection issue" (warning)]             |
|  ...                                                     |
|  [Test Connection]  [Badge: alert "Token expired..." (danger)]  |
+----------------------------------------------------------+
```

- This state is driven by server-side status. The admin settings GET endpoint should include an `igdb_status` field ("connected", "not_configured", "error") and optional `igdb_error` message.
- The card-level badge shows "Connection issue" in warning variant
- Inline error shows specific message from server

---

## 2. Warning Banner on Settings Page

Shown at the top of the settings page when IGDB is not configured. Uses the same pattern as `BiosWarningBanner`.

```
+----------------------------------------------------------+
|  [AlertTriangle icon]                                    |
|  Game metadata is unavailable. IGDB credentials have     |
|  not been configured. Game cover art, descriptions,      |
|  and other metadata will not be fetched during scans.    |
|                                                          |
|  Configure IGDB below                                    |
+----------------------------------------------------------+
```

**Design notes:**
- Same styling as `BiosWarningBanner`: `rounded-xl bg-warning-500/10 border border-warning-500/30 px-4 py-3`
- `AlertTriangle` icon in `text-warning-500`
- Message text in `text-sm text-warning-400`
- "Configure IGDB below" is an anchor link (`text-brand-400 hover:text-brand-300 underline`) that scrolls to the IGDB card (or simply serves as visual guidance since the card is on the same page, just below)
- No dismiss button -- this banner should persist until IGDB is configured. The admin needs to fix it.
- This banner only appears on the settings page itself, so a dismiss is unnecessary.

---

## 3. Dashboard Warning Banner

Shown on the dashboard page for admin users when IGDB is not configured. Brief and actionable.

```
+----------------------------------------------------------+
|  [AlertTriangle icon]                                    |
|  IGDB is not configured. Game metadata will not be       |
|  fetched during library scans.                           |
|                                                          |
|  Go to Settings                              [X dismiss] |
+----------------------------------------------------------+
```

**Design notes:**
- Same `BiosWarningBanner` component pattern
- `AlertTriangle` icon, warning color scheme
- "Go to Settings" links to `/admin/settings` (using react-router `Link`)
- Dismissible (X button), same as the BIOS banner -- admin may want to dismiss until later
- Uses the same `useState` dismiss pattern as `biosDismissed` in `DashboardPage`
- Only shown to admin users (`isAdmin` check)
- Positioned after the BIOS warning banner (if both are showing), before `PersonalStatsCard`

---

## 4. Sidebar Warning Indicator

When IGDB is not configured, the "Settings" link in the admin sidebar should show a warning dot.

```
  Admin
    Users
    Settings  [orange dot]     <-- warning indicator
    BIOS Files [orange dot]    <-- existing pattern
    Library Scan
    Metadata Fix
```

**Design notes:**
- Uses the existing `warning` property on the sidebar link item (already supported by the `SidebarLink` component -- it renders an orange dot)
- In `app-layout.tsx`, add an `igdbConfigured` check (from a new hook or by extending `useServerSettings`) and set `warning: !igdbConfigured` on the Settings link
- This pattern is already established for BIOS files, so it will feel familiar

---

## 5. General Card Changes

Remove the "Default Scraper Source" `Select` from the General card. Since IGDB is the only scraper, this dropdown is no longer needed. The General card keeps only:
- Allow Registration toggle
- Auto-scrape on Scan toggle

---

## 6. Component Reuse and Data Flow

### Components to create:
- `IgdbConfigCard` -- new component in `web/src/features/admin/components/igdb-config-card.tsx`
- `IgdbWarningBanner` -- new component in `web/src/features/admin/components/igdb-warning-banner.tsx` (reuses the `BiosWarningBanner` visual pattern)

### Hooks needed:
- `useTestIgdbCredentials` -- mutation hook calling `POST /api/admin/igdb/test` with `{ clientId, clientSecret }`
- `useIgdbStatus` -- query hook calling `GET /api/admin/igdb/status` returning `{ configured: boolean, status: "connected" | "not_configured" | "error", error?: string }`
- Extend `useServerSettings` / `useUpdateSettings` to include `igdb_client_id` and `igdb_client_secret` fields

### Server settings keys (stored in `ServerSetting` table):
- `igdb_client_id` -- Twitch Client ID
- `igdb_client_secret` -- Twitch Client Secret (encrypted at rest, ideally)

### Server endpoints needed:
- `POST /api/admin/igdb/test` -- Accepts `{ clientId, clientSecret }`, attempts Twitch OAuth token exchange, returns `{ success: boolean, error?: string }`
- `GET /api/admin/igdb/status` -- Returns current IGDB connection status `{ configured: boolean, status: string, error?: string }`

---

## 7. Interaction Details

### Field editing resets test status
When the admin changes either the Client ID or Client Secret input, reset the inline test status badge to "idle" (hidden). This prevents stale "Connected" badges when credentials have been modified.

### Save button behavior
The existing page-level "Save Settings" button at the bottom saves all settings including IGDB credentials. No separate save button on the IGDB card.

### Collapsible setup instructions
- Uses a simple toggle with a chevron icon (`ChevronDown` / `ChevronRight`)
- Text: "How to get your credentials"
- Default expanded when both fields are empty, collapsed when either has a value
- Smooth height transition (CSS `transition-all`)

### Test button disabled states
- Disabled when Client ID is empty OR Client Secret is empty
- Disabled while test is in progress (loading state)
- Tooltip or visual cue: button appears muted/grayed when disabled

---

## 8. Summary of Removed Elements

1. **ScreenScraper Credentials card** -- entire card removed (title, username, password inputs, test button)
2. **"Default Scraper Source" Select** -- removed from General card
3. **Related state**: `ssUsername`, `ssPassword`, `scraperSource`, `testStatus`/`testError` for ScreenScraper, `useTestScreenScraperCredentials` hook
4. **Server settings**: `screenscraper_username`, `screenscraper_password`, `defaultScraperSource` keys no longer needed

---

## 9. Accessibility

- All inputs have associated `<label>` elements (via the `label` prop on `Input`)
- Warning banners use `role="alert"` for screen reader announcements
- Collapsible section uses `aria-expanded` attribute
- Status badges use semantic color (not color-only -- they also have text labels like "Connected" or "Not configured")
- External links include `target="_blank" rel="noopener noreferrer"` with a subtle external-link icon or `(opens in new tab)` screen reader text
