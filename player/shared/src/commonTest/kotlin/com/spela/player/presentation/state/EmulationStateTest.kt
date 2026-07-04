package com.spela.player.presentation.state

import com.spela.player.domain.model.ShaderPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EmulationStateTest {

    @Test
    fun defaultStateHasSecondaryDisplayInactive() {
        val state = EmulationState()
        assertFalse(state.secondaryDisplayActive)
    }

    @Test
    fun defaultStateHasCorrectDefaults() {
        val state = EmulationState()
        assertEquals("", state.gameId)
        assertEquals("", state.gameTitle)
        assertEquals("", state.consoleId)
        assertFalse(state.isRunning)
        assertFalse(state.isPaused)
        assertFalse(state.isLoading)
        assertFalse(state.showOverlay)
        assertFalse(state.showPerformanceOverlay)
        assertEquals(ShaderPreset.NONE, state.selectedShader)
        assertFalse(state.supportsSaveStates)
        assertFalse(state.showExitConfirm)
        assertFalse(state.requestExit)
        assertNull(state.statusMessage)
        assertEquals(0f, state.fps)
        assertEquals(0f, state.frameTime)
        assertFalse(state.isFastForward)
        assertFalse(state.showKeyMapping)
        assertNull(state.error)
        assertNull(state.fatalError)
        assertNull(state.achievementEvent)
        assertFalse(state.isHardcoreMode)
        assertFalse(state.secondaryDisplayActive)
        assertFalse(state.isDualScreenConsole)
        assertEquals(0, state.dualScreenSplitY)
    }

    @Test
    fun secondaryDisplayActiveCanBeSet() {
        val state = EmulationState(secondaryDisplayActive = true)
        assertEquals(true, state.secondaryDisplayActive)
    }

    @Test
    fun copyPreservesSecondaryDisplayActive() {
        val state = EmulationState(secondaryDisplayActive = true)
        val copied = state.copy(isRunning = true)
        assertEquals(true, copied.secondaryDisplayActive)
        assertEquals(true, copied.isRunning)
    }

    @Test
    fun isDualScreenConsoleCanBeSet() {
        val state = EmulationState(isDualScreenConsole = true, dualScreenSplitY = 192)
        assertEquals(true, state.isDualScreenConsole)
        assertEquals(192, state.dualScreenSplitY)
    }

    @Test
    fun copyPreservesDualScreenState() {
        val state = EmulationState(isDualScreenConsole = true, dualScreenSplitY = 192)
        val copied = state.copy(isRunning = true)
        assertEquals(true, copied.isDualScreenConsole)
        assertEquals(192, copied.dualScreenSplitY)
        assertEquals(true, copied.isRunning)
    }

    @Test
    fun dualScreenDefaultsToFalse() {
        val state = EmulationState()
        assertFalse(state.isDualScreenConsole)
        assertEquals(0, state.dualScreenSplitY)
    }
}
