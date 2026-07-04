package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubBiosRepository
import com.spela.player.domain.model.BiosMissingFile
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelSharedSessionAndBiosTest {

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

    // ── Shared session mode ─────────────────────────────────────────────────

    @Test
    fun startGameWithSharedSessionDownloadsAutoSave() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", sharedSessionId = "ss-1", turnToken = "token-1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertEquals(1, builder.sharedSessionRepository.downloadSharedSessionAutoSaveCallCount)
    }

    @Test
    fun startGameWithSharedSessionUnserializesAutoSave() = runTest {
        val saveData = byteArrayOf(77, 88, 99)
        builder.sharedSessionRepository.downloadSharedSessionAutoSaveResult = Result.success(saveData)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", sharedSessionId = "ss-1", turnToken = "token-1"))
        builder.advanceTimeBy(100)

        assertTrue(builder.libretroController.unserializeCallCount >= 1)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(saveData))
        val calls = builder.libretroController.calls
        assertTrue(
            calls.indexOf("unserialize") > calls.indexOf("start"),
            "shared-session restore must run after core start; calls=$calls",
        )
    }

    @Test
    fun stopGameInSharedSessionUploadsAndReleasesTurn() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", sharedSessionId = "ss-1", turnToken = "token-1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.StopGame)
        builder.advanceTimeBy(100)

        assertEquals(1, builder.sharedSessionRepository.uploadSharedSessionAutoSaveCallCount)
        assertEquals(1, builder.sharedSessionRepository.releaseTurnCallCount)
    }

    @Test
    fun sharedSessionHeartbeatRunsPeriodically() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", sharedSessionId = "ss-1", turnToken = "token-1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isRunning)

        // Heartbeat starts after 3-second delay, runs every 60 seconds;
        // advance past the delay + one beat
        builder.advanceTimeBy(64_000)
        assertTrue(builder.sharedSessionRepository.heartbeatCallCount >= 1)
    }

    // ── Netplay confirmations ───────────────────────────────────────────────

    @Test
    fun showNetplayLeaveConfirmSetsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
        assertTrue(vm.state.value.netplayShowLeaveConfirm)
    }

    @Test
    fun dismissNetplayLeaveConfirmClearsState() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.ShowNetplayLeaveConfirm)
        assertTrue(vm.state.value.netplayShowLeaveConfirm)

        vm.onIntent(EmulationIntent.DismissNetplayLeaveConfirm)
        assertFalse(vm.state.value.netplayShowLeaveConfirm)
    }

    @Test
    fun confirmNetplayLeaveSetsExitAndStops() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmNetplayLeave)
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.netplayShowLeaveConfirm)
    }

    // ── BIOS ────────────────────────────────────────────────────────────────

    @Test
    fun missingBiosShowsDialogWithFileList() = runTest {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS (USA)", required = true),
            BiosMissingFile(fileName = "scph5502.bin", description = "PS1 BIOS (EUR)", required = true),
        )
        builder.biosRepository = StubBiosRepository(missingFiles = missingFiles)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.showMissingBiosDialog)
        assertEquals(2, vm.state.value.missingBiosFiles.size)
        assertEquals("scph5501.bin", vm.state.value.missingBiosFiles[0].fileName)
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun dismissMissingBiosDialogClearsState() = runTest {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS", required = true),
        )
        builder.biosRepository = StubBiosRepository(missingFiles = missingFiles)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showMissingBiosDialog)

        vm.onIntent(EmulationIntent.DismissMissingBiosDialog)
        assertFalse(vm.state.value.showMissingBiosDialog)
        assertTrue(vm.state.value.missingBiosFiles.isEmpty())
    }

    @Test
    fun tryAnywayBiosRestartsGameBypassingCheck() = runTest {
        val missingFiles = listOf(
            BiosMissingFile(fileName = "scph5501.bin", description = "PS1 BIOS", required = true),
        )
        val biosRepo = StubBiosRepository(missingFiles = missingFiles)
        builder.biosRepository = biosRepo
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showMissingBiosDialog)
        assertEquals(1, biosRepo.preLaunchBiosCheckCallCount)

        vm.onIntent(EmulationIntent.TryAnywayMissingBios)
        builder.advanceTimeBy(100)

        // Should have attempted to start the game again
        // The second call would still find missing BIOS, but skipBiosCheck
        // is set to true, so it should bypass the check
        assertFalse(vm.state.value.showMissingBiosDialog)
        // preLaunchBiosCheck is called only once (second attempt skips it)
        assertEquals(1, biosRepo.preLaunchBiosCheckCallCount)
    }

    @Test
    fun noBiosCheckWhenRepositoryIsNull() = runTest {
        builder.biosRepository = null
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        // Should start normally without BIOS dialog
        assertTrue(vm.state.value.isRunning)
        assertFalse(vm.state.value.showMissingBiosDialog)
    }
}
