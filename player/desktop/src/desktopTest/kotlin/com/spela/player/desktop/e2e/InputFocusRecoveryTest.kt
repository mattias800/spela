package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression tests for input focus recovery after overlay dismissal.
 *
 * The original bug: the control hint overlay (shown on first game launch)
 * stole keyboard focus from the emulation surface Canvas. After dismissing
 * the hint by tapping, focus remained on the overlay's Box rather than
 * returning to the Canvas, so keyboard events were silently swallowed.
 *
 * The fix added a periodic focus re-request loop in DesktopEmulationSurface
 * that calls focusRequester.requestFocus() every 500ms, recovering from
 * any transient focus loss.
 *
 * These tests verify at the ViewModel/state level that:
 * 1. The control hint is shown when expected and can be dismissed.
 * 2. After dismissal, the emulation state is correct for input to flow.
 * 3. The control hint does not reappear after being dismissed.
 * 4. Keyboard input continues to work after overlay toggle cycles.
 *
 * Note: The actual focus/key routing is handled by the platform-specific
 * DesktopEmulationSurface which casts to DesktopLibretroController. In
 * tests using FakeLibretroController the surface is not rendered, so we
 * verify the state preconditions that enable input delivery.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InputFocusRecoveryTest {

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    private fun advance(harness: SpelaTestHarness, scope: ComposeUiTest) {
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
    }

    private fun ComposeUiTest.startGameAndDismissOverlay(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness, this)

        // Start game from detail screen
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // Dismiss the initial in-game overlay by clicking Resume
        onNodeWithText("Resume").performClick()
        advance(harness, this)
    }

    @Test
    fun controlHintShownOnFirstGameStart() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        setContent { harness.App() }
        advance(harness, this)

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        advance(harness, this)

        // Dismiss in-game overlay
        onNodeWithText("Resume").performClick()
        advance(harness, this)

        // Control hint should be visible (showControlHint defaults to true)
        val state = harness.emulationViewModel.state.value
        assertTrue(state.isRunning, "Game should be running")
        assertFalse(state.showOverlay, "Overlay should be hidden")
        assertTrue(state.showControlHint, "Control hint should be shown on first launch")
    }

    @Test
    fun dismissingControlHintUpdatesState() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // Verify hint is showing
        assertTrue(
            harness.emulationViewModel.state.value.showControlHint,
            "Control hint should be shown before dismissal"
        )

        // Dismiss the control hint (simulates tapping the hint overlay)
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        // After dismissal, hint should be gone and game should still run
        val state = harness.emulationViewModel.state.value
        assertFalse(state.showControlHint, "Control hint should be dismissed")
        assertTrue(state.isRunning, "Game should still be running after hint dismissal")
        assertFalse(state.showOverlay, "Overlay should remain hidden")
    }

    @Test
    fun emulationStateCorrectForInputAfterHintDismissal() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // Dismiss control hint
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        // All conditions for input delivery should be met
        val state = harness.emulationViewModel.state.value
        assertTrue(state.isRunning, "Emulation must be running")
        assertFalse(state.isPaused, "Emulation must not be paused")
        assertFalse(state.showOverlay, "Overlay must be hidden")
        assertFalse(state.showControlHint, "Control hint must be dismissed")

        // Controller should be in the correct state for receiving input
        assertTrue(harness.libretroController.isRunning, "Controller should be running")
        assertFalse(harness.libretroController.isPaused, "Controller should not be paused")
    }

    @Test
    fun controlHintDoesNotReappearAfterDismissal() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // Dismiss control hint
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        assertFalse(harness.emulationViewModel.state.value.showControlHint)

        // Toggle overlay open and close (simulates Escape key usage)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)

        // Control hint should still be dismissed
        assertFalse(
            harness.emulationViewModel.state.value.showControlHint,
            "Control hint should not reappear after overlay toggle"
        )
    }

    @Test
    fun inputStateCorrectAfterOverlayDismissFollowingHint() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // Dismiss control hint
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        // Open overlay (Escape)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advance(harness, this)
        assertTrue(harness.emulationViewModel.state.value.showOverlay)

        // Close overlay (Resume)
        onNodeWithText("Resume").performClick()
        advance(harness, this)

        // State should be ready for input again
        val state = harness.emulationViewModel.state.value
        assertTrue(state.isRunning, "Game should be running")
        assertFalse(state.showOverlay, "Overlay should be hidden")
        assertFalse(state.showControlHint, "Control hint should remain dismissed")
        assertFalse(state.isPaused, "Game should not be paused")
    }

    @Test
    fun controllerRemainsRunningThroughHintAndOverlayCycles() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // Dismiss hint
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        // Multiple overlay toggle cycles
        repeat(3) {
            harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
            advance(harness, this)
            assertTrue(
                harness.libretroController.isRunning,
                "Controller should still run with overlay open (cycle $it)"
            )

            harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
            advance(harness, this)
            assertTrue(
                harness.libretroController.isRunning,
                "Controller should still run after overlay close (cycle $it)"
            )
            assertFalse(
                harness.libretroController.isPaused,
                "Controller should not be paused (cycle $it)"
            )
        }
    }

    @Test
    fun controlHintOverlayNodeExistsWhenHintIsActive() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // The control hint text should be in the tree while showControlHint is true
        onNodeWithText("Tap anywhere to start playing").assertIsDisplayed()

        // Dismiss hint
        harness.emulationViewModel.onIntent(EmulationIntent.DismissControlHint)
        advance(harness, this)

        // The hint text should no longer be displayed
        onNodeWithText("Tap anywhere to start playing").assertDoesNotExist()
    }

    @Test
    fun tappingControlHintOverlayDismissesIt() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameAndDismissOverlay(harness)

        // The hint overlay has a pointerInput that calls DismissControlHint on tap.
        // In the Compose test, we can click on the hint text area to trigger it.
        assertTrue(harness.emulationViewModel.state.value.showControlHint)

        onNodeWithText("Tap anywhere to start playing").performClick()
        advance(harness, this)

        assertFalse(
            harness.emulationViewModel.state.value.showControlHint,
            "Tapping the hint text should dismiss the control hint"
        )
    }
}
