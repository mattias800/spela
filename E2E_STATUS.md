# Android E2E test progress

This file is a temporary status report — delete after merge.

## Test class status (latest run on AYN Thor)

| Class | Status | Notes |
|---|---|---|
| `EmulationTest` (13 tests) | ✅ all passing | Pilot. Took most fixes — patterns reused everywhere. |
| `NavigationTest` (2 tests) | ✅ all passing | Switched to `navigateToCastlevania()` helper for shelf layout. |
| `PlayLaterTest` (5 tests) | ✅ 4/5; 1 flake | Rewrote to use the new actions-overflow menu (Play Later moved into it). The fifth (`activityFeedShowsPlayLaterEvent`) skipped via fail-fast. |
| `TouchControlsTest` (6 tests) | ⏭ skipped | Class-level `Assume.assumeFalse(hasGamepad)` — touch overlay is hidden when a gamepad is connected (always on AYN Thor). Will run on a non-gamepad emulator. |
| `GamepadNavigationTest` (3 tests) | ⏭ skipped | Class-level `Assume.assumeTrue(onDisplays <= 1)` — `Instrumentation.sendKeyDownUpSync` doesn't reach the activity when it's routed to the secondary display. Real gamepad input on this device works fine; this is a test-framework limitation. |
| `SettingsTest` (5 tests) | 🟡 in-progress | `shaderSelectionPersists` uses `restartApp()` which is fragile post-recreate. |
| `SessionTest` (5 tests) | 🟡 not yet validated | Heavy `restartApp()` use; updated to use `isOnHomeScreen` (multi-display safe) instead of `waitForText('Spela')`. |
| `ResetServerStateTest` (1 test) | ✅ passing | Validates `POST /api/test/reset` end-to-end. |
| `EstablishSessionTest` (1 test) | 🟡 not re-run since infra changes | Should still pass (override of `baseSetUp` that signs out before exercising the server-connect flow). |
| `Challenge*Test` (5 classes, 30 tests total) | 🟡 not re-run since infra changes | Updated `ensureChallengeExists()` to tolerate Play/Resume after exit (auto-save default-on). |
| `CollectionsTest` (8 tests) | 🟡 not re-run | Migrated to BaseE2ETest but content not validated. |
| `CloneSessionSmokeTest` (1 test) | 🟡 not re-run | Migrated. |
| `CoreDecisionFlagsSmokeTest` (1 test) | 🟡 not re-run | Migrated. |
| `NetplayTest` (1 test) | 🟡 not re-run | Migrated. Single-device netplay smoke (admin via UI, second player via API). |
| `HwRenderTest` (4 tests) | 🟡 not re-run | N64 cores — needs `mupen64plus_next` core which the e2e setup doesn't pre-cache. Would download on demand. |

## Key infrastructure changes (load-bearing)

- **Reset endpoint perf**: 5+ sec → ~2 ms via tmpfs DB + hard-coded bcrypt seed hashes + skip-scrape-worker-in-test-mode. See `c46b5718`, `275f6769`, `9ae04e0b`.
- **`isOnHomeScreen()` substring fix**: don't false-match "Nu spelar vi!" on auth screens. See `8c8689be`.
- **assert*Visible Compose fallback**: helpers now use both UiAutomator and the Compose semantics tree, so tests work on multi-display hardware. See `8ea4a3a8`.
- **`restartApp()` retry**: scenario.recreate() retried 3x. See `d95b6410`.
- **IGDB credential preflight + scan-wait** in `run-e2e.sh`. See `98824ea0`, `d1f497ae`.

## Outstanding issues to investigate

1. `restartApp()` post-recreate session restoration occasionally lands on Login (not Home). The test then waits for Home and times out. Need to investigate whether `NavigationViewModel` re-runs its session restore after recreate, and whether there's a transient "no session yet" window.
2. `PlayLaterTest.playLaterTogglePersistsOnGameDetail` has a transient flake (passed once, failed once). Likely a timing issue on the game-detail re-fetch.
3. Tests that mention specific game titles (e.g., "Castlevania" in overlay) need to either use `navigateToCastlevania()` or accept any game title — the default helper picks Balloon Fight.

## Untouched classes (might just work)

The Challenge* / Smoke / Netplay classes have not been re-run since the major infrastructure fixes landed. Many could be passing already; they need a run-and-see pass.
