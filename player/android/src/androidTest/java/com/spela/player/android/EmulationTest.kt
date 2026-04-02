package com.spela.player.android

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulationTest {

    

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun setupGame() {
        rule.startLoggedIn()
        rule.navigateToGameAndPlay()
    }

    @Test
    fun playCastlevania() {
        setupGame()
        rule.openOverlay()
        rule.assertTextVisible("Exit Game")
        rule.exitGame()
    }

    @Test
    fun overlayShowsAllControls() {
        setupGame()
        rule.openOverlay()

        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertVisible("Screenshot")
        rule.assertVisible("Fast")
        rule.assertTextVisible("Exit Game")

        // Game title in overlay
        rule.assertVisible("Castlevania")

        // Performance stats (proves emulation running)
        rule.waitForVisible("Frame", timeout = 15_000)

        rule.exitGame()
    }

    @Test
    fun backButtonTogglesOverlay() {
        setupGame()

        rule.pressBack()
        rule.waitForText("Exit Game")
        rule.assertTextVisible("Continue")

        // Dismiss overlay
        rule.onNodeWithText("Continue").performClick()
        rule.waitForTextNotVisible("Exit Game")

        // Reopen
        rule.pressBack()
        rule.waitForText("Exit Game")
        rule.assertTextVisible("Continue")

        rule.exitGame()
    }

    @Test
    fun dismissOverlayByBackdropTap() {
        setupGame()
        rule.openOverlay()

        rule.tapAtPercent(50f, 10f)
        rule.waitForTextNotVisible("Exit Game")

        // Game still running
        rule.pressBack()
        rule.waitForText("Exit Game")

        rule.exitGame()
    }

    @Test
    fun exitReturnsToGameDetail() {
        setupGame()
        rule.openOverlayAndExit()

        rule.waitForVisible("Castlevania", timeout = 8_000)
        rule.waitForText("Play", timeout = 3_000)

        rule.assertTextNotVisible("Spela")
        rule.assertTextNotVisible("Exit Game")
        rule.assertTextNotVisible("Continue")
    }

    @Test
    fun exitAndResume() {
        setupGame()
        rule.openOverlayAndExit()

        rule.waitForText("Download", timeout = 8_000)

        // Second play session — button may be "Resume" if saves exist
        val hasResume = rule.onAllNodesWithText("Resume", substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (hasResume) {
            rule.onNodeWithText("Resume").performClick()
        } else {
            rule.waitForText("Play", timeout = 3_000)
            rule.onNodeWithText("Play").performClick()
        }
        rule.waitForVisible("Game running", timeout = 15_000)

        rule.openOverlay()
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")

        rule.exitGame()
    }

    @Test
    fun exitNoConfirmationWithAutoSave() {
        rule.startLoggedIn()

        // Verify auto-save is visible in Settings
        rule.navigateToSettings()
        rule.waitForText("Auto Save on Exit", timeout = 8_000)

        rule.pressBack()
        rule.waitForText("Spela")

        rule.navigateToGameAndPlay()
        rule.openOverlay()
        rule.exitGame()

        rule.waitForText("Play", timeout = 15_000)

        rule.assertTextNotVisible("Exit without saving?")
        rule.assertTextNotVisible("Keep Playing")
        rule.assertTextNotVisible("Exit Anyway")

        rule.assertVisible("Castlevania")
    }

    @Test
    fun fastForwardToggle() {
        setupGame()
        rule.openOverlay()

        rule.assertVisible("Fast")
        rule.tapOn("Fast")
        rule.waitForVisible("Normal", timeout = 3_000)
        rule.tapOn("Normal")
        rule.waitForVisible("Fast", timeout = 3_000)

        rule.exitGame()
    }

    @Test
    fun nesStability() {
        setupGame()
        rule.openOverlay()

        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")

        rule.exitGame()
        rule.waitForText("Play", timeout = 8_000)
    }

    @Test
    fun noAutoOverlayOnStart() {
        setupGame()

        rule.assertTextNotVisible("Exit Game")
        rule.assertTextNotVisible("Continue")
        rule.assertVisible("Touch controls")

        rule.pressBack()
        rule.waitForText("Exit Game")

        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")
        rule.assertNotVisible("Touch controls")

        rule.onNodeWithText("Continue").performClick()
        rule.waitForTextNotVisible("Exit Game")

        rule.assertTextNotVisible("Exit Game")
        rule.assertVisible("Touch controls")

        rule.openOverlayAndExit()
    }

    @Test
    fun nesFpsCheck() {
        setupGame()

        rule.waitForContentDescription("FPS", timeout = 10_000)

        rule.openOverlayAndExit()
        rule.waitForText("Play", timeout = 8_000)
    }

    @Test
    fun fpsHudVisible() {
        setupGame()

        rule.waitForContentDescription("FPS", timeout = 10_000)

        rule.pressBack()
        rule.waitForText("Exit Game")
        rule.assertTextVisible("Continue")

        rule.exitGame()
    }

    @Test
    fun saveAndLoadState() {
        setupGame()
        rule.openOverlay()

        // Save state
        rule.tapOn("Save")
        rule.waitForIdle()

        // Ensure overlay is open (save may dismiss it via click propagation)
        rule.ensureOverlayOpen()

        // Dismiss overlay and reopen for load
        rule.onNodeWithText("Continue").performClick()
        rule.waitForTextNotVisible("Exit Game")

        rule.openOverlay()

        // Load state
        rule.tapOn("Load")
        rule.waitForIdle()

        // Ensure overlay is open after load
        rule.ensureOverlayOpen()

        rule.exitGame()
    }

    @Test
    fun screenshotCapture() {
        setupGame()
        rule.openOverlay()

        rule.tapOn("Screenshot")

        rule.waitForText("Exit Game", timeout = 3_000)
        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")

        rule.exitGame()
    }
}
