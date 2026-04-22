package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.viewmodel.emulation.StubLibretroController
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import com.spela.player.presentation.viewmodel.emulation.StubSaveDataRepository
import com.spela.player.presentation.viewmodel.emulation.StubSessionRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Rehearsal-mode coverage for SaveManager (#672). Pins the contract:
 *
 *   - With `rehearsalMode = false` every save path uploads as normal.
 *   - With `rehearsalMode = true` every save path silently drops the
 *     upload and emits exactly one `RehearsalSaveBlocked` with the
 *     right kind, so the UI can show the rehearsal snackbar.
 *
 * The sealed-class + SharedFlow surface is what the decision UI will
 * subscribe to — if this contract slips, the "Try with my save" flow
 * can write saves the user thought were paused.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerRehearsalTest {

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }

    private fun makeManager(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): Triple<SaveManager, StubSessionRepository, StubLibretroController> {
        val dispatchers = testDispatchers(dispatcher)
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        val connectivity = ConnectivityMonitor(apiClient, dispatchers, scope)
        val sessionRepo = StubSessionRepository()
        val libretro = StubLibretroController()
        val state = MutableStateFlow(EmulationState())
        val manager = SaveManager(
            saveDataRepository = StubSaveDataRepository(),
            connectivityMonitor = connectivity,
            libretroController = libretro,
            screenshotCapture = null,
            _state = state,
            dispatchers = dispatchers,
            scope = scope,
            sessionRepository = sessionRepo,
        )
        manager.currentSessionId = "s1"
        manager.currentCoreName = "nestopia"
        return Triple(manager, sessionRepo, libretro)
    }

    @Test
    fun saveStateUploadsWhenNotRehearsing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, sessionRepo, _) = makeManager(scope, dispatcher)

        manager.rehearsalMode = false
        manager.saveState()
        advanceUntilIdle()

        assertEquals(1, sessionRepo.uploadSessionSaveCallCount)
    }

    @Test
    fun saveStateDropsAndEmitsRehearsalBlockedWhenRehearsing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, sessionRepo, _) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true

        val received = mutableListOf<RehearsalSaveBlocked>()
        val collectJob = scope.launch {
            manager.rehearsalSaveBlocked.collect { received += it }
        }
        advanceUntilIdle()

        manager.saveState()
        advanceUntilIdle()

        assertEquals(0, sessionRepo.uploadSessionSaveCallCount,
            "manual save must NOT upload while rehearsing")
        assertEquals(1, received.size)
        assertEquals(RehearsalSaveKind.Manual, received[0].kind)

        collectJob.cancel()
    }

    @Test
    fun saveToSlotDropsAndEmitsRehearsalBlockedWhenRehearsing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, _, _) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true
        val received = mutableListOf<RehearsalSaveBlocked>()
        val collectJob = scope.launch {
            manager.rehearsalSaveBlocked.collect { received += it }
        }
        advanceUntilIdle()

        manager.saveToSlot(3)
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertEquals(RehearsalSaveKind.Slot, received[0].kind)

        collectJob.cancel()
    }

    @Test
    fun autoSaveOnStopDropsAndEmitsRehearsalBlockedWhenRehearsing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, sessionRepo, libretro) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true
        val received = mutableListOf<RehearsalSaveBlocked>()
        val collectJob = scope.launch {
            manager.rehearsalSaveBlocked.collect { received += it }
        }
        advanceUntilIdle()

        manager.autoSaveOnStop("g1")

        assertEquals(0, sessionRepo.uploadSessionAutoSaveCallCount,
            "auto-save must NOT upload while rehearsing")
        assertEquals(0, libretro.serializeCallCount,
            "rehearsal mode must skip libretroController.serialize()")
        assertEquals(1, received.size)
        assertEquals(RehearsalSaveKind.Auto, received[0].kind)

        collectJob.cancel()
    }

    @Test
    fun saveSramOnStopDropsAndEmitsRehearsalBlockedWhenRehearsing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, sessionRepo, libretro) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true
        val received = mutableListOf<RehearsalSaveBlocked>()
        val collectJob = scope.launch {
            manager.rehearsalSaveBlocked.collect { received += it }
        }
        advanceUntilIdle()

        manager.saveSramOnStop("g1")

        assertEquals(0, sessionRepo.uploadSessionSramCallCount,
            "SRAM flush must NOT upload while rehearsing")
        assertEquals(0, libretro.getSRAMCallCount,
            "rehearsal mode must skip libretroController.getSRAM()")
        assertEquals(1, received.size)
        assertEquals(RehearsalSaveKind.Sram, received[0].kind)

        collectJob.cancel()
    }

    @Test
    fun togglingRehearsalModeOffReenablesUploads() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, sessionRepo, _) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true
        manager.saveState()
        advanceUntilIdle()
        assertEquals(0, sessionRepo.uploadSessionSaveCallCount)

        manager.rehearsalMode = false
        manager.saveState()
        advanceUntilIdle()
        assertEquals(1, sessionRepo.uploadSessionSaveCallCount,
            "upload must resume after rehearsal mode is cleared")
    }

    @Test
    fun rehearsalModeEmitsOneEventPerBlockedSave() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val (manager, _, _) = makeManager(scope, dispatcher)

        manager.rehearsalMode = true
        val received = mutableListOf<RehearsalSaveBlocked>()
        val collectJob = scope.launch {
            manager.rehearsalSaveBlocked.collect { received += it }
        }
        advanceUntilIdle()

        manager.saveState()
        manager.saveState()
        manager.saveState()
        advanceUntilIdle()

        assertEquals(3, received.size, "every blocked save must emit its own event")
        assertTrue(received.all { it.kind == RehearsalSaveKind.Manual })

        collectJob.cancel()
    }
}
