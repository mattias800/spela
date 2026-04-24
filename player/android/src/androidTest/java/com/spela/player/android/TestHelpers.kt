package com.spela.player.android

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasScrollToNodeAction
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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
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
private val testConfigured = run {
    // 3s gives Coil AsyncImage loading time to settle (console icons, cover art)
    IdlingPolicies.setMasterPolicyTimeout(3, TimeUnit.SECONDS)
    IdlingPolicies.setIdlingResourceTimeout(3, TimeUnit.SECONDS)
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
                if (FailureDiagnosticsListener.anyTestFailed) {
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
            // Only nestopia is pre-cached by run-e2e.sh + scripts/cache-nestopia.sh;
            // every other core is downloaded on-demand at first use, matching the
            // real user flow. The list used to include 8 more cores aspirationally,
            // but nothing in the current suite tests them and they just dragged
            // Gradle's APK install setup with irrelevant /data/local/tmp/ copies.
            val knownCores = listOf("nestopia")
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

private const val TIMEOUT_SHORT = 5_000L
private const val TIMEOUT_MEDIUM = 10_000L
private const val TIMEOUT_LONG = 15_000L
private const val TIMEOUT_EXTRA_LONG = 30_000L

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
    val url = java.net.URL("http://127.0.0.1:8080/api/test/reset")
    val conn = url.openConnection() as java.net.HttpURLConnection
    try {
        conn.requestMethod = "POST"
        conn.connectTimeout = 5_000
        // The reset handler deletes ~20 tables and does two UPDATEs. On
        // docker-on-macOS (colima VM + overlayfs) this can take 7-10s
        // even with an effectively empty DB. 30s gives us headroom for
        // a busy dev machine without masking a genuinely hung endpoint.
        conn.readTimeout = 30_000
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
    val deadline = System.currentTimeMillis() + timeoutMillis
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
            append("Condition still not satisfied after ${timeoutMillis}ms.\n")
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
    val deadline = System.currentTimeMillis() + timeout
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
    val deadline = System.currentTimeMillis() + timeout
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
    val deadline = System.currentTimeMillis() + timeout
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

fun ComposeRule.waitForTextNotVisible(text: String, timeout: Long = TIMEOUT_SHORT) {
    val obj = uiDevice().findObject(UiSelector().textContains(text))
    if (obj.exists()) {
        check(obj.waitUntilGone(timeout)) {
            "waitForTextNotVisible('$text'): still visible after ${timeout}ms"
        }
    }
}

fun ComposeRule.assertTextVisible(text: String) {
    check(uiDevice().findObject(UiSelector().textContains(text)).exists()) {
        "Expected '$text' to be visible, but it was not found"
    }
}

fun ComposeRule.assertTextNotVisible(text: String) {
    check(!uiDevice().findObject(UiSelector().textContains(text)).exists()) {
        "Expected '$text' to NOT be visible, but it was found"
    }
}

/** Assert visible by checking BOTH text and content description via UiAutomator. */
fun ComposeRule.assertVisible(label: String) {
    val device = uiDevice()
    val hasText = device.findObject(UiSelector().textContains(label)).exists()
    val hasDesc = device.findObject(UiSelector().descriptionContains(label)).exists()
    check(hasText || hasDesc) { "Expected '$label' to be visible (text or description), but not found" }
}

/** Assert NOT visible by checking BOTH text and content description via UiAutomator. */
fun ComposeRule.assertNotVisible(label: String) {
    val device = uiDevice()
    val hasText = device.findObject(UiSelector().textContains(label)).exists()
    val hasDesc = device.findObject(UiSelector().descriptionContains(label)).exists()
    check(!hasText && !hasDesc) { "Expected '$label' to NOT be visible, but it was found" }
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
private fun ComposeRule.isOnHomeScreen(): Boolean {
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
        onAllNodesWithTag(TestTags.SCREEN_HOME, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText("Spela").fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
}

/** Wait until label is visible in either text or content description.
 * Tries UiAutomator first (fast, works during gameplay). Falls back to
 * Compose semantic tree for zero-size marker nodes (e.g., "Game running",
 * "Core idle") that UiAutomator can't see in the accessibility tree. */
fun ComposeRule.waitForVisible(label: String, timeout: Long = TIMEOUT_MEDIUM) {
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + timeout
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
        "waitForVisible('$label'): not found within $timeout ms"
    )
}

/** Wait until label is NOT visible in either text or content description (UiAutomator). */
fun ComposeRule.waitForNotVisible(label: String, timeout: Long = TIMEOUT_SHORT) {
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + timeout
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
    val tagNodes = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
    if (tagNodes.isNotEmpty()) {
        onAllNodesWithTag(tag, useUnmergedTree = true)[0].performClick()
        waitForIdle()
        return
    }
    if (fallbackLabel != null) {
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
 * Wait for a node with the given [testTag] to appear in the semantic
 * tree. Preferred over [waitForText] for standardised controls — see
 * [tapOnTag].
 */
fun ComposeRule.waitForTag(tag: String, timeout: Long = TIMEOUT_MEDIUM) {
    pollUntil(timeoutMillis = timeout) {
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
        val nodes = onAllNodesWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNodes()
        if (nodes.isNotEmpty()) {
            onAllNodesWithTag(tag, useUnmergedTree = true)[0].performClick()
            waitForIdle()
            return
        }
        // Fall through to text/description matching if the tag isn't
        // present — could be a screen that hasn't adopted the tag yet
        // or a node not in the current composition.
    }
    tapOnByLabel(label)
}

private fun ComposeRule.tapOnByLabel(label: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val emulationRunning = device.findObject(UiSelector().descriptionContains("Core running")).exists()

    if (emulationRunning) {
        // UiAutomator path — bypasses Espresso idle
        val byText = device.findObject(UiSelector().textContains(label))
        if (byText.exists()) {
            byText.click()
            Thread.sleep(300)
            return
        }
        val byDesc = device.findObject(UiSelector().descriptionContains(label))
        if (byDesc.exists()) {
            byDesc.click()
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
            onNodeWithText(label, substring = true).performClick()
        } else {
            val descNodes = onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes()
            if (descNodes.size == 1) {
                onNodeWithContentDescription(label, substring = true).performClick()
            } else if (descNodes.isNotEmpty()) {
                onAllNodesWithContentDescription(label, substring = true)[0].performClick()
            } else if (textNodes.isNotEmpty()) {
                onAllNodesWithText(label, substring = true)[0].performClick()
        } else {
            // Force failure with a clear error
            onNodeWithText(label, substring = true).performClick()
        }
        }
        waitForIdle()
    } catch (_: Exception) {
        // Compose failed (AppNotIdleException from image loading, etc.)
        // Fall back to UiAutomator which bypasses Espresso idle.
        val byText = device.findObject(UiSelector().textContains(label))
        if (byText.exists()) {
            byText.click()
            Thread.sleep(300)
            return
        }
        val byDesc = device.findObject(UiSelector().descriptionContains(label))
        if (byDesc.exists()) {
            byDesc.click()
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
    // Simulate a real app restart: recreate the Activity.
    // This mimics a configuration change or process recreation.
    // Navigate back to Home first so overlays/sub-screens are dismissed.
    navigateBackToHome()

    activityRule.scenario.recreate()

    // Give the system time to tear down the old Activity and create the new one.
    Thread.sleep(2_000)

    // Wait for the new Activity's Compose hierarchy to be fully established.
    // Note: Activity recreation via scenario.recreate() is unreliable on emulators.
    // The Compose hierarchy sometimes fails to re-establish, causing this to timeout.
    // When this happens, we attempt to recover by pressing Home and relaunching,
    // rather than leaving the app in a broken state for subsequent tests.
    try {
        pollUntil(timeoutMillis = 30_000L) {
            try {
                isOnHomeScreen() ||
                    isOnServerConnectionScreen() ||
                    isOnLoginScreen()
            } catch (_: Exception) {
                false // Compose hierarchy not yet available after recreate
            }
        }
    } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
        // Recreation failed — attempt recovery by relaunching via intent
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        Thread.sleep(1_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent != null) {
            context.startActivity(intent)
            Thread.sleep(3_000)
        }
        // Final attempt — if this also fails, the test will fail but at least
        // the app is in a recoverable state for subsequent tests.
        pollUntil(timeoutMillis = 30_000L) {
            try {
                isOnHomeScreen() ||
                    isOnServerConnectionScreen() ||
                    isOnLoginScreen()
            } catch (_: Exception) {
                false
            }
        }
    }
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

    // UiAutomator has a multi-second delay before Compose elements appear in the
    // accessibility tree. Use Compose APIs directly for all form interactions.
    val device = uiDevice()
    val hasServer = try {
        onAllNodesWithText(SERVER_NAME, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    if (!hasServer) {
        // The form auto-opens via LaunchedEffect(servers, isLoading) AFTER
        // LoadServers completes. LaunchedEffect is a coroutine — waitForIdle()
        // returns before it fires. Need Thread.sleep to let the coroutine execute,
        // trigger ToggleAddServer, and recompose with the form visible.
        Thread.sleep(5_000)

        onNode(hasText("Server Name") and hasSetTextAction())
            .performTextInput(SERVER_NAME)
        onNode(hasText("Server URL") and hasSetTextAction())
            .performTextInput(SERVER_URL)
        onNode(hasText("Server URL") and hasSetTextAction())
            .performImeAction()
        Thread.sleep(2_000)
    }

    // Tap server card to connect (Compose API — fast with isTestMode)
    waitForText(SERVER_NAME, TIMEOUT_MEDIUM)
    onNodeWithText(SERVER_NAME).performClick()
    Thread.sleep(500)

    // Login
    doLogin(username, password)
}

private fun ComposeRule.doLogin(username: String, password: String) {
    val device = uiDevice()

    // Wait for login form (UiAutomator — no Espresso idle dependency)
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        isOnLoginScreen() ||
            device.findObject(UiSelector().textContains("Sign In")).exists()
    }

    // Enter credentials with timing logs to diagnose idle blocking
    var t = System.currentTimeMillis()
    onNode(hasText("Username") and hasSetTextAction())
        .performTextClearance()
    android.util.Log.d("E2E_TIMING", "clearUsername: ${System.currentTimeMillis()-t}ms")

    t = System.currentTimeMillis()
    onNode(hasText("Username") and hasSetTextAction())
        .performTextInput(username)
    android.util.Log.d("E2E_TIMING", "inputUsername: ${System.currentTimeMillis()-t}ms")

    t = System.currentTimeMillis()
    onNode(hasText("Password") and hasSetTextAction())
        .performTextClearance()
    android.util.Log.d("E2E_TIMING", "clearPassword: ${System.currentTimeMillis()-t}ms")

    t = System.currentTimeMillis()
    onNode(hasText("Password") and hasSetTextAction())
        .performTextInput(password)
    android.util.Log.d("E2E_TIMING", "inputPassword: ${System.currentTimeMillis()-t}ms")

    // Tap Sign In — try UiAutomator first, Compose fallback
    val signInBtn = device.findObject(UiSelector().textContains("Sign In"))
    if (signInBtn.exists()) {
        signInBtn.click()
    } else {
        onNodeWithText("Sign In").performClick()
    }

    // Verify home screen (UiAutomator — no Espresso idle dependency)
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        isOnHomeScreen()
    }
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
    val device = uiDevice()

    // Navigate to Consoles tab
    tapOn("Consoles")
    waitForContentDescription("Nintendo Entertainment System", TIMEOUT_EXTRA_LONG)

    // Tap the NES console card
    scrollToAndTapMatchingBoth("Nintendo Entertainment System", "games")

    // Wait for console game list screen
    waitForContentDescription("Console settings", TIMEOUT_EXTRA_LONG)

    // Find the first game card by looking for "Download" or any game with a cover
    // The Top Rated section shows games. Tap the first one visible.
    Thread.sleep(2_000) // Let game list load
    waitForText("Top Rated", TIMEOUT_LONG)

    // Find any game card by looking for nodes that have both a title and the console name
    // Just tap the first game we find after "Top Rated"
    val device2 = uiDevice()
    // Swipe right in the Top Rated carousel to see games, then tap the first one
    tapOn("Top Rated") // This might tap the section header — OK, it scrolls to it

    // Wait a moment for carousel to render, then tap on any visible game
    Thread.sleep(1_000)

    // Find the first clickable game by trying common NES game names
    val commonGames = listOf("Super Mario Bros.", "Castlevania", "Mega Man", "Zelda", "Metroid",
        "Contra", "Ninja Gaiden", "Double Dragon", "Kirby", "Punch-Out")
    for (name in commonGames) {
        val gameNode = device2.findObject(UiSelector().textContains(name))
        if (gameNode.exists()) {
            gameNode.click()
            Thread.sleep(500)
            // Wait for game detail
            pollUntil(timeoutMillis = TIMEOUT_LONG) {
                device2.findObject(UiSelector().textContains("Download")).exists() ||
                    device2.findObject(UiSelector().textContains("Play")).exists() ||
                    device2.findObject(UiSelector().textContains("Resume")).exists()
            }
            return name
        }
    }
    throw IllegalStateException("No NES game found from common game list")
}

/**
 * Navigate to a game's detail screen by finding it in the NES console game list.
 * Handles both flat game lists (≤15 games) and shelved layouts (>15 games)
 * where a "Browse" button is needed to access the full list.
 */
fun ComposeRule.navigateToGameByTitle(gameTitle: String) {
    val device = uiDevice()
    val tag = "E2E_NAV"

    // Navigate to Consoles tab
    android.util.Log.d(tag, "Step 1: Tapping Consoles tab")
    tapOn("Consoles")
    android.util.Log.d(tag, "Step 2: Waiting for NES content description")
    waitForContentDescription("Nintendo Entertainment System", TIMEOUT_EXTRA_LONG)
    android.util.Log.d(tag, "Step 2: NES found")

    // Tap the NES console card. With isTestMode=true, Compose APIs are fast.
    // Use the compound matcher to find the card with both "NES" and "games".
    android.util.Log.d(tag, "Step 3: Tapping NES card")
    scrollToAndTapMatchingBoth("Nintendo Entertainment System", "games")
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

        if (clickableNodes.isNotEmpty()) {
            // Click the first node that has both text AND click action
            onAllNodes(clickableMatcher)[0].performClick()
            waitForIdle()
            android.util.Log.d(tag, "Step 7: Clicked via clickable+text matcher")
        } else if (textNodes.size == 1) {
            // Only text node exists — try performClick which MAY propagate
            // up the semantic tree to find an onClick ancestor
            onNodeWithText(gameTitle, substring = true).performClick()
            waitForIdle()
            android.util.Log.d(tag, "Step 7: Clicked text node")
        } else if (textNodes.size > 1) {
            // Multiple matches — try each one
            for (i in textNodes.indices) {
                try {
                    onAllNodesWithText(gameTitle, substring = true)[i].performClick()
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

    // Wait for game detail — look for Download/Play/Resume button
    // Use longer timeout — game detail page loads game info from API
    android.util.Log.d(tag, "Step 8: Waiting for game detail (Download/Play/Resume)")
    pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        device.findObject(UiSelector().textContains("Download")).exists() ||
            device.findObject(UiSelector().textContains("Play")).exists() ||
            device.findObject(UiSelector().textContains("Resume")).exists()
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

    waitForText("Download", TIMEOUT_LONG)
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

fun ComposeRule.downloadGameIfNeeded() {
    // Use Compose tree — UiAutomator accessibility tree can be stale after navigation
    val hasDownload = try {
        onAllNodesWithText("Download", substring = false).fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    if (hasDownload) {
        android.util.Log.d("E2E_NAV", "downloadGameIfNeeded: Clicking Download")
        onNodeWithText("Download", substring = false).performClick()
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
    onAllNodes(hasTestTag(playButtonTag))[0].performClick()
    waitForIdle()

    // Wait for emulation to start using multiple signals
    val device = uiDevice()
    // 20s is enough for download + extract + core init on a real device.
    // First-time runs that need to fetch a fresh core from libretro
    // buildbot may take longer; bump only if a passing test starts
    // failing here, NOT to mask a hanging emulation pipeline.
    val deadline = System.currentTimeMillis() + 20_000
    while (System.currentTimeMillis() < deadline) {
        // Signal 1: Compose "Game running" marker
        try {
            if (onAllNodesWithContentDescription("Game running", substring = false)
                    .fetchSemanticsNodes().isNotEmpty()) {
                android.util.Log.d("E2E_GAMEPLAY", "Game started! Compose 'Game running' marker")
                Thread.sleep(2_000)
                return
            }
        } catch (_: Exception) {}

        // Signal 2: UiAutomator "Core running" marker
        if (device.findObject(UiSelector().descriptionContains("Core running")).exists()) {
            android.util.Log.d("E2E_GAMEPLAY", "Game started! UiAutomator 'Core running' marker")
            return
        }

        // Signal 3: Logcat core messages
        try {
            val logOutput = device.executeShellCommand("logcat -d -t 30 | grep -i 'retro_run\\|nativeRun\\|Core running\\|emulation started'")
            if (logOutput.isNotEmpty()) {
                android.util.Log.d("E2E_GAMEPLAY", "Game started! Logcat: ${logOutput.take(100)}")
                Thread.sleep(2_000)
                return
            }
        } catch (_: Exception) {}

        Thread.sleep(2_000)
    }
    throw IllegalStateException("Game did not start within 20 seconds")
}

fun ComposeRule.openOverlay() {
    pressBack()
    // During emulation, Compose/Espresso APIs block on idle (60fps Choreographer).
    // Use UiAutomator-only polling to wait for the overlay to appear.
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + TIMEOUT_MEDIUM
    while (System.currentTimeMillis() < deadline) {
        if (device.findObject(UiSelector().textContains("Exit Game")).exists()) return
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
 * we tap the first card in the tree, which depends on seeded game ordering
 * and can land on a ROM nestopia rejects (Super Mario Bros.nes in the
 * test fixtures fails retro_load_game, while Balloon Fight loads cleanly).
 * Balloon Fight is the known-good default; it's the first NES game in
 * Browse Games (alphabetical) and has been verified to start emulation
 * end-to-end on the nestopia Android core.
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
    // Pop back-stack within the Consoles tab until the NES card testTag
    // is visible on screen (= we're on the consoles list).
    var landedOnList = false
    repeat(8) {
        try {
            if (onAllNodes(hasTestTag(nesTag)).fetchSemanticsNodes().isNotEmpty()) {
                landedOnList = true
                return@repeat
            }
        } catch (_: Exception) {}
        pressBack()
        Thread.sleep(400)
    }
    if (!landedOnList) {
        try {
            pollUntil(timeoutMillis = 5_000L) {
                try { onAllNodes(hasTestTag(nesTag)).fetchSemanticsNodes().isNotEmpty() }
                catch (_: Exception) { false }
            }
        } catch (_: androidx.compose.ui.test.ComposeTimeoutException) {
            throw IllegalStateException("Could not reach Consoles list — NES card testTag not visible after Home→Consoles bounce + 8 back presses")
        }
    }
    onAllNodes(hasTestTag(nesTag))[0].performClick()
    waitForIdle()
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
        try {
            onAllNodes(hasTestTag(com.spela.player.presentation.ui.TestTags.consoleBrowseGames("nes")))[0].performClick()
            waitForIdle()
            Thread.sleep(2_000)
        } catch (e: Exception) {
            android.util.Log.d(tag, "navigateToGameAndPlay: Browse Games tap failed: ${e.message?.take(120)}")
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
            try {
                onAllNodes(hasTestTag(targetTag), useUnmergedTree = true)[0].performClick()
                waitForIdle()
                android.util.Log.d(tag, "navigateToGameAndPlay: tapped $targetTag")
            } catch (e: Exception) {
                android.util.Log.d(tag, "navigateToGameAndPlay: tap failed: ${e.message?.take(120)}")
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
    // SpNavigationRail.
    tapOnTag(TestTags.NAV_SETTINGS, fallbackLabel = "Settings")
    Thread.sleep(1_000)
    // Wait for the category list by its testTag — resilient to label
    // renames and localisation.
    waitForTag(TestTags.SETTINGS_CATEGORY_GENERAL, TIMEOUT_LONG)
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
    val hasOverlay = onAllNodesWithText("Give Up", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
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
    val hasOverlay = onAllNodesWithContentDescription("Complete", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
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
    openOverlay()
    tapOn("Challenge")
    waitForText("Create Challenge", timeout = 5_000)

    // During emulation, Compose test performTextInput/performClick block on idle.
    // Use UiAutomator for text input and button click.
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val titleField = device.findObject(UiSelector().textContains("Title").className("android.widget.EditText"))
    if (!titleField.exists()) {
        // Fallback: find by resource ID or any editable field
        val anyField = device.findObject(UiSelector().className("android.widget.EditText"))
        check(anyField.exists()) { "createChallengeFromOverlay: no text field found" }
        anyField.clearTextField()
        anyField.setText(title)
    } else {
        titleField.clearTextField()
        titleField.setText(title)
    }

    // Tap the "Create" button (not the "Create Challenge" dialog title)
    tapOn("Create")
    waitForText("Challenge created!", timeout = 15_000)

    // Game resumes after toast
    waitForVisible("Game running", timeout = 5_000)
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
    navigateToGameAndPlay()
    createChallengeFromOverlay(title)
    openOverlayAndExit()
    waitForText("Download", TIMEOUT_LONG)
    challengesCreated.add(title)

    // Navigate all the way back to Home and then through the full path.
    // The game detail screen restored from behind the overlay has a stale
    // LazyColumn whose "View Challenges" button doesn't respond to clicks.
    navigateBackToHome()
    waitForText("Spela", TIMEOUT_LONG)
    navigateToCastlevania()
}

/**
 * Clear a text field by its label. Uses performTextClearance on the field
 * matched by label + hasSetTextAction.
 */
fun ComposeRule.clearTextField(label: String) {
    onNode(hasText(label) and hasSetTextAction())
        .performTextClearance()
}

