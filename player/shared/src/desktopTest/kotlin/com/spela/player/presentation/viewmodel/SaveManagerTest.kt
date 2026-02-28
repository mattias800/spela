package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.ConnectionState
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.usecase.LoadGameStateUseCase
import com.spela.player.domain.usecase.SaveGameStateUseCase
import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.viewmodel.emulation.StubLibretroController
import com.spela.player.presentation.viewmodel.emulation.StubMockEngineFactory
import com.spela.player.presentation.viewmodel.emulation.StubSaveDataRepository
import com.spela.player.presentation.viewmodel.emulation.StubSaveRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SaveManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatchers = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
    }

    private lateinit var libretroController: StubLibretroController
    private lateinit var saveDataRepository: StubSaveDataRepository
    private lateinit var saveRepository: StubSaveRepository
    private lateinit var connectivityMonitor: ConnectivityMonitor

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        libretroController = StubLibretroController()
        saveDataRepository = StubSaveDataRepository()
        saveRepository = StubSaveRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSaveManager(scope: CoroutineScope): SaveManager {
        val apiClient = SpelaApiClient(StubMockEngineFactory, TokenManager())
        connectivityMonitor = ConnectivityMonitor(apiClient, testDispatchers, scope)
        return SaveManager(
            saveGameStateUseCase = SaveGameStateUseCase(saveRepository),
            loadGameStateUseCase = LoadGameStateUseCase(saveRepository),
            saveDataRepository = saveDataRepository,
            saveRepository = saveRepository,
            connectivityMonitor = connectivityMonitor,
            libretroController = libretroController,
            screenshotCapture = null,
            _state = MutableStateFlow(EmulationState()),
            dispatchers = testDispatchers,
            scope = scope,
        )
    }

    // ── saveSramOnStop: normal SRAM path ────────────────────────────────────

    @Test
    fun saveSramOnStopWithSramDataUsesNormalPath() = runTest(testDispatcher) {
        libretroController.getSRAMResult = byteArrayOf(1, 2, 3)
        val saveManager = createSaveManager(this)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.saveLocalSRAMCallCount)
        assertTrue(saveDataRepository.lastSavedSRAMData!!.contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(0, saveDataRepository.zipSaveDirectoryCallCount)
    }

    @Test
    fun saveSramOnStopWithSramDataUploadsWhenOnline() = runTest(testDispatcher) {
        libretroController.getSRAMResult = byteArrayOf(10, 20, 30)
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.uploadActiveSaveDataCallCount)
        assertEquals(0, saveDataRepository.zipSaveDirectoryCallCount)
    }

    // ── saveSramOnStop: directory-save fallback ─────────────────────────────

    @Test
    fun saveSramOnStopWithNullSramFallsBackToZipDirectory() = runTest(testDispatcher) {
        libretroController.getSRAMResult = null
        saveDataRepository.zipSaveDirectoryResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 99)
        val saveManager = createSaveManager(this)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.zipSaveDirectoryCallCount)
        assertEquals(1, saveDataRepository.saveLocalSRAMCallCount)
        assertTrue(saveDataRepository.lastSavedSRAMData!!.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 99)))
    }

    @Test
    fun saveSramOnStopWithEmptySramFallsBackToZipDirectory() = runTest(testDispatcher) {
        libretroController.getSRAMResult = byteArrayOf()
        saveDataRepository.zipSaveDirectoryResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2)
        val saveManager = createSaveManager(this)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.zipSaveDirectoryCallCount)
        assertEquals(1, saveDataRepository.saveLocalSRAMCallCount)
    }

    @Test
    fun saveSramOnStopFallbackUploadsWhenOnline() = runTest(testDispatcher) {
        libretroController.getSRAMResult = null
        saveDataRepository.zipSaveDirectoryResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 5)
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.uploadActiveSaveDataCallCount)
    }

    @Test
    fun saveSramOnStopFallbackSkipsUploadWhenOffline() = runTest(testDispatcher) {
        libretroController.getSRAMResult = null
        saveDataRepository.zipSaveDirectoryResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 5)
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.saveLocalSRAMCallCount)
        assertEquals(0, saveDataRepository.uploadActiveSaveDataCallCount)
    }

    @Test
    fun saveSramOnStopWithNullSramAndEmptyDirectoryDoesNothing() = runTest(testDispatcher) {
        libretroController.getSRAMResult = null
        saveDataRepository.zipSaveDirectoryResult = null
        val saveManager = createSaveManager(this)

        saveManager.saveSramOnStop("game1")

        assertEquals(1, saveDataRepository.zipSaveDirectoryCallCount)
        assertEquals(0, saveDataRepository.saveLocalSRAMCallCount)
        assertEquals(0, saveDataRepository.uploadActiveSaveDataCallCount)
    }

    // ── loadSramOnStart: normal SRAM path ───────────────────────────────────

    @Test
    fun loadSramOnStartWithLocalNormalDataCallsSetSRAM() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = byteArrayOf(1, 2, 3, 4)
        val saveManager = createSaveManager(this)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, libretroController.setSRAMCallCount)
        assertTrue(libretroController.lastSetSRAMData!!.contentEquals(byteArrayOf(1, 2, 3, 4)))
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
    }

    // ── loadSramOnStart: ZIP detection ──────────────────────────────────────

    @Test
    fun loadSramOnStartWithLocalZipDataCallsUnzipNotSetSRAM() = runTest(testDispatcher) {
        // ZIP magic bytes: PK\x03\x04
        saveDataRepository.loadLocalSRAMResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 10, 20, 30)
        val saveManager = createSaveManager(this)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, saveDataRepository.unzipToSaveDirectoryCallCount)
        assertTrue(saveDataRepository.lastUnzippedData!!.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 10, 20, 30)))
        assertEquals(0, libretroController.setSRAMCallCount)
    }

    @Test
    fun loadSramOnStartWithServerZipDataCallsUnzipNotSetSRAM() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = null
        val zipData = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 50, 60)
        saveDataRepository.downloadActiveSaveDataResult = Result.success(zipData)
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, saveDataRepository.downloadActiveSaveDataCallCount)
        assertEquals(1, saveDataRepository.unzipToSaveDirectoryCallCount)
        assertTrue(saveDataRepository.lastUnzippedData!!.contentEquals(zipData))
        assertEquals(0, libretroController.setSRAMCallCount)
        // Also caches locally
        assertEquals(1, saveDataRepository.saveLocalSRAMCallCount)
    }

    @Test
    fun loadSramOnStartWithServerNormalDataCallsSetSRAM() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = null
        val sramData = byteArrayOf(0x00, 0xFF.toByte(), 0x10, 0x20)
        saveDataRepository.downloadActiveSaveDataResult = Result.success(sramData)
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, saveDataRepository.downloadActiveSaveDataCallCount)
        assertEquals(1, libretroController.setSRAMCallCount)
        assertTrue(libretroController.lastSetSRAMData!!.contentEquals(sramData))
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
    }

    // ── loadSramOnStart: edge cases for ZIP detection ───────────────────────

    @Test
    fun loadSramOnStartWithTooShortDataDoesNotTreatAsZip() = runTest(testDispatcher) {
        // Only 3 bytes — too short for ZIP magic
        saveDataRepository.loadLocalSRAMResult = byteArrayOf(0x50, 0x4B, 0x03)
        val saveManager = createSaveManager(this)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, libretroController.setSRAMCallCount)
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
    }

    @Test
    fun loadSramOnStartWithPartialZipMagicDoesNotTreatAsZip() = runTest(testDispatcher) {
        // Starts with PK but wrong 3rd/4th byte
        saveDataRepository.loadLocalSRAMResult = byteArrayOf(0x50, 0x4B, 0x00, 0x00, 10, 20)
        val saveManager = createSaveManager(this)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, libretroController.setSRAMCallCount)
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
    }

    @Test
    fun loadSramOnStartWithExactlyFourZipMagicBytesIsTreatedAsZip() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val saveManager = createSaveManager(this)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, saveDataRepository.unzipToSaveDirectoryCallCount)
        assertEquals(0, libretroController.setSRAMCallCount)
    }

    // ── loadSramOnStart: no data available ──────────────────────────────────

    @Test
    fun loadSramOnStartWithNoLocalAndOfflineDoesNothing() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = null
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Offline)

        saveManager.loadSramOnStart("game1")

        assertEquals(0, libretroController.setSRAMCallCount)
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
        assertEquals(0, saveDataRepository.downloadActiveSaveDataCallCount)
    }

    @Test
    fun loadSramOnStartWithServerFailureDoesNotCrash() = runTest(testDispatcher) {
        saveDataRepository.loadLocalSRAMResult = null
        saveDataRepository.downloadActiveSaveDataResult = Result.failure(RuntimeException("Active save data download failed: HTTP 404"))
        val saveManager = createSaveManager(this)
        connectivityMonitor.forceConnectionState(ConnectionState.Online)

        saveManager.loadSramOnStart("game1")

        assertEquals(1, saveDataRepository.downloadActiveSaveDataCallCount)
        assertEquals(0, libretroController.setSRAMCallCount)
        assertEquals(0, saveDataRepository.unzipToSaveDirectoryCallCount)
    }
}
