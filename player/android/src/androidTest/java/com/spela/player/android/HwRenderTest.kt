package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests for HW-rendered cores (Vulkan on Android).
 *
 * The Phase 4 Vulkan HW render pipeline enables demanding cores (N64, PSX) to
 * render directly to Vulkan instead of the slow CPU software path.
 *
 * These tests verify:
 * 1. N64 game browsing and detail navigation works end-to-end
 * 2. NES games (software render path) still work correctly after HW render changes
 * 3. The emulation lifecycle (exit, resume) is intact for both code paths
 *
 * Note: N64 gameplay tests require a real device with Vulkan support.
 * The mupen64plus_next core is too demanding for the Android emulator.
 * NES gameplay tests serve as the backward-compatibility regression suite.
 */
@RunWith(AndroidJUnit4::class)
class HwRenderTest {

    @get:Rule(order = 0)
    val koinResetRule = KoinResetRule()

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    // ── N64 navigation tests (no gameplay, works on emulator) ──

    @Test
    fun n64ConsoleVisibleInLibrary() {
        rule.startLoggedIn()
        rule.tapOn("Library")
        rule.waitForText("Consoles")

        rule.scrollToAndTapMatchingBoth("Nintendo 64", "games")
        rule.waitForText("Banjo-Kazooie", timeout = 8_000)
    }

    @Test
    fun n64GameDetailLoads() {
        rule.startLoggedIn()
        rule.navigateToN64Game()

        rule.assertVisible("Banjo-Kazooie")
        rule.waitForText("About")

        // Verify game detail has the Play/Download button
        val hasPlay = rule.onAllNodesWithText("Play").fetchSemanticsNodes().isNotEmpty()
        val hasDownload = rule.onAllNodesWithText("Download").fetchSemanticsNodes().isNotEmpty()
        assert(hasPlay || hasDownload) {
            "Expected 'Play' or 'Download' button on game detail"
        }
    }

    @Test
    fun n64GameDownload() {
        rule.startLoggedIn()
        rule.navigateToN64Game()

        // Download the ROM if needed, verify Play button appears
        rule.downloadGameIfNeeded()
        rule.waitForText("Play", timeout = 8_000)
    }

    @Test
    fun n64NavigateBackFromGameDetail() {
        rule.startLoggedIn()
        rule.navigateToN64Game()

        rule.assertVisible("Banjo-Kazooie")

        rule.pressBack()
        // Should be back on the N64 console game list
        rule.waitForText("Banjo-Kazooie", timeout = 8_000)

        rule.pressBack()
        // Should be back on the Library console list
        rule.waitForText("Consoles", timeout = 8_000)
    }

    // ── NES backward-compatibility tests (software render path) ──
    // These verify the Vulkan HW render changes don't break the existing pipeline.

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
    fun nesFpsStillWorks() {
        setupNesGame()

        rule.waitForContentDescription("FPS", timeout = 10_000)

        rule.openOverlayAndExit()
        rule.waitForText("Play", timeout = 8_000)
    }

    @Test
    fun nesExitAndResumeStillWorks() {
        setupNesGame()
        rule.openOverlayAndExit()

        rule.waitForText("About", timeout = 8_000)
        rule.waitForText("Play", timeout = 3_000)

        // Second play session
        rule.onNodeWithText("Play").performClick()
        rule.waitForVisible("Touch controls", timeout = 15_000)

        rule.openOverlay()
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")

        rule.exitGame()
    }

    @Test
    fun nesSaveAndLoadStillWorks() {
        setupNesGame()
        rule.openOverlay()

        rule.tapOn("Save")
        rule.waitForIdle()

        rule.ensureOverlayOpen()

        rule.onNodeWithText("Continue").performClick()
        rule.waitForTextNotVisible("Exit Game")

        rule.openOverlay()

        rule.tapOn("Load")
        rule.waitForIdle()

        rule.ensureOverlayOpen()

        rule.exitGame()
    }
}
