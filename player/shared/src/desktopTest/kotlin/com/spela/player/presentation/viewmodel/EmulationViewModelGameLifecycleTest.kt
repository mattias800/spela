package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubGameRepository
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.model.UserPreferences
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelGameLifecycleTest {

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

    @Test
    fun startGameSetsIsLoadingTrue() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        // Before any dispatching, isLoading should be set synchronously
        assertTrue(vm.state.value.isLoading)
        builder.tearDown()
    }

    @Test
    fun startGameSetsIsRunningTrueAfterLoad() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        builder.tearDown()
    }

    @Test
    fun startGameSetsGameIdInState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("my-game-42"))
        advanceTimeBy(100)
        assertEquals("my-game-42", vm.state.value.gameId)
        builder.tearDown()
    }

    @Test
    fun startGameFetchesGameDetail() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertEquals("Test Game", vm.state.value.gameTitle)
        assertEquals("nes", vm.state.value.consoleId)
        builder.tearDown()
    }

    @Test
    fun startGameLoadsUserPreferences() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(showPerformanceOverlay = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.showPerformanceOverlay)
        builder.tearDown()
    }

    @Test
    fun startGameResolvesShader() = runTest(testDispatcher) {
        builder.preferencesRepository.resolveShaderResult = ShaderPreset.CRT_SIMPLE
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertEquals(ShaderPreset.CRT_SIMPLE, vm.state.value.selectedShader)
        builder.tearDown()
    }

    @Test
    fun startGameCallsLoadCoreThenLoadGameThenStart() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertEquals(1, builder.libretroController.loadCoreCallCount)
        assertEquals(1, builder.libretroController.loadGameCallCount)
        assertEquals(1, builder.libretroController.startCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameDetectsDualScreenConsoleNds() = runTest(testDispatcher) {
        builder.gameRepository = StubGameRepository(consoleId = "nds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(192, vm.state.value.dualScreenSplitY)
        builder.tearDown()
    }

    @Test
    fun startGameDetectsDualScreenConsole3ds() = runTest(testDispatcher) {
        builder.gameRepository = StubGameRepository(consoleId = "3ds")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isDualScreenConsole)
        assertEquals(240, vm.state.value.dualScreenSplitY)
        builder.tearDown()
    }

    @Test
    fun startGameSetsSessionElapsedSecondsToZero() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertEquals(0, vm.state.value.sessionElapsedSeconds)
        builder.tearDown()
    }

    @Test
    fun sessionTimerIncrementsEverySecond() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        // Advance 3 seconds
        advanceTimeBy(3000)
        assertTrue(vm.state.value.sessionElapsedSeconds >= 3)
        builder.tearDown()
    }

    @Test
    fun sessionTimerStopsOnStopGame() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        advanceTimeBy(2000)
        val elapsed = vm.state.value.sessionElapsedSeconds
        assertTrue(elapsed >= 2)
        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(3000)
        // After stop, isRunning should be false
        assertFalse(vm.state.value.isRunning)
        builder.tearDown()
    }

    @Test
    fun pauseGameCallsControllerAndSetsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.pauseCallCount)
        builder.tearDown()
    }

    @Test
    fun resumeGameCallsControllerAndSetsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        vm.onIntent(EmulationIntent.PauseGame)
        assertTrue(vm.state.value.isPaused)
        vm.onIntent(EmulationIntent.ResumeGame)
        assertFalse(vm.state.value.isPaused)
        assertEquals(1, builder.libretroController.resumeCallCount)
        builder.tearDown()
    }

    @Test
    fun stopGameResetsRunningState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isPaused)
        builder.tearDown()
    }

    @Test
    fun startGameFailureShowsError() = runTest(testDispatcher) {
        builder.libretroController.loadCoreShouldThrow = RuntimeException("Core load failed")
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to start emulation"))
        builder.tearDown()
    }
}
