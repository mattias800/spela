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

/**
 * Coverage for the user-supplied [SaveManager.saveState] name path
 * shipped in #830. Slot-primary stays the default for medium / large
 * tiers; this verifies the secondary "Save with name…" affordance
 * routes through the existing manual-save queue with the right
 * server-visible label.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerNamedSaveTest {

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }

    private fun makeFixture(scope: CoroutineScope, dispatcher: CoroutineDispatcher): Fixture {
        val dispatchers = testDispatchers(dispatcher)
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        val connectivity = ConnectivityMonitor(apiClient, dispatchers, scope)
        val sessionRepo = StubSessionRepository()
        val pending = StubPendingSaveUploadRepository()
        val state = MutableStateFlow(EmulationState())
        val manager = SaveManager(
            saveDataRepository = StubSaveDataRepository(),
            connectivityMonitor = connectivity,
            libretroController = StubLibretroController(),
            screenshotCapture = null,
            _state = state,
            dispatchers = dispatchers,
            scope = scope,
            sessionRepository = sessionRepo,
            fileStorage = TestFileStorage(),
            pendingUploadRepository = pending,
        )
        manager.currentSessionId = "session-1"
        manager.currentCoreName = "duckstation"
        return Fixture(manager, pending)
    }

    private data class Fixture(
        val manager: SaveManager,
        val pending: StubPendingSaveUploadRepository,
    )

    @Test
    fun saveStateWithUserNameStoresThatNameOnTheQueueRow() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.saveState(name = "Before final boss")
        advanceUntilIdle()

        val queued = fx.pending.enqueueLog
        assertEquals(1, queued.size, "exactly one row enqueued")
        assertEquals("Before final boss", queued.single().name)
    }

    @Test
    fun saveStateBlankNameFallsBackToManualSavePlaceholder() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        // Empty name should not produce a row labelled "" on the
        // server. We back-fill the historical placeholder so the row
        // is still searchable / displayable in session detail.
        fx.manager.saveState(name = "   ")
        advanceUntilIdle()

        val queued = fx.pending.enqueueLog
        assertEquals(1, queued.size)
        assertEquals("Manual Save", queued.single().name)
    }

    @Test
    fun saveStateWithoutNameKeepsHistoricalManualSaveLabel() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        // No-arg saveState() — used by the existing in-overlay Save
        // button on small-tier consoles. Backwards-compat check.
        fx.manager.saveState()
        advanceUntilIdle()

        val queued = fx.pending.enqueueLog
        assertEquals("Manual Save", queued.single().name)
    }

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
