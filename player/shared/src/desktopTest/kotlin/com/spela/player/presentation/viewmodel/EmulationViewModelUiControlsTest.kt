package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.intent.EmulationIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelUiControlsTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var builder: EmulationViewModelTestBuilder

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        builder = EmulationViewModelTestBuilder(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        builder.tearDown()
        Dispatchers.resetMain()
    }

    // ── Overlay toggle ──────────────────────────────────────────────────────

    @Test
    fun toggleOverlayShowsAndPauses() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertFalse(vm.state.value.showOverlay)

        vm.onIntent(EmulationIntent.ToggleOverlay)
        assertTrue(vm.state.value.showOverlay)
        assertTrue(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.pauseCallCount)
        builder.tearDown()
    }

    @Test
    fun toggleOverlayHidesAndResumes() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleOverlay) // show
        assertTrue(vm.state.value.showOverlay)

        vm.onIntent(EmulationIntent.ToggleOverlay) // hide
        assertFalse(vm.state.value.showOverlay)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.resumeCallCount)
        builder.tearDown()
    }

    // ── Fast forward ────────────────────────────────────────────────────────

    @Test
    fun toggleFastForwardSetsStateAndCallsController() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertTrue(vm.state.value.isFastForward)
        assertEquals(1, builder.libretroController.setFastForwardCallCount)
        assertEquals(true, builder.libretroController.lastFastForwardEnabled)
        builder.tearDown()
    }

    @Test
    fun toggleFastForwardTogglesBack() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertTrue(vm.state.value.isFastForward)
        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertFalse(vm.state.value.isFastForward)
        assertEquals(false, builder.libretroController.lastFastForwardEnabled)
        builder.tearDown()
    }

    @Test
    fun toggleFastForwardBlockedInChallengeMode() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isChallengeMode)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertFalse(vm.state.value.isFastForward)
        assertEquals(0, builder.libretroController.setFastForwardCallCount)
        builder.tearDown()
    }

    // ── Exit confirm ────────────────────────────────────────────────────────

    @Test
    fun showExitConfirmExitsDirectlyWhenSaveStatesSupported() = runTest(testDispatcher) {
        builder.libretroController.supportsSaveStatesResult = true
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.supportsSaveStates)

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        advanceTimeBy(100)

        // Should exit directly without showing dialog
        assertFalse(vm.state.value.showExitConfirm)
        assertTrue(vm.state.value.requestExit)
        builder.tearDown()
    }

    @Test
    fun showExitConfirmShowsDialogWhenNoSaveStates() = runTest(testDispatcher) {
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertFalse(vm.state.value.supportsSaveStates)

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        assertTrue(vm.state.value.showExitConfirm)
        builder.tearDown()
    }

    @Test
    fun dismissExitConfirmClearsDialog() = runTest(testDispatcher) {
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        assertTrue(vm.state.value.showExitConfirm)

        vm.onIntent(EmulationIntent.DismissExitConfirm)
        assertFalse(vm.state.value.showExitConfirm)
        builder.tearDown()
    }

    @Test
    fun confirmExitSetsRequestExitAndStops() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmExit)
        advanceTimeBy(100)

        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
        builder.tearDown()
    }

    @Test
    fun clearExitRequestResetsFlag() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmExit)
        advanceTimeBy(100)
        assertTrue(vm.state.value.requestExit)

        vm.onIntent(EmulationIntent.ClearExitRequest)
        assertFalse(vm.state.value.requestExit)
        builder.tearDown()
    }

    // ── Key mapping / gamepad config ────────────────────────────────────────

    @Test
    fun showKeyMappingSetsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowKeyMapping)
        assertTrue(vm.state.value.showKeyMapping)
        assertFalse(vm.state.value.showOverlay)
        assertFalse(vm.state.value.showGamepadConfig)
        builder.tearDown()
    }

    @Test
    fun hideKeyMappingResetsAndShowsOverlay() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowKeyMapping)
        assertTrue(vm.state.value.showKeyMapping)

        vm.onIntent(EmulationIntent.HideKeyMapping)
        assertFalse(vm.state.value.showKeyMapping)
        assertTrue(vm.state.value.showOverlay)
        builder.tearDown()
    }

    @Test
    fun showGamepadConfigSetsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowGamepadConfig)
        assertTrue(vm.state.value.showGamepadConfig)
        assertFalse(vm.state.value.showOverlay)
        builder.tearDown()
    }

    @Test
    fun hideGamepadConfigResetsAndShowsOverlay() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowGamepadConfig)
        assertTrue(vm.state.value.showGamepadConfig)

        vm.onIntent(EmulationIntent.HideGamepadConfig)
        assertFalse(vm.state.value.showGamepadConfig)
        assertTrue(vm.state.value.showOverlay)
        builder.tearDown()
    }

    @Test
    fun dismissAchievementClearsEvent() = runTest(testDispatcher) {
        val vm = builder.build()
        // achievementEvent defaults to null; calling dismiss should keep it null
        vm.onIntent(EmulationIntent.DismissAchievement)
        assertNull(vm.state.value.achievementEvent)
        builder.tearDown()
    }
}
