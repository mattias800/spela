# Second Screen Keyboard & Trackpad Controls

**Date:** 2026-03-26
**Status:** Approved

## Overview

Add keyboard and trackpad input modes to the secondary screen companion's Controls page. The existing touch gamepad becomes one of three tabs in a segmented control: **Gamepad**, **Keyboard**, and **Trackpad**. This enables proper input for platforms that need keyboard (DOS, Amiga, C64, MSX) and mouse (ScummVM, point-and-click adventures, RTS games).

## Scope

- Extends the existing Controls page (page 1 in the secondary screen pager)
- Single-screen consoles only — dual-screen consoles (NDS, 3DS) continue to show the bottom screen directly
- Android only in practice (secondary display is a no-op on desktop)

## Controls Page Structure

The Controls page gains a **pill-style segmented control** at the top with three tabs:

```
┌─────────────────────────────────┐
│ ┌──────────┬──────────┬────────┐│
│ │ Gamepad  │ Keyboard │Trackpad││
│ └──────────┴──────────┴────────┘│
├─────────────────────────────────┤
│                                 │
│      [Tab content area]         │
│                                 │
└─────────────────────────────────┘
```

- **Gamepad tab** — Existing touch gamepad content (P1/P2 port selector + buttons) moves inside this tab
- **Keyboard tab** — Layered keyboard (see below)
- **Trackpad tab** — Relative-mode trackpad with click buttons (see below)

The selected tab is persisted per console ID locally on-device, so the app remembers the last-used input mode for each platform across sessions.

## Keyboard Tab

### Layer System

The keyboard uses layers to keep keys large enough on the ~3.92" secondary screen. Three base layers are available for all platforms, plus optional per-platform layers.

**Layer 1: QWERTY (default)**

Standard alphanumeric layout with number row. Bottom row contains: `[Fn]`, `[Sym]`, `[Space]`, `[Enter]`, `[←→]` (arrow key cluster).

**Layer 2: Fn**

Function keys (F1–F12), Escape, Tab, Insert, Delete, Home, End, Page Up, Page Down. Accessed by tapping the Fn key (toggle mode).

**Layer 3: Symbols**

All standard symbols and punctuation: `!@#$%^&*()-={}[]\|;':"<>?/~`. Accessed by tapping the Sym key (toggle mode).

**Arrow keys:** The `[←→]` button expands into a temporary D-pad cluster (←↑↓→) when tapped, saving space in the default layout.

### Per-Platform Extra Layers

When running a platform that has special keys, an additional layer tab appears in the bottom row. Only one platform layer is shown at a time, determined by the currently running console.

| Console IDs | Layer Name | Keys |
|-------------|-----------|------|
| `amiga`, `ademo` | Amiga | Left Amiga, Right Amiga, Help |
| `dos`, `ddemo` | DOS | Common shortcut combos (Ctrl+C, Ctrl+Z, Alt+F4) |
| `c64` | C64 | RUN/STOP, RESTORE, C= (Commodore key), CTRL |
| `c128` | C128 | RUN/STOP, RESTORE, C= key, CTRL, 40/80 Display |
| `vic20` | VIC | RUN/STOP, RESTORE, C= key |
| `msx`, `msx2` | MSX | SELECT, STOP, GRAPH, CODE |

When a platform layer is available, the bottom row gains a fourth layer key: `[Fn]`, `[Sym]`, `[C64]`, `[Space]`, `[Enter]`, `[←→]`. The space bar shrinks slightly to accommodate it. On platforms with no extra layer, the row stays as three layer keys with a wider space bar.

### Key Behavior

- **Shift:** Toggle mode — tap to activate, tap again to deactivate. Double-tap for caps lock.
- **Fn/Sym/Platform:** Toggle mode — tap to switch layer, tap again to return to QWERTY.
- **Key feedback:** Brief visual highlight on press. Haptic feedback on Android.
- **Modifier keys (Ctrl, Alt, Shift):** Sticky — tap to activate, remains active for the next key press, then auto-deactivates. Tap again to lock.

## Trackpad Tab

The trackpad maximizes the touch surface area for precision cursor control.

```
┌─────────────────────────────────┐
│ ┌─────────┬──────────┬────────┐ │
│ │ Gamepad │ Keyboard │Trackpad│ │
│ └─────────┴──────────┴────────┘ │
├─────────────────────────────────┤
│                                 │
│                                 │
│         Trackpad Area           │
│     (drag to move cursor)       │
│                                 │
│                                 │
├─────────────────────────────────┤
│   [Left Click]   [Right Click]  │
└─────────────────────────────────┘
```

### Pointer Movement

- **Relative mode only** — dragging a finger moves the cursor relative to the current position, like a laptop trackpad
- Lifting the finger and placing it again starts a new relative movement — no cursor jump
- Sensitivity uses a reasonable hardcoded default (can be made configurable in a future iteration if needed)

### Click Actions

**Dedicated buttons:** Large Left Click and Right Click buttons at the bottom of the trackpad area. Always visible, clearly labeled.

**Gesture shortcuts** (in addition to buttons):
- Single-finger tap on trackpad area = left click
- Two-finger tap on trackpad area = right click
- Both methods work — buttons for discoverability, gestures for speed

### Click-and-Drag

Two methods:
1. Tap-and-hold on the trackpad area, then drag = click-and-drag (left button)
2. Press and hold the Left Click button with one thumb, drag on trackpad with other finger

### Visual Design

- Trackpad area has a subtle border and slightly different background to indicate the touch zone
- A small, static cursor icon in the center as a visual hint (decorative only)
- Click buttons are large and thumb-friendly with clear left/right visual distinction

## Libretro Input Bridge Changes

### New JNI Functions

The native bridge (`player/native/src/`) needs two new functions:

**`input_set_mouse(dx, dy, leftButton, rightButton)`**
- Sets relative mouse deltas and button state
- Values are read when the core polls `RETRO_DEVICE_MOUSE` input
- `dx`/`dy` are relative pixel deltas accumulated since last poll, cleared after read

**`input_set_keyboard(key, pressed)`**
- Sets individual key press/release state
- `key` is a `RETROK_*` constant (libretro keyboard code)
- `pressed` is boolean (true = key down, false = key up)
- State is delivered via `retro_keyboard_callback` if the core registered one, otherwise queried via `retro_input_state_t` with `RETRO_DEVICE_KEYBOARD`

### Device Type Switching

- When the user switches to the **Trackpad** tab, call `retro_set_controller_port_devices()` to set port 0 to `RETRO_DEVICE_MOUSE`
- When switching to **Keyboard** tab, keyboard input is supplementary (not a port device in libretro) — port device stays as-is, keyboard events flow through the keyboard callback
- When switching back to **Gamepad** tab, restore port 0 to `RETRO_DEVICE_JOYPAD`
- Physical gamepad input continues to work regardless of which tab is active — it uses a separate input path

### Key Mapping

A mapping table in shared commonMain code translates Compose key identifiers to `RETROK_*` constants. Platform-specific keys (e.g., Amiga key → `RETROK_LSUPER`/`RETROK_RSUPER`, C64 Commodore key) map to the `RETROK_*` codes that each platform's core expects.

## Local Persistence

### SQLDelight Schema

New table for storing the last-used control tab per console:

```sql
CREATE TABLE ControlTabPreferenceEntity (
    console_id TEXT NOT NULL PRIMARY KEY,
    selected_tab TEXT NOT NULL DEFAULT 'gamepad'
);
```

`selected_tab` values: `"gamepad"`, `"keyboard"`, `"trackpad"`

### Repository

A `ControlTabPreferenceRepository` with:
- `getSelectedTab(consoleId: String): String` — returns the saved tab or `"gamepad"` as default
- `setSelectedTab(consoleId: String, tab: String)` — upserts the preference

Injected into `EmulationViewModel`, which already manages secondary screen state.

### Scope

- Purely local, never synced to the server
- Follows the same pattern as `ShaderOverrideEntity` for device-local per-console preferences

## OLED Burn-In

The existing burn-in protection (15s idle timeout, fade to black, touch to wake) applies to all three tabs equally. No changes needed — the idle timer already resets on any touch input, which covers trackpad drags, keyboard taps, and tab switches.

## Testing Strategy

Following the project's testing conventions:

**Desktop E2E tests (primary):**
- Segmented control rendering and tab switching
- Keyboard layer switching (QWERTY → Fn → Sym → platform layer)
- Keyboard key press/release callbacks
- Trackpad gesture recognition (drag, tap, two-finger tap)
- Tab persistence per console (mock SQLDelight)
- Correct tab restoration on game start
- Tabs hidden for dual-screen consoles
- OLED burn-in timer resets on keyboard/trackpad interaction

**Android smoke tests:**
- Touch input actually reaches the native bridge (real device, real haptics)
- Mouse cursor movement works in a DOS/ScummVM game
- Keyboard input produces characters in a DOS game
- Tab switch triggers correct `retro_set_controller_port_devices()` call

## Out of Scope

- Keyboard + trackpad shown simultaneously (one tab at a time)
- Absolute pointer mode (use main screen touch for that)
- Configurable trackpad sensitivity (hardcoded default, revisit if needed)
- Server-side sync of tab preferences (local only)
- Custom key remapping or user-defined keyboard layouts
