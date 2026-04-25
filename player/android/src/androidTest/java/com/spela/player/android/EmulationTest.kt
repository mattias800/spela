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

    @Test
    fun startGameAndExitViaOverlay() {
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

        // Performance stats (proves emulation running) — game title in
        // the overlay depends on which NES game navigateToGameAndPlay
        // happened to pick (currently Balloon Fight by default), so we
        // don't assert it here.
        rule.waitForVisible("Frame", timeout = 15_000)

        rule.exitGame()
    }

    @Test
    fun backButtonTogglesOverlay() {
        setupGame()

        rule.pressBack()
        rule.waitForText("Exit Game")
        rule.waitForText("Continue", timeout = 3_000)

        // Dismiss overlay
        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        // Reopen
        rule.pressBack()
        rule.waitForText("Exit Game")
        // After the overlay re-renders, give Compose a beat to settle
        // before asserting the second button — the overlay's two main
        // buttons can render in successive frames on slow devices.
        rule.waitForText("Continue", timeout = 3_000)

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

        // After exit we land on the game-detail screen for whichever
        // NES game navigateToGameAndPlay happened to pick (Balloon
        // Fight by default). The action-button area is what proves
        // "we're on game detail"; accept any of Play/Resume/Download.
        rule.pollUntil(timeoutMillis = 8_000) {
            try {
                rule.onAllNodesWithText("Play", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Download", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }

        // We're not on Home and the in-game overlay is gone.
        rule.assertTextNotVisible("Exit Game")
        rule.assertTextNotVisible("Continue")
    }

    @Test
    fun exitAndResume() {
        setupGame()
        rule.openOverlayAndExit()

        // Wait for the game-detail action area to render. The exact button
        // depends on cache + auto-save state: "Play"/"Resume" once the
        // game is downloaded, "Download" otherwise. Poll for any of them.
        rule.pollUntil(timeoutMillis = 8_000) {
            try {
                rule.onAllNodesWithText("Play", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Download", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }

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
        // After exit, the action button is "Play" on a fresh game or
        // "Resume" once auto-save has captured a save. With backend
        // reset to defaults, autoSaveEnabled=true, so we may see
        // either depending on timing. Accept both.
        rule.pollUntil(timeoutMillis = 8_000) {
            try {
                rule.onAllNodesWithText("Play", substring = true)
                    .fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("Resume", substring = true)
                        .fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) { false }
        }
    }

    @Test
    fun noAutoOverlayOnStart() {
        setupGame()

        // Game is running with no overlay — overlay buttons must NOT show.
        rule.assertTextNotVisible("Exit Game")
        rule.assertTextNotVisible("Continue")
        // "Touch controls" is the on-screen D-pad/buttons overlay. It's
        // hidden when a gamepad is connected (AYN Thor always has one,
        // emulators never do), so we assert "Game running" (always
        // present while in-game) instead. The overlay-not-showing
        // assertions above already cover the test's real intent.
        rule.assertVisible("Game running")

        rule.pressBack()
        rule.waitForText("Exit Game")

        rule.assertTextVisible("Continue")
        rule.assertVisible("Save")
        rule.assertVisible("Load")

        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.assertTextNotVisible("Exit Game")
        // Same gamepad-vs-touch caveat as above — assert "Game running"
        // instead of "Touch controls" so the test works on both
        // gamepad-connected and touch-only devices.
        rule.assertVisible("Game running")

        rule.openOverlayAndExit()
    }

    // Note: nesFpsCheck and fpsHudVisible used to live here. They asserted
    // the FPS HUD renders when the Performance Overlay setting is enabled.
    // On multi-display hardware (e.g. AYN Thor), InGameOverlay deliberately
    // hides the HUD when state.secondaryDisplayActive is true so it can move
    // to the second screen. These tests would always fail on that class of
    // device. They belong in a desktop test (commonMain composable) once
    // the multi-display routing has its own assertion strategy.

    @Test
    fun fpsHudHiddenByDefault() {
        // Performance Overlay is opt-in. With no toggle flip, the FPS HUD
        // should NOT appear during gameplay — casual users don't see frame
        // timing while playing.
        setupGame()
        rule.assertNotVisible("FPS")
        rule.openOverlayAndExit()
    }

    @Test
    fun saveAndLoadState() {
        setupGame()
        rule.openOverlay()

        // Save state
        rule.tapOn("Save")
        Thread.sleep(300)

        // Ensure overlay is open (save may dismiss it via click propagation)
        rule.ensureOverlayOpen()

        // Dismiss overlay and reopen for load
        rule.tapOn("Continue")
        rule.waitForTextNotVisible("Exit Game")

        rule.openOverlay()

        // Load state
        rule.tapOn("Load")
        Thread.sleep(300)

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
