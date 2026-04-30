# UX Proposal: ScreenScraper "Test Credentials" Button

## Current State

The admin settings page (`web/src/pages/admin/settings-page.tsx`) has a "ScreenScraper Credentials" card with Username and Password inputs in a 2-column grid. There is no way to verify if the credentials work. The only action is the page-level "Save Settings" button at the bottom.

## Design System Components Available

- `Button` — variants: primary, secondary, ghost, danger. Sizes: sm, md, lg. Has `loading` prop with spinner.
- `Badge` — variants: default, brand, success, warning, danger. Pill-shaped inline indicator.
- `Input` — has `label` and `error` props.
- `useToast` — success/error/info toasts, auto-dismiss after 4s, slide-in from right.
- `Card`, `CardHeader`, `CardContent` — standard card layout used for all settings sections.

## Proposal

### Button Placement

Place a "Test Credentials" button **below the credential inputs, left-aligned** within the same `CardContent`. This keeps it visually grouped with the credentials it tests, separate from the page-level "Save Settings" button.

```
+----------------------------------------------------------+
| [Key icon] ScreenScraper Credentials                     |
| Required for metadata scraping. Get credentials at ...   |
|                                                          |
|  Username                    Password                    |
|  +------------------------+  +------------------------+  |
|  | myuser                 |  | ********               |  |
|  +------------------------+  +------------------------+  |
|                                                          |
|  [Test Credentials]   <-- secondary button, left-aligned |
|                                                          |
+----------------------------------------------------------+
```

After clicking, the status feedback appears **inline, to the right of the button**, using a `Badge` component. This is better than a toast because:
- It stays visible (toasts auto-dismiss after 4s, which is too fleeting for a credential check result)
- It's spatially associated with the action
- It doesn't compete with the "Save Settings" toast

### Button States

| State | Button text | Button variant | Button loading | Badge |
|-------|------------|---------------|----------------|-------|
| Idle (no test run yet) | Test Credentials | `secondary` | `false` | None |
| Testing... | Test Credentials | `secondary` | `true` (spinner) | None |
| Success | Test Credentials | `secondary` | `false` | `success` "Authenticated" with CheckCircle icon |
| Error (bad creds) | Test Credentials | `secondary` | `false` | `danger` "Authentication failed" with AlertCircle icon |
| Error (network/server) | Test Credentials | `secondary` | `false` | `danger` "Connection failed" with AlertCircle icon |
| Fields empty | Test Credentials | `secondary` | `disabled` | None |

### ASCII Mockups

**Idle state (fields populated):**
```
  Username                    Password
  +------------------------+  +------------------------+
  | myuser                 |  | ********               |
  +------------------------+  +------------------------+

  [ Test Credentials ]
```

**Idle state (fields empty -- button disabled):**
```
  Username                    Password
  +------------------------+  +------------------------+
  |                        |  |                        |
  +------------------------+  +------------------------+

  [ Test Credentials ]  (disabled/dimmed)
```

**Loading state:**
```
  Username                    Password
  +------------------------+  +------------------------+
  | myuser                 |  | ********               |
  +------------------------+  +------------------------+

  [ (o) Test Credentials ]    <-- (o) = spinner
```

**Success state:**
```
  Username                    Password
  +------------------------+  +------------------------+
  | myuser                 |  | ********               |
  +------------------------+  +------------------------+

  [ Test Credentials ]   [v Authenticated]
                          ^-- success Badge (green)
```

**Error state (bad credentials):**
```
  Username                    Password
  +------------------------+  +------------------------+
  | myuser                 |  | ********               |
  +------------------------+  +------------------------+

  [ Test Credentials ]   [! Authentication failed]
                          ^-- danger Badge (red)
```

**Error state (network/server error):**
```
  Username                    Password
  +------------------------+  +------------------------+
  | myuser                 |  | ********               |
  +------------------------+  +------------------------+

  [ Test Credentials ]   [! Connection failed]
                          ^-- danger Badge (red)
```

### Status Persistence & Transitions

- **Badge appears** with a subtle fade-in after the API response returns.
- **Badge persists** until the user:
  1. Modifies either the Username or Password input (badge clears immediately -- stale result is misleading), OR
  2. Clicks "Test Credentials" again (badge clears, spinner starts).
- **No auto-dismiss timer.** The result stays visible so the admin can confirm it before saving. This differs from the toast pattern intentionally.

### Interaction with Save Button

- "Test Credentials" does NOT save settings. It sends the current form values (unsaved) to a test endpoint.
- The admin can test credentials before or after saving -- both flows work.
- Testing does not block or affect the "Save Settings" button in any way.
- Recommended flow for the user: enter credentials, test them, then save if authenticated.

### Error Message Content

| Failure Mode | Badge Text | Badge Variant |
|-------------|-----------|---------------|
| Invalid username/password (401/403) | Authentication failed | `danger` |
| ScreenScraper API unreachable | Connection failed | `danger` |
| Server-side error (500) | Connection failed | `danger` |
| Network error (no response) | Connection failed | `danger` |
| Both fields empty | (button disabled, no badge) | -- |
| One field empty | (button disabled, no badge) | -- |

We keep error messages short and actionable. "Authentication failed" clearly tells the admin their credentials are wrong. "Connection failed" tells them the issue is infrastructure, not credentials. No need for verbose messages -- the admin audience is technical.

### Implementation Details

**Components used:**
- `Button` variant="secondary" with `loading` and `disabled` props -- already exists
- `Badge` variant="success" or "danger" -- already exists
- No new shared components needed

**New backend endpoint needed:**
- `POST /api/admin/screenscraper/test` -- accepts `{ username, password }`, returns `{ success: boolean, error?: string }`
- This endpoint should attempt a lightweight ScreenScraper API call (e.g., `ssuserInfos.php`) to validate the credentials without side effects.

**New frontend hook needed:**
- `useTestScreenScraperCredentials()` mutation in `use-admin.ts` that calls the new endpoint.

**State management:**
- Local `useState` for the test result: `"idle" | "success" | "error"` plus error message string.
- Clear state when username or password inputs change (via `useEffect` or `onChange` wrapper).
- The mutation's `isPending` drives the button's `loading` prop.

### Accessibility

- Badge uses semantic color + icon (not color alone) to convey success/failure.
- Button has proper disabled state when fields are empty.
- Loading spinner is already part of the Button component's accessible markup.
- Badge text is readable by screen readers as it appears in the DOM.

### Summary of Design Decisions

1. **Inline Badge over Toast** -- persists, spatially associated, no auto-dismiss confusion.
2. **Secondary button variant** -- this is a secondary action; primary is "Save Settings".
3. **Left-aligned below inputs** -- follows the natural reading flow, grouped with the credential fields.
4. **Clear on input change** -- prevents stale/misleading status display.
5. **Disabled when empty** -- prevents unnecessary API calls and error noise.
6. **Two error messages only** -- "Authentication failed" vs "Connection failed" covers all cases clearly for a technical admin audience.
