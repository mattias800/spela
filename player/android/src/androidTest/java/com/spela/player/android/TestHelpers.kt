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
import org.junit.rules.TestWatcher
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
class KoinResetRule : TestWatcher() {
    override fun starting(description: Description?) {
        MainActivity.isTestMode = true
        // Ensure cores are cached before each test (app reinstall wipes files)
        preCacheCores()
    }

    private fun preCacheCores() {
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val coresDir = java.io.File(context.filesDir, "cores")
            coresDir.mkdirs()
            val knownCores = listOf("nestopia", "snes9x", "mgba", "gambatte", "genesis_plus_gx",
                "mupen64plus_next", "mednafen_psx_hw", "ppsspp", "desmume")
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
private const val PLAYER_USERNAME = "player"
private const val PLAYER_PASSWORD = "player123"
private const val ADMIN_USERNAME = "admin"
private const val ADMIN_PASSWORD = "admin123"

private const val TIMEOUT_SHORT = 5_000L
private const val TIMEOUT_MEDIUM = 10_000L
private const val TIMEOUT_LONG = 15_000L
private const val TIMEOUT_EXTRA_LONG = 30_000L

/** Tracks challenges created in this JVM process to skip expensive re-creation. */
private val challengesCreated = mutableSetOf<String>()

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
    while (System.currentTimeMillis() < deadline) {
        try {
            if (condition()) return
        } catch (_: Exception) {}
        Thread.sleep(100)
    }
    throw androidx.compose.ui.test.ComposeTimeoutException(
        "Condition still not satisfied after $timeoutMillis ms"
    )
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

/** Check if we're in the Spela app (not the Android launcher or another app). */
private fun isInSpelaApp(): Boolean =
    uiDevice().currentPackageName == "com.spela.player"

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
 * Uses isInSpelaApp() to avoid matching launcher app labels. */
private fun ComposeRule.isOnHomeScreen(): Boolean {
    if (!isInSpelaApp()) return false
    val device = uiDevice()
    if (device.findObject(UiSelector().descriptionContains(TestTags.SCREEN_HOME)).exists() ||
        device.findObject(UiSelector().textContains("Spela")).exists() ||
        device.findObject(UiSelector().textContains("Your library is empty")).exists() ||
        device.findObject(UiSelector().textContains("Top Rated")).exists() ||
        device.findObject(UiSelector().textContains("Continue Playing")).exists() ||
        device.findObject(UiSelector().textContains("Loading your library")).exists()
    ) return true
    return try {
        onAllNodesWithText("Spela", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
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

/** Tap a node by text OR content description. Prefers unique matches.
 *  During emulation (Core running), Compose test performClick() blocks on Espresso
 *  idle which never arrives due to the 60fps render loop. In that case, this
 *  function falls back to UiAutomator which bypasses Espresso idle. */
fun ComposeRule.tapOn(label: String) {
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
private fun ComposeRule.navigateBackToHome() {
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

    // Wait for any recognizable screen to load (UiAutomator — no Espresso idle).
    val device = uiDevice()
    pollUntil(timeoutMillis = 30_000L) {
        isOnHomeScreen() ||
            isOnServerConnectionScreen() ||
            isOnLoginScreen() ||
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

private fun ComposeRule.signOutIfLoggedIn() {
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

private fun ComposeRule.addServerAndLogin(username: String, password: String) {
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
    // Click Play/Resume using Compose tree
    // Find the Play/Resume button — must have BOTH text AND click action
    // to avoid clicking non-interactive text nodes
    val playMatcher = hasText("Play", substring = false) and hasClickAction()
    val resumeMatcher = hasText("Resume", substring = false) and hasClickAction()
    val hasResume = try {
        onAllNodes(resumeMatcher).fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }
    val hasPlay = try {
        onAllNodes(playMatcher).fetchSemanticsNodes().isNotEmpty()
    } catch (_: Exception) { false }

    android.util.Log.d("E2E_NAV", "startGameAndWait: hasResume=$hasResume, hasPlay=$hasPlay")

    if (hasResume) {
        android.util.Log.d("E2E_NAV", "startGameAndWait: Clicking Resume")
        onAllNodes(resumeMatcher)[0].performClick()
    } else if (hasPlay) {
        android.util.Log.d("E2E_NAV", "startGameAndWait: Clicking Play (clickable)")
        onAllNodes(playMatcher)[0].performClick()
    } else {
        android.util.Log.d("E2E_NAV", "startGameAndWait: No clickable Play/Resume, trying text-only")
        onNodeWithText("Play", substring = false).performClick()
    }
    waitForIdle()

    // Wait for emulation to start using multiple signals
    val device = uiDevice()
    val deadline = System.currentTimeMillis() + 120_000
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
    throw IllegalStateException("Game did not start within 120 seconds")
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

fun ComposeRule.navigateToGameAndPlay() {
    val device = uiDevice()
    val tag = "E2E_NAV"

    // Navigate to Consoles → NES → tap a game directly on the console page.
    // The console page has a hero banner. Scroll down to find game cards.
    android.util.Log.d(tag, "navigateToGameAndPlay: Starting")
    tapOn("Consoles")
    waitForContentDescription("Nintendo Entertainment System", TIMEOUT_EXTRA_LONG)
    scrollToAndTapMatchingBoth("Nintendo Entertainment System", "games")
    waitForContentDescription("screen_console", TIMEOUT_LONG)

    // Wait for games to load (Browse button appears after state.games > 15)
    Thread.sleep(3_000)
    try {
        val browseNodes = onAllNodesWithText("Browse", substring = false).fetchSemanticsNodes()
        if (browseNodes.isNotEmpty()) {
            android.util.Log.d(tag, "navigateToGameAndPlay: Clicking Browse")
            onNodeWithText("Browse", substring = false).performClick()
            waitForIdle()
            Thread.sleep(3_000)

            // On ConsoleGamesScreen — find first game and click
            pollUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
                try {
                    onAllNodes(hasTestTag("console_games_screen")).fetchSemanticsNodes().isNotEmpty()
                } catch (_: Exception) { false }
            }
            Thread.sleep(2_000)

            // Find any clickable node with a game title
            // The first game alphabetically is "Balloon Fight"
            val gameMatcher = hasText("Balloon Fight", substring = true) and hasClickAction()
            val gameNodes = onAllNodes(gameMatcher).fetchSemanticsNodes()
            android.util.Log.d(tag, "navigateToGameAndPlay: Balloon Fight clickable nodes: ${gameNodes.size}")
            if (gameNodes.isNotEmpty()) {
                onAllNodes(gameMatcher)[0].performClick()
                waitForIdle()
                Thread.sleep(5_000) // Give time for navigation + API + recomposition
                android.util.Log.d(tag, "navigateToGameAndPlay: Clicked Balloon Fight")
            }
        }
    } catch (e: Exception) {
        android.util.Log.d(tag, "navigateToGameAndPlay: Failed: ${e.message?.take(100)}")
    }

    // Wait for game detail — use Compose tree to check for game detail content
    // UiAutomator may show stale accessibility nodes from previous screens
    android.util.Log.d(tag, "navigateToGameAndPlay: Waiting for game detail")
    pollUntil(timeoutMillis = 60_000) {
        try {
            val hasDownload = onAllNodesWithText("Download", substring = true).fetchSemanticsNodes().isNotEmpty()
            val hasPlay = onAllNodesWithText("Play", substring = true).fetchSemanticsNodes().isNotEmpty()
            val hasResume = onAllNodesWithText("Resume", substring = true).fetchSemanticsNodes().isNotEmpty()
            val hasBalloon = onAllNodesWithText("Balloon Fight", substring = true).fetchSemanticsNodes().isNotEmpty()
            android.util.Log.d(tag, "navigateToGameAndPlay: Poll — Download=$hasDownload Play=$hasPlay Resume=$hasResume BalloonFight=$hasBalloon")
            hasDownload || hasPlay || hasResume
        } catch (_: Exception) { false }
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
    tapOn("Settings")
    Thread.sleep(1_000) // Let the navigation settle
    // Wait for the category list — "General" is always the first category
    waitForText("General", TIMEOUT_LONG)
}

/**
 * Navigate to a specific Settings category. On wide screens, the category content
 * appears on the right. On phones, it replaces the category list.
 */
fun ComposeRule.navigateToSettingsCategory(category: String) {
    navigateToSettings()
    tapOn(category)
    waitForIdle()
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

