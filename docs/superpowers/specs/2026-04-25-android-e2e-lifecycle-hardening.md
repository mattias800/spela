# Android E2E Lifecycle Hardening

**Date:** 2026-04-25
**Status:** Draft — pending user review
**Scope:** `player/android/src/androidTest/**`, `player/run-e2e.sh`, core-caching scripts, test base class wiring

## Problem

The Android E2E suite is unreliable. Symptoms vary — sometimes the whole batch fails, sometimes individual tests — and diagnosis is hard because failures cascade through the fail-fast gate. Three structural issues underlie most of the pain:

1. **App-side state leaks across runs.** The backend is reset (`docker compose down -v`) before every run, but the APK on the device is never uninstalled. The player's SQLDelight DB keeps last run's auth tokens, server connection, cached game metadata, downloads, shader overrides, and play history. A "fresh" backend meets an app that believes it is still logged into last run's server.
2. **Backend state leaks across tests within a run.** There is no equivalent of the web suite's `POST /api/test/reset` in the Android setup. Favorites, saves, collections, challenges, sessions, and play history created by test N are visible to test N+1.
3. **No single bootstrap, no exit contract.** Every test re-runs the "detect screen → maybe add server → maybe log in" path via `ensureLoggedIn()`. Recovery from a crashed test lives in the *following* test's slow polling logic, so one failure degrades every test after it.

The goal is a suite where the **entry contract** is known (logged in, on Home, backend reset to seed state) and the **exit contract** is known (logged in, on Home), so each test only verifies what it is actually about.

## Non-goals

- Rewriting individual test assertions (unless existing tests break under the new lifecycle — then they are updated minimally).
- Changing the ComposeRule-per-test model (`createAndroidComposeRule<MainActivity>()` relaunches the Activity per test, which is fine and gives us natural isolation).
- Running the suite in CI (CI does not currently run Android E2E; that remains true).

## Must preserve (existing debugging infrastructure)

The previous session invested heavily in failure diagnostics. All of it stays wired through this work:

- **`FailureDiagnosticsListener`** (registered globally via `testInstrumentationRunnerArguments["listener"]` in `player/android/build.gradle.kts:52-53`) captures per-failure:
  - `screenshot.png` — display 0 screenshot via `UiAutomation.takeScreenshot`
  - `ui.xml` — `uiautomator dump` of the accessibility tree
  - `logcat.txt` — last 2000 lines
  - `state.json` — package, display ids, a11y services, timestamp
  - `failure.txt` — exception class, message, stack, causes
  - `repro.txt` — single-class gradle invocation to reproduce
- **Fail-fast gate** via `FailureDiagnosticsListener.anyTestFailed`: `KoinResetRule` throws `AssumptionViolatedException` on every test after the first failure so they land as SKIPPED instead of burning minutes on doomed reruns.
- **Host-side pull** in `run-e2e.sh` copies `/sdcard/spela-test-failures/` to `player/build/test-failures/` on every exit and prints absolute paths.

The new `BaseE2ETest` class does not replace any of this — it sits beside it. The fail-fast gate continues to run inside `KoinResetRule` (order=0), so it still fires before `BaseE2ETest.baseSetUp()`. The `@After` `tearDown()` path is wrapped in `runCatching` so teardown failures cannot mask the original assertion failure already captured by the listener.

## Design

Three layers. Each can land independently, in order.

### Layer 1 — Run lifecycle (`player/run-e2e.sh`)

Between runs, reset the world:

1. `docker compose down -v && up -d --build --wait` *(already done)*
2. `adb uninstall com.spela.player || true` *(new)* — wipes SQLDelight DB, shared prefs, filesDir. Guarantees the first test sees the real first-install UX.
3. `adb reverse tcp:8080 tcp:8080` *(already done)*
4. **Core cache step** *(new)* — see below.
5. Device hygiene: wake, screen timeout, a11y off, launch on display 0 *(already done)*
6. Run test classes with fail-fast *(already done)*

#### Core cache step

Real users download cores when they start a game. But we cannot have E2E runs thrash libretro buildbot every time. Middle ground:

- **Pre-cache exactly one core: `nestopia` (NES).** Every current emulation test uses Castlevania or Balloon Fight — both NES.
- Before the test batch, check `/data/local/tmp/nestopia_libretro_android.so` on the device. If absent, check a host-local cache at `player/.e2e-cores/nestopia_libretro_android.so`. If that is absent too, curl it from libretro buildbot once, save it locally, then `adb push` to the device.
- `KoinResetRule.preCacheCores()` keeps reading `/data/local/tmp/` but its hard-coded list shrinks from 9 cores to just `nestopia`. The other 8 cores in the list are aspirational — no current Android test uses them.
- **Tests that intentionally use a non-NES core** (e.g., `HwRenderTest` on a different console) let the download happen in-test. This still exercises the real download path on a minority of runs.
- **Opt-out:** environment variable `SPELA_E2E_REAL_CORE_DOWNLOAD=1` skips the pre-cache step entirely, so a nightly run can genuinely exercise the first-install flow.

Net result: we hit libretro buildbot **once per dev machine lifetime** for the common case.

### Layer 2 — Shared test base class (`BaseE2ETest`)

New abstract class under `player/android/src/androidTest/java/com/spela/player/android/`. All test classes extend it.

```kotlin
abstract class BaseE2ETest {
    @get:Rule(order = 0) val koinResetRule = KoinResetRule()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Before open fun baseSetUp() {
        resetServerState()     // POST /api/test/reset, tolerate 200/401
        ensureLoggedIn()       // fast-path when Home visible; slow-path only first run
        assertOnHome()         // fails loudly if contract broken
    }

    @After open fun baseTearDown() {
        runCatching { exitGameIfRunning() }
        runCatching { navigateBackToHome() }
        // FailureDiagnosticsListener captures artefacts separately
    }
}
```

Contract enforced by the base class:

- **Entry:** logged-in on Home with backend reset to seed state.
- **Exit:** logged-in on Home.

Tests that need a different starting state (explicit sign-out, first-install, multi-user) override `baseSetUp()` or use explicit helpers.

#### `resetServerState()` — new helper

POSTs to `http://127.0.0.1:8080/api/test/reset` from the instrumentation process. Unauthenticated endpoint (by design — see `huma_test_reset.go`). Idempotent. Fast (<100 ms). Expects a 200 response with `{"status":"reset"}`; any other response fails the test loudly — reset is a hard dependency, not best-effort.

**JWT caveat.** `HumaReset` deletes `RefreshToken` records and resets `User.token_version` to 0. In the common case (player logged in at token_version=0), the currently-held JWT remains valid because the claim still matches. When it does not match (for instance after a test that rotates tokens), the next API call 401s and the player app's auth flow redirects to the login screen. `ensureLoggedIn()` handles that path natively — it detects the login screen and re-authenticates. The reset itself always succeeds; the *consequence* for a stale JWT is handled in the `ensureLoggedIn()` call that follows.

#### `ensureLoggedIn()` — simplified

Current logic is retained, but restructured into two unambiguous paths:

- **Fast path:** if `isOnHomeScreen()` returns true within 2 s, return immediately. This is the hot path for every test after the first.
- **Slow path:** inspect the current screen. If on server-connection → add server and log in. If on login → log in. If on any other logged-in screen → navigate back to Home and assert.

The current `navigateBackToHome()` fallback inside `ensureLoggedIn()` becomes redundant because `@After` in the base class already returns to Home. If `@Before` ever finds itself on a non-Home logged-in screen, that is a test-infra bug — we fail loudly instead of silently recovering.

#### `assertOnHome()` — new helper

Cheap check: `isOnHomeScreen()` must be true. Fails with a diagnostic listing the visible top-level testTags if not. Used at the end of `baseSetUp()` to turn silent contract violations into a loud early failure.

#### `navigateBackToHome()` — visibility upgrade

Currently `private`. Promote to internal so the base class `@After` can call it. Same implementation: press back until `isOnHomeScreen()`, stopping at auth screens. Safe for repeated invocation.

### Layer 3 — Existing tests absorb the new contract

Most tests lose their `rule.startLoggedIn()` first line (moved to `@Before`). The tests that use `setupGame()` keep it — just the login part is now a no-op inside `ensureLoggedIn()`'s fast path.

Specific impact:

- **`EmulationTest`** — all 14 methods inherit the contract. `fpsHudHiddenByDefault` (currently staged) becomes trivially stable because `/api/test/reset` restores `showPerformanceOverlay=false`.
- **`SettingsTest`** — preference-mutating tests stop polluting each other. Tests that rely on "auto-save is on" no longer need to re-enable it explicitly.
- **`CloneSessionSmokeTest`, `ChallengeIntegrationTest`, `CollectionsTest`** — mutating tests stop leaving debris.
- **`CoreDecisionFlagsSmokeTest`, `HwRenderTest`** — may need to download a non-NES core. Call out in those tests that they rely on a live libretro buildbot; consider gating them behind an opt-in flag if they become too flaky (follow-up, not this design).

Tests that override the contract:

- **`EstablishSessionTest`** — explicitly tests the add-server + login flow. Overrides `baseSetUp()` to call `clearAppState()` (wipes SQLDelight auth + server connection) and then skips `ensureLoggedIn()`, so the test genuinely starts from the first-install server-connection screen regardless of run order.
- Any persistence/restart test that uses `restartApp()` — still calls `restartApp()` inside the test body after base setup; the docs already flag this as unreliable on emulators.

### Layer 4 — Documentation

`docs/e2e-testing.md` gets a new "Android test lifecycle" section describing:

- The `BaseE2ETest` contract (entry/exit).
- The `SPELA_E2E_REAL_CORE_DOWNLOAD=1` opt-out.
- How to write a new Android E2E test (extend `BaseE2ETest`, assume Home + logged-in, clean up by returning to Home).

## Alternatives considered

1. **Clear app data via `pm clear` between tests instead of per-run.** Rejected: slow (~3 s per test), would require re-login every test, defeats the "one login per run" optimisation. Reset-via-API is an order of magnitude faster.
2. **Base class as a `TestWatcher` rule instead of `@Before`/`@After`.** Rejected: same behavior, more wiring, less familiar to Kotlin devs. `@Before`/`@After` is idiomatic.
3. **Keep per-test `startLoggedIn()` calls and skip the base class.** Rejected: we still need the exit contract (return-to-Home in `@After`) somewhere. A base class consolidates both entry and exit and makes the contract visible.

## Risks

- **Reset endpoint coupling.** The Android suite now depends on `SPELA_TEST_MODE=true`. Already enforced by `docker-compose.e2e.yml`. `StartupDiagnostics.assertClean()` already validates the server is the expected test build; extend it to probe `/api/test/reset` availability at suite start.
- **Re-login after reset.** If the JWT is invalidated mid-suite (token_version mismatch), `ensureLoggedIn()` has to handle the login screen transition cleanly. Existing code already does this, but the path is less well-exercised; we will see it fire more often now.
- **Non-NES core tests flake on buildbot outages.** Follow-up, not blocking.

## Open questions for the user

None blocking — the user has approved the direction. Remaining judgement calls (e.g., exactly which tests to opt out of the base contract) land during implementation and will be called out in the implementation plan.

## Success criteria

- `./player/run-e2e.sh --no-fail-fast` runs the full suite green on a clean dev machine twice in a row (second run proves idempotency).
- Each test file reads as "what am I verifying" without infra boilerplate in the first five lines.
- Failures are isolated: a crashed test does not cascade into the next.
