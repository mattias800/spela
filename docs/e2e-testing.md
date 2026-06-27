# E2E Testing Guide

This document explains how to run E2E tests across all three platforms (web,
Android, desktop), what the infrastructure does, and how to troubleshoot
common issues.

## Quick Start — All Platforms

### Web (Playwright)

From the repository root:

```bash
# Run all E2E tests (builds server, seeds DB, starts everything, runs tests)
./run-e2e.sh

# Run a specific test file
./run-e2e.sh admin-settings

# Run tests matching a pattern
./run-e2e.sh "registration"

# Open Playwright UI mode (interactive)
./run-e2e.sh --ui

# Run with visible browser
./run-e2e.sh --headed

# Skip Go build (reuse cached binary from previous run)
SKIP_BUILD=1 ./run-e2e.sh
```

That's it. The script handles everything.

### Desktop Player (Compose UI Test)

```bash
cd player
./run-desktop-tests.sh
```

Desktop tests use `SpelaTestHarness` with fake repositories — no device or
backend needed. This is the primary UI test suite for the player app.

**Netplay E2E lives here, not on Android.** Netplay requires two real
clients exchanging WebSocket frames; only desktop can stand up two
`SpelaTestHarness` instances in the same JVM and drive both. Android
E2E can't honestly test netplay — a single device can only simulate
the second player via direct REST calls, which tests the lobby UI
in isolation but not the actual peer sync. Add new netplay tests
under `player/desktop/src/test/`. Do not migrate netplay tests
under `player/android/src/androidTest/`; the existing
`NetplayTest.kt` there is `@Ignore`d for this reason and is
scheduled to move to desktop.

### Android Player (Instrumented Tests)

```bash
cd player
./run-e2e.sh
```

**Important:** Always use `run-e2e.sh`, never `./gradlew :android:connectedDebugAndroidTest` directly. The script handles:
- Waking the device (`KEYCODE_WAKEUP`)
- Setting screen timeout to 10 minutes (prevents sleep during tests)
- Setting up reverse port forwarding (`device:8080 → host:8080`)
- Restoring screen timeout after tests complete

**Prerequisites:**
- Android emulator OR physical device (see "Emulator vs Physical Device" below)
- Docker E2E environment running (`docker compose -f docker-compose.e2e.yml up -d --build --wait`)
- Reverse port forwarding: `adb reverse tcp:8080 tcp:8080`

#### Emulator vs Physical Device

**Recommended: Android Emulator (ARM64, API 35)**
- AVD name: `spela-test` (sdk_gphone64_arm64)
- **Density override required:** `adb shell wm density 280` — the Settings screen uses a 600dp breakpoint for list-detail layout. At the default 420 DPI, the emulator is only 411dp wide. Lowering to 280 DPI gives 617dp, crossing the breakpoint.
- JNI/libretro works: ARM64 emulator runs NES (nestopia) and other cores via the JNI bridge. Games actually run on the emulator.
- The emulator must have reverse port forwarding set up: `adb -s emulator-5554 reverse tcp:8080 tcp:8080`

**Alternative: AYN Thor physical device (serial `54071896`)**
- Clamshell device — **must be physically OPEN** or the screen sleeps instantly
- `KEYCODE_WAKEUP` does NOT prevent sleep when the clamshell is closed
- The `run-e2e.sh` script targets this device by default

**Common pitfalls:**
- **Stale local Go servers** → If a local `go run` server is running on port 8080, it intercepts requests before Docker gets them. Kill stale servers: `lsof -i :8080` and kill any non-Docker processes.
- **Signature mismatch** → If switching between debug and release APKs, uninstall first: `adb uninstall com.spela.player`
- **Stale app data** → Always uninstall before installing test APK so you start with clean app data. Leftover server connections from manual testing will cause tests to log into the wrong server.
- **Console page layout** → The console page shows game shelves (Top Rated, Continue Playing) not a flat game list. For libraries with >15 games, games are behind a "Browse" button. Test helpers handle this via `navigateToGameByTitle()`.
- **Emulator timeouts** → Emulators are slower than physical devices. Timeouts: SHORT=5s, MEDIUM=10s, LONG=15s, EXTRA_LONG=30s.
- **Keyboard blocking buttons** → On portrait emulators, the soft keyboard can block buttons. Use `performImeAction()` instead of keyboard dismiss + button click.
- **Use `run-e2e.sh`** → DO NOT run `./gradlew :android:connectedDebugAndroidTest` directly. The script handles device wake, screen timeout, port forwarding, and cleanup. Running gradle directly skips all of this and tests will fail.
- **AppNotIdleException during gameplay** → The 60fps emulation loop keeps the Choreographer busy. Compose test's `performClick()` and `assertIsDisplayed()` wait for Espresso idle, which never arrives. **Fix:** `tapOn()` auto-detects emulation (via "Core running" content description) and falls back to UiAutomator, which bypasses Espresso idle. All gameplay overlay interactions use this pattern.
- **Settings list-detail layout** → Settings was redesigned with a 600dp breakpoint. On narrow screens, the category list shows first (General, Emulation, Controls, etc.). Use `navigateToSettingsCategory("About")` to access Sign Out, `navigateToSettingsCategory("Emulation")` for Auto Save, etc.
- **`restartApp()` unreliable on emulators** → `activityRule.scenario.recreate()` sometimes fails to re-establish the Compose hierarchy. Tests that use `restartApp()` (sessionPersistsAcrossRestart, preferencesSyncAcrossRestart, shaderSelectionPersists) may timeout. These persistence tests are better covered by desktop tests.
- **Cascading test failures** → A failed `restartApp()` can leave the app in a broken state, causing subsequent tests to fail. Tests are designed to recover via `ensureLoggedIn()` but this doesn't always work after a stuck Activity recreation.
- **Continuous Compose animations** → The neon UI has continuous animations (gradient glow, ambient blobs). `IdlingPolicies.setMasterPolicyTimeout(10s)` is set as mitigation. System animation scale = 0 doesn't help because Compose animations are independent of system settings.

#### Iterating on a single Android E2E test

The full `run-e2e.sh` invocation tears down the docker volumes, rebuilds the
backend image, uninstalls + reinstalls the APK, and then runs the Android test
set for the selected device. On physical devices that means every test class,
one at a time. On `--emulator`, class-level `@RequiresPhysicalDevice` and
`@Ignore` tests are filtered before Gradle starts and the remaining emulator-safe
smoke classes are batched into one instrumentation run.

Use `player/scripts/e2e-android-quick.sh` to keep the backend + AVD warm
between iterations. After a one-time setup, each test cycle is ~2 min:

```bash
# One-time per machine (once per dev session):
emulator -avd spela-test -no-snapshot-load &
docker compose -f docker-compose.e2e.yml up -d --build --wait

# Iterate on a single class as many times as needed:
./player/scripts/e2e-android-quick.sh CloneSessionSmokeTest
# (FQCN also works: com.spela.player.android.CloneSessionSmokeTest)
```

The wrapper validates both prereqs up-front, then delegates to
`run-e2e.sh --emulator --skip-reset <class>` so the heavy lifting (adb
reverse, animation disable, core pre-cache, failure diagnostics) stays
in one place. Backend state is preserved across iterations because the
`--skip-reset` flag avoids `docker compose down -v` — the trade-off is
that you must call `POST /api/test/reset` from any test that needs a
clean DB (`BaseE2ETest.@Before` already does).

If you want to log diagnostic data from `commonMain` and have it surface
in the workflow's failure dump, use `spelaLog(tag, message)` from
`com.spela.player.util` rather than `println`. On Android it routes to
`Log.i("Spela", ...)`, which the dump's `-s Spela:V` filter explicitly
includes; `println` (tag `System.out`) is silently dropped.

### Test Tag Convention (Player App)

Screen-level and element-level test tags are defined in `player/shared/src/commonMain/kotlin/com/spela/player/presentation/ui/TestTags.kt`. Both the composables and the test suites reference these constants, so tests don't break when display text changes.

```kotlin
// In composables:
Modifier.testTag(TestTags.SCREEN_SERVER_CONNECTION)

// In tests:
onNodeWithTag(TestTags.SCREEN_SERVER_CONNECTION, useUnmergedTree = true)
```

**Always use `useUnmergedTree = true`** when searching by test tag — Compose's merged semantics tree may not expose tags on container nodes like `BoxWithConstraints`.

When adding new screens or interactive elements, add a test tag constant and apply it. This makes tests resilient to text changes and helps agents understand that a component is tested.

### Android Test Lifecycle

Most Android E2E test classes extend `BaseE2ETest`. The base class enforces a per-method contract:

- **Entry:** user is logged in, on the Home screen, backend reset to seed state.
- **Exit:** user is logged in, on the Home screen.

What this buys you:

- Tests don't carry login or screen-recovery boilerplate in their first five lines — they start with what they're actually verifying.
- A crashed test does not leak its state into the next test. `@After` returns to Home; `@Before` resets the backend; the next test starts clean.
- Backend state (favorites, saves, collections, challenges, sessions, play history, shader prefs) is wiped between tests via `POST /api/test/reset`.

**To add a new test class:**

```kotlin
class MyFeatureTest : BaseE2ETest() {

    @Test
    fun doesTheThing() {
        // app is logged in, on Home, backend is clean
        rule.tapOn("Consoles")
        // ...
    }
}
```

**If you need a different entry state** (explicit sign-out, first-install, multi-user), override `baseSetUp()` and annotate it with `@Before`. See `EstablishSessionTest` for the canonical example.

**Run lifecycle (`player/run-e2e.sh`):**

1. `docker compose -f docker-compose.e2e.yml down -v` then `up -d --build --wait` — fresh backend.
2. `adb uninstall com.spela.player` — fresh app (no leftover auth, server, or local DB).
3. `player/scripts/cache-nestopia.sh` — pre-cache the NES core from a host-local cache (first time only, downloaded once from libretro buildbot and reused forever after).
4. Device hygiene: wake, screen timeout 10 min, a11y services off, launch-prime on display 0.
5. Gradle runs the test set. Physical-device runs execute classes one at a time, fail-fast by default. Default emulator runs filter out class-level `@RequiresPhysicalDevice` / `@Ignore` tests before Gradle starts, then batch the remaining smoke classes into one invocation.

**Opting out of core pre-cache:**

```bash
SPELA_E2E_REAL_CORE_DOWNLOAD=1 ./run-e2e.sh
```

Skips the cache step. Tests that start a game exercise the real first-download flow end-to-end.

**Failure diagnostics (preserved from the previous iteration):**

When any test fails, `FailureDiagnosticsListener` captures under `/sdcard/spela-test-failures/<class>.<method>/`:
- `screenshot.png` — display 0 screenshot.
- `ui.xml` — accessibility tree dump.
- `logcat.txt` — last 2000 lines.
- `state.json` — focused package, displays, a11y services, timestamp.
- `failure.txt` — exception + stack.
- `repro.txt` — one-liner to rerun this failure.

`run-e2e.sh` pulls the tree to `player/build/test-failures/` at the end of the run and prints the host paths. The listener is registered globally via `testInstrumentationRunnerArguments["listener"]` in `player/android/build.gradle.kts` — you don't wire it per-class.

`KoinResetRule` enforces fail-fast: after the first failure, every subsequent test method throws `AssumptionViolatedException` and is marked SKIPPED. One broken test no longer costs 13 redundant failure bundles.

---

## Web E2E Details

## What `run-e2e.sh` Does

The script performs 8 steps in order:

| Step | What happens |
|------|-------------|
| 1. Clear ports | Kills any existing processes on ports 8080 (server) and 5173 (Vite) |
| 2. npm deps | Installs `node_modules` and Playwright browsers if missing |
| 3. Clean auth | Removes stale `storage-state.json` from previous runs |
| 4. Build & seed | Compiles the Go server, creates a fresh SQLite DB in a temp dir, seeds consoles/cores/users |
| 5. Start server | Launches the server with `SPELA_TEST_MODE=true` and waits for the health check |
| 6. Game scan | Triggers a library scan and **polls the scan status endpoint until complete**, then verifies games exist |
| 7. Start Vite | Starts the Vite dev server and waits for it to respond |
| 8. Run tests | Executes `npx playwright test` with any arguments you passed |

On exit (success, failure, or Ctrl+C), the script kills the server and Vite
processes and deletes the temp directory.

## Prerequisites

- **Go** (1.21+) with CGO enabled (for SQLite)
- **Node.js** (18+) and npm
- **Python 3** (used by the script to parse JSON responses)
- **curl** and **lsof** (standard on macOS/Linux)

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SKIP_BUILD` | unset | Set to `1` to skip the Go build and reuse cached binaries from `.e2e-cache/` |
| `SKIP_INSTALL` | unset | Set to `1` to skip the npm dependency check |
| `E2E_SERVERS_RUNNING` | unset | Set automatically by `run-e2e.sh` to tell Playwright not to start its own webServer |

## Architecture

### Fresh state every run

Each run creates a **completely fresh temp directory** with a new SQLite database.
There is no persistent state between runs. This eliminates an entire class of
flakiness caused by leftover data.

```
/tmp/xxx-e2e/
  spela.db      # fresh database, seeded with consoles + cores + admin/player users
  saves/        # empty
  cores/        # empty
  images/       # empty
  bios/         # empty
  dats/         # empty
```

### Test isolation within a run

Tests share a single server instance (Playwright runs with `workers: 1`).
Between tests, the `resetServer()` function calls `POST /api/test/reset` which:

1. Deletes all user-generated data (sessions, saves, collections, challenges, etc.)
2. Preserves consoles, cores, and scanned games
3. Resets the `admin` and `player` users to their default state

Tests that mutate server state should call `resetServer()` in `beforeEach` or `beforeAll`:

```typescript
import { test, expect, resetServer } from "./fixtures";

test.describe("My Feature", () => {
  test.beforeEach(async () => {
    await resetServer();
  });

  test("does something", async ({ page }) => {
    // ...
  });
});
```

### Readiness gate

Before any tests run, the global setup (`global-setup.spec.ts`) verifies:

1. Server health check passes
2. Test reset endpoint is available (confirms `SPELA_TEST_MODE=true`)
3. Admin login works
4. Games have been scanned (game count > 0)

If any of these fail, all tests are skipped with a clear error message.

### Authentication

The global setup logs in as `admin/admin123` and saves the browser storage state
to `e2e/.auth/storage-state.json`. All subsequent tests inherit this
authenticated context automatically.

Tests that need an unauthenticated context can override this:

```typescript
test.use({ storageState: { cookies: [], origins: [] } });
```

## Test Patterns

### Tests that hit the real server

These tests exercise the actual API. Use `resetServer()` to ensure clean state:

```typescript
test.beforeEach(async () => {
  await resetServer();
});

test("admin can change settings", async ({ page }) => {
  await page.goto("/admin/settings");
  // interact with real server...
});
```

### Tests with mocked API routes

Many tests mock API responses using Playwright's `page.route()`. These don't
need `resetServer()` since they never hit the real server:

```typescript
test("shows challenge cards", async ({ page }) => {
  await page.route("**/api/challenges?*", (route) => {
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ data: [...], total: 1, page: 1, pageSize: 20 }),
    });
  });

  await page.goto("/challenges");
  await expect(page.getByText("My Challenge")).toBeVisible();
});
```

## Seeded Test Data

The E2E environment provides:

| Data | Details |
|------|---------|
| **Admin user** | `admin` / `admin123` (role: `owner`) |
| **Player user** | `player` / `player123` (role: `user`) |
| **Consoles** | ~60 systems (NES, SNES, GBA, PSX, etc.) |
| **Cores** | All libretro core definitions |
| **Games** | ~171 games scanned from `testdata/roms/` |

## Alternative: Manual Setup

If you want to start the environment manually (e.g., to keep it running while
you iterate on tests):

```bash
# Terminal 1: Start the E2E environment
bash web/e2e/start-e2e.sh

# Terminal 2: Run tests (will reuse the running server)
cd web
npx playwright test
npx playwright test --ui          # interactive mode
npx playwright test admin-settings  # specific file
```

The `start-e2e.sh` script performs the same setup as `run-e2e.sh` steps 1-7
and then blocks until Ctrl+C.

## Alternative: Docker Compose

For CI or when you don't want to build locally:

```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
cd web
npx playwright test
docker compose -f docker-compose.e2e.yml down -v  # -v removes volumes
```

Note: Docker volumes persist between runs. Always use `down -v` to get a clean
state, or rely on `resetServer()` within tests.

## Troubleshooting

### "Server did not become ready"

- Check if something else is using port 8080: `lsof -i :8080`
- The script should kill it automatically, but if `lsof` fails (permissions),
  you may need to kill it manually

### "No games found after scan"

- Verify `testdata/roms/` exists and contains ROM files
- Check server logs for scan errors (the scan runs async and the script polls
  for completion)

### "Test reset failed"

- The server must be started with `SPELA_TEST_MODE=true` for the reset endpoint
  to be registered

### Stale auth / "401 Unauthorized" in tests

- `run-e2e.sh` automatically removes old `storage-state.json`
- If running manually, delete `web/e2e/.auth/storage-state.json`

### Port already in use / stale local servers

```bash
# Kill processes on E2E ports
kill $(lsof -ti :8080) 2>/dev/null
kill $(lsof -ti :5173) 2>/dev/null
```

**Watch out for stale Go servers.** If you previously ran the backend with
`go run ./cmd/server`, those processes survive in the background and intercept
port 8080 before Docker. The Docker E2E container maps to `0.0.0.0:8080`, but
a local process on `localhost:8080` takes priority. Symptoms: health check
passes (local server), but `POST /api/test/reset` returns 404 (local server
doesn't have test mode enabled).

Fix: `lsof -i :8080` — kill any `server` process that isn't Docker.

### Tests pass locally but fail in CI

- CI does not currently run Playwright E2E tests (only unit tests and desktop
  player tests). See `.github/workflows/ci.yml` for the current CI setup.

## File Reference

| File | Purpose |
|------|---------|
| `run-e2e.sh` | One-command test runner (recommended entry point) |
| `web/e2e/start-e2e.sh` | Starts E2E environment only (no test execution) |
| `web/run-e2e.sh` | Thin wrapper around `npx playwright test` |
| `web/playwright.config.ts` | Playwright configuration (ports, workers, auth) |
| `web/e2e/fixtures.ts` | Exports `test`, `expect`, and `resetServer()` |
| `web/e2e/global-setup.spec.ts` | Readiness checks + auth setup |
| `web/e2e/*.spec.ts` | Individual test files |
| `docker-compose.e2e.yml` | Docker-based E2E environment |
| `server/internal/api/test_handler.go` | Server-side reset endpoint |
| `server/cmd/seed/main.go` | Database seeding logic |
| `testdata/roms/` | Test ROM files (not in git) |

## Android E2E — Landscape Handhelds (AYN Thor, Odin 2, etc.)

The reference Android test device is a landscape gaming handheld (1920×1080).
The on-screen keyboard covers the entire screen on these devices, so test
flows must include `hideKeyboard` after every `inputText` step or the next
assertion won't find any visible UI.

When running tests against a portrait emulator, switch the emulator to
landscape to match the handheld layout:

```bash
adb -s emulator-5554 shell settings put system accelerometer_rotation 0
adb -s emulator-5554 shell settings put system user_rotation 1
```

`user_rotation 1` = landscape (90° counter-clockwise); `user_rotation 0` =
portrait. Apply this *after* the emulator boots, before running tests.

### Touch input on clamshell devices

Some landscape handhelds (AYN Thor) are clamshells — when closed, the
touchscreen digitizer is off and `adb shell input tap` is a no-op. Touch
input only works when the device is open. For automated runs, use
gamepad / DPAD navigation instead:

| Keycode | Action |
|---------|--------|
| `KEYCODE_BUTTON_R1` / `KEYCODE_BUTTON_L1` | Cycle bottom-nav tabs (Home, Consoles, Collections, Activity, Settings) |
| `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` | Move focus within a screen |
| `KEYCODE_BUTTON_A` | Confirm / select |
| `KEYCODE_BACK` | Back |

The bottom tab bar is hidden when a controller is connected — use L1/R1.

Gamepad button correctness (BUTTON_A, BUTTON_B, L1/R1) requires manual QA
with a physical controller; D-pad navigation tests use the standard Android
DPAD keycodes via UiAutomator.
