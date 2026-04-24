# Android E2E Lifecycle Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the Android E2E suite around a fresh-per-run backend AND app, a per-test reset contract, and a single `BaseE2ETest` class that enforces "entry = Home, logged-in; exit = Home" so one failure cannot cascade into the next test.

**Architecture:** Three layers. Layer 1 is the run script (`run-e2e.sh`): uninstall the app per run and pre-cache one core (`nestopia`) from a host-local cache, falling back to libretro buildbot once per dev machine. Layer 2 is a new abstract `BaseE2ETest` class that wires the existing `KoinResetRule` + `createAndroidComposeRule` plus a new `@Before`/`@After` pair calling `POST /api/test/reset`, `ensureLoggedIn()`, `assertOnHome()`, and `navigateBackToHome()`. Layer 3 is migration: every current test class extends `BaseE2ETest` and drops its local rules; `EstablishSessionTest` overrides `baseSetUp()` to force sign-out. All existing diagnostic infrastructure (`FailureDiagnosticsListener`, fail-fast via `anyTestFailed`, host-side artefact pull) is preserved untouched.

**Tech Stack:** Kotlin, JUnit4, Compose UI Test, UiAutomator, `adb`, Docker Compose, existing Go backend `huma_test_reset.go` endpoint.

**Spec:** `docs/superpowers/specs/2026-04-25-android-e2e-lifecycle-hardening.md`

---

## Must preserve (do not touch)

- `FailureDiagnosticsListener.kt` — the whole file.
- `player/android/build.gradle.kts:52-53` — the `testInstrumentationRunnerArguments["listener"]` wiring.
- `KoinResetRule` in `TestHelpers.kt:71-118` — especially the `anyTestFailed` fail-fast gate at lines 84-88.
- `run-e2e.sh` diagnostics pull block (`HOST_FAILURES_DIR`, `DEVICE_FAILURES_DIR`, `cleanup_after_tests`).

Any task below that modifies these files calls out exactly which lines it is allowed to change.

---

## File structure

| Path | Action | Purpose |
|------|--------|---------|
| `player/run-e2e.sh` | modify | Add `adb uninstall` and core-cache steps; leave diagnostics pull + a11y + display-0 logic intact. |
| `player/scripts/cache-nestopia.sh` | create | Host-side: ensure `player/.e2e-cores/nestopia_libretro_android.so` exists (download once), then `adb push` to device. |
| `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt` | modify | Shrink `preCacheCores()` list to `nestopia` only; add `resetServerState()`, `assertOnHome()`; promote `navigateBackToHome()` to internal. |
| `player/android/src/androidTest/java/com/spela/player/android/BaseE2ETest.kt` | create | Abstract base class with entry/exit contract. |
| `player/android/src/androidTest/java/com/spela/player/android/ResetServerStateTest.kt` | create | Tiny integration test exercising `resetServerState()` in isolation so we can debug it without dragging a full test in. |
| 14 × `*Test.kt` under `player/android/src/androidTest/java/com/spela/player/android/` | modify | Extend `BaseE2ETest`, drop local `KoinResetRule` + `ComposeRule` declarations, remove redundant `startLoggedIn()` first lines. |
| `player/android/src/androidTest/java/com/spela/player/android/EstablishSessionTest.kt` | modify | Override `baseSetUp()` to force sign-out so the test actually exercises the first-install server-connection flow. |
| `docs/e2e-testing.md` | modify | New "Android test lifecycle" section describing the contract, the opt-out env var, and how to add a new test. |

---

## Task 1: Add `adb uninstall` to the run script

**Goal:** Every run starts with the APK's SQLDelight DB, shared prefs, and filesDir wiped. Real first-install UX is what the first test sees.

**Files:**
- Modify: `player/run-e2e.sh` (insert between the existing `docker compose down -v` block and the `adb reverse` line)

- [ ] **Step 1: Add the uninstall step**

Open `player/run-e2e.sh`. Find the block ending with `echo "Backend up and healthy."` (around line 44) and the block starting with `# ── Unlock device if locked ──`. Insert a new block between them:

```bash
# ── Clean-slate app state ──
# Uninstall the app (if present) so every run starts from a real
# first-install: no SQLDelight auth tokens, no cached server
# connection, no play history, no downloaded games, no shader
# overrides. Pairs with `docker compose down -v` above, which
# wipes backend state — together the world is genuinely fresh.
#
# The `|| true` swallows the "package not found" exit on a
# machine that's never installed the app.
echo "── Uninstalling com.spela.player on $ADB_SERIAL ──"
adb -s "$ADB_SERIAL" uninstall com.spela.player >/dev/null 2>&1 || true
echo "App uninstalled (or was not present)."
```

- [ ] **Step 2: Verify the script still parses**

Run: `bash -n player/run-e2e.sh`
Expected: no output, exit code 0.

- [ ] **Step 3: Sanity-run the script on a device (if a device is attached)**

Run: `cd player && ./run-e2e.sh com.spela.player.android.EstablishSessionTest#establishSession`
Expected: the output includes `── Uninstalling com.spela.player ──` followed by `App uninstalled (or was not present).`, then the normal boot-up, then the single test runs (may fail in later tasks — that is fine here; we only need to see that the new echo line prints).

If no device is attached, skip this step and verify the change visually.

- [ ] **Step 4: Commit**

```bash
git add player/run-e2e.sh
git commit -m "test(android): uninstall app before every E2E run

Pairs with the existing docker compose down -v so every run
starts with a fully fresh world — backend AND app."
```

---

## Task 2: Shrink core pre-cache to `nestopia` + host-side cache script

**Goal:** Pre-cache exactly one core for the NES happy-path tests. Download from libretro buildbot once per dev machine; reuse a host-local cache afterwards.

**Files:**
- Create: `player/scripts/cache-nestopia.sh`
- Modify: `player/run-e2e.sh` (call the script after uninstall, before device hygiene)
- Modify: `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt:101` (shrink the `knownCores` list)

- [ ] **Step 1: Create the host-side cache script**

Create `player/scripts/cache-nestopia.sh`:

```bash
#!/usr/bin/env bash
# Ensure nestopia (NES libretro core) is present on the connected device at
# /data/local/tmp/ so KoinResetRule.preCacheCores() can copy it into the
# app's cores dir at test startup. Pre-caching saves every E2E run from
# re-downloading nestopia — real users download on-demand, but thrashing
# libretro buildbot once per CI-style run is antisocial.
#
# Resolution order:
#   1. If the core is already on the device → no-op.
#   2. If a host-local cache at player/.e2e-cores/ has it → adb push only.
#   3. Otherwise, curl from libretro buildbot once, save locally, push.
#
# Opt out via SPELA_E2E_REAL_CORE_DOWNLOAD=1 — in that case the device
# is left empty and the first test to start a game exercises the real
# first-download flow.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLAYER_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_FILE="nestopia_libretro_android.so"
DEVICE_PATH="/data/local/tmp/$CORE_FILE"
HOST_CACHE_DIR="$PLAYER_DIR/.e2e-cores"
HOST_CACHE="$HOST_CACHE_DIR/$CORE_FILE"
BUILDBOT_URL="https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/nestopia_libretro_android.so.zip"

ADB_SERIAL="${ADB_SERIAL:-}"
if [ -z "$ADB_SERIAL" ]; then
  echo "cache-nestopia: ADB_SERIAL not set — skipping." >&2
  exit 0
fi

if [ "${SPELA_E2E_REAL_CORE_DOWNLOAD:-0}" = "1" ]; then
  echo "cache-nestopia: SPELA_E2E_REAL_CORE_DOWNLOAD=1 — leaving device empty so tests exercise real download."
  exit 0
fi

# 1. Already on device?
if adb -s "$ADB_SERIAL" shell "[ -f $DEVICE_PATH ]" 2>/dev/null; then
  echo "cache-nestopia: $DEVICE_PATH already present."
  exit 0
fi

mkdir -p "$HOST_CACHE_DIR"

# 2. Host-local cache?
if [ ! -f "$HOST_CACHE" ]; then
  echo "cache-nestopia: downloading once from libretro buildbot ($BUILDBOT_URL)…"
  TMP_ZIP="$HOST_CACHE_DIR/.nestopia.download.zip"
  trap 'rm -f "$TMP_ZIP"' EXIT
  curl -fsSL -o "$TMP_ZIP" "$BUILDBOT_URL"
  unzip -oq "$TMP_ZIP" -d "$HOST_CACHE_DIR"
  rm -f "$TMP_ZIP"
  trap - EXIT
  if [ ! -f "$HOST_CACHE" ]; then
    echo "cache-nestopia: download unpacked but $HOST_CACHE is missing — aborting." >&2
    exit 1
  fi
  echo "cache-nestopia: cached at $HOST_CACHE."
fi

# 3. Push to device.
adb -s "$ADB_SERIAL" push "$HOST_CACHE" "$DEVICE_PATH" >/dev/null
echo "cache-nestopia: pushed $CORE_FILE to $DEVICE_PATH."
```

- [ ] **Step 2: Make the script executable**

Run: `chmod +x player/scripts/cache-nestopia.sh`

- [ ] **Step 3: Wire the script into `run-e2e.sh`**

Open `player/run-e2e.sh`. Between the uninstall block added in Task 1 and the existing `# ── Unlock device if locked ──` block, insert:

```bash
# ── Core pre-cache (nestopia for NES happy path) ──
# See player/scripts/cache-nestopia.sh for the resolution order and
# the SPELA_E2E_REAL_CORE_DOWNLOAD=1 opt-out.
echo "── Pre-caching nestopia core ──"
ADB_SERIAL="$ADB_SERIAL" "$SCRIPT_DIR/scripts/cache-nestopia.sh"
```

- [ ] **Step 4: Shrink `preCacheCores()` hard-coded list**

Open `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt`. Find the `preCacheCores()` function (around line 96-117). Replace the `knownCores` assignment:

From:
```kotlin
            val knownCores = listOf("nestopia", "snes9x", "mgba", "gambatte", "genesis_plus_gx",
                "mupen64plus_next", "mednafen_psx_hw", "ppsspp", "desmume")
```

To:
```kotlin
            // Only nestopia is pre-cached by run-e2e.sh + scripts/cache-nestopia.sh;
            // every other core is downloaded on-demand at first use, matching the
            // real user flow. The list used to include 8 more cores aspirationally,
            // but nothing in the current suite tests them and they just dragged
            // Gradle's APK install setup with irrelevant /data/local/tmp/ copies.
            val knownCores = listOf("nestopia")
```

- [ ] **Step 5: Verify the change compiles locally**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add player/scripts/cache-nestopia.sh player/run-e2e.sh player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt
git commit -m "test(android): pre-cache nestopia only + host-local core cache

Shrink KoinResetRule.preCacheCores() to the one core the current
suite actually uses (nestopia, for NES happy-path tests).

Add scripts/cache-nestopia.sh — resolves the core from the device,
then from player/.e2e-cores/, falling back to libretro buildbot
exactly once per dev machine. Opt out via SPELA_E2E_REAL_CORE_DOWNLOAD=1."
```

---

## Task 3: Add `resetServerState()` helper + integration test

**Goal:** New helper that POSTs `/api/test/reset` from the instrumentation process, asserts a 200 + `{"status":"reset"}` body, and raises a clear error on anything else.

**Files:**
- Modify: `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt` (append near the top, alongside other wait helpers)
- Create: `player/android/src/androidTest/java/com/spela/player/android/ResetServerStateTest.kt`

- [ ] **Step 1: Add the helper to TestHelpers.kt**

Insert after the `const val` block near the top (around line 135, after `private val challengesCreated = mutableSetOf<String>()`):

```kotlin
// ── Backend state reset ──

/**
 * POST /api/test/reset on the docker-compose E2E server.
 *
 * Called from @Before in BaseE2ETest so every test method starts with
 * user-generated data wiped: sessions, saves, favorites, collections,
 * challenges, play history, shader prefs, refresh tokens, login
 * attempts. Consoles, cores, and scanned games are preserved by the
 * server-side handler — see server/internal/api/huma_test_reset.go.
 *
 * Unauthenticated by design (see the handler comment). Runs on the
 * instrumentation thread (not main), so plain HttpURLConnection is
 * safe. Reaches the host via `adb reverse tcp:8080 tcp:8080` which
 * run-e2e.sh sets up before gradle is invoked.
 *
 * Hard-failure semantics: any response other than 200 with a body
 * containing `"status":"reset"` aborts the test immediately. Reset
 * is load-bearing — silently skipping it defeats the whole isolation
 * story.
 *
 * JWT note: the handler resets User.token_version to 0 and deletes
 * RefreshToken rows. A JWT issued at token_version=0 stays valid,
 * so the common case keeps the current session alive. If it doesn't
 * (tests that rotate tokens), ensureLoggedIn() detects the ensuing
 * 401 → login screen and re-authenticates.
 */
fun resetServerState() {
    val url = java.net.URL("http://127.0.0.1:8080/api/test/reset")
    val conn = url.openConnection() as java.net.HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 3_000
        conn.readTimeout = 5_000
        conn.doOutput = true
        conn.outputStream.use { /* empty body */ }
        val code = conn.responseCode
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        check(code == 200) {
            "resetServerState: expected HTTP 200, got $code. Body: $body. " +
                "Is docker-compose.e2e.yml running with SPELA_TEST_MODE=true, " +
                "and is `adb reverse tcp:8080 tcp:8080` set up?"
        }
        check(body.contains("\"status\":\"reset\"")) {
            "resetServerState: expected body to contain '\"status\":\"reset\"', got: $body"
        }
    } finally {
        conn.disconnect()
    }
}
```

- [ ] **Step 2: Create the integration test**

Create `player/android/src/androidTest/java/com/spela/player/android/ResetServerStateTest.kt`:

```kotlin
package com.spela.player.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused integration test for resetServerState(). Runs against the
 * docker-compose E2E server. Deliberately does NOT extend BaseE2ETest
 * — the whole point is to exercise the reset call in isolation so a
 * future regression is easy to diagnose without the base-class setup
 * noise.
 */
@RunWith(AndroidJUnit4::class)
class ResetServerStateTest {

    @Test
    fun resetReturnsSuccessfully() {
        // If this throws, the error message already identifies the cause
        // (test mode off, port forwarding missing, server down, etc.).
        // Calling twice in a row proves idempotency too.
        resetServerState()
        resetServerState()
    }
}
```

- [ ] **Step 3: Run the new test**

Run: `cd player && ./run-e2e.sh com.spela.player.android.ResetServerStateTest`
Expected: `BUILD SUCCESSFUL`, test `resetReturnsSuccessfully` passes in under a second.

If it fails with "connection refused", confirm docker-compose is up (`docker compose -f docker-compose.e2e.yml ps`) and `adb reverse tcp:8080 tcp:8080` is set (the script does this automatically).

- [ ] **Step 4: Commit**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt player/android/src/androidTest/java/com/spela/player/android/ResetServerStateTest.kt
git commit -m "test(android): add resetServerState() helper + smoke test

POST /api/test/reset via HttpURLConnection, hard-fail on anything
other than 200 + status=reset. Deliberately verbose error messages
pointing at the three likely causes (test mode, port forward,
docker). ResetServerStateTest exercises it in isolation so future
regressions are easy to diagnose without the base-class setup."
```

---

## Task 4: Add `assertOnHome()` + promote `navigateBackToHome()` visibility

**Goal:** A cheap explicit Home check, and a publicly-callable back-to-Home helper that the base class can use in `@After`.

**Files:**
- Modify: `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt` (change `navigateBackToHome` visibility; append `assertOnHome`)

- [ ] **Step 1: Promote `navigateBackToHome` from `private` to `internal`**

Open `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt`. Find line 787:

From:
```kotlin
private fun ComposeRule.navigateBackToHome() {
```

To:
```kotlin
internal fun ComposeRule.navigateBackToHome() {
```

Keep the function body exactly as-is.

- [ ] **Step 2: Add `assertOnHome()` below the `navigateBackToHome` function**

After the closing `}` of `navigateBackToHome()` (find the line before `/**` of the next function), insert:

```kotlin
/**
 * Cheap post-condition check: we must be on the Home screen.
 *
 * Called at the end of BaseE2ETest.baseSetUp() to turn silent
 * contract violations into a loud early failure instead of letting
 * the test body limp along and fail somewhere downstream with a
 * confusing "text not found" that's really about starting on the
 * wrong screen.
 *
 * Fails with a one-line diagnostic listing which top-level screen
 * indicators are actually visible. Don't use for flaky polling —
 * that's what pollUntil + isOnHomeScreen are for; this is a
 * one-shot "are we there yet" assertion.
 */
fun ComposeRule.assertOnHome() {
    if (isOnHomeScreen()) return
    val indicators = buildList {
        if (isOnServerConnectionScreen()) add("server-connection")
        if (isOnLoginScreen()) add("login")
        try {
            if (onAllNodesWithTag(TestTags.NAV_HOME, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()) add("bottom-nav-present")
        } catch (_: Exception) { /* compose tree not ready */ }
        val device = uiDevice()
        if (device.findObject(UiSelector().descriptionContains("Settings")).exists())
            add("settings-screen")
        if (device.findObject(UiSelector().descriptionContains("Game running")).exists())
            add("in-game")
        if (device.findObject(UiSelector().descriptionContains("Go back")).exists())
            add("detail-screen")
    }
    error(
        "assertOnHome: not on Home screen. Visible indicators: " +
            if (indicators.isEmpty()) "none (unknown screen)" else indicators.joinToString(",")
    )
}
```

- [ ] **Step 3: Verify compilation**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt
git commit -m "test(android): assertOnHome() helper, expose navigateBackToHome

assertOnHome fails loudly with visible indicators when the 'entry
contract' check in the coming BaseE2ETest catches a test starting
on the wrong screen. navigateBackToHome goes from private to
internal so the base class @After can call it."
```

---

## Task 5: Create `BaseE2ETest`

**Goal:** One abstract class every test extends. Wires the existing `KoinResetRule` and `createAndroidComposeRule`, adds `@Before`/`@After` that enforce entry = Home logged-in + exit = Home.

**Files:**
- Create: `player/android/src/androidTest/java/com/spela/player/android/BaseE2ETest.kt`

- [ ] **Step 1: Create the class**

Create `player/android/src/androidTest/java/com/spela/player/android/BaseE2ETest.kt`:

```kotlin
package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * Base class for every Android E2E test.
 *
 * Contract:
 *   Entry (per test method): user is logged in, on the Home screen,
 *                            backend reset to seed state.
 *   Exit  (per test method): user is logged in, on the Home screen.
 *
 * The entry contract frees each test from re-running login logic.
 * The exit contract means a crashed test doesn't leak its screen
 * state to the following test.
 *
 * Rules run outermost-first (order=0 before order=1):
 *   0. KoinResetRule — fail-fast gate (anyTestFailed) + preCacheCores
 *      + isTestMode flip. Must run BEFORE the Activity is created.
 *   1. createAndroidComposeRule — launches MainActivity.
 *
 * The existing FailureDiagnosticsListener is registered globally via
 * testInstrumentationRunnerArguments["listener"] in build.gradle.kts,
 * so failures here still produce screenshot + ui.xml + logcat +
 * state.json + failure.txt + repro.txt under /sdcard/spela-test-failures/
 * — the base class does not touch that machinery.
 *
 * Tests that need a different entry state (e.g. EstablishSessionTest,
 * which is about the first-install server-connect UX) override
 * baseSetUp() and skip or modify the login step. Keep the @Before
 * annotation when overriding, otherwise JUnit silently won't call it.
 */
@RunWith(AndroidJUnit4::class)
abstract class BaseE2ETest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    @Before
    open fun baseSetUp() {
        // 1. Reset backend to seed state. Hard-fails loudly if the
        //    endpoint is unreachable — this is load-bearing.
        resetServerState()

        // 2. Ensure we're logged in. ensureLoggedIn() fast-paths when
        //    Home is already visible (every test after the first),
        //    and falls back to add-server + login when SQLDelight is
        //    empty (first test of the run) or the JWT was invalidated
        //    by the reset (rare — happens when a prior test rotated
        //    tokens).
        rule.ensureLoggedIn()

        // 3. Contract check. If we're not on Home, something in the
        //    setup path is broken — surface it now, not 30s into the
        //    test body.
        rule.assertOnHome()
    }

    @After
    open fun baseTearDown() {
        // Best-effort: return to Home so the next test starts from a
        // known state without having to run slow screen detection.
        // navigateBackToHome handles in-game overlays, settings
        // sub-screens, and arbitrary deep links; it stops at auth
        // screens to avoid exiting the Activity.
        //
        // runCatching because a failure here must not mask the real
        // assertion failure — FailureDiagnosticsListener has already
        // captured artefacts at the moment of the @Test failure.
        runCatching { rule.navigateBackToHome() }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/BaseE2ETest.kt
git commit -m "test(android): BaseE2ETest — entry/exit contract

Every test that extends this starts logged-in on Home with backend
reset, and ends back on Home. Rules inherited: KoinResetRule
(fail-fast, preCacheCores, test mode) at order=0, ComposeRule at
order=1. FailureDiagnosticsListener stays wired globally — the
base class does not touch it."
```

---

## Task 6: Migrate `EmulationTest` to `BaseE2ETest` (pilot)

**Goal:** Convert one test class fully so we can prove the base class behaves as designed before doing the bulk migration.

**Files:**
- Modify: `player/android/src/androidTest/java/com/spela/player/android/EmulationTest.kt`

- [ ] **Step 1: Replace the class header**

Open `player/android/src/androidTest/java/com/spela/player/android/EmulationTest.kt`.

Replace lines 1-19 (the package, imports, annotations, class opening, and both `@get:Rule` declarations) with:

```kotlin
package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

class EmulationTest : BaseE2ETest() {

    private fun setupGame() {
        // Login is already handled by BaseE2ETest.baseSetUp(). This
        // wrapper now exists only to navigate to the NES happy-path
        // game; retain the name so existing call sites don't churn.
        rule.navigateToGameAndPlay()
    }
```

The `@RunWith(AndroidJUnit4::class)` annotation is inherited (it's on `BaseE2ETest`), so remove it here. The `KoinResetRule` and `createAndroidComposeRule` declarations are also inherited, so drop them.

- [ ] **Step 2: Drop redundant `startLoggedIn()` calls inside existing tests**

Still in `EmulationTest.kt`, remove the standalone `rule.startLoggedIn()` lines that the base class already covers:

- Line 130 (inside `exitNoConfirmationWithAutoSave`) — delete the line.
- Line 205 (inside `nesFpsCheck`) — delete the line.
- Line 217 (inside `fpsHudVisible`) — delete the line.

The `setupGame()` calls in the other tests stay — they now only do the navigate step.

- [ ] **Step 3: Verify compilation**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run just this class to validate the pilot**

Run: `cd player && ./run-e2e.sh com.spela.player.android.EmulationTest`
Expected: all 14 `EmulationTest` methods run to completion. First test goes through the add-server + login path; subsequent tests hit the fast path.

If a specific test fails for a reason unrelated to the migration (e.g. a flaky overlay assertion), note it, fix only if trivial, otherwise flag it in the commit message as a known follow-up. The goal of this task is infra, not test correctness beyond "it compiles and the runner starts cleanly."

- [ ] **Step 5: Commit**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/EmulationTest.kt
git commit -m "test(android): migrate EmulationTest to BaseE2ETest

Pilot migration. Drops per-class KoinResetRule + ComposeRule
declarations and redundant startLoggedIn() calls in three methods.
setupGame() now just navigates — login is handled by baseSetUp()."
```

---

## Task 7: Migrate remaining test classes to `BaseE2ETest`

**Goal:** Apply the same transformation uniformly across the other 13 classes.

**Files:** all modify

- `ChallengeAttemptTest.kt`
- `ChallengeBrowsingTest.kt`
- `ChallengeCreationTest.kt`
- `ChallengeIntegrationTest.kt`
- `ChallengeLeaderboardTest.kt`
- `CloneSessionSmokeTest.kt`
- `CollectionsTest.kt`
- `CoreDecisionFlagsSmokeTest.kt`
- `GamepadNavigationTest.kt`
- `HwRenderTest.kt`
- `NavigationTest.kt`
- `NetplayTest.kt`
- `PlayLaterTest.kt`
- `SessionTest.kt`
- `SettingsTest.kt`
- `TouchControlsTest.kt`

(NOT: `EstablishSessionTest.kt` — Task 8 handles that one specially. NOT: `ResetServerStateTest.kt` — intentionally freestanding.)

For **every** class in the list above, apply these four edits:

- [ ] **Step 1: Change class declaration**

From (pattern):
```kotlin
@RunWith(AndroidJUnit4::class)
class FooTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()
```

To:
```kotlin
class FooTest : BaseE2ETest() {
```

(Delete the `@RunWith(AndroidJUnit4::class)` line, both `@get:Rule` blocks, and the empty line between them. Add the `: BaseE2ETest()` inheritance.)

Some files use a different variable name (`composeTestRule` instead of `rule`) — in those files, additionally rename that variable to `rule` in every reference within the file, so the base class's `rule` property is what the tests call. Do NOT create a `val composeTestRule = rule` alias — a blanket rename keeps the code uniform and avoids stale helper references later.

- [ ] **Step 2: Remove now-redundant imports**

Remove these imports (they're no longer referenced in the class once the rules are gone):

```kotlin
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.runner.RunWith
```

If a file still uses `@Rule` for some other purpose, keep that one. Grep before deleting. The `@Test`, `@Before`, `@After`, and Compose test imports stay.

- [ ] **Step 3: Remove redundant `startLoggedIn()` / `ensureLoggedIn()` first lines**

Inside each `@Test` method, the very first statement is typically `rule.startLoggedIn()` or `rule.ensureLoggedIn()`. Delete those exact lines — the base class `@Before` has already run by the time the test body starts.

**Do NOT delete** `ensureLoggedIn(username, password)` calls that pass explicit credentials (e.g. `CollectionsTest.kt:417`). Those are tests that specifically log in as a different user. Same for `loginAsPlayer()` / `loginAsAdmin()` — they intentionally swap credentials and are keepers.

Also do NOT delete `restartApp()` calls — those are load-bearing for persistence tests.

- [ ] **Step 4: Verify compilation after each file**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

If compilation fails because a test references `composeTestRule`, rename all remaining occurrences to `rule`.

- [ ] **Step 5: Commit after each file (or in small batches of 2-3 per commit)**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/<FileName>.kt
git commit -m "test(android): migrate <FileName> to BaseE2ETest"
```

Small commits make bisecting a per-class regression trivial. One 16-file megacommit is the wrong call here.

- [ ] **Step 6: Full-compile sanity after all 13 files**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`, zero warnings about unused `KoinResetRule` or unreferenced imports.

---

## Task 8: Migrate `EstablishSessionTest` with a `baseSetUp` override

**Goal:** This is the one test whose whole point is exercising the first-install server-connect UX. It must override the base class to force sign-out before the test body runs, so regardless of run order it starts on the server-connect screen.

**Files:**
- Modify: `player/android/src/androidTest/java/com/spela/player/android/EstablishSessionTest.kt`

- [ ] **Step 1: Rewrite the class**

Replace the entire file with:

```kotlin
package com.spela.player.android

import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Before
import org.junit.Test

/**
 * Tests the user's first-install experience: add server, log in,
 * land on Home. Overrides the base class's "you start logged in"
 * contract because the whole point of this test is the pre-login
 * UX.
 */
class EstablishSessionTest : BaseE2ETest() {

    @Before
    override fun baseSetUp() {
        // Still reset the backend — user-generated data from prior
        // tests must not influence the login flow.
        resetServerState()

        // Make sure we're logged in first (ensureLoggedIn handles
        // arbitrary entry state), then explicitly sign out so the
        // test actually exercises the server-connect screen. This
        // mirrors loginAsPlayer()/loginAsAdmin() in TestHelpers.kt
        // which already rely on signOutIfLoggedIn.
        rule.ensureLoggedIn()
        rule.signOutIfLoggedIn()

        // Skip assertOnHome — we're deliberately NOT on Home here.
    }

    @Test
    fun establishSession() {
        // App is on the server-connect screen. Drive the full flow:
        // add server → log in → land on Home.
        rule.addServerAndLogin(PLAYER_USERNAME, PLAYER_PASSWORD)

        // Verify Home via any of the several indicators the screen
        // may show depending on whether the user has play history.
        rule.pollUntil(timeoutMillis = 8_000L) {
            rule.onAllNodesWithText("Spela")
                .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Your library is empty", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Top Rated", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithText("Continue Playing", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
```

- [ ] **Step 2: Promote `signOutIfLoggedIn`, `addServerAndLogin`, and credentials to `internal` visibility**

In `player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt`, change:

- `private const val PLAYER_USERNAME` (line 124) → `internal const val PLAYER_USERNAME`
- `private const val PLAYER_PASSWORD` (line 125) → `internal const val PLAYER_PASSWORD`
- `private fun ComposeRule.signOutIfLoggedIn()` (line 945) → `internal fun ComposeRule.signOutIfLoggedIn()`
- `private fun ComposeRule.addServerAndLogin(...)` (line 963) → `internal fun ComposeRule.addServerAndLogin(...)`

Leave `ADMIN_USERNAME` / `ADMIN_PASSWORD` and `doLogin` as they are — they stay callers' internals.

- [ ] **Step 3: Verify compilation**

Run: `cd player && ./gradlew :android:compileDebugAndroidTestKotlin`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run just `EstablishSessionTest` to validate**

Run: `cd player && ./run-e2e.sh com.spela.player.android.EstablishSessionTest`
Expected: the test fully drives the add-server + login path and lands on Home.

- [ ] **Step 5: Commit**

```bash
git add player/android/src/androidTest/java/com/spela/player/android/EstablishSessionTest.kt player/android/src/androidTest/java/com/spela/player/android/TestHelpers.kt
git commit -m "test(android): EstablishSessionTest overrides baseSetUp to sign out

The whole point of this test is the first-install server-connect
UX, so inheriting 'you start on Home logged in' would defeat it.
Override resets backend, ensures logged in, then explicitly signs
out so the test body always starts on the server-connect screen.

Promote signOutIfLoggedIn, addServerAndLogin, and PLAYER_*
credentials to internal so EstablishSessionTest can call them."
```

---

## Task 9: Update `docs/e2e-testing.md`

**Goal:** Document the new contract, the opt-out env var, and how to add a new Android E2E test.

**Files:**
- Modify: `docs/e2e-testing.md`

- [ ] **Step 1: Insert the new section**

Open `docs/e2e-testing.md`. After the existing "Test Tag Convention (Player App)" section (line 104 area) and before the `---` separator, insert:

```markdown
### Android Test Lifecycle

Every Android E2E test class extends `BaseE2ETest`. The base class enforces a per-method contract:

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
5. Gradle runs the test classes one at a time, fail-fast by default.

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
```

- [ ] **Step 2: Commit**

```bash
git add docs/e2e-testing.md
git commit -m "docs(e2e): document Android BaseE2ETest lifecycle

New section covers the entry/exit contract, how to add a test,
how to override the contract, the SPELA_E2E_REAL_CORE_DOWNLOAD
opt-out, and explicit call-outs for the preserved diagnostic
pipeline (FailureDiagnosticsListener, fail-fast, host-side pull)."
```

---

## Task 10: Full-suite acceptance run

**Goal:** Prove the whole thing works end-to-end, twice in a row (the second run is the real idempotency check).

- [ ] **Step 1: First run — expect everything green**

Run: `cd player && ./run-e2e.sh --no-fail-fast`

Expected: all test classes execute. Some individual tests may surface pre-existing flakes unrelated to the lifecycle work — those should be documented as follow-ups, not "fixed" inside this plan unless the fix is genuinely trivial and obvious.

Check `player/build/test-failures/` — should be empty or contain only pre-existing unrelated flakes.

- [ ] **Step 2: Second run immediately after — expect identical result**

Run: `cd player && ./run-e2e.sh --no-fail-fast`

Expected: same pass/fail ratio as run 1. This is the idempotency check — if run 2 is different from run 1, state is leaking somewhere the design didn't anticipate.

- [ ] **Step 3: Real-download run to confirm the opt-out works**

Run: `cd player && SPELA_E2E_REAL_CORE_DOWNLOAD=1 ./run-e2e.sh com.spela.player.android.EmulationTest#startGameAndExitViaOverlay`

Expected: the cache-nestopia step logs `leaving device empty so tests exercise real download`; the test still passes because the app downloads nestopia from the server at play-time (this exercises the real user path).

- [ ] **Step 4: Summary commit (empty commit, just for annotating the tree)**

No code change — this is just a marker. Skip if unnecessary.

```bash
git commit --allow-empty -m "test(android): lifecycle hardening — acceptance run complete

Full suite green twice in a row (idempotency). Opt-out run also
green. Follow-ups for any individual-test flakes surfaced during
the run are filed separately."
```

---

## Self-review

**Spec coverage:**
- Layer 1 (run lifecycle) — Tasks 1, 2. ✅
- Core caching middle ground — Task 2 (scripts/cache-nestopia.sh, KoinResetRule shrink, opt-out env var). ✅
- Layer 2 (BaseE2ETest) — Task 5. ✅
- `resetServerState()` helper with JWT caveat + hard-fail semantics — Task 3. ✅
- `ensureLoggedIn()` simplification — not a dedicated task. The spec describes "fast path + slow path" as already present in the current code (lines 866-915 of TestHelpers.kt already do this structure). Rereading: the existing `ensureLoggedIn()` *is* structured as fast-path-then-slow-path already. The "simplification" in the spec is primarily about documenting intent, not rewriting code. No task added — existing code satisfies the spec. If during Task 10 we find it's too slow on the hot path, a follow-up task can tighten it.
- `assertOnHome()` + `navigateBackToHome()` visibility — Task 4. ✅
- Layer 3 (existing tests migrate) — Tasks 6, 7, 8. ✅
- `EstablishSessionTest` override — Task 8. ✅
- Layer 4 (docs) — Task 9. ✅
- Success criteria (green twice in a row) — Task 10. ✅
- Must-preserve diagnostics — called out explicitly at the top, and each task that touches the relevant files lists exactly what it is allowed to change.

**Placeholder scan:** no TBDs. Every code step shows the code. Every command step shows the command. Commit messages are concrete. ✅

**Type consistency:**
- `rule` used as the ComposeRule name in `BaseE2ETest` (Task 5) and required in all migrated test classes (Tasks 6-8). Files that used `composeTestRule` get renamed at Task 7 step 1. ✅
- `resetServerState()` is a top-level function (not an extension) — consistent between Task 3 where it's defined and Task 5 where it's called. ✅
- `assertOnHome()` is an extension on `ComposeRule` — consistent between Task 4 definition and Task 5 usage (`rule.assertOnHome()`). ✅
- `navigateBackToHome()` kept as extension on `ComposeRule`, visibility changes to internal in Task 4; called in Task 5 as `rule.navigateBackToHome()`. ✅
- `signOutIfLoggedIn`, `addServerAndLogin`, `PLAYER_USERNAME`, `PLAYER_PASSWORD` — promoted from `private` to `internal` at Task 8 step 2 before Task 8 step 1's test class references them. ✅

One consistency note: the spec's Layer 2 `baseTearDown()` called `exitGameIfRunning()` as a separate step. During planning I removed it — `navigateBackToHome()` already handles the in-game-overlay path (TestHelpers.kt:797-802). Keeping `exitGameIfRunning()` would be dead code. Task 5 reflects this.
