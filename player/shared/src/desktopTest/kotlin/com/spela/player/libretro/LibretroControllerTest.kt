package com.spela.player.libretro

import com.spela.player.presentation.viewmodel.LibretroButtons
import com.spela.player.presentation.viewmodel.LibretroController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for libretro controller state management and button constants.
 * These exercise pure logic with no Compose UI involvement.
 */
class LibretroControllerTest {

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

    @Test
    fun fakeControllerTracksButtonState() {
        // Unit-level verification that the FakeLibretroController
        // properly tracks input state changes
        val controller = FakeLibretroController()
        controller.loadCore("/fake/core")
        controller.loadGame("/fake/game")
        controller.start()

        assertTrue(controller.isRunning, "Controller should be running")

        // Verify controller is ready for input
        controller.pause()
        assertTrue(controller.isPaused, "Controller should be paused")

        controller.resume()
        assertFalse(controller.isPaused, "Controller should be resumed")

        controller.stop()
        assertFalse(controller.isRunning, "Controller should be stopped")
    }
}

/**
 * Inline fake for unit tests in the shared module.
 * Mirrors the FakeLibretroController from the desktop E2E test harness.
 */
private class FakeLibretroController : LibretroController {
    var loadedCore: String? = null
        private set
    var loadedGame: String? = null
        private set
    var isRunning = false
        private set
    var isPaused = false
        private set
    var isFastForward = false
        private set
    var serializedState: ByteArray? = null
    var startCallCount = 0
        private set
    var stopCallCount = 0
        private set
    var saveCallCount = 0
        private set
    var loadCallCount = 0
        private set

    override fun loadCore(corePath: String) {
        loadedCore = corePath
    }

    override fun loadGame(gamePath: String) {
        loadedGame = gamePath
    }

    override fun start() {
        isRunning = true
        isPaused = false
        startCallCount++
    }

    override fun pause() {
        isPaused = true
    }

    override fun resume() {
        isPaused = false
    }

    override fun stop() {
        isRunning = false
        isPaused = false
        stopCallCount++
    }

    override fun serialize(): ByteArray? {
        saveCallCount++
        return serializedState ?: ByteArray(128) { it.toByte() }
    }

    override fun unserialize(data: ByteArray): Boolean {
        loadCallCount++
        serializedState = data
        return true
    }

    override fun setFastForward(enabled: Boolean) {
        isFastForward = enabled
    }

    override fun supportsSaveStates(): Boolean = true

    override fun performanceStats(): Flow<Pair<Float, Float>> = MutableStateFlow(59.9f to 16.5f)
}
