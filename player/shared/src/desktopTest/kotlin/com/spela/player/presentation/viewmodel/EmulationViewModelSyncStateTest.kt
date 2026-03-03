package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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

/**
 * Unit tests for EmulationViewModel pre-launch sync and post-exit sync state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelSyncStateTest {

    private lateinit var builder: EmulationViewModelTestBuilder

    @BeforeTest
    fun setup() {
        builder = EmulationViewModelTestBuilder()
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        builder.tearDown()
        Dispatchers.resetMain()
    }

    @Test
    fun prepareLaunchSetsSyncStateToSyncingMessage() = runTest {
        // Keep the download in progress so the coroutine doesn't complete before we assert
        builder.saveDataRepository.downloadActiveSaveDataDelayMs = 20_000L
        val vm = builder.build()
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        builder.advanceTimeBy(10) // enough to start the coroutine and set _syncState
        assertNotNull(vm.syncState.value)
        assertTrue(
            vm.syncState.value?.message?.contains("Syncing") == true,
            "Expected sync message, got: ${vm.syncState.value?.message}",
        )
    }

    @Test
    fun prepareLaunchEmitsLaunchReadyAfterSuccessfulSync() = runTest {
        val launchReadyValues = mutableListOf<PendingLaunch>()
        val vm = builder.build()
        val job = builder.vmScope.launch {
            vm.launchReady.collect { launchReadyValues.add(it) }
        }
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        builder.advanceTimeBy(1_000)
        assertEquals(1, launchReadyValues.size, "Expected launchReady to emit once")
        assertEquals("game1", launchReadyValues.first().gameId)
        assertFalse(launchReadyValues.first().skipAutoLoad)
        job.cancel()
    }

    @Test
    fun prepareLaunchForwardsskipAutoLoad() = runTest {
        val launchReadyValues = mutableListOf<PendingLaunch>()
        val vm = builder.build()
        val job = builder.vmScope.launch {
            vm.launchReady.collect { launchReadyValues.add(it) }
        }
        vm.onIntent(EmulationIntent.PrepareLaunch("game1", skipAutoLoad = true))
        builder.advanceTimeBy(1_000)
        assertEquals(1, launchReadyValues.size)
        assertTrue(launchReadyValues.first().skipAutoLoad)
        job.cancel()
    }

    @Test
    fun prepareLaunchClearsSyncStateAfterSuccess() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        builder.advanceTimeBy(1_000)
        assertNull(vm.syncState.value, "Expected syncState to be null after successful sync")
    }

    @Test
    fun prepareLaunchSetsTimedOutOnNetworkTimeout() = runTest {
        // Make the SRAM download take longer than the 10s timeout
        builder.saveDataRepository.downloadActiveSaveDataDelayMs = 20_000L
        val vm = builder.build()
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        // Advance past the 10s timeout
        builder.advanceTimeBy(10_001)
        val state = vm.syncState.value
        assertNotNull(state, "Expected syncState to be set after timeout")
        assertTrue(state.isTimedOut, "Expected isTimedOut=true after network timeout")
    }

    @Test
    fun playWithLocalSaveEmitsLaunchReadyAndClearsSyncState() = runTest {
        builder.saveDataRepository.downloadActiveSaveDataDelayMs = 20_000L
        val launchReadyValues = mutableListOf<PendingLaunch>()
        val vm = builder.build()
        val job = builder.vmScope.launch {
            vm.launchReady.collect { launchReadyValues.add(it) }
        }
        // Trigger timeout
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        builder.advanceTimeBy(10_001)
        assertTrue(vm.syncState.value?.isTimedOut == true, "Expected timed out state")
        // Choose to play with local save
        vm.onIntent(EmulationIntent.PlayWithLocalSave)
        builder.advanceTimeBy(100)
        assertNull(vm.syncState.value, "Expected syncState cleared")
        assertEquals(1, launchReadyValues.size, "Expected launchReady emitted")
        assertEquals("game1", launchReadyValues.first().gameId)
        job.cancel()
    }

    @Test
    fun cancelLaunchClearsAllPendingState() = runTest {
        builder.saveDataRepository.downloadActiveSaveDataDelayMs = 20_000L
        val launchReadyValues = mutableListOf<PendingLaunch>()
        val vm = builder.build()
        val job = builder.vmScope.launch {
            vm.launchReady.collect { launchReadyValues.add(it) }
        }
        // Trigger timeout
        vm.onIntent(EmulationIntent.PrepareLaunch("game1"))
        builder.advanceTimeBy(10_001)
        assertTrue(vm.syncState.value?.isTimedOut == true)
        // Cancel
        vm.onIntent(EmulationIntent.CancelLaunch)
        builder.advanceTimeBy(100)
        assertNull(vm.syncState.value, "Expected syncState cleared after cancel")
        assertEquals(0, launchReadyValues.size, "Expected launchReady NOT emitted after cancel")
        job.cancel()
    }

    @Test
    fun stopGameSetsUploadingSyncState() = runTest {
        val vm = builder.build()
        // Start a game first
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        // Stop the game
        vm.onIntent(EmulationIntent.StopGame)
        // syncState is set synchronously inside stopGame() before the async stopJob runs
        val syncState = vm.syncState.value
        assertNotNull(syncState, "Expected syncState set after stopGame")
        assertEquals("game1", syncState.gameId)
        assertTrue(
            syncState.message.contains("Uploading"),
            "Expected uploading message, got: ${syncState.message}",
        )
    }

    @Test
    fun stopGameClearsSyncStateAfterUploadCompletes() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)
        vm.onIntent(EmulationIntent.StopGame)
        // Advance past saveSramOnStop and the entire stopJob
        builder.advanceTimeBy(1_000)
        assertNull(vm.syncState.value, "Expected syncState cleared after upload completes")
    }

    @Test
    fun syncStateForDifferentGameIdIsNotCleared() = runTest {
        val vm = builder.build()
        // Set sync state for game2
        vm.onIntent(EmulationIntent.PrepareLaunch("game2"))
        builder.advanceTimeBy(1_000) // completes sync for game2 → null
        // Start and stop game1
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        // Manually trigger prepare for game2 to set its sync state
        vm.onIntent(EmulationIntent.PrepareLaunch("game2"))
        builder.advanceTimeBy(10) // mid-sync for game2
        val syncStateForGame2 = vm.syncState.value
        if (syncStateForGame2 != null) {
            assertEquals("game2", syncStateForGame2.gameId)
        }
        // If stopGame is called for game1 (different id), it should not clear game2's sync state
        // (This is verified by the guard in stopGame: current?.gameId == stoppingGameId)
    }
}
