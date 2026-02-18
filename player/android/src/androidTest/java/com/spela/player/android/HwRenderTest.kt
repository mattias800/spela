package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * E2E tests for N64 cores on Android.
 *
 * These tests use Nintendo 64 (Banjo-Kazooie) via the mupen64plus_next core.
 * They verify:
 * 1. N64 games launch and run correctly
 * 2. Emulation lifecycle (overlay, exit, resume, save/load) works for N64 cores
 * 3. NES games (software render path) still work after HW render changes
 *
 * IMPORTANT: N64 tests are combined into fewer methods to minimize core
 * load/unload cycles. The mupen64plus_next Angrylion renderer leaks native
 * threads across dlclose/dlopen, causing instability after ~4-5 sessions.
 * Alphabetical ordering ensures the multi-session test runs first (fresh state).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HwRenderTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    // N64 core shutdown is slower than NES: auto-save serialization +
    // emulation thread join + native deinit. Angrylion gets progressively
    // slower across sessions due to leaked threads.
    private val n64CoreIdleTimeout = 60_000L

    // ── N64 gameplay tests ──

    private fun setupN64Game() {
        rule.startLoggedIn()
        rule.navigateToN64GameAndPlay()
    }

    private fun exitN64Game() {
        rule.exitGame(n64CoreIdleTimeout)
    }

    /**
     * Tests exit-and-resume lifecycle with two N64 sessions.
     * Runs first (alphabetically) when native state is freshest.
     * Verifies: exit returns to game detail, second session starts,
     * overlay shows correct controls after resume.
     */
    @Test
    fun n64ExitAndResume() {
        setupN64Game()
        rule.openOverlay()
        exitN64Game()

        // Verify we returned to game detail screen
        rule.waitForVisible("Banjo-Kazooie", timeout = 8_000)
        rule.waitForText("Play", timeout = 3_000)

        // Second play session
        rule.onNodeWithText("Play").performClick()
        rule.waitForVisible("Touch controls", timeout = 30_000)

        rule.openOverlay()
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")

        exitN64Game()
    }

    /**
     * Tests N64 game launch, overlay controls, FPS display, save/load state,
     * and exit — all in a single session to minimize core load/unload cycles.
     */
    @Test
    fun n64GameplayAndSaveLoad() {
        setupN64Game()

        // Verify FPS overlay is visible during gameplay
        rule.waitForContentDescription("FPS", timeout = 15_000)

        rule.openOverlay()

        // Verify overlay shows all expected controls
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Fast")
        rule.assertTextVisible("Exit Game")
        rule.assertVisible("Banjo-Kazooie")

        // Save state
        rule.tapOn("Save")
        rule.waitForIdle()
        rule.ensureOverlayOpen()

        // Resume gameplay briefly
        rule.onNodeWithText("Continue").performClick()
        rule.waitForTextNotVisible("Exit Game")

        // Reopen overlay and load state
        rule.openOverlay()
        rule.tapOn("Load")
        rule.waitForIdle()
        rule.ensureOverlayOpen()

        // Exit and verify return to game detail
        exitN64Game()
        rule.waitForVisible("Banjo-Kazooie", timeout = 8_000)
        rule.waitForText("Play", timeout = 3_000)
    }

    // ── NES backward-compatibility tests (software render path) ──

    private fun setupNesGame() {
        rule.startLoggedIn()
        rule.navigateToGameAndPlay()
    }

    @Test
    fun nesStillWorksAfterHwRenderChanges() {
        setupNesGame()
        rule.openOverlay()

        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertTextVisible("Exit Game")
        rule.assertVisible("Castlevania")

        rule.exitGame()
    }

    @Test
    fun nesExitAndResumeStillWorks() {
        setupNesGame()
        rule.openOverlayAndExit()

        rule.waitForText("About", timeout = 8_000)
        rule.waitForText("Play", timeout = 3_000)

        rule.onNodeWithText("Play").performClick()
        rule.waitForVisible("Touch controls", timeout = 15_000)

        rule.openOverlay()
        rule.assertTextVisible("Continue")

        rule.exitGame()
    }
}
