package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubBiosRepository
import com.spela.player.domain.model.BiosMissingFile
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelRelayAndBiosTest {

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

    // ── Relay mode ──────────────────────────────────────────────────────────

    @Test
    fun startGameWithRelayDownloadsAutoSave() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", relayId = "relay-1", turnToken = "token-1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.relayRepository.downloadRelayAutoSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameWithRelayUnserializesAutoSave() = runTest(testDispatcher) {
        val saveData = byteArrayOf(77, 88, 99)
        builder.relayRepository.downloadRelayAutoSaveResult = Result.success(saveData)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", relayId = "relay-1", turnToken = "token-1"))
        advanceTimeBy(100)

        assertTrue(builder.libretroController.unserializeCallCount >= 1)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(saveData))
        builder.tearDown()
    }

    @Test
    fun stopGameInRelayUploadsAndReleasesTurn() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", relayId = "relay-1", turnToken = "token-1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.StopGame)
        advanceTimeBy(100)

        assertEquals(1, builder.relayRepository.uploadRelayAutoSaveCallCount)
        assertEquals(1, builder.relayRepository.releaseTurnCallCount)
        builder.tearDown()
    }

    @Test
    fun relayHeartbeatRunsPeriodically() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", relayId = "relay-1", turnToken = "token-1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)

        // Heartbeat runs every 60 seconds; advance past one beat
        advanceTimeBy(61_000)
        assertTrue(builder.relayRepository.heartbeatCallCount >= 1)
        builder.tearDown()
    }

    // ── Netplay confirmations ───────────────────────────────────────────────

    @Test
    fun showNetplayLeaveConfirmSetsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
        assertTrue(vm.state.value.netplayShowLeaveConfirm)
        builder.tearDown()
    }

    @Test
    fun dismissNetplayLeaveConfirmClearsState() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
        assertTrue(vm.state.value.netplayShowLeaveConfirm)

        vm.onIntent(EmulationIntent.DismissNetplayLeaveConfirm)
        assertFalse(vm.state.value.netplayShowLeaveConfirm)
        builder.tearDown()
    }

    @Test
    fun confirmNetplayLeaveSetsExitAndStops() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmNetplayLeave)
        advanceTimeBy(100)

        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.netplayShowLeaveConfirm)
        builder.tearDown()
    }

    // ── BIOS ────────────────────────────────────────────────────────────────

    @Test
    fun missingBiosShowsDialogWithFileList() = runTest(testDispatcher) {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS (USA)", required = true),
            BiosMissingFile(fileName = "scph5502.bin", description = "PS1 BIOS (EUR)", required = true),
        )
        builder.biosRepository = StubBiosRepository(missingFiles = missingFiles)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.showMissingBiosDialog)
        assertEquals(2, vm.state.value.missingBiosFiles.size)
        assertEquals("scph5501.bin", vm.state.value.missingBiosFiles[0].fileName)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
        builder.tearDown()
    }

    @Test
    fun dismissMissingBiosDialogClearsState() = runTest(testDispatcher) {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS", required = true),
        )
        builder.biosRepository = StubBiosRepository(missingFiles = missingFiles)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.showMissingBiosDialog)

        vm.onIntent(EmulationIntent.DismissMissingBiosDialog)
        assertFalse(vm.state.value.showMissingBiosDialog)
        assertTrue(vm.state.value.missingBiosFiles.isEmpty())
        builder.tearDown()
    }

    @Test
    fun tryAnywayBiosRestartsGameBypassingCheck() = runTest(testDispatcher) {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS", required = true),
        )
        val biosRepo = StubBiosRepository(missingFiles = missingFiles)
        builder.biosRepository = biosRepo
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.showMissingBiosDialog)
        assertEquals(1, biosRepo.preLaunchBiosCheckCallCount)

        vm.onIntent(EmulationIntent.TryAnywayMissingBios)
        advanceTimeBy(100)

        // Should have attempted to start the game again
        // The second call would still find missing BIOS, but skipBiosCheck
        // is set to true, so it should bypass the check
        assertFalse(vm.state.value.showMissingBiosDialog)
        // preLaunchBiosCheck is called only once (second attempt skips it)
        assertEquals(1, biosRepo.preLaunchBiosCheckCallCount)
        builder.tearDown()
    }

    @Test
    fun noBiosCheckWhenRepositoryIsNull() = runTest(testDispatcher) {
        builder.biosRepository = null
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        // Should start normally without BIOS dialog
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.showMissingBiosDialog)
        builder.tearDown()
    }
}
