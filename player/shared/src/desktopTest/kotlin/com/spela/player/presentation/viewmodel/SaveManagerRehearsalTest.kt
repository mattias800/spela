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
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

    /** Bag of test fixtures returned by [makeManager]. Adds [state] so
     *  tests can assert transitions on EmulationState fields the
     *  manager mutates (e.g. isSaveInProgress, saveStateError). */
    private data class ManagerFixture(
        val manager: SaveManager,
        val sessionRepo: StubSessionRepository,
        val libretro: StubLibretroController,
        val state: MutableStateFlow<EmulationState>,
    )

    private fun makeManagerFixture(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): ManagerFixture {
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
            fileStorage = TestFileStorage(),
            pendingUploadRepository = com.spela.player.presentation.viewmodel.emulation.StubPendingSaveUploadRepository(),
        )
        manager.currentSessionId = "s1"
        manager.currentCoreName = "nestopia"
        return ManagerFixture(manager, sessionRepo, libretro, state)
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
            fileStorage = TestFileStorage(),
            pendingUploadRepository = com.spela.player.presentation.viewmodel.emulation.StubPendingSaveUploadRepository(),
        )
        manager.currentSessionId = "s1"
        manager.currentCoreName = "nestopia"
        return Triple(manager, sessionRepo, libretro)
    }

    /** Minimal in-test FileStorage. SaveManager reads from this only when
     *  the libretro controller's native fast path produced a temp file —
     *  the stub controller never does, so these methods are unreached in
     *  practice but must exist to satisfy the interface. */
    private class TestFileStorage : FileStorage {
        override fun getGamesDir(): String = "/tmp/games"
        override fun getCoresDir(): String = "/tmp/cores"
        override fun getSavesDir(): String = "/tmp/saves"
        override fun getBiosDir(): String = "/tmp/bios"
        override suspend fun createDirectory(path: String) {}
        override suspend fun writeFile(path: String, data: ByteArray) {}
        override suspend fun readFile(path: String): ByteArray = byteArrayOf()
        override suspend fun fileExists(path: String): Boolean = false
        override suspend fun deleteFile(path: String) {}
        override suspend fun deleteDirectory(path: String) {}
        override suspend fun getDirectorySize(path: String): Long = 0
        override suspend fun writeFileStreaming(path: String, writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit) {}
        override suspend fun getFileSize(path: String): Long = 0
        override suspend fun listFiles(path: String): List<String> = emptyList()
        override suspend fun isDirectory(path: String): Boolean = false
        override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
        override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
        override suspend fun sha256File(path: String): String? = null
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

    /** #803 — the in-game overlay's Save button needs feedback even
     *  if the user dismisses the overlay before the success toast
     *  renders. SaveManager must publish an in-progress flag while
     *  the upload is in flight, and clear it before the success path
     *  runs. */
    @Test
    fun saveStateClearsInProgressOnSuccess() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeManagerFixture(scope, dispatcher)

        fx.manager.rehearsalMode = false
        fx.manager.saveState()
        advanceUntilIdle()

        assertEquals(false, fx.state.value.isSaveInProgress,
            "isSaveInProgress should be false after a successful save")
        assertEquals(null, fx.state.value.saveStateError,
            "saveStateError should be null after success")
    }

    /** After a successful save the "Saved" flag flashes true and then
     *  auto-clears so the in-game overlay's checkmark disappears
     *  cleanly (#803). */
    @Test
    fun saveStateFlashesSuccessAndAutoClears() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeManagerFixture(scope, dispatcher)

        fx.manager.rehearsalMode = false
        fx.manager.saveState()
        // Settle the upload but don't yet pass the auto-clear delay.
        // (UnconfinedTestDispatcher runs everything inline; here we
        // verify the flag is true between settle and auto-clear by
        // checking right after advanceUntilIdle which blocks on the
        // launched timer too — so we instead advance time slightly
        // less than the flash window.)
        testScheduler.advanceTimeBy(1_400)
        testScheduler.runCurrent()
        assertEquals(true, fx.state.value.saveStateJustSucceeded,
            "saveStateJustSucceeded should still be true within the flash window")

        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()
        assertEquals(false, fx.state.value.saveStateJustSucceeded,
            "saveStateJustSucceeded should auto-clear after the flash window")
    }

    /** Deferred-sync contract (#804 phase 6): a failed upload no
     *  longer surfaces saveStateError directly — the row stays in
     *  the persistent queue and is retried opportunistically. The
     *  user sees "Saved locally · syncing" until a future drain
     *  succeeds. Slice 4 of phase 6 will add an explicit indicator
     *  after N failed retries; for slice 2 the contract is "no
     *  immediate error UI, save is captured."
     */
    @Test
    fun saveStateOnUploadFailureKeepsRowQueuedWithoutSurfacingError() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeManagerFixture(scope, dispatcher)

        // Configure the stub to fail the upload.
        fx.sessionRepo.uploadSessionSaveResult = Result.failure(RuntimeException("test: 413 too large"))

        fx.manager.rehearsalMode = false
        fx.manager.saveState()
        advanceUntilIdle()

        assertEquals(false, fx.state.value.isSaveInProgress,
            "isSaveInProgress should be false after the enqueue completes")
        assertTrue(fx.state.value.hasPendingUploads,
            "hasPendingUploads must stay true while the row sits in the queue with a retry counter")
        assertEquals(null, fx.state.value.saveStateError,
            "deferred-sync failures stay in the queue rather than surfacing as saveStateError")
    }

    /** Regression: if staging the save state throws (rather than
     *  returns null), the in-progress flag must still reach false —
     *  otherwise the Save button is stuck on "Saving…" for the rest
     *  of the session. The inner runCatching in stageSaveToTempFile
     *  only covers the file write, not the controller / FileStorage
     *  calls around it, so this is a real path. */
    @Test
    fun saveStateClearsInProgressWhenStagingThrows() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeManagerFixture(scope, dispatcher)

        fx.libretro.serializeThrows = true
        fx.manager.rehearsalMode = false
        fx.manager.saveState()
        advanceUntilIdle()

        assertEquals(false, fx.state.value.isSaveInProgress,
            "isSaveInProgress must reach false even when staging throws")
        assertTrue(
            fx.state.value.saveStateError?.contains("native serialize threw") == true,
            "saveStateError should carry the staging exception; got '${fx.state.value.saveStateError}'",
        )
    }

    /** A new save attempt clears any prior failure so the button isn't
     *  stuck in "Save failed" state forever. */
    @Test
    fun newSaveAttemptClearsPriorError() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeManagerFixture(scope, dispatcher)

        // Seed the state with a failure from a prior attempt.
        fx.state.update { it.copy(saveStateError = "stale failure") }

        fx.manager.rehearsalMode = false
        fx.manager.saveState()
        advanceUntilIdle()

        assertEquals(null, fx.state.value.saveStateError,
            "starting a new save attempt must clear any prior error")
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
