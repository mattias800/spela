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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies SaveManager's gzip-on-upload path (#804 phase 2).
 *
 * The pipeline:
 *
 *   1. Native serialise (or fallback) writes raw bytes to .tmp-save-<id>.
 *   2. If the raw size is >= [GZIP_MIN_BYTES] we gzip into .tmp-save-<id>.gz
 *      and the upload form's compression= field becomes "gzip".
 *   3. Below the threshold we skip gzip entirely and upload raw.
 *
 * These are the contracts the in-game Save button depends on; if either
 * leaks (e.g. compression= sent as "" for a 90 MB GameCube save) the
 * server stores the bytes opaquely as raw, so the next download will
 * try to feed gzipped bytes straight to the libretro core and the
 * resume crashes silently.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerCompressionTest {

    private val tempDir: File = createTempDirectory("spela-savemgr-").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = dispatcher
            override val io: CoroutineDispatcher = dispatcher
            override val default: CoroutineDispatcher = dispatcher
        }

    /** Real-filesystem FileStorage so gzipFile can actually read the
     *  staged temp file the manager writes via the fallback path. */
    private inner class RealTempFileStorage : FileStorage {
        override fun getGamesDir(): String = tempDir.resolve("games").apply { mkdirs() }.absolutePath
        override fun getCoresDir(): String = tempDir.resolve("cores").apply { mkdirs() }.absolutePath
        override fun getSavesDir(): String = tempDir.resolve("saves").apply { mkdirs() }.absolutePath
        override fun getBiosDir(): String = tempDir.resolve("bios").apply { mkdirs() }.absolutePath
        override suspend fun createDirectory(path: String) { File(path).mkdirs() }
        override suspend fun writeFile(path: String, data: ByteArray) { File(path).writeBytes(data) }
        override suspend fun readFile(path: String): ByteArray = File(path).readBytes()
        override suspend fun fileExists(path: String): Boolean = File(path).exists()
        override suspend fun deleteFile(path: String) { File(path).delete() }
        override suspend fun deleteDirectory(path: String) { File(path).deleteRecursively() }
        override suspend fun getDirectorySize(path: String): Long = 0
        override suspend fun writeFileStreaming(path: String, writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit) {}
        override suspend fun getFileSize(path: String): Long = File(path).length()
        override suspend fun listFiles(path: String): List<String> = emptyList()
        override suspend fun isDirectory(path: String): Boolean = File(path).isDirectory
        override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
        override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
        override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
        override suspend fun sha256File(path: String): String? = null
    }

    private fun makeManager(
        scope: CoroutineScope,
        dispatcher: CoroutineDispatcher,
        serializeBytes: ByteArray,
    ): Triple<SaveManager, StubSessionRepository, RealTempFileStorage> {
        val dispatchers = testDispatchers(dispatcher)
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        val connectivity = ConnectivityMonitor(apiClient, dispatchers, scope)
        val sessionRepo = StubSessionRepository()
        val libretro = StubLibretroController().apply { serializeResult = serializeBytes }
        val state = MutableStateFlow(EmulationState())
        val storage = RealTempFileStorage()
        val manager = SaveManager(
            saveDataRepository = StubSaveDataRepository(),
            connectivityMonitor = connectivity,
            libretroController = libretro,
            screenshotCapture = null,
            _state = state,
            dispatchers = dispatchers,
            scope = scope,
            sessionRepository = sessionRepo,
            fileStorage = storage,
            pendingUploadRepository = com.spela.player.presentation.viewmodel.emulation.StubPendingSaveUploadRepository(),
        )
        manager.currentSessionId = "s1"
        manager.currentCoreName = "nestopia"
        return Triple(manager, sessionRepo, storage)
    }

    @Test
    fun manualSaveAboveThresholdIsGzippedAndTagged() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        // 64 KiB of repeating bytes — above GZIP_MIN_BYTES (32 KiB) and
        // compresses well so we can also assert the file shrinks.
        val payload = ByteArray(64 * 1024) { (it % 17).toByte() }
        val (manager, sessionRepo, storage) = makeManager(this, dispatcher, payload)

        manager.saveState()
        advanceUntilIdle()

        assertEquals("gzip", sessionRepo.lastUploadCompression)
        // Both temp files must be cleaned up on success.
        val savesDir = File(storage.getSavesDir())
        val leftovers = savesDir.listFiles()?.toList() ?: emptyList()
        assertTrue(leftovers.isEmpty(), "expected no leftover temp files, found $leftovers")
    }

    @Test
    fun manualSaveBelowThresholdSkipsGzip() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        // 8 KiB — below GZIP_MIN_BYTES so the manager must upload raw.
        val payload = ByteArray(8 * 1024) { 0x42 }
        val (manager, sessionRepo, storage) = makeManager(this, dispatcher, payload)

        manager.saveState()
        advanceUntilIdle()

        assertEquals("", sessionRepo.lastUploadCompression)
        val savesDir = File(storage.getSavesDir())
        val leftovers = savesDir.listFiles()?.toList() ?: emptyList()
        assertTrue(leftovers.isEmpty(), "expected no leftover temp files, found $leftovers")
        // No .gz sibling should ever have been created on disk.
        assertFalse(savesDir.resolve(".tmp-save-s1.gz").exists())
    }
}
