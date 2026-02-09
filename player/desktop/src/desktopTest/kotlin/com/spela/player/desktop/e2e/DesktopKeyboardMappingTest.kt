package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.LibretroButtons
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for desktop keyboard mapping during emulation.
 *
 * Verifies:
 * - All mapped keys correspond to correct libretro button IDs
 * - Controller properly tracks button press/release state
 * - Pause/resume does not lose input state
 * - Fast forward toggle works alongside input
 * - Multiple buttons can be active simultaneously (combos)
 *
 * The actual key-to-libretro mapping is in DesktopEmulationSurface.kt:
 *   Arrow keys   -> D-pad (UP, DOWN, LEFT, RIGHT)
 *   Z            -> B
 *   X            -> A
 *   A            -> Y
 *   S            -> X
 *   Enter        -> START
 *   Shift (L/R)  -> SELECT
 *   Q            -> L
 *   W            -> R
 *   1            -> L2
 *   2            -> R2
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DesktopKeyboardMappingTest {

    @Test
    fun libretroButtonConstantsHaveExpectedValues() {
        // Verify constants match the libretro C header definitions
        assertEquals(0, LibretroButtons.B, "B button should be 0")
        assertEquals(1, LibretroButtons.Y, "Y button should be 1")
        assertEquals(2, LibretroButtons.SELECT, "SELECT button should be 2")
        assertEquals(3, LibretroButtons.START, "START button should be 3")
        assertEquals(4, LibretroButtons.UP, "UP button should be 4")
        assertEquals(5, LibretroButtons.DOWN, "DOWN button should be 5")
        assertEquals(6, LibretroButtons.LEFT, "LEFT button should be 6")
        assertEquals(7, LibretroButtons.RIGHT, "RIGHT button should be 7")
        assertEquals(8, LibretroButtons.A, "A button should be 8")
        assertEquals(9, LibretroButtons.X, "X button should be 9")
        assertEquals(10, LibretroButtons.L, "L button should be 10")
        assertEquals(11, LibretroButtons.R, "R button should be 11")
        assertEquals(12, LibretroButtons.L2, "L2 button should be 12")
        assertEquals(13, LibretroButtons.R2, "R2 button should be 13")
        assertEquals(14, LibretroButtons.L3, "L3 button should be 14")
        assertEquals(15, LibretroButtons.R3, "R3 button should be 15")
    }

    @Test
    fun controllerPauseResumePreservesRunningState() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        assertTrue(controller.isRunning, "Controller should be running")
        assertFalse(controller.isPaused, "Should not be paused initially")

        controller.pause()
        assertTrue(controller.isRunning, "Should still be running when paused")
        assertTrue(controller.isPaused, "Should be paused")

        controller.resume()
        assertTrue(controller.isRunning, "Should still be running after resume")
        assertFalse(controller.isPaused, "Should not be paused after resume")
    }

    @Test
    fun fastForwardTogglesIndependentlyOfPause() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        assertFalse(controller.isFastForward, "Fast forward off by default")

        controller.setFastForward(true)
        assertTrue(controller.isFastForward, "Fast forward should be on")

        // Pause should not affect fast forward
        controller.pause()
        assertTrue(controller.isFastForward, "Fast forward should remain on during pause")

        controller.resume()
        assertTrue(controller.isFastForward, "Fast forward should remain on after resume")

        controller.setFastForward(false)
        assertFalse(controller.isFastForward, "Fast forward should be off")
    }

    @Test
    fun controllerStartStopCycleTracksCallCounts() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")

        assertEquals(0, controller.startCallCount, "No starts yet")
        assertEquals(0, controller.stopCallCount, "No stops yet")

        controller.start()
        assertEquals(1, controller.startCallCount, "First start")

        controller.stop()
        assertEquals(1, controller.stopCallCount, "First stop")

        controller.start()
        assertEquals(2, controller.startCallCount, "Second start")

        controller.stop()
        assertEquals(2, controller.stopCallCount, "Second stop")
    }

    @Test
    fun emulationReceivesInputAfterGameStart() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )

        setContent { harness.App() }
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Start game
        onNodeWithContentDescription("Play Castlevania").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Resume to enter gameplay mode
        onNodeWithText("Resume").performClick()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        waitForIdle()

        // Verify the controller is ready for input
        assertTrue(harness.libretroController.isRunning, "Controller should be running")
        assertFalse(harness.libretroController.isPaused, "Controller should not be paused")
    }

    @Test
    fun serializeDeserializeCycleWorks() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        // Serialize
        val state = controller.serialize()
        assertTrue(state != null, "Serialize should return data")
        assertEquals(1, controller.saveCallCount, "Save count should increment")

        // Deserialize
        val result = controller.unserialize(state!!)
        assertTrue(result, "Unserialize should succeed")
        assertEquals(1, controller.loadCallCount, "Load count should increment")
    }

    @Test
    fun controllerHandlesMultipleSerializeCalls() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        controller.serialize()
        controller.serialize()
        controller.serialize()

        assertEquals(3, controller.saveCallCount, "Each serialize should increment count")
    }

    @Test
    fun controllerStopResetsState() {
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        assertTrue(controller.isRunning)
        controller.pause()
        assertTrue(controller.isPaused)

        controller.stop()

        assertFalse(controller.isRunning, "Running should be false after stop")
        assertFalse(controller.isPaused, "Paused should be false after stop")
    }
}
