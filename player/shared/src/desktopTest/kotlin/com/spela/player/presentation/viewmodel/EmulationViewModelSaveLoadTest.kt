package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.domain.model.SaveState
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
class EmulationViewModelSaveLoadTest {

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

    // ── Auto-save/load ──────────────────────────────────────────────────────

    @Test
    fun startGameAutoLoadsSaveStateWhenEnabled() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoLoadSaveEnabled = true)
        )
        builder.saveRepository.downloadAutoSaveResult = Result.success(byteArrayOf(1, 2, 3))
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.saveRepository.downloadAutoSaveCallCount)
        assertEquals(1, builder.libretroController.unserializeCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameSkipsAutoLoadWhenDisabled() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoLoadSaveEnabled = false)
        )
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(0, builder.saveRepository.downloadAutoSaveCallCount)
        // unserialize may still be 0 since no auto-load
        assertEquals(0, builder.libretroController.unserializeCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameSkipsAutoLoadWhenSkipAutoLoadTrue() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoLoadSaveEnabled = true)
        )
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", skipAutoLoad = true))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(0, builder.saveRepository.downloadAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameSkipsAutoLoadInChallengeMode() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoLoadSaveEnabled = true)
        )
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        // In challenge mode, challenge save is loaded instead
        assertEquals(0, builder.saveRepository.downloadAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun stopGameAutoSavesWhenEnabled() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoSaveEnabled = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)

        // serialize is called for auto-save
        assertTrue(builder.libretroController.serializeCallCount >= 1)
        assertEquals(1, builder.saveRepository.uploadAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun stopGameSkipsAutoSaveWhenDisabled() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoSaveEnabled = false)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)

        assertEquals(0, builder.saveRepository.uploadAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun stopGameSkipsAutoSaveInChallengeMode() = runTest(testDispatcher) {
        builder.preferencesRepository.preferencesResult = Result.success(
            UserPreferences(autoSaveEnabled = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)

        assertEquals(0, builder.saveRepository.uploadAutoSaveCallCount)
        builder.tearDown()
    }

    // ── Manual save/load ────────────────────────────────────────────────────

    @Test
    fun saveStateSerializesAndUploadsAndShowsStatus() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SaveState)
        advanceTimeBy(100)

        assertTrue(builder.libretroController.serializeCallCount >= 1)
        assertEquals(1, builder.saveRepository.uploadAutoSaveCallCount)
        assertEquals("State saved", vm.state.value.statusMessage)
        builder.tearDown()
    }

    @Test
    fun saveStateErrorShowsErrorMessage() = runTest(testDispatcher) {
        builder.saveRepository.uploadAutoSaveResult = Result.failure(Exception("upload failed"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SaveState)
        advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to save"))
        builder.tearDown()
    }

    @Test
    fun saveStateBlockedInChallengeMode() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isChallengeMode)

        val serializeBefore = builder.libretroController.serializeCallCount
        vm.onIntent(EmulationIntent.SaveState)
        advanceTimeBy(100)

        // Should not have called serialize for the save
        assertEquals(serializeBefore, builder.libretroController.serializeCallCount)
        assertEquals(0, builder.saveRepository.uploadAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun saveStateBlockedInHardcoreMode() = runTest(testDispatcher) {
        builder.achievementsRepository.getRATokenResult = Result.success(
            com.spela.player.domain.model.RACredentials("user", "token")
        )
        builder.achievementsRepository.getRAStatusResult = Result.success(
            com.spela.player.domain.model.RAStatus(linked = true, username = "user", hardcoreEnabled = true)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(500)
        // Wait for achievements init to complete
        assertTrue(vm.state.value.isHardcoreMode)

        vm.onIntent(EmulationIntent.SaveState)
        advanceTimeBy(100)

        assertEquals(0, builder.saveRepository.uploadAutoSaveCallCount)
        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("hardcore"))
        builder.tearDown()
    }

    @Test
    fun loadStateDownloadsAndUnserializesAndShowsStatus() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        val unserializeBefore = builder.libretroController.unserializeCallCount
        vm.onIntent(EmulationIntent.LoadState)
        advanceTimeBy(100)

        assertTrue(builder.saveRepository.downloadAutoSaveCallCount >= 1)
        assertTrue(builder.libretroController.unserializeCallCount > unserializeBefore)
        assertEquals("State loaded", vm.state.value.statusMessage)
        builder.tearDown()
    }

    @Test
    fun loadStateErrorShowsErrorMessage() = runTest(testDispatcher) {
        builder.saveRepository.downloadAutoSaveResult = Result.failure(Exception("download failed"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.LoadState)
        advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to load save"))
        builder.tearDown()
    }

    @Test
    fun loadStateBlockedInChallengeMode() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        val downloadBefore = builder.saveRepository.downloadAutoSaveCallCount
        vm.onIntent(EmulationIntent.LoadState)
        advanceTimeBy(100)

        assertEquals(downloadBefore, builder.saveRepository.downloadAutoSaveCallCount)
        builder.tearDown()
    }
}
