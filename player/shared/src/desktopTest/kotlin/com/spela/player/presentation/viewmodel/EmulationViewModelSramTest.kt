package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.intent.EmulationIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelSramTest {

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
    fun startGameLoadsLocalSramFirst() = runTest {
        val sramData = byteArrayOf(10, 20, 30)
        builder.saveDataRepository.loadLocalSRAMResult = sramData
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.saveDataRepository.loadLocalSRAMCallCount)
        assertEquals(1, builder.libretroController.setSRAMCallCount)
        assertTrue(builder.libretroController.lastSetSRAMData.contentEquals(sramData))
        // Should not have tried server download since local was available
        assertEquals(0, builder.saveDataRepository.downloadActiveSaveDataCallCount)
    }

    @Test
    fun startGameDownloadsSramFromServerWhenLocalIsNull() = runTest {
        builder.saveDataRepository.loadLocalSRAMResult = null
        val serverSram = byteArrayOf(40, 50, 60)
        builder.saveDataRepository.downloadActiveSaveDataResult = Result.success(serverSram)
        val vm = builder.build()
        // Connectivity monitor defaults to online

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.saveDataRepository.loadLocalSRAMCallCount)
        assertEquals(1, builder.saveDataRepository.downloadActiveSaveDataCallCount)
        // Should have set the SRAM from server data
        assertEquals(1, builder.libretroController.setSRAMCallCount)
        assertTrue(builder.libretroController.lastSetSRAMData.contentEquals(serverSram))
        // Should have cached locally
        assertEquals(1, builder.saveDataRepository.saveLocalSRAMCallCount)
    }

    @Test
    fun startGameSkipsSramDownloadWhenOffline() = runTest {
        builder.saveDataRepository.loadLocalSRAMResult = null
        val vm = builder.build()
        builder.connectivityMonitor.forceConnectionState(com.spela.player.data.remote.ConnectionState.Offline)

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.saveDataRepository.loadLocalSRAMCallCount)
        // Should not attempt server download when offline
        assertEquals(0, builder.saveDataRepository.downloadActiveSaveDataCallCount)
        assertEquals(0, builder.libretroController.setSRAMCallCount)
    }

    @Test
    fun stopGameSavesSramLocally() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)

        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        // getSRAM is called during stop
        assertTrue(builder.libretroController.getSRAMCallCount >= 1)
        // SRAM should be saved locally
        assertTrue(builder.saveDataRepository.saveLocalSRAMCallCount >= 1)
    }

    @Test
    fun stopGameUploadsSramWhenOnline() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        // connectivityMonitor defaults to online

        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        assertTrue(builder.saveDataRepository.uploadActiveSaveDataCallCount >= 1)
    }

    @Test
    fun stopGameSkipsSramUploadWhenOffline() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        builder.connectivityMonitor.forceConnectionState(com.spela.player.data.remote.ConnectionState.Offline)

        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        // SRAM should be saved locally
        assertTrue(builder.saveDataRepository.saveLocalSRAMCallCount >= 1)
        // But not uploaded
        assertEquals(0, builder.saveDataRepository.uploadActiveSaveDataCallCount)
    }
}
