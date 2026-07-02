package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.WidescreenMode
import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubGameRepository
import com.spela.player.presentation.intent.EmulationIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

    private lateinit var builder: EmulationViewModelTestBuilder

    @BeforeTest
    fun setup() {
        builder = EmulationViewModelTestBuilder()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        builder.tearDown()
        Dispatchers.resetMain()
    }

    // ── Overlay toggle ──────────────────────────────────────────────────────

    @Test
    fun toggleOverlayShowsAndPauses() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.showOverlay)

        vm.onIntent(EmulationIntent.ToggleOverlay)
        assertTrue(vm.state.value.showOverlay)
        assertTrue(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.pauseCallCount)
    }

    @Test
    fun toggleOverlayHidesAndResumes() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleOverlay) // show
        assertTrue(vm.state.value.showOverlay)

        vm.onIntent(EmulationIntent.ToggleOverlay) // hide
        assertFalse(vm.state.value.showOverlay)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.resumeCallCount)
    }

    // ── Fast forward ────────────────────────────────────────────────────────

    @Test
    fun toggleFastForwardSetsStateAndCallsController() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertTrue(vm.state.value.isFastForward)
        assertEquals(1, builder.libretroController.setFastForwardCallCount)
        assertEquals(true, builder.libretroController.lastFastForwardEnabled)
    }

    @Test
    fun toggleFastForwardTogglesBack() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertTrue(vm.state.value.isFastForward)
        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertFalse(vm.state.value.isFastForward)
        assertEquals(false, builder.libretroController.lastFastForwardEnabled)
    }

    @Test
    fun toggleFastForwardBlockedInChallengeMode() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isChallengeMode)

        vm.onIntent(EmulationIntent.ToggleFastForward)
        assertFalse(vm.state.value.isFastForward)
        assertEquals(0, builder.libretroController.setFastForwardCallCount)
    }

    // ── Widescreen mode ────────────────────────────────────────────────────

    @Test
    fun startGameResolvesWidescreenModeForCurrentGame() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "wii")
        builder.preferencesRepository.resolveWidescreenModeResult = WidescreenMode.ZOOM
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        assertEquals(WidescreenMode.ZOOM, vm.state.value.widescreenMode)
        assertEquals("game1", builder.preferencesRepository.lastResolvedWidescreenModeGameId)
        assertEquals("wii", builder.preferencesRepository.lastResolvedWidescreenModeConsoleId)
    }

    @Test
    fun setWidescreenModeUpdatesStateAndPersistsForCurrentGame() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "wii")
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.SetWidescreenMode(WidescreenMode.FOUR_THREE))

        assertEquals(WidescreenMode.FOUR_THREE, vm.state.value.widescreenMode)
        assertEquals("game1", builder.preferencesRepository.lastSetWidescreenModeGameId)
        assertEquals("wii", builder.preferencesRepository.lastSetWidescreenModeConsoleId)
        assertEquals(WidescreenMode.FOUR_THREE, builder.preferencesRepository.lastSetWidescreenMode)
        assertEquals(WidescreenMode.FOUR_THREE, builder.libretroController.lastWidescreenMode)
        assertEquals(0, builder.libretroController.refreshPausedVideoCallCount)
    }

    @Test
    fun setWidescreenModeRefreshesPausedVideoForImmediateOverlayPreview() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "gc")
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.ToggleOverlay)
        assertTrue(vm.state.value.isPaused)

        vm.onIntent(EmulationIntent.SetWidescreenMode(WidescreenMode.ZOOM))

        assertEquals(WidescreenMode.ZOOM, vm.state.value.widescreenMode)
        assertEquals(WidescreenMode.ZOOM, builder.libretroController.lastWidescreenMode)
        assertEquals(1, builder.libretroController.refreshPausedVideoCallCount)
        assertEquals(
            listOf("setWidescreenMode:zoom", "refreshPausedVideo"),
            builder.libretroController.presentationEvents,
        )
    }

    // ── Exit confirm ────────────────────────────────────────────────────────

    @Test
    fun showExitConfirmExitsDirectlyWhenSaveStatesSupported() = runTest {
        builder.libretroController.supportsSaveStatesResult = true
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(4000) // Wait past the 3-second save-state probe
        assertTrue(vm.state.value.supportsSaveStates)

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        builder.advanceTimeBy(100)

        // Should exit directly without showing dialog
        assertFalse(vm.state.value.showExitConfirm)
        assertTrue(vm.state.value.requestExit)
    }

    @Test
    fun showExitConfirmShowsDialogWhenNoSaveStates() = runTest {
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(4000) // Wait past the 3-second save-state probe
        assertFalse(vm.state.value.supportsSaveStates)

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        assertTrue(vm.state.value.showExitConfirm)
    }

    @Test
    fun dismissExitConfirmClearsDialog() = runTest {
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(4000) // Wait past the 3-second save-state probe

        vm.onIntent(EmulationIntent.ShowExitConfirm)
        assertTrue(vm.state.value.showExitConfirm)

        vm.onIntent(EmulationIntent.DismissExitConfirm)
        assertFalse(vm.state.value.showExitConfirm)
    }

    @Test
    fun confirmExitSetsRequestExitAndStops() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmExit)
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun clearExitRequestResetsFlag() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmExit)
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.requestExit)

        vm.onIntent(EmulationIntent.ClearExitRequest)
        assertFalse(vm.state.value.requestExit)
    }

    // ── Key mapping / gamepad config ────────────────────────────────────────

    @Test
    fun showKeyMappingSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowKeyMapping)
        assertTrue(vm.state.value.showKeyMapping)
        assertFalse(vm.state.value.showOverlay)
        assertFalse(vm.state.value.showGamepadConfig)
    }

    @Test
    fun hideKeyMappingResetsAndShowsOverlay() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowKeyMapping)
        assertTrue(vm.state.value.showKeyMapping)

        vm.onIntent(EmulationIntent.HideKeyMapping)
        assertFalse(vm.state.value.showKeyMapping)
        assertTrue(vm.state.value.showOverlay)
    }

    @Test
    fun showGamepadConfigSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowGamepadConfig)
        assertTrue(vm.state.value.showGamepadConfig)
        assertFalse(vm.state.value.showOverlay)
    }

    @Test
    fun hideGamepadConfigResetsAndShowsOverlay() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowGamepadConfig)
        assertTrue(vm.state.value.showGamepadConfig)

        vm.onIntent(EmulationIntent.HideGamepadConfig)
        assertFalse(vm.state.value.showGamepadConfig)
        assertTrue(vm.state.value.showOverlay)
    }

    @Test
    fun dismissAchievementClearsEvent() = runTest {
        val vm = builder.build()
        // achievementEvent defaults to null; calling dismiss should keep it null
        vm.onIntent(EmulationIntent.DismissAchievement)
        assertNull(vm.state.value.achievementEvent)
    }
}
