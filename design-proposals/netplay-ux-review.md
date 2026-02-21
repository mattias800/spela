# Netplay UX Review and Design Requirements

**Author:** ui-agent
**Date:** 2026-02-15
**Status:** Proposal (Updated to match Phase 1 Acceptance Criteria)

## Summary

This document provides comprehensive UX review and design system requirements for Phase 1 netplay. It covers both the web frontend and the player app (Kotlin/Compose), ensuring consistency with existing patterns (Relay, game detail, emulation overlay) and the Sp* design system.

**Phase 1 constraints that shape UX decisions:**
- **Two players only** (host + one client) -- simplifies all player list UI to exactly two slots
- **Server-relay only** -- no ICE/STUN/peer-to-peer complexity
- **Invite-code-only** -- no matchmaking, no public sessions
- **Supported consoles only** -- NES, SNES, GB, GBA, Genesis
- **Web manages, player app plays** -- web UI cannot join as a player

---

## 1. Design System Audit: What We Have

### Web UI Component Library
- `Button` (primary, secondary, ghost, danger) with loading state
- `Badge` (default, brand, success, warning, danger)
- `Card` / `CardHeader` / `CardContent`
- `EmptyState` (icon + title + description + optional action)
- `Modal` (sm, md, lg, xl) with escape/overlay-close
- `Skeleton` / `GameCardSkeleton` / `GameDetailSkeleton`
- `StateTabNav` / `StateTabItem` for in-page tab switching
- `Toast` (success, error, info) via `useToast`
- `DropdownMenu` for overflow menus

### Player App Sp* Components
- `SpButton` (Primary, Secondary, Outlined, Ghost) with loading and focus ring
- `SpCard` / `SpGradientCard`
- `SpChip` / `SpConsoleChip`
- `SpEmptyState` / `SpEmptyStates` factory methods
- `SpLoadingIndicator`
- `SpProgressBar`
- `SpSnackbar` (Error, Success)
- `SpTopBar` with back navigation
- `SpDialog`
- `SpTextField`
- `SpCoverArt` / `SpHeroCover`
- `GameDetailLayout` / `GameDetailSkeleton`
- Theme tokens: `SpColor`, `SpSpacing`, `SpTypography`

### Existing Patterns to Follow
- **Relay pages** (web + player) are the closest analogy and should be the template
- **InGameOverlay** for the emulation pause menu pattern
- **EmulatorOverlay** for loading/error states during gameplay
- **Online users** for presence/connection status visualization
- **PlayToolbar** for the toolbar during web emulation

---

## 2. Screen Inventory: Required Netplay Screens

### 2a. Web Frontend Screens

The web UI manages sessions but **does NOT allow playing**. The actual gameplay happens only in the player app.

| Screen | Route | Purpose |
|--------|-------|---------|
| Netplay Sessions List | `/netplay` | Browse active/recent sessions, create new sessions |
| Netplay Session Detail | `/netplay/:id` | View session status, invite code, player info |

Note: No "in-game" web screen. Web shows a message directing users to the player app (AC-4). No admin netplay page in Phase 1 scope.

### 2b. Player App Screens

| Screen | Purpose |
|--------|---------|
| NetplayLobbyScreen | Pre-game lobby with host/client, input delay config, readiness |
| Netplay In-Game HUD | Overlay elements during active netplay session |
| Join flow (dialog) | Code entry dialog, accessible from home/game detail |

Note: No dedicated `NetplayListScreen` needed in Phase 1. Sessions are created from game detail, joined from home screen or a "Join Netplay" flow. The home screen shows an active session card (AC-15).

---

## 3. Loading / Error / Empty States

Every netplay screen must handle these three states. This is non-negotiable.

### 3a. Web Frontend

**Sessions List Page (`/netplay`)**
- **Loading:** Vertical list of skeleton rows (3-4 rows), each showing: cover art skeleton (48x64), text skeletons for title/status/code. Match the `InvitationsSkeleton` pattern from `relays-page.tsx`.
- **Empty:** `EmptyState` with `Gamepad2` icon, title: "No netplay sessions yet", description: "Create one to play with a friend.", action: Button "Create Session" (AC-14)
- **Error:** Toast notification on fetch failure. Maintain stale data if available.

**Session Detail (`/netplay/:id`)**
- **Loading:** Custom skeleton matching `RelayDetailSkeleton` pattern -- cover art skeleton, title skeleton, two player slot skeletons
- **Not Found:** `EmptyState` with `Gamepad2` icon, title: "Session not found", description: "This session may have ended or the invite code has expired.", action: Button "Go back"
- **Session Expired:** `EmptyState` with `Clock` icon, title: "Session expired", description: "No one joined within 15 minutes. You can create a new session.", action: Button "Create New Session" (AC-12)
- **Session Ended:** Show final status with end reason. "Session ended: [Host/Client] left" or "Session ended: Timed out" (from `end_reason` field).

### 3b. Player App

**Home Screen Netplay Section (AC-15)**
- Shown only when there is an active session or pending invite -- no empty state needed
- **Active session card:** Cover art + "[Game] with [Username] -- Tap to rejoin"
- **No active session:** Section is simply not rendered

**NetplayLobbyScreen (AC-5)**
- **Loading (joining):** `SpLoadingIndicator(message = "Joining session...")`
- **Lobby idle (waiting for client):** Show pulsing "Waiting for player..." indicator (see Section 7b)
- **Error states (AC-13):** `SpSnackbar` for each specific error message:
  - "Session not found. The code may have expired or the host may have cancelled."
  - "This session already has two players."
  - "You need to download [Game Name] first" with action button to game detail
  - "Both players must have the same version of [Game Name]. Please re-download the game."
  - "You can't join your own session."
- **Session expired while waiting:** Full-screen message: "Session expired -- no one joined. You can create a new session." with "Create New Session" action (AC-12)

**In-Game Netplay HUD**
- **Connecting:** Overlay matching `InGameOverlay` scrim pattern, with `SpLoadingIndicator` centered, message: "Starting game..."
- **Connected:** Small connection status pill in top-left (below status bar insets)
- **Disconnected (client or host):** Full-screen overlay with countdown timer: "[Username] disconnected. Waiting for reconnection... [60s countdown]" (AC-9, AC-10)
- **Reconnection failed:** "[Username] disconnected. Session ended." with "OK" button returning to home (or continuing single-player for host per AC-9)
- **Paused:** "Paused by [Username]" overlay centered on screen. After 5 minutes: adds "Waiting for [username] to resume. You can leave the session." with "Leave" button (AC-8)

---

## 4. Connection Status Visualization

### 4a. Connection Status Badge (New Shared Component)

**Web:** Create a `ConnectionStatusBadge` component using `Badge`:
```
Badge variants by state:
- "connected"    -> variant="success", pulsing green dot + "Connected"
- "connecting"   -> variant="warning", spinning icon + "Connecting..."
- "disconnected" -> variant="danger", "Disconnected"
```

Note: No "degraded" state in Phase 1. The latency thresholds from AC-5 are:
- Green: < 50ms
- Yellow: < 150ms
- Red: > 150ms

**Player App:** Create `SpConnectionBadge` composable:
```
States map to:
- Connected    -> SpColor.Success, green dot
- Connecting   -> SpColor.Warning, animated indicator
- Disconnected -> SpColor.Error, red dot
```

Use `RoundedCornerShape(20.dp)` to match `SpChip` shape language. Include a subtle pulse animation on the green dot for the "connected" state to convey liveness.

### 4b. In-Game Connection Indicator (AC-7)

During gameplay, the netplay HUD must show:
- The other player's username (small, top corner, semi-transparent)
- Current ping (updated every 2 seconds)
- If ping exceeds 200ms: a subtle warning icon (no intrusive popup)
- The overlay can be toggled off in emulation settings

**Player App:** Add to the existing `InGameOverlay` area. Position: top-left, mirroring the FPS HUD in top-right. Use same `SpColor.Scrim` background, `SpTypography.LabelSmall` text, `RoundedCornerShape(8.dp)` matching the FPS pill.

Content: `[username] [colored-dot] [42ms]`

Ping color thresholds (matching AC-5 lobby thresholds):
| Ping | Color | Token |
|------|-------|-------|
| < 50ms | Green | SpColor.Success / text-success-500 |
| 50-150ms | Yellow | SpColor.Warning / text-warning-500 |
| > 150ms | Red | SpColor.Error / text-danger-500 |

At > 200ms, add a small `AlertTriangle` icon next to the ping value (AC-7).

Fade to low opacity (0.3) after 5s of stable connection. Become fully visible on status change or when pausing.

**Web (session detail page only -- web does not show in-game):** Show real-time session status with connection info for both players if the session is in_progress.

---

## 5. Latency and Desync Communication

### 5a. Latency Display

Show latency as a simple number in milliseconds. Use the color coding from Section 4b.

Display format: `42ms` -- no label needed, the "ms" suffix is self-explanatory.

### 5b. Desync Communication

Phase 1 uses deterministic lockstep, so desync should theoretically not occur for supported consoles. However, if it does:

**Player App:** Persistent warning banner (not auto-dismissing):
- Background: `SpColor.WarningContainer`
- Text color: `SpColor.Warning`
- Text: "Game state may be out of sync. If gameplay looks wrong, try leaving and recreating the session."
- Position: top of emulator area, below status bar insets
- This is a rare edge case in Phase 1 -- no elaborate resync UI needed yet

### 5c. In Pause Menu (Netplay Mode)

When the in-game overlay (pause menu) is open during netplay, modify the panel:

**Session Info section showing:**
- Other player's username + connection status badge
- Current ping value
- Input delay setting (display only for client, adjustable for host)
- Session duration

**Modified action buttons:**
- **Remove:** "Save", "Load", "Fast Forward", "Screenshot" -- these break sync or are not applicable (save/load deferred per scope, fast-forward breaks lockstep)
- **Keep:** "Controls" (key mapping dialog)
- **Replace:** "Exit Game" becomes "Leave Session" with netplay-specific confirmation (AC-11)
- **Add:** "Input Delay" adjustment for host (slider or +/- buttons, 1-10 frames)

**Leave Session confirmation dialog (AC-11):**
- Title: "Leave Netplay Session?"
- Message: "The other player will be disconnected."
- Actions: "Keep Playing" (outlined) + "Leave Session" (primary)
- Match the existing exit confirmation dialog pattern in `InGameOverlay`

---

## 6. Invite Code / Join Flow

### 6a. Code Format (AC-1, AC-2)

6-character uppercase alphanumeric code. Exclude ambiguous characters: 0/O, 1/I/L. This gives a clean character set: `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (30 chars, 30^6 = 729M combinations).

Display format: `A7K3MN` (no dash/separator for 6 chars -- short enough to not need one).

### 6b. Session Creation from Game Detail (AC-1, AC-2)

**Player App (AC-1):**
On the game detail screen, for supported consoles only (NES, SNES, GB, GBA, Genesis), add a "Netplay" button in the action buttons row (next to Play, Favorite, Play Later).

- Button: `SpButton(text = "Netplay", style = SpButtonStyle.Outlined)` with a gamepad/wifi icon
- Tapping creates a session via `POST /api/netplay/sessions` and navigates to `NetplayLobbyScreen`
- For unsupported consoles: button is **hidden** (not disabled -- cleaner UX, matches AC-16 "hidden or disabled")

**Web (AC-2):**
On the game detail page, add a "Create Netplay Session" button in the `GameHero` action buttons row.
- Button: `Button variant="secondary" size="sm"` with `Gamepad2` icon
- For unsupported consoles: button disabled with `title="Netplay is not yet supported for this console"` (AC-16)
- Clicking creates a session and navigates to `/netplay/:id` session detail page
- Web does NOT allow joining as a player (AC-4) -- show message: "Open this session in the Spela player app to join."

### 6c. Code Display in Lobby (Host Waiting)

**Player App lobby:**
```
Invite Code
[ A7K3MN ]  [Copy Code]
```
- Code: `SpTypography.DisplaySmall`, `fontFamily = FontFamily.Monospace`, `letterSpacing = 4.sp`
- Container: `SpCard` with `SpColor.SurfaceVariant` background, `SpSpacing.XLarge` padding
- Copy button: `SpButton(text = "Copy Code", style = SpButtonStyle.Outlined)` -- copies to clipboard, shows `SpSnackbar` "Code copied to clipboard"
- On Android: additional "Share" button using platform share intent

**Web session detail:**
```
Invite Code
[ A7K3MN ]  [Copy]
```
- Code: `font-mono text-3xl tracking-[0.3em] text-surface-100`
- Container: `Card` with centered layout
- Copy button: `Button variant="secondary" size="sm"` -- on click, copy to clipboard + swap icon to checkmark for 2 seconds + toast "Copied to clipboard"

### 6d. Join Flow (AC-3)

**Player App:**
Two entry points:
1. From home screen: "Join Netplay" button or card in the Netplay section
2. From a dedicated "Join" dialog accessible from the home screen top bar

Flow:
1. `SpDialog` opens with `SpTextField` for 6-char code entry
2. Client-side validation: uppercase, alphanumeric, exactly 6 chars, no ambiguous chars
3. Auto-uppercase input as user types
4. "Join" button disabled until valid format
5. On submit: `POST /api/netplay/sessions/join` with the code
6. **Success:** Show game name, host username, and "Join" confirmation button (AC-3)
7. **Error states (specific messages per AC-3):**
   - Invalid/expired code: "Session not found. The code may have expired or the host may have cancelled."
   - Session full: "This session already has two players."
   - Missing ROM: "You need to download [Game Name] first" with button navigating to game detail page
8. After joining: both see each other's usernames, "Starting game..." loading, auto-launch within 3 seconds (AC-3)

**Web (AC-4):**
On the `/netplay` page, add a "Join Session" section with a text input for the invite code. On submit, navigate to the session detail page. Show message: "Open this session in the Spela player app to join." Web cannot join as a player.

---

## 7. Session List and Lobby Screens

### 7a. Sessions List Page -- Web (`/netplay`) (AC-14)

Follow the `relays-page.tsx` structure. Since Phase 1 has no public sessions and no invitations tab (invite codes only), the tabs are simplified:

**Single list (no tabs needed):** "My Sessions" -- active and recent sessions the user created or joined.

**Header:**
- Title: "Netplay" with `Gamepad2` icon
- Subtitle: "Real-time multiplayer sessions."
- "Create Session" button (opens modal)
- "Join Session" text input + button (inline, like a search bar)

**Session Row (vertical list, not card grid -- sessions have more metadata than relays):**
Each session shows:
- Game cover art thumbnail (48x64, `rounded-lg`)
- Game title + console badge
- Status badge:
  - `waiting` -> `Badge variant="warning"`, text "Waiting"
  - `in_progress` -> `Badge variant="success"`, text "In Progress"
  - `ended` -> `Badge variant="default"`, text "Ended"
- Host/client usernames
- Invite code (for `waiting` sessions only, with copy button)
- Duration or time since creation
- End reason (for `ended` sessions): "Host left", "Client left", "Timed out", "Completed"

Match the `InvitationCard` row layout from `relays-page.tsx` -- horizontal card with cover art left, info right.

**Create Session Modal:**
`Modal size="md"` with:
1. Game search/select (autocomplete text input, filtered to supported consoles only)
2. "Create" button -> creates session, navigates to `/netplay/:id`

No session name field (auto-generated from username + game). No max players (always 2). No visibility toggle (always private).

### 7b. Lobby Screen -- Player App (AC-5)

This is the critical screen. It handles two states: **waiting for client** and **both players connected**.

**Layout:** Single scrollable column.

**Header:**
- `SpTopBar(title = "Netplay Lobby", showBack = true, onBack = { cancel/leave })`

**Game Info Section:**
- `SpCoverArt` (80x107, matching `RelayHeader`)
- Game title: `SpTypography.HeadlineMedium`
- Console chip: `SpConsoleChip`

**Invite Code Section (visible while status == "waiting"):**
- Prominent code display (see Section 6c)
- "Copy Code" button
- "Share" button (Android)

**Players Section:**
Two player "slots" displayed as `SpCard` rows:

Slot 1 (Host -- always filled):
```
[Avatar]  HostUsername  [Crown icon]  [Ping badge: green/yellow/red dot + ms]
```

Slot 2 (Client -- empty or filled):
```
[Empty avatar placeholder]  "Waiting for player..."  [pulsing dot animation]
-- OR --
[Avatar]  ClientUsername  [Ping badge: green/yellow/red dot + ms]
```

The empty slot uses `SpColor.SurfaceVariant` background with a dashed border and pulsing opacity animation (0.3 to 0.6 alpha cycle, 2s period) to feel alive while waiting.

**Input Delay Section (AC-5, host only):**
- Label: "Input Delay"
- Value: "3 frames" with +/- buttons (range 1-10)
- Uses `SpButton(style = SpButtonStyle.Outlined)` for +/- at 40.dp size
- Explanation text below: `SpTypography.BodySmall`, `SpColor.OnBackgroundTertiary`
- Text: "Higher delay = smoother online play. Lower delay = more responsive controls."

Client sees the input delay value but cannot change it. Display as read-only text: "Input Delay: 3 frames (set by host)".

**First-Time Tooltip (AC-17):**
On first netplay lobby visit, show an info card (`SpCard` with `SpColor.PrimaryContainer` background):
- Text: "Online multiplayer uses a small input delay to keep both players in sync. You might notice your button presses take a moment to register. This is normal! Adjust the delay setting if the game feels too sluggish or too choppy."
- "Got it" dismiss button
- Persisted in local preferences (shown once per user)

**Action Buttons:**
- Host (waiting): "Cancel" button (`SpButton style = SpButtonStyle.Outlined`)
- Host (client joined): "Start Game" button (`SpButton style = SpButtonStyle.Primary`) -- becomes available when client has connected
- Client: "Leave" button (`SpButton style = SpButtonStyle.Outlined`)
- Both players present: game auto-launches within 3 seconds (AC-3). Show "Starting game in 3..." countdown text.

**Auto-Launch UX (AC-3):**
When both players are connected:
1. Show "Starting game..." with a 3-second countdown
2. Both player slots show green connected state
3. A `SpProgressBar` (indeterminate or 3-second countdown) appears
4. Game launches automatically
5. If the host wants to adjust input delay first, they can do so during the 3-second window. Any settings change resets the countdown.

### 7c. Session Detail Page -- Web (`/netplay/:id`)

Follow `relay-detail-page.tsx` layout.

**Layout:** `BackButton` + hero section.

**Hero Section (match `RelayHero` pattern):**
- Game cover art (left, `w-48 md:w-64`)
- Session info (right):
  - Game title as `h1`
  - Console badge + status badge
  - Invite code (for waiting sessions) with Copy button
  - Host username + client username (or "Waiting for player...")
  - Input delay value
  - "Cancel Session" button (host, waiting status only)
  - Message: "Open the Spela player app to play" (since web cannot play)

**Status-specific content:**
- `waiting`: Invite code prominent, "Waiting for player..." with animated dots
- `in_progress`: "Game in progress" with player names and duration counter
- `ended`: End reason displayed, session duration, "Create New Session" button

---

## 8. In-Game Overlay Elements for Netplay

### 8a. Player App In-Game Overlay Modifications

The emulation happens only in the player app. Extend the existing `InGameOverlay` composable.

**Netplay HUD (always visible during gameplay, AC-7):**

Position: top-left corner (mirroring FPS HUD in top-right).

Content layout (horizontal):
```
[colored dot] [username] [ping value]
```

Example: `[green dot] PlayerTwo 42ms`

- Background: `SpColor.Scrim`, matching the FPS pill
- Text: `SpTypography.LabelSmall`
- Shape: `RoundedCornerShape(8.dp)`
- Padding: `horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall`
- Tap: opens the full in-game overlay
- Fade: opacity to 0.3f after 5s of stable connection, `animateFloatAsState`
- At > 200ms ping: add `AlertTriangle` icon (16.dp, `SpColor.Warning`)
- HUD can be toggled off via emulation settings (persisted preference)

**Modified Pause Menu (when in netplay mode):**

The `InGameOverlay` panel shows different content during netplay:

```
[Game Title]
[Ping: 42ms]  [Session: 12:34]

[Controls]     (only action button)

[Input Delay: 3 frames]  [+] [-]    (host only)
"Set by host: 3 frames"             (client only)

[ Leave Session ]  [ Resume ]
```

Actions removed during netplay: Save, Load, Screenshot, Fast Forward. These either break sync or are out of scope (AC defers save/load during netplay).

"Leave Session" shows the confirmation dialog per AC-11.

**Pause Propagation (AC-8):**

When either player pauses:
- Both emulators pause simultaneously
- Both see overlay: "Paused by [Username]" centered in large text (`SpTypography.HeadlineMedium`)
- Either player can tap "Resume" to unpause both
- After 5 minutes of pause: add text below: "Waiting for [username] to resume. You can leave the session." with `SpButton(text = "Leave Session", style = SpButtonStyle.Outlined)`

**Disconnect Handling (AC-9, AC-10):**

Full-screen scrim overlay (match `InGameOverlay` scrim):
- Title: "[Username] disconnected"
- Subtitle: "Waiting for reconnection..."
- Countdown: large countdown timer from 60s, `SpTypography.DisplayMedium`, `SpColor.Warning`
- The countdown ticks down visually (update every second)
- Cancel button: "Leave Session" (`SpButton style = SpButtonStyle.Outlined`)
- On reconnection: overlay dismisses, "Reconnected!" toast via `SpSnackbar` (Success), game resumes
- On timeout (0s): message changes to "[Username] disconnected. Session ended." with:
  - Host: "Continue Solo" (outlined) + "Exit" (primary) -- per AC-9 optional single-player continuation
  - Client: "OK" (primary) -- returns to home screen per AC-10

**Graceful Leave (AC-11):**

When the other player leaves voluntarily:
- Overlay: "[Username] left the session."
- Host: "Continue Solo" + "Exit"
- Client: "OK" -> returns to home

### 8b. Web Play Page

Web does NOT support netplay gameplay. No changes needed to `PlayPage` or `PlayToolbar` for Phase 1.

The web session detail page shows real-time status updates via WebSocket when a session is `in_progress`.

---

## 9. Game Detail Integration

### 9a. Player App -- Netplay Button (AC-1, AC-16)

On `GameDetailScreen`, in the action buttons `Row` (alongside Play, Favorite, Play Later):

**For supported consoles (NES, SNES, GB, GBA, Genesis):**
```kotlin
SpButton(
    text = "Netplay",
    onClick = { viewModel.onIntent(GameDetailIntent.CreateNetplaySession) },
    style = SpButtonStyle.Outlined,
    leadingIcon = {
        Icon(
            imageVector = Icons.Filled.SportsEsports, // or Wifi
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
    },
)
```

**For unsupported consoles:** Button is not rendered (hidden, not disabled).

The game must be downloaded first. If not cached (`!state.isGameCached`), the Netplay button should be disabled with a label change: "Download to Netplay" or simply not shown until downloaded.

### 9b. Web -- Netplay Button (AC-2, AC-16)

On `GameHero`, add to the `extraButtons` or inline button row:

**For supported consoles:**
```tsx
<Button variant="secondary" size="sm" onClick={onCreateNetplay}>
  <Gamepad2 className="h-5 w-5" />
  Create Netplay
</Button>
```

**For unsupported consoles:**
```tsx
<Button variant="secondary" size="sm" disabled title="Netplay is not yet supported for this console">
  <Gamepad2 className="h-5 w-5" />
  Netplay
</Button>
```

### 9c. Player App Home Screen -- Active Session Card (AC-15)

On `HomeScreen`, add a "Netplay" section at the top (before "Continue Playing") when there is an active session:

**Active session:**
- `SpCard` with horizontal layout: cover art (48x64) + "Playing [Game] with [Username]" + "Tap to rejoin"
- Background: subtle gradient using `SpColor.Primary` alpha (match `SpGradientCard` pattern)
- On tap: navigate to emulation screen or lobby, depending on session status

**This section is only shown when the user has an active session.** No empty state needed (AC-15).

---

## 10. Micro-Animations and Transitions

### 10a. Web Animations (CSS/Tailwind)

All animations should use `transition-all duration-300` as baseline, matching existing patterns.

| Element | Animation | Implementation |
|---------|-----------|----------------|
| Client joins lobby | Slide in from right + fade | `animate-in slide-in-from-right-5 fade-in duration-300` (matches toast) |
| Client leaves lobby | Fade out + slide right | `animate-out fade-out slide-out-to-right-5 duration-200` |
| "Waiting for player" dots | Pulsing opacity | `animate-pulse` on 3 dots with staggered delay |
| Session code copy | Check icon swap | 2s timer, icon crossfade |
| Connection status change | Color transition | `transition-colors duration-500` |
| Session status badge change | Background color transition | `transition-colors duration-300` |
| Auto-launch countdown | Number transition | CSS counter or JS interval |

### 10b. Player App Animations (Compose)

Match existing patterns from `InGameOverlay` (`fadeIn() + slideInVertically()`).

| Element | Animation | Implementation |
|---------|-----------|----------------|
| Client joins lobby | `fadeIn() + expandVertically()` | `AnimatedVisibility` on player slot |
| Client leaves lobby | `fadeOut() + shrinkVertically()` | `AnimatedVisibility` on player slot |
| Waiting dot pulse | `InfiniteTransition` on alpha | 0.3f to 0.6f, 2s period |
| Connection pill | `fadeIn() + slideInHorizontally()` | `AnimatedVisibility` for initial appear |
| Connection pill fade | `animateFloatAsState(targetAlpha)` | Opacity to 0.3f after 5s |
| Disconnect overlay | `fadeIn()` | Match existing scrim fade pattern |
| Countdown timer | `animateIntAsState` | Countdown from 60 to 0 |
| Pause overlay appear | `fadeIn() + slideInVertically()` | Match `InGameOverlay` enter transition |
| Auto-launch countdown | `SpProgressBar` with timed progress | 3-second linear progress bar |

---

## 11. Accessibility Requirements

- All interactive elements must have `contentDescription` (player) / `aria-label` (web)
- Connection status changes must be announced to screen readers (web: `aria-live="polite"` region; player: `LiveRegion`)
- Latency values must include unit in screen reader text ("42 milliseconds", not just "42")
- Player join/leave events should be announced (web: polite live region)
- Color is never the sole indicator of status -- always pair with text label and/or icon (ping uses color + ms value + warning icon at >200ms)
- All buttons must have visible focus indicators (existing patterns handle this)
- Disconnect countdown must be announced (screen reader: "Reconnection timeout in [N] seconds")
- Pause attribution: screen reader announces "Game paused by [username]"

---

## 12. Platform Consistency Checklist

| Feature | Web | Player App |
|---------|-----|------------|
| Session list | Row list on `/netplay` page | Home screen active session card (AC-15) |
| Session creation | Modal from game detail/netplay page | Button on game detail screen |
| Session detail | Dedicated page `/netplay/:id` | NetplayLobbyScreen |
| Invite code display | Monospace text + copy button | DisplaySmall text + copy/share |
| Join flow | Text input on `/netplay` page | Dialog with text field |
| Player slots (lobby) | Card per player | SpCard per player |
| Connection indicator | Badge on session detail | HUD pill during gameplay |
| Ping color coding | text-success/warning/danger | SpColor.Success/Warning/Error |
| Disconnect handling | N/A (web doesn't play) | Full-screen overlay + countdown |
| Pause propagation | N/A (web doesn't play) | Overlay with attribution |
| Leave confirmation | N/A (web doesn't play) | SpDialog with confirmation |
| Empty state | EmptyState component | SpEmptyState component |
| Loading state | Skeleton components | SpLoadingIndicator |
| Error state | Toast + inline messages | SpSnackbar + inline |
| Supported console check | Disabled button + tooltip | Hidden button |

---

## 13. New Components Required

### Web (add to `web/src/components/netplay/`)
1. `ConnectionStatusBadge` -- reusable badge showing connection state
2. `SessionCode` -- monospace code display with copy button
3. `NetplayPlayerSlot` -- player slot for lobby (filled or empty/waiting state)
4. `NetplaySessionRow` -- session list row item
5. `NetplayCreateModal` -- session creation modal

### Player App (add to `presentation/ui/components/`)
1. `SpConnectionBadge` -- connection status indicator (green/yellow/red dot + text)
2. `SpSessionCode` -- large monospace code display with copy/share action
3. `SpNetplayPlayerSlot` -- player slot composable (avatar + username + ping + host badge)
4. `SpNetplayHud` -- in-game connection/player HUD overlay
5. `SpCountdownOverlay` -- disconnect countdown overlay (reusable for reconnection timer)
6. Add `SpEmptyStates.NoNetplaySessions()` factory method

### Components NOT needed (Phase 1 simplifications):
- No `PlayerReadyIndicator` -- game auto-launches, no manual ready toggle
- No `LatencyBadge` as separate component -- integrated into `SpConnectionBadge` and HUD
- No `NetplayInGamePill` on web -- web does not host gameplay

---

## 14. Design Tokens Needed

### Web (Tailwind config)
- No new color tokens needed -- existing success/warning/danger cover all connection states
- `font-mono` for session codes (already in Tailwind defaults)

### Player App
- No new SpColor tokens needed -- existing palette covers all states
- No new SpSpacing tokens needed -- existing Small/XSmall work for pill padding

---

## 15. Error Message Specifications (AC-13)

Every error from the acceptance criteria must use specific, actionable messaging. Map each to UI treatment:

| Error | Message | UI Treatment |
|-------|---------|-------------|
| Server unreachable | "Lost connection to server. Attempting to reconnect..." | Persistent banner + auto-retry |
| ROM mismatch | "Both players must have the same version of [Game]. Please re-download the game." | Dialog with "Go to Game" button |
| Unsupported console | "Netplay is not yet supported for [Console]." | Button disabled/hidden (prevention) |
| Session ended | "This session has already ended." | EmptyState on detail page |
| Join own session | "You can't join your own session." | SpSnackbar Error / Toast error |
| Server busy | "The server is busy. Try again in a moment." | SpSnackbar Error / Toast error |
| Session full | "This session already has two players." | SpSnackbar Error / Toast error |
| Session expired | "Session expired -- no one joined. You can create a new session." | Full-screen message with action |
| Code not found | "Session not found. The code may have expired or the host may have cancelled." | Error in join dialog |

Rule: **Never show a generic error.** Every error has a specific message and at least one action the user can take.

---

## 16. Key UX Principles for Netplay

1. **Never leave the user in the dark about connection state.** The HUD must always be visible during gameplay (toggleable off, but on by default).
2. **Errors must be actionable.** "Connection lost" has "Leave Session". "ROM mismatch" has "Go to Game". No dead ends.
3. **The lobby must feel alive.** Pulsing waiting indicator, player join animations, real-time code display. Not a static page.
4. **Session codes must be dead simple.** 6 chars, large monospace, one-tap copy. No friction.
5. **Netplay modifies the emulation experience.** Save/Load/Fast-Forward/Screenshot are removed from the pause menu. "Exit Game" becomes "Leave Session" with different confirmation text. This must be clearly communicated, not silently broken.
6. **Two players is intimate.** With only two players, every state change is personal. "[Username] disconnected" -- name the person. "[Username] paused" -- attribute the action. No anonymous "a player".
7. **Auto-launch reduces friction.** No manual "Ready" toggle needed. Both connected = game starts in 3s. Settings adjustable during the countdown.
8. **Disconnection is recoverable.** 60-second reconnection window with visible countdown. Not instant death.
9. **Consistent with existing patterns.** Relay layouts, InGameOverlay structure, SpCard/SpChip/SpButton, existing color tokens. No new visual language.
10. **Web manages, app plays.** Web is for creating sessions and sharing codes. All actual gameplay is in the player app. This must be clear on every web page.
