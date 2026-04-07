# Controller Status Indicators — Design Spec

**Date:** 2026-04-07
**Phase:** 1 of 3
**Scope:** Player app (Kotlin Multiplatform — shared UI)

## Problem

Local multiplayer on PC and Android lacks the controller identity feedback that consoles provide (PlayStation light bar colors, Wii/Switch player LEDs). When multiple controllers are connected, players cannot see at a glance which player they are or confirm their controller is recognized — they have to dig into settings. This friction discourages local multiplayer.

## Solution

Always-visible, compact controller status indicators that appear automatically when 2+ controllers are connected. Three visual variants adapt to the app's responsive navigation layout. Each connected controller's dot flashes on input, letting players physically confirm "this controller in my hands is P2" without navigating anywhere.

## Design Decisions

- **Visibility rule:** All indicators are hidden when fewer than 2 controllers are connected. Single-player = zero clutter.
- **No per-player colors:** Unlike PlayStation, we don't assign colors to players. Colors would only be visible in the app chrome, not inside emulated games, making them misleading. Green (connected) and white (active flash) are sufficient.
- **Port stability on disconnect:** When a controller disconnects, its port is freed but other players' ports are not renumbered. Reconnecting fills the first available gap, which is typically the same port. This matches console behavior.
- **Phase 1 only:** In-game overlay controller management (Phase 2) and drag-to-assign port picker (Phase 3) are out of scope.

## State Model

A new derived `StateFlow<ControllerStatusState>` on `GamepadPortManager`, combining existing port assignment and activity data into a UI-ready model.

```
ControllerStatusState
  ports: List<PortStatus>       // Up to MAX_PORTS (8), ordered by port number
    port: Int                   // 0-based port index
    connected: Boolean          // Device assigned to this port
    active: Boolean             // Input received within the last ~300ms
  connectedCount: Int           // Number of connected ports (convenience)
  isMultiplayer: Boolean        // connectedCount >= 2 (drives visibility)
```

### Activity Tracking

The existing `reportActivity(port)` method updates per-port timestamps. A coroutine within `GamepadPortManager` periodically checks timestamps (every ~100ms) and updates the `active` flag on each `PortStatus`:

- **Active:** `now - lastActivityMs[port] < 300ms`
- **Idle:** `now - lastActivityMs[port] >= 300ms`

The 300ms window keeps the flash responsive (one quick button press lights up) but prevents flickering during sustained input.

### No New ViewModel

`GamepadPortManager` is already a Koin singleton injected throughout the app. The new `StateFlow` is consumed directly by composables — no intermediate ViewModel needed. This follows Approach 1 from brainstorming: single source of truth, no extra wiring.

## UI Variants

### Dot States

Each player dot has three visual states:

| State | Visual | Description |
|-------|--------|-------------|
| Disconnected | Hollow ring, dim `P#` label | Port has no controller |
| Connected (idle) | Solid green circle, subtle green glow, normal `P#` label | Controller connected, no recent input |
| Active (input) | White circle, bright white glow | Input received within last 300ms; animates back to green |

The transition from active → idle is an animated color fade (white → green, ~300ms ease-out).

### Variant 1: Rail Card (Labeled Rail, >840dp)

**Location:** Inside `SpNavigationRail`, in the spacer area between the main tabs and the Settings tab (pinned to bottom).

**Layout:** A subtle card with:
- "CONTROLLERS" label (uppercase, 10sp, secondary color)
- Horizontal row of P1–P4 dots with labels
- Shows all 4 slots: connected ports as green/white dots, empty ports as hollow rings
- Subtle background (`Color.White` at 5% alpha) with thin border (`Color.White` at 8% alpha), 8dp corner radius

**Interaction:** Clickable — navigates to `GamepadConfigScreen` in Settings. Focusable for gamepad navigation.

**Visibility:** `AnimatedVisibility` (fade + slide from bottom), shown when `isMultiplayer == true`.

### Variant 2: Stacked Dots (Icon-Only Rail, 600–840dp)

**Location:** Inside `SpNavigationRail` (icon-only mode), in the spacer area above Settings.

**Layout:** A vertical column of dots (no labels, no card background). Centered in the 72dp rail width. Only shows connected ports.

**Interaction:** Clickable — navigates to `GamepadConfigScreen`. Focusable.

**Visibility:** Same `AnimatedVisibility` as Variant 1.

### Variant 3: Pill Extension (Gamepad Mode)

**Location:** Appended to the existing `SpSectionIndicator` pill.

**Layout:** After the R1 label:
- A thin vertical separator (1dp wide, 18dp tall, `Color.White` at 15% alpha)
- Horizontal row of dots for connected ports with `P#` labels below each dot

Only shows connected ports — no empty slots.

**Interaction:** None (informational only). The pill is not clickable today and adding interaction would conflict with L1/R1 navigation.

**Visibility:** The dots section is conditionally included when `isMultiplayer == true`. The pill itself follows its existing visibility logic (gamepad input mode).

### Variant 4: Floating Mini-Pill (Phone, <600dp)

**Location:** Overlaid on content in `SpelaApp`, top-center with 16dp padding. Same position and styling as `SpSectionIndicator`.

**Layout:** A standalone pill (same semi-transparent black background, 24dp corner radius) containing only the controller dots with `P#` labels. No nav icons, no L1/R1.

**Interaction:** None (informational only).

**Visibility:** `AnimatedVisibility` (fade + slide from top), shown when:
- `isMultiplayer == true`
- `layoutMode == BOTTOM_BAR`
- The section indicator pill is NOT visible (to avoid overlap)

## Component Architecture

Following the project's Design → Content → Role hierarchy:

### New Components (`presentation/ui/components/`)

**`SpControllerDot`** (design layer)
- Renders a single dot in one of three states: disconnected, connected, active
- Accepts: `connected: Boolean`, `active: Boolean`
- Handles the white→green flash animation internally
- No labels, no outer spacing
- Size: 8dp circle

**`SpControllerStatusRow`** (content layer)
- Horizontal row of `SpControllerDot` + `P#` labels
- Accepts: `ports: List<PortStatus>`, `showEmptySlots: Boolean`
- `showEmptySlots = true`: Shows ports 0–3, disconnected ports as hollow rings (used by rail card)
- `showEmptySlots = false`: Only shows connected ports (used by pill variants)
- Labels: `P1`, `P2`, `P3`, `P4` (1-indexed for display, 0-indexed internally)

**`SpControllerStatusCard`** (content layer)
- The rail card wrapper: "CONTROLLERS" label + `SpControllerStatusRow(showEmptySlots = true)`
- Subtle card background, clickable
- Accepts: `ports: List<PortStatus>`, `onClick: () -> Unit`

### Modified Components

**`SpNavigationRail`**
- Accepts new parameter: `controllerStatus: ControllerStatusState`
- In labeled mode: renders `SpControllerStatusCard` in the spacer area (above Settings) wrapped in `AnimatedVisibility`
- In icon-only mode: renders a vertical column of `SpControllerDot` in the spacer area wrapped in `AnimatedVisibility`
- Click handler navigates to `GamepadConfigScreen`

**`SpSectionIndicator`**
- Accepts new parameter: `controllerStatus: ControllerStatusState`
- When `isMultiplayer`: appends separator + `SpControllerStatusRow(showEmptySlots = false)` after R1

**`SpelaApp`**
- Reads `controllerStatus` from `GamepadPortManager`
- Passes it to `SpNavigationRail` and `SpSectionIndicator`
- Renders the floating mini-pill (Variant 4) when conditions are met

## Animations

| Trigger | Animation |
|---------|-----------|
| Indicator appears (crossing 2-controller threshold) | Fade in + slide from bottom (rail) or top (pill/mini-pill) |
| Indicator disappears (dropping below 2 controllers) | Fade out + reverse slide |
| Input activity on a port | Dot color animates to white with bright glow, then fades back to green over ~300ms |
| Controller disconnects (still 2+ connected) | Dot animates from green to hollow ring; brief red flash before settling on hollow (signals "something disconnected") |
| Controller connects (already 2+ connected) | New dot animates from hollow ring to green |

## Edge Cases

### Battery disconnect/reconnect
When a controller disconnects and reconnects (e.g., battery swap), the OS may assign a new device ID. `GamepadPortManager` treats it as a new device, but since disconnection freed the port and `connectDevice()` assigns the first available port, the controller naturally reclaims its original slot. Other players' ports are unaffected.

**Risk:** If a different controller connects before the original one returns, it takes the freed port. The player would need to use `GamepadConfigScreen` (via the rail card) to swap back. This is acceptable for Phase 1.

### More than 4 controllers
The state model supports up to 8 ports (`MAX_PORTS`). The rail card shows 4 slots by default (the most common local multiplayer scenario). If 5+ controllers connect, the card could expand or show a "+N more" indicator. This is a rare edge case — defer detailed design until someone actually requests 5+ player support.

### Gamepad mode on desktop/tablet
When a gamepad is connected on desktop/tablet, the app switches to gamepad input mode and shows the section indicator pill instead of the rail. In this case:
- The rail card is hidden (rail itself is hidden)
- The pill extension (Variant 3) shows the controller dots
- This is consistent — the dots follow whichever navigation element is visible

## Testing Strategy

### Desktop E2E Tests (Primary)
- Controller card appears in labeled rail when 2+ controllers connected via `GamepadPortManager`
- Controller card hidden with 0-1 controllers
- Dot states: correct rendering for disconnected, connected, active
- Click on card navigates to `GamepadConfigScreen`
- Pill extension shows dots when multiplayer + gamepad mode
- Floating mini-pill shows on phone layout with 2+ controllers
- Disconnect/reconnect: dots update correctly, no port renumbering
- Animation: verify `AnimatedVisibility` entrance/exit

### Unit Tests
- `ControllerStatusState` derivation from port assignments + activity timestamps
- `isMultiplayer` threshold logic
- `active` flag timing (300ms window)
- `showEmptySlots` filtering logic in `SpControllerStatusRow`

### Android Smoke Tests
- Connect 2 physical controllers → indicator appears
- Disconnect one → indicator disappears
- Activity flash works with real gamepad input

## Future Phases

### Phase 2: In-Game Overlay Controller Panel
A "Controllers" button in the in-game overlay opens a sub-panel showing:
- Full controller names (e.g., "Xbox Wireless Controller")
- Per-player activity indicators
- Swap buttons for reordering ports mid-game

### Phase 3: Drag-to-Assign Port Picker
A more intuitive controller assignment UI where players can drag controllers to ports or use a "press a button to claim this port" flow, replacing the current swap-based system.
