package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.state.SaveSlotInfo
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for SaveManager.renameSlot / deleteSlot — the in-game slot
 * manage actions shipped in #831. Each action does an optimistic local
 * update + a background server call with rollback on failure; this
 * test pins both halves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerSlotManageTest {

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
        val state = MutableStateFlow(
            EmulationState(
                saveSlots = mapOf(
                    7 to SaveSlotInfo(
                        timestamp = "13:42",
                        isFilled = true,
                        saveId = "save-7",
                        name = "Pre-boss",
                    ),
                ),
            ),
        )
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
            pendingUploadRepository = StubPendingSaveUploadRepository(),
        )
        manager.currentSessionId = "session-1"
        manager.currentCoreName = "nestopia"
        return Fixture(manager, sessionRepo, state)
    }

    private data class Fixture(
        val manager: SaveManager,
        val sessionRepo: StubSessionRepository,
        val state: MutableStateFlow<EmulationState>,
    )

    @Test
    fun renameSlotOptimisticallyUpdatesAndCallsServer() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.renameSlot(slot = 7, name = "After boss")
        advanceUntilIdle()

        assertEquals("After boss", fx.state.value.saveSlots[7]?.name)
        assertEquals(
            Triple("session-1", "save-7", "After boss"),
            fx.sessionRepo.lastRenameCall,
        )
    }

    @Test
    fun renameSlotEmptyNameClearsLocalNameAndStillCallsServer() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.renameSlot(slot = 7, name = "")
        advanceUntilIdle()

        // Local convention: blank → null on the SaveSlotInfo so the
        // picker stops showing the user-supplied name.
        assertNull(fx.state.value.saveSlots[7]?.name)
        // Server call still happens — the user submitted the rename
        // form deliberately, even with an empty value.
        assertEquals(
            Triple("session-1", "save-7", ""),
            fx.sessionRepo.lastRenameCall,
        )
    }

    @Test
    fun renameSlotRollsBackOnServerFailure() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)
        fx.sessionRepo.renameFailure = RuntimeException("server 500")

        fx.manager.renameSlot(slot = 7, name = "Lost name")
        advanceUntilIdle()

        // Optimistic update reverted — the slot shows the original name.
        assertEquals("Pre-boss", fx.state.value.saveSlots[7]?.name)
    }

    @Test
    fun renameSlotIsNoOpForEmptyOrUnsavedSlot() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        // Empty slot 8 — no SaveSlotInfo entry at all.
        fx.manager.renameSlot(slot = 8, name = "Nope")
        advanceUntilIdle()

        assertNull(fx.sessionRepo.lastRenameCall, "no server call for an empty slot")

        // Filled slot but no saveId (e.g. legacy entry) — also a no-op.
        fx.state.update {
            it.copy(saveSlots = it.saveSlots + (9 to SaveSlotInfo(isFilled = true, saveId = null)))
        }
        fx.manager.renameSlot(slot = 9, name = "Nope")
        advanceUntilIdle()

        assertNull(fx.sessionRepo.lastRenameCall, "no server call when saveId is missing")
    }

    @Test
    fun deleteSlotOptimisticallyClearsAndCallsServer() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)

        fx.manager.deleteSlot(slot = 7)
        advanceUntilIdle()

        assertTrue(7 !in fx.state.value.saveSlots)
        assertEquals("session-1" to "save-7", fx.sessionRepo.lastDeleteCall)
    }

    @Test
    fun deleteSlotRollsBackOnServerFailure() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)
        fx.sessionRepo.deleteFailure = RuntimeException("server 500")

        fx.manager.deleteSlot(slot = 7)
        advanceUntilIdle()

        // Slot restored — picker re-renders the cell as filled again.
        val restored = fx.state.value.saveSlots[7]
        assertEquals("save-7", restored?.saveId)
        assertEquals("Pre-boss", restored?.name)
    }

    @Test
    fun deleteSlotIsNoOpWhenSaveIdMissing() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val fx = makeFixture(scope, dispatcher)
        fx.state.update {
            it.copy(
                saveSlots = it.saveSlots + (9 to SaveSlotInfo(isFilled = true, saveId = null)),
            )
        }

        fx.manager.deleteSlot(slot = 9)
        advanceUntilIdle()

        assertNull(fx.sessionRepo.lastDeleteCall)
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
        override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
        override suspend fun sha256File(path: String): String? = null
    }
}
