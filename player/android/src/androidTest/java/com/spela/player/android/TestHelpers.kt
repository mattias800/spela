package com.spela.player.android

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import com.spela.player.presentation.ui.TestTags
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import org.junit.runner.Description
import org.koin.mp.KoinPlatformTools
import androidx.test.espresso.IdlingPolicies
import java.util.concurrent.TimeUnit

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

// Configure Espresso to not wait forever for idle — our neon UI animations
// (gradient glow, ambient blobs) keep the Choreographer busy, causing
// Configure Espresso idle timeout. With LocalAnimationsEnabled=false (set via
// isTestMode), infinite animations are disabled. But the Compose framework
// still schedules Choreographer callbacks for recomposition/layout, keeping the
// main looper non-idle briefly. A 1-second timeout lets these one-shot operations
// complete while preventing long hangs from any remaining continuous activity.
// Emulator detection: Build.PRODUCT looks like "sdk_gphone64_arm64" on
// the AVD and "thor" on the AYN Thor; FINGERPRINT contains "generic" on
// emulators. Used to scale timeouts — the emulator runs Compose at
// wall-clock pace, so recomposition / first-frame / Coil image load is
// ~2-3x slower than on a physical device.
internal val isEmulator: Boolean =
    android.os.Build.PRODUCT?.contains("sdk") == true ||
        android.os.Build.PRODUCT?.contains("emulator") == true ||
        android.os.Build.FINGERPRINT?.contains("generic") == true

private val timeoutMultiplier: Long = if (isEmulator) 3L else 1L

private val testConfigured = run {
    // 3s on physical, 9s on emulator: Coil AsyncImage settle time scales
    // with frame budget; emulator runs Compose at wall-clock pace, so
    // recomposition + image load can take 2-3x longer than on the AYN Thor.
    val idleSeconds = if (isEmulator) 9L else 3L
    IdlingPolicies.setMasterPolicyTimeout(idleSeconds, TimeUnit.SECONDS)
    IdlingPolicies.setIdlingResourceTimeout(idleSeconds, TimeUnit.SECONDS)
    MainActivity.isTestMode = true
    true
}

// ── Koin reset rule ──

/**
 * Reloads Koin modules between tests so singleton definitions are replaced.
 * Uses unloadModules + loadModules to fully clear cached singleton instances.
 * loadModules(allowOverride=true) alone may not clear the scope's instance cache,
 * causing stale singleton state to leak between tests (e.g., requestExit=true).
 * Must be order=0 (outer) so singletons are refreshed BEFORE ComposeRule (order=1)
 * creates the Activity.
 */
/**
 * Sets isTestMode=true BEFORE the Activity is created (order=0).
 * This disables continuous animations so Compose test's waitForIdle() doesn't hang.
 *
 * Note: does NOT reset Koin modules. Resetting Koin creates new ViewModels
 * while the Compose tree keeps old LaunchedEffect keys, causing LaunchedEffects
 * to not re-fire (e.g., the server form auto-open doesn't trigger).
 */
class KoinResetRule : org.junit.rules.TestRule {
    override fun apply(base: org.junit.runners.model.Statement, description: Description): org.junit.runners.model.Statement {
        return object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                // FAIL-FAST GATE: once any test in this run has failed,
                // skip every subsequent test. Without this, when
                // navigation or login breaks, gradle still runs all 14
                // EmulationTest methods, producing 14 identical failure
                // bundles and wasting ~9 minutes per suite. Throwing
                // AssumptionViolatedException from inside the rule's
                // statement (not from a TestWatcher callback) makes
                // JUnit mark the test as ignored/skipped, which gradle
                // surfaces as SKIPPED rather than FAILED.
                // Per-test fail-fast: once any test in this class run has
                // failed, skip every subsequent test. Saves ~9 minutes per
                // suite on a broken navigation/login. Bypass for a
                // diagnostic run with `-P android.testInstrumentationRunnerArguments.failFast=off`
                // to see every failure in a class at once.
                val ffArg = androidx.test.platform.app.InstrumentationRegistry
                    .getArguments().getString("failFast")
                if (ffArg != "off" && FailureDiagnosticsListener.anyTestFailed) {
                    throw org.junit.AssumptionViolatedException(
                        "Skipping ${description.methodName} — earlier failure in this run, fail-fast active"
                    )
                }
                MainActivity.isTestMode = true
                preCacheCores()
                base.evaluate()
            }
        }
    }

    private fun preCacheCores() {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val coresDir = java.io.File(context.filesDir, "cores")
            coresDir.mkdirs()
            // Cores pre-cached by run-e2e.sh + scripts/cache-cores.sh
            // and copied into the app's cores dir here so the first
            // game start in a test class doesn't pay the libretro
            // buildbot fetch + extract cost.
            //
            // nestopia: NES happy path (Castlevania, used by every
            //   Challenge*, Settings, Emulation, Session test).
            // mupen64plus_next_gles3: N64 happy path
            //   (Banjo-Kazooie, used by HwRenderTest). The gles3
            //   variant is what the libretro buildbot ships for
            //   Android — the player maps the abstract
            //   "mupen64plus_next" onto this binary at runtime via
            //   ANDROID_CORE_SUBSTITUTIONS in EmulationUseCases.
            // Other cores download on-demand at first use, matching
            // the real user flow.
            val knownCores = listOf("nestopia", "mupen64plus_next_gles3")
            for (coreName in knownCores) {
                val fileName = "${coreName}_libretro_android.so"
                val src = java.io.File("/data/local/tmp/$fileName")
                val dest = java.io.File(coresDir, fileName)
                if (src.exists() && !dest.exists()) {
                    src.inputStream().use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    android.util.Log.d("E2E_SETUP", "Pre-cached core: $fileName")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("E2E_SETUP", "Core pre-cache failed: ${e.message}")
        }
    }
}

// ── Constants ──

private const val SERVER_NAME = "Local"
private const val SERVER_URL = "http://127.0.0.1:8080"
internal const val PLAYER_USERNAME = "player"
internal const val PLAYER_PASSWORD = "player123"
private const val ADMIN_USERNAME = "admin"
private const val ADMIN_PASSWORD = "admin123"

// Timeouts are physical-device baselines. The wait helpers (waitForText
// etc.) apply timeoutMultiplier internally, so a `timeout = 15_000`
// passed in a test becomes 45_000 on the emulator. This keeps callers
// honest about the wait length they expect on real hardware while
// auto-scaling for the slower frame budget on the AVD.
private const val TIMEOUT_SHORT = 5_000L
private const val TIMEOUT_MEDIUM = 10_000L
private const val TIMEOUT_LONG = 15_000L
private const val TIMEOUT_EXTRA_LONG = 30_000L

/** Apply the per-environment timeout multiplier (3x on emulator, 1x on physical). */
private fun Long.scaledTimeout(): Long = this * timeoutMultiplier

/** Tracks challenges created in this JVM process to skip expensive re-creation. */
private val challengesCreated = mutableSetOf<String>()

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
    // Reset the JVM-side cache too — challengesCreated tracks
    // whether a given challenge title was created in this run, so
    // ensureChallengeExists can short-circuit. The server reset
    // wipes every challenge, so the cache lies after this point
    // unless we clear it.
    challengesCreated.clear()
    val url = java.net.URL("http://127.0.0.1:8080/api/test/reset")
    val conn = url.openConnection() as java.net.HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 3_000
        // Reset is sub-2ms after the first call (DB on tmpfs, seed
        // bcrypt hashes cached). First call pays a one-time ~500ms
        // bcrypt warmup. 5s is comfortable margin for both, and tight
        // enough to flag a genuinely hung endpoint quickly.
        conn.readTimeout = 5_000
        conn.doOutput = true
        conn.outputStream.use { /* empty body */ }
        // Trigger the actual request and get the code BEFORE reading a
        // stream — getInputStream() throws on 4xx/5xx, so we need to
        // pick the right stream based on the code.
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
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

// ── Wait helpers ──

/**
 * Wait for the libretro core to fully shut down after exiting a game.
 *
 * stopGame() launches an async coroutine that serializes save state, calls
 * libretroController.stop() (which joins the emulation thread for up to 2s
 * and deinits the native core), then updates ViewModel state (isRunning = false).
 * The UI navigates away immediately (requestExit = true) before the coroutine finishes.
 *
 * SpelaApp exposes a hidden semantics node with contentDescription "Core idle"
 * (when isRunning = false) or "Core running" (when isRunning = true).
 * This function polls the Compose tree for "Core idle" instead of sleeping.
 *
 * Without this wait, starting a new game can race with the old emulation thread,
 * causing core_load() to unload the native library while the old thread still
 * calls nativeRun() → SIGSEGV in the SpelaEmulation thread.
 */
/**
 * UiAutomator-based wait/assert helpers.
 *
 * ALL Compose test APIs (including fetchSemanticsNodes()) call waitForIdle()
 * internally via getRoots(), which triggers Espresso.onIdle(). During gameplay,
 * the 60fps emulation loop keeps the Choreographer busy, causing AppNotIdleException.
 *
 * UiAutomator bypasses Espresso entirely — it accesses the accessibility tree
 * directly via AccessibilityService, independent of Espresso's idle mechanism.
 * These helpers use UiAutomator universally (not just during gameplay) for simplicity
 * and reliability.
 */

private fun uiDevice(): UiDevice =
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * Drop-in replacement for ComposeRule.waitUntil() that doesn't trigger Espresso
 * idle synchronization. Uses Thread.sleep polling.
 */
fun ComposeRule.pollUntil(timeoutMillis: Long = 1000L, condition: () -> Boolean) {
    val scaled = timeoutMillis.scaledTimeout()
    val deadline = System.currentTimeMillis() + scaled
    var lastException: Throwable? = null
    while (System.currentTimeMillis() < deadline) {
        try {
            if (condition()) return
        } catch (e: Exception) {
            lastException = e
        }
        Thread.sleep(100)
    }
    throw androidx.compose.ui.test.ComposeTimeoutException(
        buildString {
            append("Condition still not satisfied after ${scaled}ms.\n")
            append(lastObservedSnapshot())
            if (lastException != null) {
                append("\nLast condition exception: ")
                append(lastException::class.simpleName)
                append(": ")
                append(lastException.message)
            }
        },
    )
}

/**
 * Snapshots the semantic tree's currently visible texts, content
 * descriptions and testTags. Attached to every [pollUntil] timeout so
 * the failure message tells you what the screen actually looked like
 * when the wait gave up — instead of you having to chase the
 * screenshot artifact for "what tags were present?"
 *
 * Capped at 30 entries per category to keep the message tractable.
 */
private fun ComposeRule.lastObservedSnapshot(): String = try {
    val nodes = onAllNodes(
        androidx.compose.ui.test.isRoot(),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
        .flatMap { collectAllNodes(it) }
    val texts = mutableSetOf<String>()
    val descs = mutableSetOf<String>()
    val tags = mutableSetOf<String>()
    for (n in nodes) {
        for ((key, value) in n.config) {
            when (key.name) {
                "Text" -> {
                    @Suppress("UNCHECKED_CAST")
                    (value as? List<androidx.compose.ui.text.AnnotatedString>)
                        ?.forEach { texts.add(it.text) }
                }
                "EditableText" -> {
                    (value as? androidx.compose.ui.text.AnnotatedString)
                        ?.let { texts.add(it.text) }
                }
                "ContentDescription" -> {
                    @Suppress("UNCHECKED_CAST")
                    (value as? List<String>)?.forEach { descs.add(it) }
                }
                "TestTag" -> {
                    (value as? String)?.let { tags.add(it) }
                }
            }
        }
    }
    buildString {
        append("Last observed UI:\n")
        append("  texts (${texts.size}): ")
        append(texts.take(30).joinToString(", ").ifEmpty { "<none>" })
        append('\n')
        append("  contentDescriptions (${descs.size}): ")
        append(descs.take(30).joinToString(", ").ifEmpty { "<none>" })
        append('\n')
        append("  testTags (${tags.size}): ")
        append(tags.take(30).joinToString(", ").ifEmpty { "<none>" })
    }
} catch (e: Throwable) {
    "Could not capture last-observed snapshot: ${e.message}"
}

private fun collectAllNodes(
    node: androidx.compose.ui.semantics.SemanticsNode,
): List<androidx.compose.ui.semantics.SemanticsNode> {
    val out = mutableListOf<androidx.compose.ui.semantics.SemanticsNode>()
    out.add(node)
    node.children.forEach { out.addAll(collectAllNodes(it)) }
    return out
}

fun ComposeRule.waitForCoreIdle(timeout: Long = 10_000) {
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + timeout.scaledTimeout()
    while (System.currentTimeMillis() < deadline) {
        // UiAutomator path
        if (device.findObject(UiSelector().descriptionContains("Core idle")).exists()) return
        // Compose fallback for zero-size marker node
        try {
            if (onAllNodesWithContentDescription("Core idle", substring = false)
                    .fetchSemanticsNodes().isNotEmpty()) return
        } catch (_: Exception) {}
        Thread.sleep(100)
    }
    throw androidx.compose.ui.test.ComposeTimeoutException(
        "waitForCoreIdle: 'Core idle' not found within ${timeout}ms"
    )
}

fun ComposeRule.waitForText(text: String, timeout: Long = TIMEOUT_MEDIUM) {
    // With isTestMode=true (animations disabled), Compose APIs are fast (~700ms).
    // Use Compose semantic tree (reliable) with UiAutomator fallback.
    val deadline = System.currentTimeMillis() + timeout.scaledTimeout()
    while (System.currentTimeMillis() < deadline) {
        // Fast: UiAutomator check
        if (uiDevice().findObject(UiSelector().textContains(text)).exists()) return
        // Slow fallback: Compose semantic tree (for elements not yet in accessibility tree)
        try {
            if (onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()) return
        } catch (_: Exception) {}
        Thread.sleep(200)
    }
    throw IllegalStateException("waitForText('$text'): not found within ${timeout}ms")
}

fun ComposeRule.waitForContentDescription(desc: String, timeout: Long = TIMEOUT_MEDIUM) {
    val deadline = System.currentTimeMillis() + timeout.scaledTimeout()
    while (System.currentTimeMillis() < deadline) {
        if (uiDevice().findObject(UiSelector().descriptionContains(desc)).exists()) return
        try {
            if (onAllNodesWithContentDescription(desc, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()) return
        } catch (_: Exception) {}
        Thread.sleep(200)
    }
    throw IllegalStateException("waitForContentDescription('$desc'): not found within ${timeout}ms")
}

/**
 * Wait for the libretro core to have actually started running.
 *
 * Uses three independent signals so the wait survives:
 * - Thor multi-display routing (UiAutomator sees display 0, marker
 *   may be on display 4)
 * - the 60fps render loop that defeats Compose semantic-tree fetches
 *   with AppNotIdleException
 * - any one signal missing in a particular state transition
 *
 * Signals: Compose `Game running` content description, UiAutomator
 * `Core running` description, and Spela's own logcat lines
 * (`Game loaded:`, `libretroController.start() returned`).
 */
fun ComposeRule.waitForGameRunning(timeout: Long = TIMEOUT_LONG) {
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + timeout.scaledTimeout()
    var iter = 0
    while (System.currentTimeMillis() < deadline) {
        iter++
        try {
            if (onAllNodesWithContentDescription("Game running", substring = false)
                    .fetchSemanticsNodes().isNotEmpty()) {
                android.util.Log.d("E2E_GAMEPLAY", "waitForGameRunning iter=$iter: Compose marker hit")
                return
            }
        } catch (_: Exception) { /* AppNotIdle or tree unavailable */ }
        if (device.findObject(UiSelector().descriptionContains("Core running")).exists()) {
            android.util.Log.d("E2E_GAMEPLAY", "waitForGameRunning iter=$iter: UiAutomator marker hit")
            return
        }
        try {
            val raw = device.executeShellCommand("logcat -d -s System.out:I SpelaLibretro:I -t 500")
            val hit = raw.lineSequence().firstOrNull { line ->
                line.contains("Game loaded:") ||
                    line.contains("libretroController.start() returned")
            }
            if (hit != null) {
                android.util.Log.d("E2E_GAMEPLAY", "waitForGameRunning iter=$iter: logcat hit: ${hit.take(160)}")
                return
            }
        } catch (_: Exception) {}
        Thread.sleep(500)
    }
    throw IllegalStateException("waitForGameRunning: no signal within ${timeout}ms")
}

fun ComposeRule.waitForTextNotVisible(text: String, timeout: Long = TIMEOUT_SHORT) {
    val scaled = timeout.scaledTimeout()
    val obj = uiDevice().findObject(UiSelector().textContains(text))
    if (obj.exists()) {
        check(obj.waitUntilGone(scaled)) {
            "waitForTextNotVisible('$text'): still visible after ${scaled}ms"
        }
    }
}

// All assert*Visible helpers fall through to the Compose semantics
// tree when UiAutomator doesn't find the text/description. UiAutomator
// reads the accessibility tree of display 0, which on multi-display
// hardware (AYN Thor) misses anything routed to the secondary display.
// Compose's semantics tree comes directly from the activity window
// regardless of display.

private fun ComposeRule.composeHasText(text: String): Boolean = try {
    onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
} catch (_: Exception) { false }

private fun ComposeRule.composeHasDescription(desc: String): Boolean = try {
    onAllNodesWithContentDescription(desc, substring = true).fetchSemanticsNodes().isNotEmpty()
} catch (_: Exception) { false }

fun ComposeRule.assertTextVisible(text: String) {
    val ok = uiDevice().findObject(UiSelector().textContains(text)).exists() ||
        composeHasText(text)
    check(ok) { "Expected '$text' to be visible, but it was not found" }
}

fun ComposeRule.assertTextNotVisible(text: String) {
    val visible = uiDevice().findObject(UiSelector().textContains(text)).exists() ||
        composeHasText(text)
    check(!visible) { "Expected '$text' to NOT be visible, but it was found" }
}

/** Assert visible by checking text OR content description, via UiAutomator with Compose fallback. */
fun ComposeRule.assertVisible(label: String) {
    val device = uiDevice()
    val ok = device.findObject(UiSelector().textContains(label)).exists() ||
        device.findObject(UiSelector().descriptionContains(label)).exists() ||
        // SpTextField renders its `label` as the Android EditText hint
        // (PR #847 wraps a real EditText in AndroidView). Hints aren't
        // covered by getText() / contentDescription, so UiSelector misses
        // them — fall back to By.hint() (UiAutomator 2.2+, API 26+).
        device.findObjects(By.hint(label)).isNotEmpty() ||
        composeHasText(label) ||
        composeHasDescription(label)
    check(ok) { "Expected '$label' to be visible (text or description), but not found" }
}

/** Assert NOT visible by checking BOTH text and content description, with Compose fallback. */
fun ComposeRule.assertNotVisible(label: String) {
    val device = uiDevice()
    val visible = device.findObject(UiSelector().textContains(label)).exists() ||
        device.findObject(UiSelector().descriptionContains(label)).exists() ||
        composeHasText(label) ||
        composeHasDescription(label)
    check(!visible) { "Expected '$label' to NOT be visible, but it was found" }
}

/**
 * Like [assertNotVisible] but uses exact-match equality instead of substring
 * matching. Use this when the label collides with longer strings elsewhere
 * in the tree — e.g. "Save" matches "Save Slots" on the secondary display
 * companion, which is irrelevant to in-game overlay action assertions.
 */
fun ComposeRule.assertNotVisibleExact(label: String) {
    val device = uiDevice()
    val visible = device.findObject(UiSelector().text(label)).exists() ||
        device.findObject(UiSelector().description(label)).exists() ||
        runCatching {
            onAllNodesWithText(label, substring = false).fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false) ||
        runCatching {
            onAllNodesWithContentDescription(label, substring = false)
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    check(!visible) { "Expected exact '$label' to NOT be visible, but it was found" }
}

/** Check if we're in the Spela app (not the Android launcher or another app).
 *
 * `currentPackageName` only reports the focused window on display 0. On
 * multi-display hardware (the AYN Thor secondary display) the Spela
 * activity can be focused on a non-primary display, which makes the
 * UiAutomator query report the launcher even though Spela is alive
 * and rendering. Since the Compose test rule's semantics tree comes
 * directly from the activity's window — regardless of display — fall
 * back to "any Spela-owned testTag is in the tree" before declaring
 * we're outside the app.
 */
private fun ComposeRule.isInSpelaApp(): Boolean {
    if (uiDevice().currentPackageName == "com.spela.player") return true
    return try {
        // NAV_HOME ships on every screen with a bottom nav (i.e. every
        // logged-in screen). If it's not present, we're likely on the
        // server-connect / login screens, so check for those tags too.
        val anyTag = listOf(
            TestTags.NAV_HOME,
            TestTags.SCREEN_HOME,
            TestTags.SCREEN_LOGIN,
            TestTags.SCREEN_SERVER_CONNECTION,
            TestTags.SCREEN_CONSOLE,
        ).any { tag ->
            try { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            catch (_: Exception) { false }
        }
        anyTag
    } catch (_: Exception) { false }
}

/** Check if we're on the server connection screen. UiAutomator + Compose fallback. */
private fun ComposeRule.isOnServerConnectionScreen(): Boolean {
    if (!isInSpelaApp()) return false
    val device = uiDevice()
    if (device.findObject(UiSelector().textContains("Add Server")).exists() ||
        device.findObject(UiSelector().textContains("Nu spelar vi")).exists()
    ) return true
    return try {
        onAllNodesWithText("Add Server", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
}

/** Check if we're on the login screen. UiAutomator + Compose fallback. */
private fun ComposeRule.isOnLoginScreen(): Boolean {
    if (!isInSpelaApp()) return false
    val device = uiDevice()
    if (device.findObject(UiSelector().textContains("Sign In")).exists() ||
        device.findObject(UiSelector().textContains("Username")).exists()
    ) return true
    return try {
        onAllNodesWithText("Sign In", substring = true)
            .fetchSemanticsNodes().isNotEmpty() ||
        onAllNodesWithText("Username", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
}

/** Check if we're on the Home screen. UiAutomator + Compose fallback.
 *
 * Uses isInSpelaApp() to avoid matching launcher app labels.
 *
 * Deliberately does NOT substring-match "Spela" — UiAutomator's
 * textContains turns out to be case-insensitive, so "Spela" matches
 * the decorative "Nu spelar vi!" quote rendered on ServerConnection
 * and Login screens. The Home screen is identified by:
 *   - the SCREEN_HOME content description on HomeScreen (most reliable)
 *   - Home-only text strings that DO NOT appear on any auth screen
 *
 * Exact `text()` matches against "Spela" are safe (the brand-mark
 * top-bar uses exactly "Spela"), but Android's contentDescription
 * substring-matching semantics make the UiAutomator API unsafe for
 * that case — so we use the Compose semantics tree for the exact
 * match instead.
 */
internal fun ComposeRule.isOnHomeScreen(): Boolean {
    if (!isInSpelaApp()) return false
    val device = uiDevice()
    if (device.findObject(UiSelector().descriptionContains(TestTags.SCREEN_HOME)).exists() ||
        device.findObject(UiSelector().textContains("Your library is empty")).exists() ||
        device.findObject(UiSelector().textContains("Top Rated")).exists() ||
        device.findObject(UiSelector().textContains("Continue Playing")).exists() ||
        device.findObject(UiSelector().textContains("Loading your library")).exists()
    ) return true
    return try {
        // Compose semantics-tree fallback. Prefers the testTag (safe
        // from any text collisions) and falls back to an exact-match
        // (not substring!) on the "Spela" brand-mark. The exact-match
        // side is necessary because the SCREEN_HOME testTag can race
        // with the Compose tree snapshot — the text node appears
        // before the screen container's testTag does.
        //
        // On the AVD the brand mark surfaces as a contentDescription
        // ("Spela"), not as a Text node — observed in the
        // ComposeTimeoutException dump for EstablishSessionTest. So
        // also check description, otherwise we time out 30s on a
        // screen that's clearly Home.
        onAllNodesWithTag(TestTags.SCREEN_HOME, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Spela").fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithContentDescription("Spela").fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
}

/** Wait until label is visible in either text or content description.
 * Tries UiAutomator first (fast, works during gameplay). Falls back to
 * Compose semantic tree for zero-size marker nodes (e.g., "Game running",
 * "Core idle") that UiAutomator can't see in the accessibility tree. */
fun ComposeRule.waitForVisible(label: String, timeout: Long = TIMEOUT_MEDIUM) {
    val device = uiDevice()
    val scaled = timeout.scaledTimeout()
    val deadline = System.currentTimeMillis() + scaled
    while (System.currentTimeMillis() < deadline) {
        // Fast: UiAutomator (no Espresso idle dependency)
        if (device.findObject(UiSelector().textContains(label)).exists() ||
            device.findObject(UiSelector().descriptionContains(label)).exists()
        ) return
        // Slow fallback: Compose semantic tree (for zero-size marker nodes)
        try {
            if (onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()
            ) return
        } catch (_: Exception) {
            // AppNotIdleException during gameplay — UiAutomator path will retry
        }
        Thread.sleep(100)
    }
    throw androidx.compose.ui.test.ComposeTimeoutException(
        "waitForVisible('$label'): not found within $scaled ms"
    )
}

/** Wait until label is NOT visible in either text or content description (UiAutomator). */
fun ComposeRule.waitForNotVisible(label: String, timeout: Long = TIMEOUT_SHORT) {
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + timeout.scaledTimeout()
    while (System.currentTimeMillis() < deadline) {
        val hasText = device.findObject(UiSelector().textContains(label)).exists()
        val hasDesc = device.findObject(UiSelector().descriptionContains(label)).exists()
        if (!hasText && !hasDesc) return
        Thread.sleep(100)
    }
    throw androidx.compose.ui.test.ComposeTimeoutException(
        "waitForNotVisible('$label'): still visible after $timeout ms"
    )
}

/**
 * Click a node by its `Modifier.testTag`. Prefer this over [tapOn] for
 * standardised app controls — test tags are compile-time constants
 * from [TestTags] and survive label renames / localisation.
 *
 * [fallbackLabel] is used if no node with the tag exists (e.g. an
 * older screen variant still in the semantic tree). When both are
 * specified and the tag is found, we take the tag match.
 */
fun ComposeRule.tapOnTag(tag: String, fallbackLabel: String? = null) {
    // Try both unmerged and merged trees — testTag on a child of a
    // mergeDescendants composable (e.g. Material3 Button, which sets
    // mergeDescendants=true on its semantic root) can disappear from
    // the unmerged tree in some Compose versions.
    val tagNodes = try {
        val unmerged = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        if (unmerged.isNotEmpty()) unmerged
        else onAllNodesWithTag(tag, useUnmergedTree = false).fetchSemanticsNodes()
    } catch (_: Exception) {
        // AppNotIdleException etc. — Compose tree is busy. Fall
        // through to the UiAutomator fallback.
        emptyList()
    }
    if (tagNodes.isNotEmpty()) {
        // Prefer the merged-tree node so testTag and the OnClick action
        // resolve onto the same semantic node — invoking OnClick is
        // more reliable than dispatching a synthetic touch (which can
        // miss on multi-display devices like the AYN Thor and is
        // sensitive to tiny tagged hitboxes).
        try {
            val merged = onAllNodesWithTag(tag, useUnmergedTree = false).fetchSemanticsNodes()
            if (merged.isNotEmpty()) {
                val node = onAllNodesWithTag(tag, useUnmergedTree = false)[0]
                val hasOnClick = node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick)
                if (hasOnClick) {
                    node.performSemanticsAction(SemanticsActions.OnClick)
                } else {
                    node.performClick()
                }
            } else {
                onAllNodesWithTag(tag, useUnmergedTree = true)[0].performClick()
            }
            waitForIdle()
            return
        } catch (_: Exception) {
            // Click attempt threw — fall through to UiAutomator
            // fallback below.
        }
    }
    // UiAutomator fallback by content description (testTag isn't
    // exposed as a resource id, but Compose's semantics block on the
    // same node usually carries a contentDescription that matches
    // the tab label or icon name).
    if (fallbackLabel != null) {
        val device = uiDevice()
        val byDesc = device.findObject(UiSelector().descriptionContains(fallbackLabel))
        if (byDesc.exists()) {
            byDesc.click()
            Thread.sleep(300)
            return
        }
        tapOn(fallbackLabel)
        return
    }
    throw AssertionError(
        "tapOnTag('$tag') found no nodes. The composable that should " +
            "apply this testTag may be off-screen, not yet composed, or " +
            "the tag was renamed without updating the TestTags constant.",
    )
}

/**
 * Click the first node carrying [tag] by invoking its OnClick semantic
 * action directly, falling back to [performClick] only when the node has
 * no OnClick action. Returns false if no node with the tag exists.
 *
 * Why not just `performClick()`? A synthetic touch dispatched by
 * `performClick()` can be routed to the wrong display and silently
 * dropped on multi-display devices (notably the AYN Thor, which exposes
 * a Screen-2 secondary display). Invoking the OnClick semantic action
 * runs the registered `onClick` lambda regardless of pointer routing —
 * the same reason [tapOnTag] prefers the action over a touch. Use this
 * for content cards (console cards, game cards) that are reached by
 * tag, where [tapOnTag]'s label fallback doesn't apply.
 */
fun ComposeRule.clickNodeByTag(tag: String): Boolean {
    // Prefer the merged tree so testTag and the OnClick action resolve
    // onto the same semantic node; fall back to the unmerged tree.
    for (useUnmerged in listOf(false, true)) {
        val present = try {
            onAllNodesWithTag(tag, useUnmergedTree = useUnmerged).fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (!present) continue
        try {
            val interaction = onAllNodesWithTag(tag, useUnmergedTree = useUnmerged)[0]
            val hasOnClick = interaction.fetchSemanticsNode().config.contains(SemanticsActions.OnClick)
            if (hasOnClick) {
                interaction.performSemanticsAction(SemanticsActions.OnClick)
            } else {
                interaction.performClick()
            }
            waitForIdle()
            return true
        } catch (_: Exception) {
            // Tree busy / node detached mid-resolution — try the other tree.
        }
    }
    return false
}

/**
 * Click this node via its OnClick semantic action when it exposes one,
 * else a synthetic touch. Action dispatch is immune to the multi-display
 * touch-routing drop that silently no-ops `performClick()` on the AYN
 * Thor (Screen-2). For label/text taps the resolved merged node is the
 * enclosing clickable (button), which carries the OnClick action. See
 * [clickNodeByTag] for the rationale.
 */
fun SemanticsNodeInteraction.clickPreferAction() {
    val hasOnClick = try {
        fetchSemanticsNode().config.contains(SemanticsActions.OnClick)
    } catch (_: Exception) {
        false
    }
    if (hasOnClick) {
        performSemanticsAction(SemanticsActions.OnClick)
    } else {
        performClick()
    }
}

/**
 * Wait for a node with the given [testTag] to appear in the semantic
 * tree. Preferred over [waitForText] for standardised controls — see
 * [tapOnTag].
 */
fun ComposeRule.waitForTag(tag: String, timeout: Long = TIMEOUT_MEDIUM) {
    pollUntil(timeoutMillis = timeout.scaledTimeout()) {
        onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * Click the Back button in [SpTopBar]. The button is tagged with
 * [TestTags.BACK_BUTTON]; use this helper instead of searching for
 * "Go back" content description so callers don't break if the icon
 * label changes.
 */
fun ComposeRule.pressTopBarBack() {
    tapOnTag(TestTags.BACK_BUTTON, fallbackLabel = "Go back")
}

/**
 * Mapping from a human label to the standardised testTag for the same
 * element. Lets old tests like `tapOn("Settings")` automatically use
 * the new tag-based lookup without code changes — when the rendered
 * label changes ("Settings" → "Preferences", localised, hidden in
 * gamepad mode, etc.) the test keeps working as long as the tag stays
 * applied to the underlying composable.
 *
 * Add an entry whenever you introduce a `TestTags.*` constant for a
 * label that pre-existing tests already pass to [tapOn].
 */
private val labelToTestTag: Map<String, String> = mapOf(
    "Home" to TestTags.NAV_HOME,
    "Explore" to TestTags.NAV_EXPLORE,
    "Consoles" to TestTags.NAV_CONSOLES,
    "Library" to TestTags.NAV_CONSOLES,
    "Collections" to TestTags.NAV_COLLECTIONS,
    "Activity" to TestTags.NAV_ACTIVITY,
    "Challenges" to TestTags.NAV_CHALLENGES,
    "Netplay" to TestTags.NAV_NETPLAY,
    "Settings" to TestTags.NAV_SETTINGS,
    "General" to TestTags.SETTINGS_CATEGORY_GENERAL,
    "Emulation" to TestTags.SETTINGS_CATEGORY_EMULATION,
    "Controls" to TestTags.SETTINGS_CATEGORY_CONTROLS,
    "About" to TestTags.SETTINGS_CATEGORY_ABOUT,
    "Achievements" to TestTags.SETTINGS_CATEGORY_ACHIEVEMENTS,
    "Storage & Sync" to TestTags.SETTINGS_CATEGORY_STORAGE_SYNC,
    "Per-Console" to TestTags.SETTINGS_CATEGORY_CONSOLES,
    "Go back" to TestTags.BACK_BUTTON,
    "Back" to TestTags.BACK_BUTTON,
)

/**
 * Tap a node by text OR content description.
 *
 * **Prefer [tapOnTag] over this for any element you control.** Labels
 * are fragile against copy changes, hidden in gamepad mode, and
 * affected by localisation; testTags are compile-time constants that
 * survive all three. The [labelToTestTag] map below makes legacy
 * `tapOn("Settings")`-style calls automatically prefer the tag, but
 * new code should call [tapOnTag] directly with a [TestTags] constant.
 *
 * During emulation (Core running), Compose test performClick() blocks on Espresso
 * idle which never arrives due to the 60fps render loop. In that case, this
 * function falls back to UiAutomator which bypasses Espresso idle.
 */
fun ComposeRule.tapOn(label: String) {
    // Prefer testTag if this label is a known standardised element.
    labelToTestTag[label]?.let { tag ->
        // OnClick action, not a synthetic touch (AYN Thor multi-display
        // drop). clickNodeByTag returns false if the tag isn't present, in
        // which case we fall through to text/description matching — could be
        // a screen that hasn't adopted the tag yet.
        if (clickNodeByTag(tag)) {
            return
        }
    }
    tapOnByLabel(label)
}

private fun ComposeRule.tapOnByLabel(label: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val emulationRunning = device.findObject(UiSelector().descriptionContains("Core running")).exists()

    if (emulationRunning) {
        // UiAutomator path — bypasses Espresso idle. UiObject.click()
        // can throw UiObjectNotFoundException if a recomposition
        // removed the node between exists() and click(); retry once
        // before failing.
        val byText = device.findObject(UiSelector().textContains(label))
        if (byText.exists() && runCatching { byText.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        val byDesc = device.findObject(UiSelector().descriptionContains(label))
        if (byDesc.exists() && runCatching { byDesc.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        val byTextRetry = device.findObject(UiSelector().textContains(label))
        if (byTextRetry.exists() && runCatching { byTextRetry.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        throw AssertionError("tapOn('$label'): not found by text or description during emulation")
    }

    // Normal Compose test path. Falls back to UiAutomator if Compose throws
    // AppNotIdleException (e.g., Coil image loading keeps Choreographer busy).
    try {
        val textNodes = onAllNodesWithText(label, substring = true).fetchSemanticsNodes()
        if (textNodes.size == 1) {
            onNodeWithText(label, substring = true).clickPreferAction()
        } else {
            val descNodes = onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes()
            if (descNodes.size == 1) {
                onNodeWithContentDescription(label, substring = true).clickPreferAction()
            } else if (descNodes.isNotEmpty()) {
                onAllNodesWithContentDescription(label, substring = true)[0].clickPreferAction()
            } else if (textNodes.isNotEmpty()) {
                onAllNodesWithText(label, substring = true)[0].clickPreferAction()
        } else {
            // Force failure with a clear error
            onNodeWithText(label, substring = true).performClick()
        }
        }
        waitForIdle()
    } catch (_: Exception) {
        // Compose failed (AppNotIdleException from image loading, etc.)
        // Fall back to UiAutomator which bypasses Espresso idle. The
        // UiObject.click() can throw UiObjectNotFoundException if the
        // node disappeared between exists() and click() — common during
        // recompositions — so wrap the click in a runCatching and try
        // the next selector on failure.
        val byText = device.findObject(UiSelector().textContains(label))
        if (byText.exists() && runCatching { byText.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        val byDesc = device.findObject(UiSelector().descriptionContains(label))
        if (byDesc.exists() && runCatching { byDesc.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        // Last resort: re-fetch and retry the text click once — if
        // exists() raced with the first click, the second observation
        // is usually stable.
        val byTextRetry = device.findObject(UiSelector().textContains(label))
        if (byTextRetry.exists() && runCatching { byTextRetry.click() }.isSuccess) {
            Thread.sleep(300)
            return
        }
        throw AssertionError("tapOn('$label'): Compose failed and UiAutomator couldn't find it")
    }
}

/**
 * Tap the LAST element matching text. For dialog confirm buttons where
 * the same text appears in the title and the button (e.g., "Give Up" in both
 * the dialog title and confirm button).
 * Auto-detects emulation and uses UiAutomator when needed.
 */
fun ComposeRule.tapLastWithText(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val emulationRunning = device.findObject(UiSelector().descriptionContains("Core running")).exists()

    if (emulationRunning) {
        // Find the last instance via UiAutomator
        var lastIndex = 0
        while (device.findObject(UiSelector().text(text).instance(lastIndex + 1)).exists()) {
            lastIndex++
        }
        val obj = device.findObject(UiSelector().text(text).instance(lastIndex))
        check(obj.exists()) { "tapLastWithText('$text'): no elements found during emulation" }
        obj.click()
        Thread.sleep(300)
    } else {
        val nodes = onAllNodesWithText(text).fetchSemanticsNodes()
        check(nodes.isNotEmpty()) { "tapLastWithText('$text'): no elements found" }
        onAllNodesWithText(text)[nodes.size - 1].performClick()
        waitForIdle()
    }
}

fun ComposeRule.assertContentDescriptionVisible(desc: String) {
    check(uiDevice().findObject(UiSelector().descriptionContains(desc)).exists()) {
        "Expected description '$desc' to be visible, but it was not found"
    }
}

fun ComposeRule.assertContentDescriptionNotVisible(desc: String) {
    check(!uiDevice().findObject(UiSelector().descriptionContains(desc)).exists()) {
        "Expected description '$desc' to NOT be visible, but it was found"
    }
}

// ── Input helpers ──

fun ComposeRule.pressBack() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    device.pressBack()
    waitForIdle()
}

fun ComposeRule.sendDpad(direction: DpadDirection) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val keyCode = when (direction) {
        DpadDirection.UP -> KeyEvent.KEYCODE_DPAD_UP
        DpadDirection.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        DpadDirection.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        DpadDirection.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        DpadDirection.CENTER -> KeyEvent.KEYCODE_DPAD_CENTER
    }
    instrumentation.sendKeyDownUpSync(keyCode)
    waitForIdle()
}

enum class DpadDirection { UP, DOWN, LEFT, RIGHT, CENTER }

fun ComposeRule.tapAtPercent(xPercent: Float, yPercent: Float) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val x = (device.displayWidth * xPercent / 100f).toInt()
    val y = (device.displayHeight * yPercent / 100f).toInt()
    device.click(x, y)
    waitForIdle()
}

// ── App lifecycle helpers ──

fun ComposeRule.clearAppState() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    // Delete databases
    context.databaseList().forEach { dbName ->
        context.deleteDatabase(dbName)
    }
    // Delete shared preferences
    val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
    if (prefsDir.exists()) {
        prefsDir.listFiles()?.forEach { it.delete() }
    }
    // Delete files in filesDir
    context.filesDir.listFiles()?.forEach { file ->
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }
}

fun ComposeRule.restartApp() {
    // Note: this uses activityRule.scenario.recreate() which sends
    // the activity through onPause→onStop→onCreate again, simulating
    // a configuration change. It does NOT kill the process — that
    // would invalidate the scenario reference and crash the test
    // runner. For persistence tests this is enough: SQLDelight is
    // re-opened, ViewModels are recreated, the dashboard refetches.
    // It does not exercise process-death restoration; that would
    // require force-stop, but force-stop is incompatible with
    // ComposeRule's lifecycle binding.
    //
    // The recreate is sometimes flaky (Compose hierarchy doesn't
    // re-establish on the first attempt); we retry up to three
    // times before giving up and surfacing the failure.
    navigateBackToHome()

    var lastError: Throwable? = null
    repeat(3) { attempt ->
        try {
            activityRule.scenario.recreate()
            Thread.sleep(2_000)
            pollUntil(timeoutMillis = 30_000L) {
                try {
                    isOnHomeScreen() ||
                        isOnServerConnectionScreen() ||
                        isOnLoginScreen()
                } catch (_: Exception) { false }
            }
            // Stabilize the bottom-nav and dashboard. The recreate sequence
            // can leave Compose mid-recomposition; subsequent tapOnTag(NAV_*)
            // calls fire OnClick before the child's
            // `clickable { onTabSelected }` lambda is wired up, and the
            // screen never switches. Wait for both NAV_HOME with an
            // OnClick action AND for one of the home dashboard's first-paint
            // shelves to be present.
            if (isOnHomeScreen()) {
                pollUntil(timeoutMillis = 15_000L) {
                    try {
                        val hasNavHomeClick = onAllNodesWithTag(
                            TestTags.NAV_HOME, useUnmergedTree = false,
                        ).fetchSemanticsNodes().any { node ->
                            node.config.contains(SemanticsActions.OnClick)
                        }
                        val hasNavSettingsClick = onAllNodesWithTag(
                            TestTags.NAV_SETTINGS, useUnmergedTree = false,
                        ).fetchSemanticsNodes().any { node ->
                            node.config.contains(SemanticsActions.OnClick)
                        }
                        hasNavHomeClick && hasNavSettingsClick
                    } catch (_: Throwable) { false }
                }
                // Settle for one more recomposition cycle so the click handlers
                // are wired up to the latest captured callbacks (recreate can
                // leave the first frame's lambdas referencing prior state).
                Thread.sleep(500)
            }
            return // recreate landed us on a recognised screen
        } catch (e: Throwable) {
            lastError = e
            android.util.Log.w(
                "E2E_RESTART",
                "restartApp attempt ${attempt + 1}/3 failed: ${e.message?.take(120)}",
            )
            Thread.sleep(1_000)
        }
    }
    error("restartApp failed after 3 attempts: ${lastError?.message}")
}

/**
 * Press back until we reach the Home screen.
 * Handles overlays, game sessions, Settings sub-screens, etc.
 * Stops early on auth screens (server connection, login) to avoid exiting the Activity.
 */
internal fun ComposeRule.navigateBackToHome() {
    val device = uiDevice()
    for (i in 1..10) {
        try {
            if (isOnHomeScreen()) return

            // Don't press back on auth screens — it would exit the app
            if (isOnServerConnectionScreen() || isOnLoginScreen()) return

            // In-game overlay — exit game (UiAutomator for gameplay)
            if (device.findObject(UiSelector().textContains("Exit Game")).exists()) {
                device.findObject(UiSelector().textContains("Exit Game")).click()
                Thread.sleep(500)
                waitForCoreIdle()
                continue
            }

            // Challenge mode overlay — give up to exit (UiAutomator for gameplay)
            val hasGiveUp = device.findObject(UiSelector().textContains("Give Up")).exists()
            val hasExitGame = device.findObject(UiSelector().textContains("Exit Game")).exists()
            if (hasGiveUp && !hasExitGame) {
                try {
                    device.findObject(UiSelector().textContains("Give Up")).click()
                    Thread.sleep(500)
                    // Confirm the give up dialog if it appeared
                    if (device.findObject(UiSelector().textContains("Give Up Challenge?")).exists()) {
                        // Find and click the last "Give Up" (confirm button)
                        var lastIdx = 0
                        while (device.findObject(UiSelector().text("Give Up").instance(lastIdx + 1)).exists()) {
                            lastIdx++
                        }
                        device.findObject(UiSelector().text("Give Up").instance(lastIdx)).click()
                        Thread.sleep(500)
                    }
                } catch (_: Exception) {
                    // Best effort — screen may have changed between check and click
                }
                waitForCoreIdle()
                continue
            }

            pressBack()
            Thread.sleep(300)
            // Let the screen transition settle before the next check
            Thread.sleep(500)
        } catch (_: Exception) {
            // Compose hierarchy temporarily unavailable (Activity recreation between tests)
            Thread.sleep(500)
        }
    }
}

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
    // Brief retry — isOnHomeScreen() can transiently return false right
    // after ensureLoggedIn finishes a backend-reset → re-login cycle:
    // the activity has reached Home but the SCREEN_HOME contentDescription
    // / "Spela" brand mark race a recomposition pass. ensureLoggedIn's own
    // pollUntil loop is what gets us on Home, so a short retry here just
    // smooths the handoff.
    val deadline = System.currentTimeMillis() + 5_000L.scaledTimeout()
    while (System.currentTimeMillis() < deadline) {
        if (isOnHomeScreen()) return
        Thread.sleep(150)
    }
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

// ── Login flows ──

/**
 * Ensures the app is in a logged-in state on the Home screen.
 * Detects the current screen and takes the appropriate action:
 * - Loading spinner (session restoring) → wait for it to finish
 * - Already on Home → no-op
 * - On server connection screen → adds server and logs in
 * - On login screen → logs in
 * - On any other screen → navigate back to Home
 */
fun ComposeRule.ensureLoggedIn(
    username: String = PLAYER_USERNAME,
    password: String = PLAYER_PASSWORD
) {
    // Surface environmental problems up-front rather than letting them
    // cascade into mysterious timeouts deeper in the test. See
    // StartupDiagnostics for the full list of things this checks.
    StartupDiagnostics.assertClean()

    // Wait for any recognizable screen to load. We accept any logged-in
    // screen here (Home, Console, Game Detail, in-game, Settings) — the
    // navigateBackToHome() call below pulls us back to Home if needed.
    // The previous version only accepted Home/Login/ServerConnect plus a
    // few UiAutomator markers, which left us stuck for 30s when the
    // previous test ended on Console or Game Detail.
    val device = uiDevice()
    pollUntil(timeoutMillis = 30_000L) {
        isOnHomeScreen() ||
            isOnServerConnectionScreen() ||
            isOnLoginScreen() ||
            // "Any logged-in screen" — the bottom-nav testTag is on every
            // screen below the login flow, and fetchSemanticsNodes works
            // regardless of which display the activity sits on.
            try {
                onAllNodesWithTag(TestTags.NAV_HOME, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false } ||
            device.findObject(UiSelector().descriptionContains("Settings")).exists() ||
            device.findObject(UiSelector().descriptionContains("Game running")).exists() ||
            device.findObject(UiSelector().descriptionContains("Go back")).exists()
    }

    // On server connection screen — add server then login
    if (isOnServerConnectionScreen()) {
        addServerAndLogin(username, password)
        return
    }

    // On login screen — just login
    if (isOnLoginScreen()) {
        doLogin(username, password)
        return
    }

    // Check if already on Home screen. Home can show "Spela" (with games) or
    // "Your library is empty" (fresh user with no play history).
    if (isOnHomeScreen()) return

    // Wait briefly for Home screen — the bottom nav renders before the page title,
    // so checking once may miss it. This prevents navigateBackToHome from pressing
    // back and accidentally exiting the app.
    val arrivedHome = try {
        pollUntil(timeoutMillis = TIMEOUT_MEDIUM) {
            try { isOnHomeScreen() } catch (_: Exception) { false }
        }
        true
    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) { false }
    if (arrivedHome) return

    // On some other logged-in screen (Settings, game detail, in-game, etc.)
    // Navigate back to Home first, then verify.
    // navigateBackToHome may need time if exiting a game (async core shutdown).
    navigateBackToHome()
    pollUntil(timeoutMillis = 30_000) {
        try { isOnHomeScreen() } catch (_: Exception) { false }
    }
}

/**
 * Ensures the app is on the Home screen with a logged-in session.
 * Use for tests that need the app in a "normal" logged-in state.
 */
fun ComposeRule.startLoggedIn() {
    ensureLoggedIn()
}

/**
 * Signs out if currently logged in, then performs a fresh login as player.
 * Use for tests that need a clean session (e.g., settings defaults, persistence tests).
 */
fun ComposeRule.loginAsPlayer() {
    ensureLoggedIn()
    signOutIfLoggedIn()
    addServerAndLogin(PLAYER_USERNAME, PLAYER_PASSWORD)
}

/**
 * Signs out if currently logged in, then performs a fresh login as admin.
 */
fun ComposeRule.loginAsAdmin() {
    ensureLoggedIn()
    signOutIfLoggedIn()
    addServerAndLogin(ADMIN_USERNAME, ADMIN_PASSWORD)
}

internal fun ComposeRule.signOutIfLoggedIn() {
    // Check if we're on Home screen (logged in) via UiAutomator
    if (!isOnHomeScreen()) return

    // Navigate to Settings → About category (where Sign Out lives)
    navigateToSettingsCategory("About")
    scrollToAndTapText("Sign Out")

    // Confirm sign out dialog — tap the LAST "Sign Out" button (UiAutomator)
    waitForText("re-enter your credentials", TIMEOUT_SHORT)
    tapLastWithText("Sign Out")

    // Wait for server connection screen
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        isOnServerConnectionScreen()
    }
}

internal fun ComposeRule.addServerAndLogin(username: String, password: String) {
    // Wait for server connection screen (use text fallback since test tag on
    // BoxWithConstraints may not be accessible)
    pollUntil(timeoutMillis = TIMEOUT_LONG) {
        isOnServerConnectionScreen()
    }

    val hasServer = try {
        onAllNodesWithText(SERVER_NAME, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    if (!hasServer) {
        // ServerConnectionScreen auto-opens the Add Server form via a
        // LaunchedEffect when the server list loads empty. Coroutine timing
        // can be flaky (especially on AYN Thor), so wait for either the
        // form input OR the toggle button to appear, then click the toggle
        // ourselves if the form didn't auto-open.
        pollUntil(timeoutMillis = TIMEOUT_LONG) {
            inputOrToggleVisible()
        }
        if (!serverNameInputVisible()) {
            onNodeWithTag(TestTags.SERVER_ADD_TOGGLE_BUTTON).performClick()
            pollUntil(timeoutMillis = TIMEOUT_MEDIUM) { serverNameInputVisible() }
        }

        // SpTextField wraps a real Android EditText via AndroidView (PR #847,
        // landscape keyboard fix), so the input is invisible to Compose UI
        // Test's hasSetTextAction matcher. Drive the EditTexts via UiAutomator.
        val device = uiDevice()
        val nameField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(0),
        )
        check(nameField.waitForExists(TIMEOUT_MEDIUM)) {
            "Server Name EditText never appeared on server-connection screen"
        }
        nameField.setText(SERVER_NAME)
        // NOTE: don't pressBack here. UiAutomator's setText uses an
        // accessibility action, which doesn't open the soft keyboard,
        // so there's no keyboard to hide. pressBack with no keyboard
        // up navigates AWAY from the Spela activity to the launcher.

        // The form is inside a LazyColumn, and on tall narrow viewports
        // the URL row can sit below the fold. Scroll to the
        // SERVER_URL_INPUT testTag first; the outer Compose node carries
        // the tag even though the inner widget is an AndroidView'd
        // EditText.
        runCatching {
            onAllNodesWithTag(TestTags.SERVER_URL_INPUT, useUnmergedTree = true)[0]
                .performScrollTo()
            waitForIdle()
        }

        val urlField = device.findObject(
            UiSelector().className("android.widget.EditText").instance(1),
        )
        check(urlField.waitForExists(TIMEOUT_MEDIUM)) {
            "Server URL EditText never appeared on server-connection screen"
        }
        urlField.setText(SERVER_URL)

        // Tap the Connect button to submit.
        onNodeWithTag(TestTags.SERVER_CONNECT_BUTTON).performClick()

        // Wait for the form to close — the SERVER_NAME_INPUT testTag
        // disappearing is the real signal that validation succeeded
        // and the server was added.
        pollUntil(timeoutMillis = TIMEOUT_LONG) { !serverNameInputVisible() }
    }

    // Tap the server card. SpActionCard sets `contentDescription =
    // server.name`, so a UiAutomator description match consistently
    // lands on a real touch handler.
    val device = uiDevice()
    val serverCard = device.findObject(UiSelector().description(SERVER_NAME))
    if (!serverCard.waitForExists(TIMEOUT_MEDIUM.scaledTimeout())) {
        onNodeWithText(SERVER_NAME).performClick()
    } else {
        serverCard.click()
    }
    Thread.sleep(500)

    // Login
    doLogin(username, password)
}

private fun ComposeRule.serverNameInputVisible(): Boolean = try {
    onAllNodesWithTag(TestTags.SERVER_NAME_INPUT).fetchSemanticsNodes().isNotEmpty()
} catch (_: Exception) { false }

private fun ComposeRule.serverAddToggleVisible(): Boolean = try {
    onAllNodesWithTag(TestTags.SERVER_ADD_TOGGLE_BUTTON).fetchSemanticsNodes().isNotEmpty()
} catch (_: Exception) { false }

private fun ComposeRule.inputOrToggleVisible(): Boolean =
    serverNameInputVisible() || serverAddToggleVisible()

private fun ComposeRule.doLogin(username: String, password: String) {
    val device = uiDevice()

    // Wait for login form (UiAutomator — no Espresso idle dependency)
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        isOnLoginScreen() ||
            device.findObject(UiSelector().textContains("Sign In")).exists()
    }

    // Login fields are SpTextField → PlatformTextFieldCore → AndroidView →
    // real Android EditText (PR #847, landscape keyboard fix). Compose UI
    // Test's hasSetTextAction can't see them, so drive via UiAutomator.
    var t = System.currentTimeMillis()
    val usernameField = device.findObject(
        UiSelector().className("android.widget.EditText").instance(0),
    )
    check(usernameField.waitForExists(TIMEOUT_MEDIUM)) {
        "Username EditText never appeared on login screen"
    }
    usernameField.setText(username)
    android.util.Log.d("E2E_TIMING", "setUsername: ${System.currentTimeMillis()-t}ms")
    // NOTE: don't pressBack — see addServerAndLogin for the rationale.
    // UiAutomator setText uses accessibility actions, no keyboard to
    // dismiss, pressBack would exit the activity.

    t = System.currentTimeMillis()
    val passwordField = device.findObject(
        UiSelector().className("android.widget.EditText").instance(1),
    )
    check(passwordField.waitForExists(TIMEOUT_MEDIUM)) {
        "Password EditText never appeared after typing username"
    }
    passwordField.setText(password)
    android.util.Log.d("E2E_TIMING", "setPassword: ${System.currentTimeMillis()-t}ms")

    // Tap Sign In and wait for home. The server's first /api/auth/login
    // call after `docker compose up` can hit the OkHttp socket timeout
    // (the server's bcrypt verification is slow on cold start under
    // docker-on-macOS), which leaves us back on the login screen with
    // the credentials still filled in. Retry up to 3 times — clicking
    // Sign In again starts a fresh request.
    repeat(3) { attempt ->
        val signInBtn = device.findObject(UiSelector().textContains("Sign In"))
        if (signInBtn.exists()) {
            signInBtn.click()
        } else {
            try { onNodeWithText("Sign In").performClick() } catch (_: Throwable) {}
        }
        try {
            pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) { isOnHomeScreen() }
            return // landed on Home, login succeeded
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            // Still on login (or somewhere else); try once more.
            android.util.Log.w(
                "E2E_LOGIN",
                "doLogin attempt ${attempt + 1}/3 didn't reach Home — retrying",
            )
        }
    }
    // Final assertion — let the framework surface a clear failure.
    pollUntil(timeoutMillis = TIMEOUT_MEDIUM) { isOnHomeScreen() }
}

// ── Navigation helpers ──

fun ComposeRule.navigateToCastlevania() {
    navigateToGameByTitle("Castlevania")
}

/**
 * Navigate to any available NES game. The test doesn't care WHICH game —
 * it just needs a downloadable/playable game.
 * Returns the game title for later assertions.
 */
fun ComposeRule.navigateToAnyNesGame(): String {
    val tag = "E2E_NAV"
    android.util.Log.d(tag, "navigateToAnyNesGame: start")
    // Discover an actual NES game title from the backend instead of
    // guessing from a list of common names — local seed has commercial
    // ROMs (Castlevania etc.), CI seed only has nestest.nes. Either
    // way, we ask the server what game IDs and titles are available
    // so we can drive the UI to a real one.
    val title = firstNesGameTitleViaApi()
        ?: throw IllegalStateException(
            "No NES game found via /api/games?consoleId=nes — check seed data"
        )
    android.util.Log.d(tag, "navigateToAnyNesGame: title='$title'")

    val device = uiDevice()

    // Navigate to Consoles tab
    android.util.Log.d(tag, "navigateToAnyNesGame: tap Consoles")
    tapOn("Consoles")
    android.util.Log.d(tag, "navigateToAnyNesGame: wait NES desc")
    waitForContentDescription("Nintendo Entertainment System", TIMEOUT_EXTRA_LONG)

    // Tap the NES console card via stable testTag (text-pair matcher
    // was broken by the card-layout refresh).
    android.util.Log.d(tag, "navigateToAnyNesGame: scroll-tap NES card")
    val nesCardTag = com.spela.player.presentation.ui.TestTags.consoleCard("nes")
    scrollToAndTapTag(nesCardTag, maxSwipes = 12)

    // Confirm we reached the ConsoleScreen by waiting for the game's own
    // title to render (below). The previous anchor — a "Console settings"
    // contentDescription — was stale: that label now lives in an
    // admin-only overflow DropdownMenu (ConsoleScreen.kt) that the
    // non-admin E2E user never sees and the test never opens, so it could
    // never be found.

    // Tap the FIRST available game card (any game) rather than hunting for
    // a specific API-picked title. The back-stack / play-later callers are
    // game-agnostic — they only need to reach game detail — and the console
    // front page shows curated shelves whose contents vary with the seed,
    // so a fixed title (e.g. the API's first NES game) often isn't visible.
    // Game cards are tagged `explore_game_card_<id>` (front shelves) or
    // `game_grid_item_<id>` (paginated Browse grid). If none are on the
    // front page, hop through Browse Games (lists every game).
    val cardPrefixes = listOf("explore_game_card_", "game_grid_item_")
    fun firstGameCardTag(): String? = try {
        onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { collectAllNodes(it) }
            .mapNotNull { n -> n.config.firstOrNull { it.key.name == "TestTag" }?.value as? String }
            .firstOrNull { t -> cardPrefixes.any { t.startsWith(it) } }
    } catch (_: Exception) {
        null
    }

    android.util.Log.d(tag, "navigateToAnyNesGame: locating a game card (any)")
    var cardTag = firstGameCardTag()
    if (cardTag == null) {
        android.util.Log.d(tag, "navigateToAnyNesGame: no card on front page; opening Browse Games")
        clickNodeByTag(com.spela.player.presentation.ui.TestTags.CONSOLE_BROWSE_ALL_CTA)
        Thread.sleep(1_500)
        try {
            pollUntil(timeoutMillis = TIMEOUT_LONG) { firstGameCardTag() != null }
        } catch (_: Throwable) {
            // fall through to the null-check below for a clear failure
        }
        cardTag = firstGameCardTag()
    }
    checkNotNull(cardTag) {
        "navigateToAnyNesGame: no NES game cards on the console screen " +
            "(front shelves and Browse Games both empty) — did the NES console " +
            "card tap navigate? (looked for one of $cardPrefixes)"
    }

    // OnClick action, not a synthetic touch — performClick() can be dropped
    // by multi-display routing on the AYN Thor. See clickNodeByTag.
    android.util.Log.d(tag, "navigateToAnyNesGame: tapping game card $cardTag")
    clickNodeByTag(cardTag)
    android.util.Log.d(tag, "navigateToAnyNesGame: card click fired, polling action button")
    // Game detail's primary action button is one of:
    //   game_detail_play_button (testTag) — for in-library games (label
    //     "New game" / "Resume" / "Continue" depending on session state)
    //   game_detail_download_button (testTag) — for not-yet-downloaded
    //     games (label "Download" or "Downloading...")
    // Anchor on the testTag set, not the text — labels vary by state
    // and CI's nestest probably shows "Download".
    try {
        pollUntil(timeoutMillis = TIMEOUT_LONG) {
            try {
                onAllNodesWithTag("game_detail_play_button", useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithTag("game_detail_download_button", useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false }
        }
        android.util.Log.d(tag, "navigateToAnyNesGame: action button found, returning")
    } catch (e: Throwable) {
        android.util.Log.e(tag, "navigateToAnyNesGame: action button never appeared")
        try {
            android.util.Log.e(tag, "currentPackage=${device.currentPackageName}")
            val texts = device.findObjects(androidx.test.uiautomator.By.clazz("android.widget.TextView"))
            android.util.Log.e(tag, "TextView count=${texts.size}")
            texts.take(20).forEachIndexed { i, obj ->
                val txt = runCatching { obj.text }.getOrDefault("?")
                android.util.Log.e(tag, "TextView[$i]='$txt'")
            }
        } catch (_: Throwable) {}
        throw e
    }
    return title
}

/**
 * Pull the first NES game's title from `GET /api/games?consoleId=nes`.
 * Used to dynamically discover whichever NES game happens to be in
 * the running backend's seed (Castlevania locally, nestest in CI).
 *
 * `/api/games` requires auth, so log in as the player first to get
 * a bearer token. This runs in the test process; the app's session
 * is irrelevant.
 */
private fun firstNesGameTitleViaApi(): String? {
    val token = quickPlayerLogin() ?: return null
    return try {
        val url = java.net.URL("http://127.0.0.1:8080/api/games?consoleId=nes&pageSize=1")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.connectTimeout = 3_000
        conn.readTimeout = 5_000
        if (conn.responseCode != 200) {
            android.util.Log.w("E2E_SETUP", "firstNesGameTitleViaApi HTTP ${conn.responseCode}")
            return null
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    } catch (e: Exception) {
        android.util.Log.w("E2E_SETUP", "firstNesGameTitleViaApi failed: ${e.message}")
        null
    }
}

private fun quickPlayerLogin(): String? {
    return try {
        val url = java.net.URL("http://127.0.0.1:8080/api/auth/login")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 3_000
        conn.readTimeout = 5_000
        conn.doOutput = true
        conn.outputStream.use {
            it.write("""{"username":"player","password":"player123"}""".toByteArray())
        }
        if (conn.responseCode != 200) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    } catch (e: Exception) {
        android.util.Log.w("E2E_SETUP", "quickPlayerLogin failed: ${e.message}")
        null
    }
}

/**
 * Navigate to a game's detail screen by finding it in the NES console game list.
 * Handles both flat game lists (≤15 games) and shelved layouts (>15 games)
 * where a "Browse" button is needed to access the full list.
 */
fun ComposeRule.navigateToGameByTitle(gameTitle: String) {
    val device = uiDevice()
    val tag = "E2E_NAV"

    // Navigate to Consoles tab. Don't wait on a generic
    // "Nintendo Entertainment System" content description — that
    // string also appears on Home game cards. Also, the bottom-nav
    // tab preserves its stack, so a previous test could have left
    // any deeper screen (per-console, game detail, console settings)
    // on top of the Consoles tab. Press back until the consoles list
    // testTag is visible.
    android.util.Log.d(tag, "Step 1: Tapping Consoles tab")
    tapOn("Consoles")
    val consolesDeadline = System.currentTimeMillis() + TIMEOUT_EXTRA_LONG
    var backPresses = 0
    while (System.currentTimeMillis() < consolesDeadline) {
        val onConsolesList = try {
            onAllNodesWithTag("consoles-list", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
        if (onConsolesList) break
        if (backPresses < 5) {
            android.util.Log.d(tag, "Step 2: not on consoles list yet, pressing back (attempt ${backPresses + 1})")
            pressBack()
            backPresses++
            Thread.sleep(400)
        } else {
            Thread.sleep(250)
        }
    }
    android.util.Log.d(tag, "Step 2: Consoles screen confirmed")

    // Tap the NES console card via its stable testTag — robust
    // against card layout/copy changes (a recent UI tweak broke the
    // old "Nintendo Entertainment System" + "games" pair matcher;
    // the text-pair fallback is dead, so don't bother with it).
    //
    // The Consoles list is a LazyColumn — items below the fold are
    // NOT in the semantics tree until we swipe to render them. Don't
    // poll the tree without scrolling first (a 320x640 GHA AVD will
    // never see the NES card on the initial render). `scrollToAndTapTag`
    // handles the scroll loop itself.
    android.util.Log.d(tag, "Step 3: Tapping NES card (testTag console_card_nes)")
    val nesCardTag = com.spela.player.presentation.ui.TestTags.consoleCard("nes")
    scrollToAndTapTag(nesCardTag, maxSwipes = 12)
    android.util.Log.d(tag, "Step 3: NES card tapped, waiting for console screen")

    // Verify we navigated to the console screen
    waitForContentDescription("screen_console", TIMEOUT_LONG)
    android.util.Log.d(tag, "Step 3: Console screen confirmed")

    // Wait for the page to load and try to find Browse button.
    // Use Compose tree (not UiAutomator) since SpButton text may not appear
    // in the accessibility tree as a standalone text node during test mode.
    android.util.Log.d(tag, "Step 4: Waiting for Browse button")
    Thread.sleep(3_000) // Let API call complete and UI recompose

    // Try multiple strategies to find and click Browse
    var browseClicked = false

    // Strategy 0: testTag — most reliable. The console screen tags the
    // "Browse all N games" CTA with TestTags.CONSOLE_BROWSE_ALL_CTA.
    // (The old `console_browse_games_<id>` tag is stale — the CTA was
    // renamed to console_browse_all_cta in the ConsoleScreen redesign.)
    if (!browseClicked) {
        try {
            val browseTag = TestTags.CONSOLE_BROWSE_ALL_CTA
            val nodes = onAllNodesWithTag(browseTag, useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                android.util.Log.d(tag, "Step 5: Found Browse via testTag '$browseTag'")
                // OnClick action, not a synthetic touch (AYN Thor multi-display drop).
                clickNodeByTag(browseTag)
                browseClicked = true
                Thread.sleep(2_000)
            }
        } catch (e: Exception) {
            android.util.Log.d(tag, "Step 5: ${TestTags.CONSOLE_BROWSE_ALL_CTA} tap failed: ${e.message?.take(80)}")
        }
    }

    // Strategy 1: Compose tree — exact text "Browse" (not "Browser play")
    if (!browseClicked) {
        try {
            val nodes = onAllNodesWithText("Browse", substring = false).fetchSemanticsNodes()
            if (nodes.isNotEmpty()) {
                android.util.Log.d(tag, "Step 5: Found 'Browse' via Compose (${nodes.size} nodes)")
                onNodeWithText("Browse", substring = false).performClick()
                waitForIdle()
                browseClicked = true
                Thread.sleep(2_000)
            }
        } catch (e: Exception) {
            android.util.Log.d(tag, "Step 5: Compose Browse failed: ${e.message?.take(80)}")
        }
    }

    // Strategy 2: UiAutomator exact text
    if (!browseClicked) {
        val browseBtn = device.findObject(UiSelector().text("Browse"))
        if (browseBtn.waitForExists(5_000)) {
            android.util.Log.d(tag, "Step 5: Found 'Browse' via UiAutomator")
            browseBtn.click()
            browseClicked = true
            Thread.sleep(2_000)
        }
    }

    // Strategy 3: UiAutomator "Browse games" (wide layout)
    if (!browseClicked) {
        val browseGamesBtn = device.findObject(UiSelector().text("Browse games"))
        if (browseGamesBtn.waitForExists(5_000)) {
            android.util.Log.d(tag, "Step 5: Found 'Browse games' via UiAutomator")
            browseGamesBtn.click()
            browseClicked = true
            Thread.sleep(2_000)
        }
    }

    if (!browseClicked) {
        android.util.Log.d(tag, "Step 5: No Browse button found — scrolling to find game")
        val centerX = device.displayWidth / 2
        val fromY = (device.displayHeight * 0.7).toInt()
        val toY = (device.displayHeight * 0.3).toInt()
        device.swipe(centerX, fromY, centerX, toY, 15)
        Thread.sleep(1_000)
    }

    // Wait for the ConsoleGames screen to load
    android.util.Log.d(tag, "Step 6: Waiting for games screen")
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        try {
            onAllNodes(hasTestTag("console_games_screen")).fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
    }
    Thread.sleep(2_000) // Let games load and grid render
    android.util.Log.d(tag, "Step 6: Games screen loaded")

    // Scroll to the game using Compose's performScrollToNode, then click.
    // Important: click the CARD (which has onClick), not the text child.
    // The card is a clickable node that is an ancestor of the text node.
    android.util.Log.d(tag, "Step 7: Finding and clicking '$gameTitle'")
    try {
        val gameMatcher = hasText(gameTitle, substring = true)

        // Scroll to make the game visible in the LazyVerticalGrid
        val gridMatcher = hasScrollToNodeAction()
        try {
            onNode(gridMatcher).performScrollToNode(gameMatcher)
            waitForIdle()
            Thread.sleep(500)
        } catch (_: Exception) {
            // Game might already be visible
        }

        // Find the clickable node containing this game title.
        // SpGameCard has onClick on the Card wrapper. The text is a child.
        // hasClickAction() matches nodes that have a semantic onClick action.
        val clickableMatcher = hasText(gameTitle, substring = true) and hasClickAction()
        val clickableNodes = onAllNodes(clickableMatcher).fetchSemanticsNodes()
        android.util.Log.d(tag, "Step 7: clickable+text nodes: ${clickableNodes.size}")

        // Also check all text nodes to understand the tree
        val textNodes = onAllNodesWithText(gameTitle, substring = true).fetchSemanticsNodes()
        android.util.Log.d(tag, "Step 7: text-only nodes: ${textNodes.size}")

        // All taps go through the OnClick semantic action (clickPreferAction)
        // rather than a synthetic touch, which the AYN Thor's Screen-2 routing
        // silently drops.
        if (clickableNodes.isNotEmpty()) {
            // Click the first node that has both text AND click action
            onAllNodes(clickableMatcher)[0].clickPreferAction()
            waitForIdle()
            android.util.Log.d(tag, "Step 7: Clicked via clickable+text matcher")
        } else if (textNodes.size == 1) {
            // Only text node exists — clickPreferAction falls back to a touch
            // which MAY propagate up the semantic tree to an onClick ancestor
            onNodeWithText(gameTitle, substring = true).clickPreferAction()
            waitForIdle()
            android.util.Log.d(tag, "Step 7: Clicked text node")
        } else if (textNodes.size > 1) {
            // Multiple matches — try each one
            for (i in textNodes.indices) {
                try {
                    onAllNodesWithText(gameTitle, substring = true)[i].clickPreferAction()
                    waitForIdle()
                    android.util.Log.d(tag, "Step 7: Clicked text node [$i]")
                    break
                } catch (_: Exception) {
                    continue
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.d(tag, "Step 7: Compose failed: ${e.message?.take(100)}")
        // Fallback: UiAutomator
        val gameText = device.findObject(UiSelector().textContains(gameTitle))
        if (gameText.exists()) {
            gameText.click()
            android.util.Log.d(tag, "Step 7: UiAutomator fallback")
            Thread.sleep(1_000)
        }
    }

    // Dump what's visible after clicking the game
    val afterClickText = mutableListOf<String>()
    try {
        for (i in 0..15) {
            val obj = device.findObject(UiSelector().className("android.widget.TextView").instance(i))
            if (obj.exists()) afterClickText.add(obj.text ?: "(null)")
        }
    } catch (_: Exception) {}
    android.util.Log.d(tag, "Step 8: After click visible: $afterClickText")

    // Dump more UI info at this point
    Thread.sleep(3_000)
    val detailText = mutableListOf<String>()
    try {
        for (i in 0..25) {
            val obj = device.findObject(UiSelector().className("android.widget.TextView").instance(i))
            if (obj.exists()) detailText.add(obj.text ?: "(null)")
        }
    } catch (_: Exception) {}
    android.util.Log.d(tag, "Step 8: Full page text: $detailText")

    // Wait for game detail. The most reliable signal is the
    // GAME_DETAIL_PLAY_BUTTON / GAME_DETAIL_DOWNLOAD_BUTTON testTag
    // — text-based searches collide with cover-art alt text and
    // shelf headers ("Continue Playing"), and AYN Thor's secondary-
    // display routing can hide UiAutomator matches even when the
    // activity is showing on a non-primary display. testTags travel
    // with the Compose semantic tree regardless of display.
    android.util.Log.d(tag, "Step 8: Waiting for game detail (Download/Play/Resume)")
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        // Fast path: testTag via Compose tree
        try {
            if (onAllNodes(hasTestTag(com.spela.player.presentation.ui.TestTags.GAME_DETAIL_PLAY_BUTTON))
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodes(hasTestTag(com.spela.player.presentation.ui.TestTags.GAME_DETAIL_DOWNLOAD_BUTTON))
                    .fetchSemanticsNodes().isNotEmpty()
            ) return@pollUntil true
        } catch (_: Throwable) { /* AppNotIdle — drop to text fallback */ }
        // Fallback: UiAutomator text match (display 0 only, but
        // sufficient when Compose tree is busy)
        if (device.findObject(UiSelector().textContains("Download")).exists() ||
            device.findObject(UiSelector().textContains("Play")).exists() ||
            device.findObject(UiSelector().textContains("Resume")).exists()
        ) return@pollUntil true
        // Last-resort: Compose text search
        try {
            onAllNodesWithText("Download", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Play", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Resume", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        } catch (_: Throwable) { false }
    }
    android.util.Log.d(tag, "Step 8: Game detail loaded")
}

fun ComposeRule.navigateToN64Game() {
    tapOn("Consoles")
    waitForContentDescription("Nintendo 64", TIMEOUT_MEDIUM)

    scrollToAndTapMatchingBoth("Nintendo 64", "games")
    waitForText("Nintendo 64", TIMEOUT_LONG)

    // Try to find Banjo-Kazooie directly, fall back to Browse
    val directlyVisible = try {
        onAllNodesWithText("Banjo-Kazooie", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    if (directlyVisible) {
        scrollToAndTapText("Banjo-Kazooie")
    } else {
        scrollToAndTapText("Browse")
        waitForText("Banjo-Kazooie", TIMEOUT_LONG)
        scrollToAndTapText("Banjo-Kazooie")
    }

    // Wait for the GameDetail primary CTA. The label depends on local
    // state: "Download" pre-download, "Play" post-download with no save,
    // "Resume" once a save exists. After a previous test already ran the
    // game, "Download" is gone — accept any of the three.
    pollUntil(timeoutMillis = TIMEOUT_LONG) {
        try {
            onAllNodesWithText("Download", substring = false).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Play", substring = false).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Resume", substring = false).fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
    }
}

fun ComposeRule.navigateToN64GameAndPlay() {
    navigateToN64Game()
    downloadGameIfNeeded()
    // N64 core (mupen64plus_next) takes longer to initialize than NES (nestopia).
    // It needs to download the core binary, set up GL/Vulkan context, and load a larger ROM.
    // On emulators this can take 60+ seconds for the first run (core download + init).
    // If saves exist, the button is "Resume"; otherwise "Play"
    val hasResume = onAllNodesWithText("Resume", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
    if (hasResume) {
        onNodeWithText("Resume").performClick()
    } else {
        onNodeWithText("Play").performClick()
    }
    // Wait for the "Game running" semantic marker which is always on the primary display,
    // regardless of touch controls visibility, physical controller, or dual-screen mode.
    waitForVisible("Game running", 120_000)
}

/**
 * Scroll to and tap a node whose text contains BOTH text1 AND text2.
 * Useful for disambiguating when multiple nodes share partial text.
 */
fun ComposeRule.tapNodeMatchingBoth(text1: String, text2: String) {
    val matcher = hasText(text1, substring = true) and hasText(text2, substring = true)
    pollUntil(timeoutMillis = TIMEOUT_LONG) {
        try {
            onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
    onNode(matcher).performScrollTo()
    onNode(matcher).performClick()
    waitForIdle()
}

/**
 * Scroll through a LazyColumn until a node matching both text1 AND text2 is visible,
 * then tap it. Matches on both visible text and contentDescription (some elements
 * like console cards use logos instead of text, with the name in contentDescription).
 */
fun ComposeRule.scrollToAndTapMatchingBoth(text1: String, text2: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val textMatcher = hasText(text1, substring = true) and hasText(text2, substring = true)
    val descMatcher = hasContentDescription(text1, substring = true) and hasContentDescription(text2, substring = true)
    val matcher = textMatcher or descMatcher
    val maxSwipes = 10
    var found = false

    for (attempt in 0..maxSwipes) {
        try {
            if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) {
                found = true
                break
            }
        } catch (_: Exception) {
            // Compose hierarchy not available or AppNotIdleException during image loading
        }

        if (attempt == 0) {
            try {
                pollUntil(timeoutMillis = 5_000) {
                    try {
                        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
                    } catch (_: Exception) {
                        false
                    }
                }
                found = true
                break
            } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                // Not found yet — start swiping
            }
        }

        if (attempt < maxSwipes) {
            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.7).toInt()
            val toY = (device.displayHeight * 0.3).toInt()
            device.swipe(centerX, fromY, centerX, toY, 15)
            waitForIdle()
        }
    }

    check(found) { "Could not find node matching both '$text1' and '$text2' after scrolling $maxSwipes times" }

    onNode(matcher).performScrollTo()
    onNode(matcher).performClick()
    waitForIdle()
}

fun ComposeRule.scrollToAndTapText(text: String) {
    // LazyColumn only composes items near the viewport, so off-screen items
    // won't appear in the semantic tree. Swipe down to find the text if needed.
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val maxSwipes = 10
    var found = false

    for (attempt in 0..maxSwipes) {
        try {
            if (onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                found = true
                break
            }
        } catch (_: Exception) {
            // Compose hierarchy not available or AppNotIdleException
        }

        if (attempt == 0) {
            // First attempt: wait briefly for initial load before swiping
            try {
                pollUntil(timeoutMillis = 5_000) {
                    try {
                        onAllNodesWithText(text, substring = true)
                            .fetchSemanticsNodes().isNotEmpty()
                    } catch (_: Exception) {
                        false
                    }
                }
                found = true
                break
            } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
                // Not found yet — start swiping
            }
        }

        if (attempt < maxSwipes) {
            // Swipe up to scroll down (from 70% to 30% of screen height)
            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.7).toInt()
            val toY = (device.displayHeight * 0.3).toInt()
            device.swipe(centerX, fromY, centerX, toY, 15)
            waitForIdle()
        }
    }

    check(found) { "Could not find text '$text' after scrolling $maxSwipes times" }

    val nodes = onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
    if (nodes.size == 1) {
        onNodeWithText(text, substring = true).performScrollTo()
        onNodeWithText(text, substring = true).performClick()
    } else {
        // Multiple matches — use the last one (usually the main item, not a "Continue Playing" card)
        val lastIndex = nodes.size - 1
        onAllNodesWithText(text, substring = true)[lastIndex].performScrollTo()
        onAllNodesWithText(text, substring = true)[lastIndex].performClick()
    }
    waitForIdle()
}

/**
 * Scroll a parent container until a node with the given testTag is in the
 * Compose semantics tree, then click it via Compose. Use this to drive
 * lazy-list rows or sections by their stable tag rather than fragile
 * text labels.
 *
 * Click is dispatched via [SemanticsActions.OnClick] when the merged-tree
 * node exposes that action, bypassing touch routing. On multi-display
 * devices like the AYN Thor, [performClick] (which dispatches a touch
 * event) sometimes lands on the wrong display, so the action-based path
 * is more reliable. Falls back to a real touch click if the action is
 * not present (e.g., nodes that handle pointer input without exposing
 * OnClick semantics).
 */
fun ComposeRule.scrollToAndTapTag(tag: String, maxSwipes: Int = 10) {
    scrollToTag(tag, maxSwipes)
    val mergedNodes = onAllNodesWithTag(tag, useUnmergedTree = false)
    val mergedSize = mergedNodes.fetchSemanticsNodes().size
    if (mergedSize > 0) {
        val node = mergedNodes[0]
        // performScrollTo throws when the parent isn't scrollable
        // (e.g., dialog buttons inside a fixed-height Column). After
        // scrollToTag succeeded, the node is already in the semantic
        // tree, so a missing scroll parent just means we don't need to
        // scroll.
        try { node.performScrollTo() } catch (_: AssertionError) { }
        val hasOnClick = node.fetchSemanticsNode().config.contains(SemanticsActions.OnClick)
        if (hasOnClick) {
            node.performSemanticsAction(SemanticsActions.OnClick)
        } else {
            node.performClick()
        }
    } else {
        val unmerged = onAllNodesWithTag(tag, useUnmergedTree = true)[0]
        try { unmerged.performScrollTo() } catch (_: AssertionError) { }
        unmerged.performClick()
    }
    waitForIdle()
}

/**
 * Scroll a parent container until a node with the given testTag is in the
 * Compose semantics tree. Does not click. Use when the tag identifies a
 * section / heading you only need to verify is reachable, not interact
 * with.
 */
fun ComposeRule.scrollToTag(tag: String, maxSwipes: Int = 10) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    for (attempt in 0..maxSwipes) {
        val present = try {
            onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
        if (present) return
        if (attempt == maxSwipes) break
        val centerX = device.displayWidth / 2
        val fromY = (device.displayHeight * 0.7).toInt()
        val toY = (device.displayHeight * 0.3).toInt()
        device.swipe(centerX, fromY, centerX, toY, 15)
        waitForIdle()
    }
    error("Could not find node with testTag '$tag' after $maxSwipes scrolls")
}

/**
 * Assert that a node with the given testTag is currently in the Compose
 * semantics tree. Tag-equivalent of [assertVisible].
 */
fun ComposeRule.assertTagVisible(tag: String) {
    val present = try {
        onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
    check(present) { "Expected node with testTag '$tag' to be visible, but not found" }
}

/**
 * Assert that a radio-button node tagged with [tag] is in the
 * "Selected" state. SpRadioOption sets `stateDescription = "Selected"
 * | "Not selected"` based on its `isSelected` flag, so this checks
 * that semantic property without depending on visual rendering or
 * label text.
 */
fun ComposeRule.assertRadioSelected(tag: String) {
    val nodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
    check(nodes.isNotEmpty()) { "No node with testTag '$tag' found" }
    val key = androidx.compose.ui.semantics.SemanticsProperties.StateDescription
    val cfg = nodes[0].config
    val state = cfg.find { it.key == key }?.value as? String
    check(state == "Selected") {
        "Expected radio '$tag' to be Selected, was '$state'"
    }
}

/**
 * Assert that a node with the given testTag is NOT in the Compose
 * semantics tree.
 */
fun ComposeRule.assertTagNotVisible(tag: String) {
    val present = try {
        onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
    check(!present) { "Expected node with testTag '$tag' to NOT be visible, but it was" }
}

fun ComposeRule.downloadGameIfNeeded() {
    // Use Compose tree — UiAutomator accessibility tree can be stale after navigation
    val hasDownload = try {
        onAllNodesWithText("Download", substring = false).fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    if (hasDownload) {
        android.util.Log.d("E2E_NAV", "downloadGameIfNeeded: Clicking Download")
        // OnClick action via tag, not a synthetic touch — performClick() can be
        // dropped on multi-display hardware (AYN Thor). See clickNodeByTag.
        if (!clickNodeByTag(com.spela.player.presentation.ui.TestTags.GAME_DETAIL_DOWNLOAD_BUTTON)) {
            onNodeWithText("Download", substring = false).performClick()
        }
        waitForIdle()
        // After download, button becomes "Play", "Resume", or "New Game"
        // ROM download can be slow on emulator — use 60s timeout
        pollUntil(timeoutMillis = 60_000) {
            try {
                onAllNodesWithText("Play", substring = false).fetchSemanticsNodes().isNotEmpty() ||
                    onAllNodesWithText("Resume", substring = false).fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false }
        }
        android.util.Log.d("E2E_NAV", "downloadGameIfNeeded: Download complete")
    }
}

fun ComposeRule.startGameAndWait() {
    // Click the play/resume CTA via its testTag. Label flips between "Play"
    // and "Resume" based on save state, and substring text matches collide
    // with copy like "Last played" / "Play time" elsewhere on the screen.
    val playButtonTag = com.spela.player.presentation.ui.TestTags.GAME_DETAIL_PLAY_BUTTON
    val nodes = onAllNodes(hasTestTag(playButtonTag)).fetchSemanticsNodes()
    android.util.Log.d("E2E_NAV", "startGameAndWait: $playButtonTag count=${nodes.size}")
    if (nodes.isEmpty()) {
        // Surface what IS visible so the failure log is actionable.
        val visibleTags = try {
            val roots = onAllNodes(androidx.compose.ui.test.isRoot(), useUnmergedTree = true)
                .fetchSemanticsNodes()
            val tags = mutableListOf<String>()
            roots.forEach { root ->
                collectAllNodes(root).forEach { n ->
                    for ((k, v) in n.config) if (k.name == "TestTag") (v as? String)?.let { tags.add(it) }
                }
            }
            tags
        } catch (_: Exception) { emptyList() }
        android.util.Log.d("E2E_NAV", "startGameAndWait: testTags on screen: ${visibleTags.take(40)}")
        throw IllegalStateException("Game detail play button (testTag=$playButtonTag) not found")
    }
    // OnClick action, not a synthetic touch — performClick() can be dropped
    // on multi-display hardware (AYN Thor), so the game never starts. See
    // clickNodeByTag.
    if (!clickNodeByTag(playButtonTag)) {
        onAllNodes(hasTestTag(playButtonTag))[0].performClick()
    }
    waitForIdle()

    // Wait for emulation to start using multiple signals
    val device = uiDevice()
    // 20s is plenty: a cached core + ROM starts in well under a
    // second. If this trips, the emulation pipeline is wedged or the
    // detection markers are missing — investigate that instead of
    // bumping the timeout.
    val deadline = System.currentTimeMillis() + 20_000
    var iter = 0
    while (System.currentTimeMillis() < deadline) {
        iter++
        // Signal 1: Compose "Game running" marker
        try {
            val composeCount = onAllNodesWithContentDescription("Game running", substring = false)
                .fetchSemanticsNodes().size
            android.util.Log.d("E2E_GAMEPLAY", "iter=$iter compose 'Game running' nodes=$composeCount")
            if (composeCount > 0) {
                android.util.Log.d("E2E_GAMEPLAY", "Game started! Compose 'Game running' marker")
                Thread.sleep(2_000)
                return
            }
        } catch (e: Exception) {
            android.util.Log.d("E2E_GAMEPLAY", "iter=$iter compose check error: ${e.message?.take(80)}")
        }

        // Signal 2: UiAutomator "Core running" marker
        val uiAutomatorRunning = device.findObject(UiSelector().descriptionContains("Core running")).exists()
        android.util.Log.d("E2E_GAMEPLAY", "iter=$iter uiautomator 'Core running' exists=$uiAutomatorRunning")
        if (uiAutomatorRunning) {
            android.util.Log.d("E2E_GAMEPLAY", "Game started! UiAutomator 'Core running' marker")
            return
        }

        // Signal 3: Logcat core messages — match the actual Spela
        // logs emitted by EmulationViewModel + LibretroController.
        // `executeShellCommand` runs a single binary (no pipes), so
        // we filter by tag at the logcat level and then do the
        // pattern match in Kotlin. The two tags between them carry
        // every "game-started" hint we need.
        try {
            val raw = device.executeShellCommand("logcat -d -s System.out:I SpelaLibretro:I -t 500")
            val hit = raw.lineSequence().firstOrNull { line ->
                line.contains("Game loaded:") ||
                    line.contains("libretroController.start() returned")
            }
            if (hit != null) {
                android.util.Log.d("E2E_GAMEPLAY", "iter=$iter logcat hit: ${hit.take(160)}")
                android.util.Log.d("E2E_GAMEPLAY", "Game started! Logcat marker found")
                Thread.sleep(2_000)
                return
            } else {
                android.util.Log.d("E2E_GAMEPLAY", "iter=$iter logcat: no match yet (raw=${raw.length} bytes)")
            }
        } catch (e: Exception) {
            android.util.Log.d("E2E_GAMEPLAY", "iter=$iter logcat error: ${e.message?.take(80)}")
        }

        Thread.sleep(2_000)
    }
    // Dump some context about what content descriptions WERE visible
    // when we gave up so the next debug session has data to work with.
    val lastDesc = try {
        onAllNodesWithContentDescription("", substring = true).fetchSemanticsNodes()
            .mapNotNull {
                it.config.find { p -> p.key.name == "ContentDescription" }?.value as? List<*>
            }
            .flatten().filterIsInstance<String>().take(20)
    } catch (_: Exception) { emptyList() }
    // Surface the actual emulation error so the failure is actionable
    // without reading screenshots. EmulationViewModel and the native
    // bridge log it ("[Emulation] EXCEPTION during emulation start: ...",
    // "retro_load_game failed"); read it back from logcat — the same
    // mechanism the start-detection above uses.
    val emulationError = try {
        device.executeShellCommand("logcat -d -s System.out:I SpelaLibretro:I -t 800")
            .lineSequence()
            .lastOrNull { line ->
                line.contains("EXCEPTION during emulation start") ||
                    line.contains("retro_load_game failed") ||
                    line.contains("Failed to load game")
            }
            ?.replace(Regex("^.*?(System\\.out|SpelaLibretro):\\s*"), "")
            ?.trim()
    } catch (_: Exception) { null }
    android.util.Log.d("E2E_GAMEPLAY", "Game did not start. emulationError='$emulationError' visible=$lastDesc")
    throw IllegalStateException(
        "Game did not start within 20 seconds" +
            (emulationError?.let { " — emulation error (from logcat): \"$it\"" } ?: ""),
    )
}

fun ComposeRule.openOverlay() {
    pressBack()
    // During emulation, Compose/Espresso APIs block on idle (60fps
    // Choreographer). Prefer UiAutomator polling. Fall back to
    // Compose's semantic-tree fetch — UiAutomator sees only the
    // primary display, so on multi-display hardware (AYN Thor) the
    // "Exit Game" Text on display 4 is invisible to UiAutomator
    // even though the overlay rendered correctly. Compose's tree
    // spans all displays.
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + TIMEOUT_MEDIUM
    while (System.currentTimeMillis() < deadline) {
        if (device.findObject(UiSelector().textContains("Exit Game")).exists()) return
        try {
            if (onAllNodesWithText("Exit Game", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()) return
        } catch (_: Exception) { /* AppNotIdleException — ignore, retry */ }
        Thread.sleep(200)
    }
    throw IllegalStateException("openOverlay: 'Exit Game' not found within ${TIMEOUT_MEDIUM}ms")
}

fun ComposeRule.exitGame(coreIdleTimeout: Long = 10_000) {
    // During emulation, performClick() blocks on Espresso idle (60fps loop).
    // Use UiAutomator which bypasses Espresso idle synchronization.
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val exitBtn = device.findObject(UiSelector().text("Exit Game"))
    if (exitBtn.exists()) {
        exitBtn.click()
    } else {
        val exitBtnFuzzy = device.findObject(UiSelector().textContains("Exit Game"))
        check(exitBtnFuzzy.exists()) { "exitGame: 'Exit Game' button not found" }
        exitBtnFuzzy.click()
    }
    // stopGame() runs async: serializes save state then calls libretroController.stop()
    // which joins the emulation thread (up to 2s) and deinits native core.
    // Wait for "Core idle" indicator before navigating to a new game,
    // otherwise the new core_load() races with the old emulation thread → SIGSEGV.
    // Heavier cores (N64, PSX) need longer shutdown time than NES.
    waitForCoreIdle(coreIdleTimeout)
}

// ── Composite helpers for common patterns ──

/**
 * Navigate from wherever we are to a game's detail screen and start playing.
 *
 * When [preferredGameTitle] is given, picks the card whose contentDescription
 * starts with that title. That makes the test deterministic — otherwise
 * we tap the first card in the tree, which depends on seeded game ordering.
 * Balloon Fight is the default because it's a small ROM that boots quickly
 * and is verified to start emulation end-to-end on the nestopia Android core.
 */
fun ComposeRule.navigateToGameAndPlay(preferredGameTitle: String? = "Balloon Fight") {
    val device = uiDevice()
    val tag = "E2E_NAV"

    // Navigate to Consoles → NES → tap a game directly on the console page.
    // The console landing shows "Essentials" / "Recently Added" shelves
    // of game cards tagged `explore_game_card_<id>`. Tapping any card
    // routes to its detail page — no separate "Browse" screen is
    // required on the modern layout.
    android.util.Log.d(tag, "navigateToGameAndPlay: Starting")
    // The Compose Navigation in this app keeps a per-tab back stack, so a
    // tap on Consoles when we're already at NES > game detail just pops
    // one level instead of returning to the consoles list. The reliable
    // way to reach a known root is to bounce off Home first — Home
    // doesn't have nested screens, so a Home tap is always idempotent.
    // After that, a single Consoles tap lands on the consoles list.
    val nesTag = com.spela.player.presentation.ui.TestTags.consoleCard("nes")
    // Wait for the bottom-nav Home tab to actually be in the Compose tree
    // before tapping. Right after ensureLoggedIn the activity may still
    // be hydrating its first composition.
    try {
        pollUntil(timeoutMillis = 10_000L) {
            try { onAllNodes(hasTestTag(TestTags.NAV_HOME)).fetchSemanticsNodes().isNotEmpty() }
            catch (_: Exception) { false }
        }
    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
        throw IllegalStateException("Bottom nav (NAV_HOME) never appeared — activity not in a logged-in state?")
    }
    // Pop back-stack until the system back press is consumed by the
    // launcher (i.e. we left the app) — but don't actually leave: stop
    // when isOnHomeScreen() is true. This guarantees we're at a tab
    // root, not a deep child like NES > game detail. Then tap Consoles
    // exactly once to land on the consoles list.
    repeat(8) {
        if (isOnHomeScreen()) return@repeat
        pressBack()
        Thread.sleep(300)
    }
    if (!isOnHomeScreen()) {
        // Last-resort: nuke via Home tab tap (works when we're inside a
        // sibling tab whose back stack we can't pop into Home).
        onAllNodes(hasTestTag(TestTags.NAV_HOME))[0].performClick()
        waitForIdle()
        Thread.sleep(500)
    }
    android.util.Log.d(tag, "navigateToGameAndPlay: reset to Home (isOnHomeScreen=${isOnHomeScreen()})")
    onAllNodes(hasTestTag(TestTags.NAV_CONSOLES))[0].performClick()
    waitForIdle()
    android.util.Log.d(tag, "navigateToGameAndPlay: tapped Consoles")
    Thread.sleep(500)
    // The Consoles tab restores its last visited screen (e.g. NES detail
    // if a previous test landed there) instead of the consoles list root.
    // Pop back-stack within the Consoles tab until the NES card is on
    // screen (= we're on the consoles list). The probe uses Compose's
    // semantic-tree query, which routes through the test rule's idleness
    // sync — that *can* hang on never-completing background coroutines
    // (e.g. a libretro core left warming after the prior test). Bound the
    // whole phase with a wall-clock deadline so a stuck idleness sync
    // surfaces a useful error in <60s instead of timing out the entire
    // gradle test slot. Also bail out early if back-press has popped us
    // off the app entirely. See issue #835 for the 7-min-hang trace.
    val landStart = System.currentTimeMillis()
    val landDeadlineMs = 60_000L
    var landedOnList = false
    var pops = 0
    while (System.currentTimeMillis() - landStart < landDeadlineMs && pops < 8) {
        val pkg = device.currentPackageName
        if (pkg != null && pkg != "com.spela.player") {
            android.util.Log.d(tag, "navigateToGameAndPlay: popped out of Spela (pkg=$pkg); aborting back-loop")
            break
        }
        try {
            if (onAllNodes(hasTestTag(nesTag)).fetchSemanticsNodes().isNotEmpty()) {
                landedOnList = true
                break
            }
        } catch (_: Exception) {}
        android.util.Log.d(tag, "navigateToGameAndPlay: NES card not visible (pop=$pops, pkg=$pkg); pressing back")
        pressBack()
        pops++
        Thread.sleep(400)
    }
    if (!landedOnList) {
        val elapsed = System.currentTimeMillis() - landStart
        throw IllegalStateException(
            "Could not reach Consoles list — NES card '$nesTag' not visible after $pops back press(es) " +
                "(${elapsed}ms elapsed, current package=${device.currentPackageName})"
        )
    }
    android.util.Log.d(tag, "navigateToGameAndPlay: landed on Consoles list after $pops pop(s)")
    // Invoke the console card's OnClick action directly rather than a
    // synthetic touch — performClick() can be dropped on multi-display
    // hardware (AYN Thor Screen-2), leaving us stranded on the consoles
    // list. See clickNodeByTag.
    if (!clickNodeByTag(nesTag)) {
        android.util.Log.d(tag, "navigateToGameAndPlay: clickNodeByTag($nesTag) found no node to click")
    }
    Thread.sleep(500)

    // Wait for game cards to render. `explore_game_card_<id>` is the
    // shared tag pattern for every shelf on the console landing page —
    // we look up the current tree, find any node whose tag starts with
    // that prefix, and tap the first one.
    // Tag prefixes used across screens that render game-pickable cards.
    // The console landing page uses `explore_game_card_<id>`; the
    // paginated ConsoleGamesScreen grid uses `game_grid_item_<id>`. The
    // caller doesn't care which screen it's on — we accept either.
    val gameCardPrefixes = listOf("explore_game_card_", "game_grid_item_")

    fun findGameCardTags(useUnmerged: Boolean = true): List<String> {
        return try {
            val nodes = onAllNodes(
                androidx.compose.ui.test.isRoot(),
                useUnmergedTree = useUnmerged,
            ).fetchSemanticsNodes()
            val tags = mutableListOf<String>()
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
                for ((key, value) in n.config) {
                    if (key.name == "TestTag") {
                        val tag = value as? String ?: continue
                        if (gameCardPrefixes.any { tag.startsWith(it) }) tags.add(tag)
                    }
                }
                n.children.forEach { walk(it) }
            }
            nodes.forEach { walk(it) }
            tags
        } catch (_: Exception) { emptyList() }
    }

    // Finds the first game card whose merged contentDescription starts with
    // [titlePrefix]. The card's contentDescription is set to
    // "$title, $subtitle(, favorited/in play later)?" by SpGameCard, so a
    // prefix match on the game title is the stable way to pick a specific
    // card without hardcoding backend autoincrement IDs.
    fun findGameCardTagByTitle(titlePrefix: String): String? {
        return try {
            val nodes = onAllNodes(
                androidx.compose.ui.test.isRoot(),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            var hit: String? = null
            val seenTitles = mutableListOf<String>()
            fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
                if (hit != null) return
                val testTag = n.config.firstOrNull { it.key.name == "TestTag" }?.value as? String
                if (testTag != null && gameCardPrefixes.any { testTag.startsWith(it) }) {
                    val cd = n.config.firstOrNull { it.key.name == "ContentDescription" }?.value
                    val cdText = (cd as? List<*>)?.joinToString(", ") { it.toString() } ?: cd?.toString() ?: ""
                    seenTitles.add("$testTag→\"$cdText\"")
                    if (cdText.startsWith(titlePrefix, ignoreCase = true)
                        || cdText.contains(titlePrefix, ignoreCase = true)) {
                        hit = testTag
                    }
                }
                n.children.forEach { walk(it) }
            }
            nodes.forEach { walk(it) }
            if (hit == null) {
                android.util.Log.d("E2E_NAV", "findGameCardTagByTitle($titlePrefix) not found. Scanned ${seenTitles.size} cards: $seenTitles")
            }
            hit
        } catch (_: Exception) { null }
    }

    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        findGameCardTags().isNotEmpty()
    }
    Thread.sleep(1_000) // let layout settle so the first card is clickable

    // Retry the tap up to N times — sometimes the first tap lands while a
    // LazyRow is still laying out and gets swallowed. We assert navigation
    // by waiting for a `game_detail_*` testTag to appear, not by polling
    // loose substring text on the previous screen.
    // If the caller asked for a specific game and it's not on the front
    // shelves of the console screen (Essentials/Launch/Recently Added/etc.),
    // hop through Browse Games — that page lists every game for the console
    // and is paginated, so titles outside the curated shelves are
    // reachable. We only do this when the title isn't already visible to
    // avoid extra navigation when the front page already has it.
    if (preferredGameTitle != null && findGameCardTagByTitle(preferredGameTitle) == null) {
        android.util.Log.d(tag, "navigateToGameAndPlay: $preferredGameTitle not on console front page; opening Browse Games")
        // OnClick action, not a synthetic touch — see clickNodeByTag.
        if (clickNodeByTag(com.spela.player.presentation.ui.TestTags.consoleBrowseGames("nes"))) {
            Thread.sleep(2_000)
        } else {
            android.util.Log.d(tag, "navigateToGameAndPlay: Browse Games button not found")
        }
    }

    val gameDetailAppeared = run {
        var appeared = false
        repeat(3) { attempt ->
            val preferredTag = preferredGameTitle?.let { findGameCardTagByTitle(it) }
            val allCards = findGameCardTags()
            val targetTag = preferredTag ?: allCards.firstOrNull()
            android.util.Log.d(tag, "navigateToGameAndPlay: attempt ${attempt + 1}, preferred=$preferredGameTitle tag=$targetTag allCards=${allCards.size}")
            if (targetTag == null) return@repeat
            // Invoke the OnClick action directly (not a synthetic touch) so
            // the tap isn't dropped by multi-display routing on the AYN Thor.
            if (clickNodeByTag(targetTag)) {
                android.util.Log.d(tag, "navigateToGameAndPlay: tapped $targetTag")
            } else {
                android.util.Log.d(tag, "navigateToGameAndPlay: tap failed for $targetTag")
                return@repeat
            }

            val deadline = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < deadline) {
                val hasPlayButton = try {
                    onAllNodes(hasTestTag(com.spela.player.presentation.ui.TestTags.GAME_DETAIL_PLAY_BUTTON))
                        .fetchSemanticsNodes().isNotEmpty()
                } catch (_: Exception) { false }
                val hasDownloadButton = try {
                    onAllNodes(hasTestTag(com.spela.player.presentation.ui.TestTags.GAME_DETAIL_DOWNLOAD_BUTTON))
                        .fetchSemanticsNodes().isNotEmpty()
                } catch (_: Exception) { false }
                if (hasPlayButton || hasDownloadButton) {
                    appeared = true
                    android.util.Log.d(tag, "navigateToGameAndPlay: game detail appeared (play=$hasPlayButton download=$hasDownloadButton)")
                    return@run true
                }
                Thread.sleep(300)
            }
            android.util.Log.d(tag, "navigateToGameAndPlay: game detail did NOT appear after tap ${attempt + 1}")
        }
        appeared
    }
    if (!gameDetailAppeared) {
        throw IllegalStateException("Could not navigate into game detail from console screen")
    }
    android.util.Log.d(tag, "navigateToGameAndPlay: Game detail loaded!")
    downloadGameIfNeeded()
    startGameAndWait()
}

/**
 * Navigate to Castlevania and start a fresh game (skip auto-load).
 * Taps "New Game" if saves exist (Resume/New Game split), or "Play" if no saves.
 */
fun ComposeRule.navigateToGameAndPlayFresh() {
    navigateToCastlevania()
    downloadGameIfNeeded()
    // If saves exist, the button is "New Game"; otherwise it's "Play"
    val hasNewGame = onAllNodesWithText("New Game", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
    if (hasNewGame) {
        onNodeWithText("New Game").performClick()
    } else {
        onNodeWithText("Play").performClick()
    }
    // "Game running" is a zero-size Compose marker invisible to UiAutomator.
    // "Touch controls" has visible size and is always shown during gameplay.
    waitForVisible("Game running", TIMEOUT_EXTRA_LONG)
}

fun ComposeRule.openOverlayAndExit() {
    openOverlay()
    exitGame()
}

/**
 * Navigate to the Settings screen via the bottom nav.
 * Settings uses a list-detail layout: category list on the left (or full screen on phones),
 * content on the right. Default category is GENERAL.
 * After this call, the category list is visible with "General" selected.
 */
fun ComposeRule.navigateToSettings() {
    // Prefer the stable testTag from TestTags.NAV_SETTINGS — the label
    // "Settings" can be hidden in gamepad mode or renamed, but the tag
    // is a compile-time constant applied by both SpBottomNavBar and
    // SpNavigationRail. Re-tap once if the first tap didn't take —
    // can happen if we're caught mid-recomposition while the home
    // screen is still loading library data and the SwitchTab intent
    // ends up dropped.
    //
    // After a previous test's scenario.recreate(), the merged-tree
    // OnClick action on nav tabs has been observed to fire on a stale
    // closure (the SwitchTab intent dispatches but the screen never
    // actually transitions). Force a synthetic-touch performClick here
    // so we're hitting the live composition's clickable handler.
    tapOnTag(TestTags.NAV_SETTINGS, fallbackLabel = "Settings")
    val device = uiDevice()
    val totalTimeout = TIMEOUT_LONG.scaledTimeout()
    val deadline = System.currentTimeMillis() + totalTimeout
    var lastTapAt = System.currentTimeMillis()
    var triedUiAutomatorTap = false
    var pressBackAttempts = 0
    while (System.currentTimeMillis() < deadline) {
        try {
            if (onAllNodesWithTag(TestTags.SETTINGS_CATEGORY_GENERAL, useUnmergedTree = true)
                    .fetchSemanticsNodes().isNotEmpty()) return
        } catch (_: Exception) {}
        Thread.sleep(500)
        val elapsed = System.currentTimeMillis() - (deadline - TIMEOUT_LONG)
        // After 3s of no Settings screen, re-issue the Compose tap. The
        // bottom-nav tab accepts a no-op re-tap.
        if (elapsed > 3_000 && System.currentTimeMillis() - lastTapAt > 1_500) {
            tapOnTag(TestTags.NAV_SETTINGS, fallbackLabel = "Settings")
            lastTapAt = System.currentTimeMillis()
        }
        // After 5s, try pressing back. The Settings tab preserves its
        // back-stack across tab switches, so if a previous test left
        // a deep sub-screen (ConsoleSettings, list-detail showingDetail
        // pane) at the top of the stack, re-tapping NAV_SETTINGS just
        // restores us to that sub-screen — SETTINGS_CATEGORY_GENERAL
        // is on the category-list root one or two levels back.
        if (elapsed > 5_000 && pressBackAttempts < 3) {
            pressBackAttempts++
            android.util.Log.d("E2E_NAV", "navigateToSettings: pressing back to pop sub-screen ($pressBackAttempts/3)")
            runCatching { pressBack() }
            Thread.sleep(400)
        }
        // After 7s, also try a physical UiAutomator touch on the
        // "Settings" content-description node. Compose's
        // performSemanticsAction can fire on a stale OnClick lambda
        // after a scenario.recreate(); a synthetic touch goes through
        // the input dispatcher and rebinds against the live composition.
        if (elapsed > 7_000 && !triedUiAutomatorTap) {
            triedUiAutomatorTap = true
            runCatching {
                val byDesc = device.findObject(UiSelector().description("Settings"))
                if (byDesc.exists()) byDesc.click()
            }
        }
    }
    error("navigateToSettings: SETTINGS_CATEGORY_GENERAL never appeared within ${totalTimeout}ms")
}

/**
 * Navigate to a specific Settings category by test tag. Prefer passing a
 * [TestTags] constant here (e.g. `TestTags.SETTINGS_CATEGORY_ABOUT`) so
 * label changes don't break callers. The label overload is kept for
 * backwards compatibility.
 */
fun ComposeRule.navigateToSettingsCategoryTag(tag: String) {
    navigateToSettings()
    tapOnTag(tag)
    waitForIdle()
}

fun ComposeRule.navigateToSettingsCategory(category: String) {
    navigateToSettings()
    tapOn(category)
    waitForIdle()
}

/**
 * Toggle a setting by its visible title (e.g. "Performance Overlay",
 * "Auto Save on Exit"). Each [SettingsToggle] composable sets
 * `contentDescription = title`, so a contentDescription match finds the
 * row. The caller must already be on the relevant Settings category
 * (e.g. via [navigateToSettingsCategoryTag]).
 *
 * No-op if the toggle is already in the desired state, so tests can
 * call this idempotently when a previous test may have flipped it.
 */
/** Convenience wrapper: enable the FPS/frame-time HUD before starting a game. */
fun ComposeRule.enablePerformanceOverlay() {
    navigateToSettingsCategoryTag(TestTags.SETTINGS_CATEGORY_EMULATION)
    setSettingsToggle("Performance Overlay", enable = true)
    pressBack()
    Thread.sleep(500)
}

fun ComposeRule.setSettingsToggle(title: String, enable: Boolean) {
    val device = uiDevice()
    // Find the toggle row. SettingsToggle sets contentDescription=title
    // and stateDescription="On"/"Off" via Role.Switch semantics.
    val node = onAllNodes(hasContentDescription(title, substring = true))
        .fetchSemanticsNodes()
        .firstOrNull()
    checkNotNull(node) { "setSettingsToggle: no toggle with title '$title' found" }
    // stateDescription tells us the current state without relying on
    // visual switch state.
    val state = node.config.firstOrNull { it.key.name == "StateDescription" }?.value as? String
    val currentlyEnabled = state == "On"
    if (currentlyEnabled == enable) return
    onAllNodes(hasContentDescription(title, substring = true))[0].performClick()
    waitForIdle()
    Thread.sleep(300)
}

fun ComposeRule.ensureOverlayOpen() {
    val hasOverlay = onAllNodesWithText("Exit Game", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
    if (!hasOverlay) {
        pressBack()
        waitForText("Exit Game", TIMEOUT_MEDIUM)
    }
}

// ── Challenge helpers ──

/**
 * Navigate from the game detail screen to the ChallengeListScreen.
 * Scrolls to the "Challenges" section and taps "View Challenges" button.
 *
 * The tap can silently fail when the LazyColumn is mid-layout after the
 * swipe-to-find phase of scrollToAndTapText. To work around this, we
 * first scroll to make "View Challenges" visible, then wait briefly for
 * layout to settle, and tap using `tapOn` which re-resolves the node.
 *
 * Assumes the test is on a game detail screen.
 */
fun ComposeRule.navigateToChallengeList() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val maxRetries = 5
    val buttonTag = "view_challenges_button"

    for (retry in 0 until maxRetries) {
        Thread.sleep(1_500)
        waitForIdle()

        // Phase 1: Scroll down using UiAutomator until the button's testTag
        // node appears in the Compose semantic tree.
        val maxSwipes = 10
        for (swipe in 0..maxSwipes) {
            val found = try {
                onAllNodes(hasTestTag(buttonTag))
                    .fetchSemanticsNodes().isNotEmpty()
            } catch (_: Exception) { false }
            if (found) break

            check(swipe < maxSwipes) {
                "Failed to navigate to challenge list after $maxRetries retries"
            }

            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.7).toInt()
            val toY = (device.displayHeight * 0.3).toInt()
            device.swipe(centerX, fromY, centerX, toY, 15)
            Thread.sleep(500)
        }

        // Phase 2: Settle, then click via testTag.
        // performScrollTo ensures the node is fully visible within the LazyColumn
        // before dispatching the semantic OnClick action.
        Thread.sleep(1_000)
        waitForIdle()

        try {
            onNodeWithTag(buttonTag).performScrollTo()
            waitForIdle()
            Thread.sleep(300)
            onNodeWithTag(buttonTag).performClick()
            waitForIdle()
        } catch (e: Exception) {
            android.util.Log.w("ChallengeNav", "retry=$retry click failed: $e")
            if (retry < maxRetries - 1) continue
            throw e
        }

        // Phase 3: Verify navigation succeeded.
        // AnimatedContent keeps the outgoing screen in the tree during the transition,
        // so the testTag node may linger for several hundred ms. Use waitUntil to
        // give the transition time to complete.
        try {
            pollUntil(timeoutMillis = 5_000) {
                try {
                    onAllNodes(hasTestTag(buttonTag))
                        .fetchSemanticsNodes().isEmpty()
                } catch (_: Exception) { true }
            }
            return // Navigation succeeded — button is gone
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            // Button still visible after 5s — click genuinely didn't work
        }

        android.util.Log.w("ChallengeNav",
            "retry=$retry click didn't navigate, retrying")

        // Scroll back up for a fresh attempt
        if (retry < maxRetries - 1) {
            val centerX = device.displayWidth / 2
            val fromY = (device.displayHeight * 0.3).toInt()
            val toY = (device.displayHeight * 0.7).toInt()
            repeat(5) {
                device.swipe(centerX, fromY, centerX, toY, 15)
                Thread.sleep(300)
            }
        }
    }

    throw IllegalStateException("Failed to navigate to challenge list after $maxRetries retries — button never navigated")
}

/**
 * Navigate from the home screen to a specific challenge by name.
 * Goes through: Home → NES → Castlevania → View Challenges → ChallengeListScreen → detail.
 */
fun ComposeRule.navigateToChallenge(challengeName: String) {
    navigateToCastlevania()
    navigateToChallengeList()
    waitForText(challengeName, TIMEOUT_LONG)
    tapOn(challengeName)
    waitForText("Attempt Challenge", TIMEOUT_MEDIUM)
}

/**
 * Start a challenge attempt from the challenge detail screen.
 * Assumes the test is on the challenge detail screen with "Attempt Challenge" visible.
 * Waits for the game to load with the challenge save state.
 */
fun ComposeRule.startChallengeAttempt() {
    tapOn("Attempt Challenge")
    // "Game running" is a zero-size Compose marker invisible to UiAutomator.
    // "Touch controls" has visible size and is always shown during gameplay.
    waitForVisible("Game running", TIMEOUT_EXTRA_LONG)
}

/**
 * Open the challenge overlay (same as pressing back during gameplay).
 * Waits for challenge-specific controls: "Give Up" instead of "Exit Game".
 */
fun ComposeRule.openChallengeOverlay() {
    pressBack()
    waitForText("Give Up", TIMEOUT_MEDIUM)
}

/**
 * Resume gameplay from the challenge overlay by tapping "Resume".
 */
fun ComposeRule.resumeChallengeFromOverlay() {
    tapOn("Resume") // tapOn auto-detects emulation → UiAutomator
    waitForTextNotVisible("Give Up")
}

/**
 * Abandon the current challenge attempt via the overlay.
 * Opens overlay if needed, taps "Give Up" → confirmation dialog → confirms.
 * Waits until back on the challenge detail screen (or game detail).
 */
fun ComposeRule.abandonChallenge() {
    // Probe for the challenge overlay first via UiAutomator — Compose's
    // fetchSemanticsNodes() blocks on Espresso idle, which never arrives
    // during the 60fps emulation render loop and throws
    // AppNotIdleException after 3s. The accessibility tree is also a
    // reliable signal for "Give Up" because the overlay uses real Text
    // nodes, not zero-size markers.
    val device = uiDevice()
    val hasOverlay = device.findObject(UiSelector().textContains("Give Up")).exists() ||
        runCatching {
            onAllNodesWithText("Give Up", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    if (!hasOverlay) {
        openChallengeOverlay()
    }
    // Tap the Give Up action button to trigger confirmation dialog
    tapOn("Give Up")
    waitForText("Give Up Challenge?", TIMEOUT_MEDIUM)
    // Tap the dialog's "Give Up" confirm button (last "Give Up" node in the tree).
    // tapLastWithText auto-detects emulation → UiAutomator during gameplay.
    tapLastWithText("Give Up")
    // Wait for async core shutdown (save + thread join + native deinit)
    waitForCoreIdle()
}

/**
 * Complete the current challenge attempt via the overlay.
 * Opens overlay if needed, taps "Complete", waits for result screen.
 */
fun ComposeRule.completeChallenge() {
    // Probe via UiAutomator first — Compose's fetchSemanticsNodes()
    // blocks on Espresso idle which never fires during the 60fps
    // emulation render loop, throwing AppNotIdleException after 3s.
    val device = uiDevice()
    val hasOverlay = device.findObject(UiSelector().textContains("Complete")).exists() ||
        device.findObject(UiSelector().descriptionContains("Complete")).exists() ||
        runCatching {
            onAllNodesWithContentDescription("Complete", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
    if (!hasOverlay) {
        openChallengeOverlay()
    }
    tapOn("Complete")
    waitForText("Challenge Complete", TIMEOUT_LONG)
}

/**
 * Dismiss the challenge completion result screen.
 * Taps "Done" — this exits the game and returns to the previous navigation screen.
 */
fun ComposeRule.dismissChallengeResult() {
    tapOn("Done")
    waitForIdle()
    waitForCoreIdle()
}

/**
 * Create a challenge from the in-game overlay during gameplay.
 * Assumes the game is currently running with the overlay NOT open.
 * Opens overlay, taps "Challenge", fills the form, and submits.
 * Returns with the game still running after the "Challenge created!" toast.
 */
fun ComposeRule.createChallengeFromOverlay(title: String = "E2E Test Challenge") {
    // The in-game challenge create flow uses ChallengeCreationPanel
    // (different from the game-detail CreateChallengeDialog). It
    // pulls save data straight from libretroController.serialize()
    // — the live emulator state — so it does NOT need a server-side
    // save state to exist first. No Save tap needed.
    openOverlay()
    tapOn("Challenge")
    waitForText("Create Challenge", timeout = 5_000)

    // During emulation, Compose test performTextInput/performClick
    // can block on Espresso idle (the 60fps render loop never goes
    // idle), so use UiAutomator. Compose's accessibility tree often
    // needs a moment to publish the EditText after the panel renders
    // — poll for it instead of failing on the first miss.
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val deadline = System.currentTimeMillis() + 5_000L
    var titleField = device.findObject(UiSelector().className("android.widget.EditText"))
    while (!titleField.exists() && System.currentTimeMillis() < deadline) {
        Thread.sleep(200)
        titleField = device.findObject(UiSelector().className("android.widget.EditText"))
    }
    check(titleField.exists()) { "createChallengeFromOverlay: no Title text field found" }
    titleField.clearTextField()
    titleField.setText(title)

    // Submit via Compose's SemanticsActions.OnClick on the clickable
    // Create button. UiAutomator's coordinate click on the "Create"
    // TextView misses the parent button's onClick when the text node
    // sits at the bottom of a small Compose Button — observed flake on
    // Thor where the Create-button TextView's bounds (100x38px) miss
    // the parent's hit-test region. The semantic-action approach fires
    // the onClick lambda directly, bypassing both touch dispatch and
    // Espresso idle (which never goes idle during 60fps emulation).
    // Also retries briefly because the button is disabled until the
    // title field validation propagates after setText.
    pollUntil(timeoutMillis = 5_000L) {
        try {
            onAllNodes(
                androidx.compose.ui.test.hasText("Create", substring = false) and
                    androidx.compose.ui.test.hasClickAction()
            ).fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
    }
    onAllNodes(
        androidx.compose.ui.test.hasText("Create", substring = false) and
            androidx.compose.ui.test.hasClickAction()
    )[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
    waitForText("Challenge created!", timeout = 15_000)

    // Game resumes after the success toast auto-dismisses (~2s delay
    // in InGameOverlay) plus another frame for emulation to restart.
    // Use waitForGameRunning rather than a bare waitForVisible — it has
    // a logcat-based fallback for "libretroController.start() returned",
    // which is more reliable on a slow emulator where the 1dp Compose
    // marker may flicker faster than the test polls.
    waitForGameRunning()
}

/**
 * Full setup: login, navigate to Castlevania, ensure a challenge with the given title
 * exists on the server. If the challenge was already created in this JVM process,
 * skips the expensive game-play + create flow. Otherwise plays the game, creates
 * the challenge, and exits.
 * Returns on the Castlevania game detail screen.
 */
fun ComposeRule.ensureChallengeExists(title: String = "E2E Test Challenge") {
    startLoggedIn()
    if (title in challengesCreated) {
        navigateToCastlevania()
        return
    }
    // Pin the game-and-play target to Castlevania — the create-flow
    // attaches the challenge to whichever game is running, and the
    // helper's contract is "challenge exists for Castlevania" since
    // it returns the user there. Without this pin,
    // navigateToGameAndPlay defaults to Balloon Fight, the challenge
    // gets created on Balloon Fight, and then the test taps Castlevania's
    // 'View Challenges' which shows an unrelated list.
    navigateToGameAndPlay(preferredGameTitle = "Castlevania")
    createChallengeFromOverlay(title)
    openOverlayAndExit()
    // Post-exit, the action button is Play/Resume/Download depending on
    // cache + auto-save state. Any of them means we're back on game
    // detail.
    pollUntil(timeoutMillis = TIMEOUT_LONG) {
        try {
            onAllNodesWithText("Play", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Resume", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Download", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        } catch (_: Exception) { false }
    }
    challengesCreated.add(title)

    // Navigate all the way back to Home and then through the full path.
    // The game detail screen restored from behind the overlay has a stale
    // LazyColumn whose "View Challenges" button doesn't respond to clicks.
    navigateBackToHome()
    pollUntil(timeoutMillis = TIMEOUT_LONG) {
        try { isOnHomeScreen() } catch (_: Exception) { false }
    }
    navigateToCastlevania()
}

/**
 * Clear a text field by its label. The actual editable widget on Android
 * is an Android EditText wrapped in AndroidView (PR #847, landscape
 * keyboard fix), invisible to Compose UI Test. Find by hint via
 * UiAutomator instead — SpTextField wires `hint = placeholder.ifEmpty {
 * label }`, so the hint matches `label` whenever no placeholder was set.
 *
 * Falls back to UiSelector by label as content-text in case the label is
 * rendered as a sibling TextView (some screens place the label outside
 * the field instead of using the EditText hint).
 */
fun ComposeRule.clearTextField(label: String) {
    typeIntoFieldByLabel(label, text = "", clearFirst = true)
}

/**
 * Type [text] into the EditText whose hint matches [label]. If
 * [clearFirst] is true, clears the field first. The label fallback path
 * locates a TextView by label and walks to the first sibling EditText —
 * forms that render the label outside the field still work.
 */
fun ComposeRule.typeIntoFieldByLabel(
    label: String,
    text: String,
    clearFirst: Boolean = false,
    timeoutMs: Long = TIMEOUT_MEDIUM,
) {
    val device = uiDevice()
    val byHint = device.findObject(
        UiSelector().className("android.widget.EditText").textContains(label),
    )
    val field = if (byHint.waitForExists(timeoutMs)) {
        byHint
    } else {
        // Fallback: pick the first EditText on screen. SpTextField hints
        // come from `placeholder.ifEmpty { label }`, so an unset
        // placeholder gives us the label as the hint and `byHint` would
        // hit. If hint matching missed, the screen has a single field
        // and instance(0) is the right one.
        device.findObject(
            UiSelector().className("android.widget.EditText").instance(0),
        )
    }
    check(field.exists()) {
        "EditText with hint or label '$label' never appeared"
    }
    if (clearFirst) {
        field.clearTextField()
    }
    if (text.isNotEmpty()) {
        field.setText(text)
    }
}

