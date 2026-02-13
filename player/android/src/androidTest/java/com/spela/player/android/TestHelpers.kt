package com.spela.player.android

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.spela.player.di.commonModule
import com.spela.player.di.platformModule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.koin.mp.KoinPlatformTools

typealias ComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

// ── Koin reset rule ──

/**
 * Reloads Koin modules between tests so singleton definitions are replaced.
 * Uses loadModules(allowOverride=true) which replaces the SingleInstanceFactory
 * for each definition — the new factory has no cached value, so the next get()
 * creates a fresh instance.
 * Must be order=0 (outer) so singletons are refreshed BEFORE ComposeRule (order=1)
 * creates the Activity.
 */
class KoinResetRule : TestWatcher() {
    override fun starting(description: Description?) {
        try {
            val koin = KoinPlatformTools.defaultContext().get()
            val modules = listOf(commonModule, platformModule())
            koin.loadModules(modules, allowOverride = true)
        } catch (_: Exception) {
            // First test in process — Koin started by SpelaApplication, nothing to reset
        }
    }
}

// ── Constants ──

private const val SERVER_NAME = "Local"
private const val SERVER_URL = "http://10.0.2.2:8080"
private const val PLAYER_USERNAME = "player"
private const val PLAYER_PASSWORD = "player123"
private const val ADMIN_USERNAME = "admin"
private const val ADMIN_PASSWORD = "admin123"

private const val TIMEOUT_SHORT = 3_000L
private const val TIMEOUT_MEDIUM = 5_000L
private const val TIMEOUT_LONG = 8_000L
private const val TIMEOUT_EXTRA_LONG = 15_000L

// ── Wait helpers ──

fun ComposeRule.waitForText(text: String, timeout: Long = TIMEOUT_MEDIUM) {
    waitUntil(timeoutMillis = timeout) {
        try {
            onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy not yet available
        }
    }
}

fun ComposeRule.waitForContentDescription(desc: String, timeout: Long = TIMEOUT_MEDIUM) {
    waitUntil(timeoutMillis = timeout) {
        try {
            onAllNodesWithContentDescription(desc, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy not yet available
        }
    }
}

fun ComposeRule.waitForTextNotVisible(text: String, timeout: Long = TIMEOUT_SHORT) {
    waitUntil(timeoutMillis = timeout) {
        try {
            onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy not yet available
        }
    }
}

fun ComposeRule.assertTextVisible(text: String) {
    onNodeWithText(text, substring = true).assertIsDisplayed()
}

fun ComposeRule.assertTextNotVisible(text: String) {
    val nodes = onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
    assert(nodes.isEmpty()) { "Expected '$text' to NOT be visible, but it was found" }
}

/** Assert visible by checking BOTH text and content description. */
fun ComposeRule.assertVisible(label: String) {
    val hasText = onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    val hasDesc = onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    assert(hasText || hasDesc) { "Expected '$label' to be visible (text or contentDescription), but it was not found" }
}

/** Assert NOT visible by checking BOTH text and content description. */
fun ComposeRule.assertNotVisible(label: String) {
    val hasText = onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    val hasDesc = onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()
    assert(!hasText && !hasDesc) { "Expected '$label' to NOT be visible, but it was found" }
}

/** Wait until label is visible in either text or content description. */
fun ComposeRule.waitForVisible(label: String, timeout: Long = TIMEOUT_MEDIUM) {
    waitUntil(timeoutMillis = timeout) {
        try {
            onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }
}

/** Wait until label is NOT visible in either text or content description. */
fun ComposeRule.waitForNotVisible(label: String, timeout: Long = TIMEOUT_SHORT) {
    waitUntil(timeoutMillis = timeout) {
        try {
            onAllNodesWithText(label, substring = true).fetchSemanticsNodes().isEmpty() &&
                onAllNodesWithContentDescription(label, substring = true).fetchSemanticsNodes().isEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }
}

/** Tap a node by text OR content description. Prefers unique matches. */
fun ComposeRule.tapOn(label: String) {
    val textNodes = onAllNodesWithText(label, substring = true).fetchSemanticsNodes()
    if (textNodes.size == 1) {
        onNodeWithText(label, substring = true).performClick()
    } else {
        // Multiple text matches or no text matches — try contentDescription
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
}

fun ComposeRule.assertContentDescriptionVisible(desc: String) {
    onNodeWithContentDescription(desc, substring = true).assertIsDisplayed()
}

fun ComposeRule.assertContentDescriptionNotVisible(desc: String) {
    val nodes = onAllNodesWithContentDescription(desc, substring = true).fetchSemanticsNodes()
    assert(nodes.isEmpty()) { "Expected content description '$desc' to NOT be visible, but it was found" }
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
    // Navigate back to Home first so overlays/sub-screens are dismissed.
    navigateBackToHome()

    // Reload Koin modules so singletons (NavigationViewModel, EmulationViewModel)
    // are replaced with fresh definitions. The new Activity will get new instances
    // whose init{} blocks re-run (e.g., restoreSession()).
    try {
        val koin = KoinPlatformTools.defaultContext().get()
        val modules = listOf(commonModule, platformModule())
        koin.loadModules(modules, allowOverride = true)
    } catch (_: Exception) {
        // Koin not yet started
    }

    activityRule.scenario.recreate()
    // Give the system time to tear down the old Activity and start recreation
    Thread.sleep(1_000)
    // Wait for the new Activity's Compose hierarchy to be fully established.
    // A simple waitForIdle() is insufficient in multi-class runs where the
    // recreation takes longer due to accumulated process state.
    waitUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        try {
            onAllNodesWithText("Consoles", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Connect to your game server", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Welcome Back", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy not yet available after recreate
        }
    }
}

/**
 * Press back until we reach the Home screen.
 * Handles overlays, game sessions, Settings sub-screens, etc.
 * Stops early on auth screens (server connection, login) to avoid exiting the Activity.
 */
private fun ComposeRule.navigateBackToHome() {
    for (i in 1..10) {
        try {
            if (onAllNodesWithText("Consoles", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) return

            // Don't press back on auth screens — it would exit the app
            if (onAllNodesWithText("Connect to your game server", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Welcome Back", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) return

            // In-game overlay — exit game
            if (onAllNodesWithText("Exit Game", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                onNodeWithText("Exit Game").performClick()
                waitForIdle()
                continue
            }

            pressBack()
            waitForIdle()
            // Let the screen transition settle before the next check
            Thread.sleep(500)
        } catch (_: IllegalStateException) {
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
    // Wait for any recognizable screen to load (extra long for fresh install / emulator).
    waitUntil(timeoutMillis = TIMEOUT_EXTRA_LONG) {
        try {
            onAllNodesWithText("Consoles", substring = true)
                .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Connect to your game server", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Welcome Back", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription("Settings", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Play", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("About", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription("Touch controls", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Account", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("Sign Out", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithContentDescription("Go back", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false // Compose hierarchy not yet available
        }
    }

    // On server connection screen — add server then login
    if (onAllNodesWithText("Connect to your game server", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    ) {
        addServerAndLogin(username, password)
        return
    }

    // On login screen — just login
    if (onAllNodesWithText("Welcome Back", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    ) {
        doLogin(username, password)
        return
    }

    // Check if already on Home screen
    val onHome = try {
        onAllNodesWithText("Consoles", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) { false }
    if (onHome) return

    // On some other logged-in screen (Settings, game detail, in-game, etc.)
    // Navigate back to Home first, then verify.
    navigateBackToHome()
    waitForText("Consoles", TIMEOUT_EXTRA_LONG)
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
    // Check if we're on Home screen (logged in)
    val onHome = onAllNodesWithText("Consoles", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
    if (!onHome) return

    // Navigate to Settings and sign out
    tapOn("Settings")
    waitForText("Account")
    waitForText("Sign Out", TIMEOUT_SHORT)

    onNodeWithText("Sign Out").performClick()

    // Confirm sign out dialog — tap the LAST "Sign Out" node
    // (dialog title + confirm button + settings text = 3 nodes; button is last)
    waitForText("re-enter your credentials", TIMEOUT_SHORT)
    val signOutNodes = onAllNodesWithText("Sign Out").fetchSemanticsNodes()
    onAllNodesWithText("Sign Out")[signOutNodes.size - 1].performClick()

    // Wait for server connection screen
    waitForText("Connect to your game server", TIMEOUT_EXTRA_LONG)
}

private fun ComposeRule.addServerAndLogin(username: String, password: String) {
    // Wait for server connection screen
    waitForText("Connect to your game server", TIMEOUT_LONG)

    // Check if "Local" server already exists
    val hasServer = onAllNodesWithText(SERVER_NAME, substring = true)
        .fetchSemanticsNodes().isNotEmpty()

    if (!hasServer) {
        // Add server
        onNodeWithText("Add Server").performClick()
        waitForIdle()

        onNode(hasText("Server Name") and hasSetTextAction())
            .performTextInput(SERVER_NAME)

        onNode(hasText("Server URL") and hasSetTextAction())
            .performTextInput(SERVER_URL)

        onNodeWithText("Add").performScrollTo()
        onNodeWithText("Add").performClick()
        waitForIdle()
    }

    // Tap server card to connect
    waitForText(SERVER_NAME, TIMEOUT_MEDIUM)
    onNodeWithText(SERVER_NAME).performClick()

    // Login
    doLogin(username, password)
}

private fun ComposeRule.doLogin(username: String, password: String) {
    waitForText("Welcome Back", TIMEOUT_MEDIUM)

    // Clear fields first in case they have pre-filled text from a previous session
    onNode(hasText("Username") and hasSetTextAction())
        .performTextClearance()
    onNode(hasText("Username") and hasSetTextAction())
        .performTextInput(username)

    onNode(hasText("Password") and hasSetTextAction())
        .performTextClearance()
    onNode(hasText("Password") and hasSetTextAction())
        .performTextInput(password)

    onNodeWithText("Sign In").performScrollTo()
    onNodeWithText("Sign In").performClick()

    // Verify home screen (extra long timeout for multi-class runs where server may be slow)
    waitForText("Consoles", TIMEOUT_EXTRA_LONG)
}

// ── Navigation helpers ──

fun ComposeRule.navigateToCastlevania() {
    // Tap the NES console card (use "games" to distinguish from Continue Playing card)
    tapNodeMatchingBoth("Nintendo Entertainment System", "games")

    // Tap Castlevania in the game list (unambiguous on console screen)
    scrollToAndTapText("Castlevania")

    // Wait for game detail
    waitForText("About", TIMEOUT_SHORT)
}

/**
 * Scroll to and tap a node whose text contains BOTH text1 AND text2.
 * Useful for disambiguating when multiple nodes share partial text.
 */
fun ComposeRule.tapNodeMatchingBoth(text1: String, text2: String) {
    val matcher = hasText(text1, substring = true) and hasText(text2, substring = true)
    waitUntil(timeoutMillis = TIMEOUT_LONG) {
        try {
            onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
        } catch (_: IllegalStateException) {
            false
        }
    }
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
        } catch (_: IllegalStateException) {
            // Compose hierarchy not yet available
        }

        if (attempt == 0) {
            // First attempt: wait briefly for initial load before swiping
            try {
                waitUntil(timeoutMillis = 2_000) {
                    try {
                        onAllNodesWithText(text, substring = true)
                            .fetchSemanticsNodes().isNotEmpty()
                    } catch (_: IllegalStateException) {
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
    val downloadNodes = onAllNodesWithText("Download").fetchSemanticsNodes()
    if (downloadNodes.isNotEmpty()) {
        onNodeWithText("Download").performClick()
        waitForText("Play", TIMEOUT_EXTRA_LONG)
    }
}

fun ComposeRule.startGameAndWait() {
    onNodeWithText("Play").performClick()
    waitForVisible("Touch controls", TIMEOUT_EXTRA_LONG)
}

fun ComposeRule.openOverlay() {
    pressBack()
    waitForText("Exit Game", TIMEOUT_MEDIUM)
}

fun ComposeRule.exitGame() {
    onNodeWithText("Exit Game").performClick()
    waitForIdle()
}

// ── Composite helpers for common patterns ──

fun ComposeRule.navigateToGameAndPlay() {
    navigateToCastlevania()
    downloadGameIfNeeded()
    startGameAndWait()
}

fun ComposeRule.openOverlayAndExit() {
    openOverlay()
    exitGame()
}

fun ComposeRule.navigateToSettings() {
    tapOn("Settings")
    waitForText("Account")
}

fun ComposeRule.ensureOverlayOpen() {
    val hasOverlay = onAllNodesWithText("Exit Game", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
    if (!hasOverlay) {
        pressBack()
        waitForText("Exit Game", TIMEOUT_MEDIUM)
    }
}

