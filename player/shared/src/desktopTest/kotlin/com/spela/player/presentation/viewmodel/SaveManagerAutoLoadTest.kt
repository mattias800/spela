package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.GameSession
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerAutoLoadTest {

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }

    private class AutoLoadFileStorage : FileStorage {
        override fun getGamesDir(): String = "/tmp/spela-autoload/games"
        override fun getCoresDir(): String = "/tmp/spela-autoload/cores"
        override fun getSavesDir(): String = "/tmp/spela-autoload/saves"
        override fun getBiosDir(): String = "/tmp/spela-autoload/bios"
        override suspend fun createDirectory(path: String) {}
        override suspend fun writeFile(path: String, data: ByteArray) {}
        override suspend fun readFile(path: String): ByteArray = ByteArray(0)
        override suspend fun fileExists(path: String): Boolean = true
        override suspend fun deleteFile(path: String) {}
        override suspend fun deleteDirectory(path: String) {}
        override suspend fun getDirectorySize(path: String): Long = 0
        override suspend fun writeFileStreaming(
            path: String,
            writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
        ) {}
        override suspend fun getFileSize(path: String): Long = 5049L
        override suspend fun listFiles(path: String): List<String> = emptyList()
        override suspend fun isDirectory(path: String): Boolean = false
        override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
        override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
        override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
        override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0L
        override suspend fun extractTarFile(tarPath: String, destDir: String) {}
        override suspend fun sha256File(path: String): String? = null
    }

    private fun makeManager(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        libretroController: StubLibretroController,
        sessionRepository: StubSessionRepository,
    ): SaveManager {
        val dispatchers = testDispatchers(dispatcher)
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        return SaveManager(
            saveDataRepository = StubSaveDataRepository(),
            connectivityMonitor = ConnectivityMonitor(apiClient, dispatchers, scope),
            libretroController = libretroController,
            screenshotCapture = null,
            _state = MutableStateFlow(EmulationState()),
            dispatchers = dispatchers,
            scope = scope,
            sessionRepository = sessionRepository,
            fileStorage = AutoLoadFileStorage(),
            pendingUploadRepository = StubPendingSaveUploadRepository(),
        )
    }

    @Test
    fun autoLoadReturnsErrorWhenCoreRejectsUnserializeFromFile() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val libretroController = StubLibretroController().apply {
            unserializeFromFileResult = false
        }
        val sessionRepository = StubSessionRepository().apply {
            existingSessions = listOf(
                GameSession(
                    id = "session-1",
                    gameId = "game1",
                    name = "Default",
                    coreName = "nestopia",
                ),
            )
            downloadSessionAutoSaveResult = Result.success(byteArrayOf(1, 2, 3))
        }
        val manager = makeManager(this, dispatcher, libretroController, sessionRepository).apply {
            currentSessionId = "session-1"
            currentCoreName = "nestopia"
        }

        val result = manager.autoLoadSaveState("game1")

        assertTrue(result is AutoLoadResult.Error)
        assertEquals(1, sessionRepository.downloadSessionAutoSaveCallCount)
        assertEquals(1, libretroController.unserializeFromFileCallCount)
    }
}
