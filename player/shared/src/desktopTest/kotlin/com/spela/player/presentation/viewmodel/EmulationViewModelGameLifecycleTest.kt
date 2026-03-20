package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubGameRepository
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelGameLifecycleTest {

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

    @Test
    fun startGameSetsIsLoadingTrue() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        // Before any dispatching, isLoading should be set synchronously
        assertTrue(vm.state.value.isLoading)
    }

    @Test
    fun startGameSetsIsRunningTrueAfterLoad() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun startGameSetsGameIdInState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("my-game-42"))
        builder.advanceTimeBy(100)
        assertEquals("my-game-42", vm.state.value.gameId)
    }

    @Test
    fun startGameFetchesGameDetail() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals("Test Game", vm.state.value.gameTitle)
        assertEquals("nes", vm.state.value.consoleId)
    }

    @Test
    fun startGameLoadsUserPreferences() = runTest {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(showPerformanceOverlay = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showPerformanceOverlay)
    }

    @Test
    fun startGameResolvesShader() = runTest {
        builder.preferencesRepository.resolveShaderResult = ShaderPreset.CRT_SIMPLE
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(ShaderPreset.CRT_SIMPLE, vm.state.value.selectedShader)
    }

    @Test
    fun startGameCallsLoadCoreThenLoadGameThenStart() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(1, builder.libretroController.loadCoreCallCount)
        assertEquals(1, builder.libretroController.loadGameCallCount)
        assertEquals(1, builder.libretroController.startCallCount)
    }

    @Test
    fun startGameDetectsDualScreenConsoleNds() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "nds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(192, vm.state.value.dualScreenSplitY)
    }

    @Test
    fun startGameDetectsDualScreenConsole3ds() = runTest {
        builder.gameRepository = StubGameRepository(consoleId = "3ds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(240, vm.state.value.dualScreenSplitY)
    }

    @Test
    fun startGameSetsSessionElapsedSecondsToZero() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertEquals(0, vm.state.value.sessionElapsedSeconds)
    }

    @Test
    fun sessionTimerIncrementsEverySecond() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        // Advance past the 3s delay + 3 timer ticks (at t=4000, 5000, 6000)
        builder.advanceTimeBy(6100)
        assertTrue(vm.state.value.sessionElapsedSeconds >= 3)
    }

    @Test
    fun sessionTimerStopsOnStopGame() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        builder.advanceTimeBy(5000)
        val elapsed = vm.state.value.sessionElapsedSeconds
        assertTrue(elapsed >= 1)
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(3000)
        // After stop, isRunning should be false
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun pauseGameCallsControllerAndSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.pauseCallCount)
    }

    @Test
    fun resumeGameCallsControllerAndSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        vm.onIntent(EmulationIntent.ResumeGame)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.resumeCallCount)
    }

    @Test
    fun stopGameResetsRunningState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
    }

    @Test
    fun startGameFailureShowsError() = runTest {
        builder.libretroController.loadCoreShouldThrow = RuntimeException("Core load failed")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to start emulation"))
    }

    @Test
    fun startGameResetsSupportsSaveStatesToTrue() = runTest {
        // First game: core does NOT support save states
        builder.libretroController.supportsSaveStatesResult = false
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(4000) // Wait past the 3-second probe
        assertFalse(vm.state.value.supportsSaveStates, "First game should not support save states")

        // Stop the first game
        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        // Second game: core DOES support save states
        builder.libretroController.supportsSaveStatesResult = true
        vm.onIntent(EmulationIntent.StartGame("game1"))
        // Immediately after startGame, supportsSaveStates should be reset to true
        assertTrue(vm.state.value.supportsSaveStates, "supportsSaveStates should reset to true on new game start")
    }

    @Test
    fun startGameAutoCreatesSessionWhenNoneProvided() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        // Session should have been auto-created
        assertNotNull(vm.state.value.sessionId, "Session should be auto-created when none provided")
        assertEquals(1, builder.sessionRepository.createSessionCallCount)
        assertEquals("Default", builder.sessionRepository.lastCreatedSessionName)
    }

    @Test
    fun startGameReusesExistingSession() = runTest {
        builder.sessionRepository.existingSessions = listOf(
            com.spela.player.domain.model.GameSession(id = "existing-42", gameId = "game1", name = "My Save")
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        // Should reuse existing session, not create a new one
        assertEquals("existing-42", vm.state.value.sessionId)
        assertEquals(0, builder.sessionRepository.createSessionCallCount)
    }

    @Test
    fun startGameWithExplicitSessionIdDoesNotAutoCreate() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", sessionId = "explicit-session"))
        builder.advanceTimeBy(100)

        assertEquals("explicit-session", vm.state.value.sessionId)
        assertEquals(0, builder.sessionRepository.createSessionCallCount)
    }
}
