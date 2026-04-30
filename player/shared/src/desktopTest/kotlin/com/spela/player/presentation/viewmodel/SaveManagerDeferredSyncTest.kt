package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.viewmodel.emulation.StubLibretroController
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import com.spela.player.presentation.viewmodel.emulation.StubPendingSaveUploadRepository
import com.spela.player.presentation.viewmodel.emulation.StubSaveDataRepository
import com.spela.player.presentation.viewmodel.emulation.StubSessionRepository
import com.spela.player.util.DispatcherProvider
import com.spela.player.util.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Positive coverage for the deferred-sync flow added in #804 phase 6
 * slice 2. Companion to [SaveManagerRehearsalTest], which covers the
 * failure path (row stays queued, no error UI). These tests verify
 * the happy path: enqueue → drain → queue empty → hasPendingUploads
 * flips back to false.
 *
 * The user-visible contract is "Save is fast and tells me whether
 * the bytes have left the device" — that's what we pin here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerDeferredSyncTest {

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }

    private fun makeFixture(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
    ): Fixture {
        val dispatchers = testDispatchers(dispatcher)
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        val connectivity = ConnectivityMonitor(apiClient, dispatchers, scope)
        val sessionRepo = StubSessionRepository()
        val libretro = StubLibretroController()
        val state = MutableStateFlow(EmulationState())
        val pending = StubPendingSaveUploadRepository()
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
            pendingUploadRepository = pending,
        )
        manager.currentSessionId = "s1"
        manager.currentCoreName = "nestopia"
        return Fixture(manager, sessionRepo, state, pending)
    }

    private data class Fixture(
        val manager: SaveManager,
        val sessionRepo: StubSessionRepository,
        val state: MutableStateFlow<EmulationState>,
        val pending: StubPendingSaveUploadRepository,
    )

    @Test
    fun saveStateEnqueuesRowAndFlipsHasPendingUploads() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.saveState()
        advanceUntilIdle()

        // Successful drain: queue ends empty (sessionRepo defaults
        // to Result.success on the manual upload path), and the
        // hasPendingUploads flag has cycled back to false. We don't
        // assert saveStateJustSucceeded here because its 1.5 s
        // auto-clear timer also fires under advanceUntilIdle, putting
        // it back at false; that transient flash is covered by the
        // existing #803 tests in SaveManagerRehearsalTest.
        assertEquals(0L, fx.pending.count())
        assertFalse(fx.state.value.isSaveInProgress)
        assertFalse(fx.state.value.hasPendingUploads)
    }

    @Test
    fun saveStateRoutesUploadThroughManualEndpoint() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.saveState()
        advanceUntilIdle()

        // The drain hits uploadSessionSaveFromFile (not auto / slot)
        // for a manual save. The session repo stub increments its
        // counter on every call regardless of fold result.
        assertEquals(1, fx.sessionRepo.uploadSessionSaveCallCount)
    }

    @Test
    fun saveStateOnUploadFailureLeavesRowInQueueWithLastError() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)
        fx.sessionRepo.uploadSessionSaveResult =
            Result.failure(RuntimeException("test: 503 unavailable"))

        fx.manager.saveState()
        advanceUntilIdle()

        // Row remains in the queue with retry counter bumped + error
        // recorded; user sees "Saved locally · syncing" indefinitely
        // until a future drain succeeds.
        assertEquals(1L, fx.pending.count())
        val row = fx.pending.getAll().single()
        assertEquals(1, row.retryCount, "markRetry incremented the counter once")
        assertTrue(row.lastError?.contains("503 unavailable") == true,
            "lastError captures the upload failure for diagnostics")
        assertTrue(fx.state.value.hasPendingUploads)
    }

    /** Minimal FileStorage that no-ops everything — the staging path
     *  in tests goes through `serialize() + writeFile`, both of which
     *  are no-ops on the stub controller, so the staged "file" is
     *  fictional. The deferred-upload drain happens on the same
     *  fiction. */
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
}
