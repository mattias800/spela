# Controller Player-Slot Assignment (#1359)

This document covers the **device-local controller → player-slot** model added in
#1359, on top of the two-layer gamepad model (#1334). Read this before changing
`GamepadPortManager`'s connect/assign path, the per-controller UI in
`ControllerControls.kt`, or the Android/desktop input wiring — several invariants
break in subtle ways if "simplified".

## The model: connected ≠ assigned

`GamepadPortManager` tracks two distinct things:

- **`connectedDevices`** — every detected controller (assigned or not), keyed by
  ephemeral session `deviceId`. A controller is "connected" the moment it's seen.
- **`deviceToPort`** — the subset of connected devices that hold a **player slot**
  (0-based port, P1–P8). `occupiedPorts` mirrors which slots are taken.

A controller can be **connected but unassigned** (`slot == null`): it is polled
(so the input tester works on it) but `getPort()` returns -1, so it routes **no
input to any game port**. That is the "clear a controller you're not using" state.

`getPort(deviceId)` returns the slot (or -1). Emulation input routing keys off
this, so unassigned controllers are inert in-game by construction.

## Assignment resolution on connect

`connectDevice(deviceId, deviceName, style, stableKey = deviceName)`:

1. **Already connected** → returns the current slot (idempotent; never re-claims).
2. **Blank `stableKey`** → auto-claim the lowest free slot **without persisting**
   (a blank key can't identify a controller; two blank-key devices must not
   collide in the cache — this was a real test failure).
3. **Remembered** (`stableKey` present in the persistence cache) → restore the
   remembered slot if still free; honor a remembered **cleared** state (null slot)
   by staying unassigned. If the remembered slot is taken, stay unassigned (don't
   steal).
4. **Never seen** → auto-claim the lowest free slot and remember it (preserves
   plug-and-play: a brand-new controller just works as the next player).

`assignSlot(deviceId, slot)` is **move-and-clear**: if another controller holds
`slot`, it is cleared (unassigned + remembered cleared) before this one takes it.
The UI must confirm the switch with the user first (see `deviceOnSlot(slot)` to
detect the conflict). `clearAssignment(deviceId)` frees the slot, keeps the
controller connected, and remembers the cleared state.

## Stable identity & persistence

Player-slot assignments persist **device-local** (never synced) via
`ControllerAssignmentRepository` → `ControllerAssignmentEntity(stable_key PK,
player_slot INTEGER NULL)`. Semantics:

- key **absent** = never seen (auto-claim), key **present with null** = explicitly
  cleared (stay cleared). Use `containsKey`, not `[]`, to tell them apart.

The stable key per platform:
- **Android**: `InputDevice.descriptor` (stable per physical unit across reconnects).
- **Desktop**: the SDL device **name** (consistent with `ControllerStyleOverride`'s
  keying). Two *identical* desktop pads share a name and so can't hold distinct
  persistent slots — a documented limitation; per-unit desktop persistence would
  need SDL serial (a native/JNI change, deferred).

**The repository is synchronous on purpose.** `GamepadPortManager` reads it from
inside its `@Synchronized` critical sections on the input threads when a device
connects, so it must not suspend. The cache is loaded lazily on first connect
(`ensureCacheLoaded`) and is the session source of truth; writes go through to the
DB synchronously. Do not make it `suspend` / async — that reintroduces a
connect-before-cache-loads race where a cleared controller would wrongly
auto-claim.

## Per-device input tester

The live input tester (#1355) is **per-device**, not a union:

- `pressedByDevice[deviceId]` holds each controller's currently-pressed positions.
- `testCaptureDeviceId: StateFlow<Int?>` is the single signal: non-null = the
  tester for *that* controller is focused. `pressedPositions` reflects **only**
  that device.
- The input pipelines (`MainActivity.captureTestInput`, `DesktopGamepadPoller`)
  capture + **consume** non-D-pad buttons **only** for `testCaptureDeviceId`, and
  **never the D-pad** — so D-pad always navigates and the tester being on screen
  never disrupts gamepad navigation. The desktop poller reports tester input
  **before** the `getPort()` check, so an *unassigned* controller is still testable.

The tester element drives `testCaptureDeviceId` from its own Compose focus
(`GamepadInputTester` → `SetInputTestActive` intent → `setTestCaptureDevice`).

## UI: in-screen list → detail

Settings → Controls is an **in-screen** list ⇄ detail toggle driven by
`GamepadConfigState.selectedDeviceId` (Settings is a category selector, not a
NavHost). The detail uses `PlatformBackHandler` so hardware Back collapses the
detail rather than leaving Settings; the on-screen Back row is the first focusable
so the documented `moveFocus(Next)` recovery lands there (no custom autofocus —
consistent with the rest of the Settings surface).

The old port-swap UI (`GamepadConfigScreen`, `SwapPorts`) is **retained** — it's
still used by `ConsoleSettingsScreen` and `InGameOverlay`. The #1359 work is
additive to `GamepadConfigViewModel`; don't remove the port-based API.
