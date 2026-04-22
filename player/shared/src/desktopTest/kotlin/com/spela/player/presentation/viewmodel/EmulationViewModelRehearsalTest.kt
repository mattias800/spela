package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
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
 * Coverage for the #672 PR 3c.ii rehearsal flow on EmulationViewModel:
 * the eight new intents (banner show / dismiss, Sheet C primary /
 * secondary / ghost, Sheet D crash trigger / start-fresh / dismiss)
 * plus the SaveManager.rehearsalSaveBlocked → snackbar wiring.
 *
 * Intent → state assertions only. The downstream re-fired StartGame
 * pipeline is owned by EmulationViewModelGameLifecycleTest; here we
 * verify the VM emits the right server-flag writes and clears the
 * rehearsal UI state on each resolution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelRehearsalTest {

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

    // ── Sheet C confirmation toggle ─────────────────────────────

    @Test
    fun showConfirmationFlipsSheetCBoolean() = runTest {
        val vm = builder.build()

        vm.onIntent(EmulationIntent.RehearsalShowConfirmation)
        builder.advanceTimeBy(50)

        assertTrue(vm.state.value.showRehearsalConfirmSheet,
            "RehearsalShowConfirmation must flip the Sheet C boolean")
    }

    @Test
    fun dismissConfirmationClosesSheetCWithoutSideEffects() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.RehearsalShowConfirmation)
        builder.advanceTimeBy(50)
        assertTrue(vm.state.value.showRehearsalConfirmSheet)

        vm.onIntent(EmulationIntent.RehearsalDismissConfirmation)
        builder.advanceTimeBy(50)

        assertFalse(vm.state.value.showRehearsalConfirmSheet,
            "Dismissing Sheet C must close the sheet but write no server flags")
        assertEquals(0, builder.sessionRepository.updateSessionCoreFlagsCalls.size)
    }

    @Test
    fun continueLongerIsAnAliasForDismissConfirmation() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.RehearsalShowConfirmation)
        builder.advanceTimeBy(50)

        vm.onIntent(EmulationIntent.RehearsalContinueLonger)
        builder.advanceTimeBy(50)

        assertFalse(vm.state.value.showRehearsalConfirmSheet,
            "Continue-longer is the labelled equivalent of dismiss — same close-only effect")
        assertEquals(0, builder.sessionRepository.updateSessionCoreFlagsCalls.size)
    }

    // ── Sheet C resolutions ───────────────────────────────────────

    @Test
    fun keepNewClearsRehearsalUiAndWritesNoServerFlags() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.RehearsalShowConfirmation)
        builder.advanceTimeBy(50)

        vm.onIntent(EmulationIntent.RehearsalCompletedKeepNew)
        builder.advanceTimeBy(50)

        assertNull(vm.state.value.coreDecision,
            "KeepNew must clear coreDecision so the banner / sheet stop rendering")
        assertFalse(vm.state.value.showRehearsalConfirmSheet)
        assertEquals(0, builder.sessionRepository.updateSessionCoreFlagsCalls.size,
            "KeepNew must not touch UserLockedCoreVersion — pin advances on next save instead",
        )
    }

    // ── Sheet D — crash trigger + state transitions ──────────────

    @Test
    fun crashedTransitionsCoreDecisionToRehearsalCrashed() = runTest {
        val vm = builder.build()

        vm.onIntent(EmulationIntent.RehearsalCrashed("mednafen_psx_hw", "Beetle PSX HW"))
        builder.advanceTimeBy(50)

        val decision = vm.state.value.coreDecision
        assertTrue(decision is CoreDecision.RehearsalCrashed,
            "core crash intent must surface Sheet D via CoreDecision.RehearsalCrashed",
        )
        assertEquals("mednafen_psx_hw", decision.coreName)
        assertEquals("Beetle PSX HW", decision.coreDisplayName)
    }

    @Test
    fun crashedFallsBackToCoreNameWhenDisplayNameIsEmpty() = runTest {
        val vm = builder.build()

        vm.onIntent(EmulationIntent.RehearsalCrashed("nestopia", ""))
        builder.advanceTimeBy(50)

        val decision = vm.state.value.coreDecision as CoreDecision.RehearsalCrashed
        assertEquals("nestopia", decision.coreDisplayName,
            "empty display name must fall back to libretro id so Sheet D body never starts with a blank space",
        )
    }

    @Test
    fun crashedSupersedesAnyOpenConfirmationSheet() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.RehearsalShowConfirmation)
        builder.advanceTimeBy(50)
        assertTrue(vm.state.value.showRehearsalConfirmSheet)

        vm.onIntent(EmulationIntent.RehearsalCrashed("nestopia", "Nestopia UE"))
        builder.advanceTimeBy(50)

        assertFalse(vm.state.value.showRehearsalConfirmSheet,
            "Sheet D supersedes Sheet C — confirmation sheet must close on crash",
        )
    }

    @Test
    fun crashDismissWithoutSessionClearsSheetAndSkipsServerWrite() = runTest {
        // Defensive path: the rehearsal flow shouldn't enter without a
        // session, but if RehearsalCrashed somehow fires from a
        // session-less context, dismissing must still clear the sheet
        // — and the SaveManager helper short-circuits at the null
        // sessionId guard, so no flag write is attempted.
        val vm = builder.build()
        vm.onIntent(EmulationIntent.RehearsalCrashed("nestopia", "Nestopia UE"))
        builder.advanceTimeBy(50)

        vm.onIntent(EmulationIntent.RehearsalCrashDismiss)
        builder.advanceTimeBy(50)

        assertNull(vm.state.value.coreDecision,
            "CrashDismiss must clear the sheet so the in-game surface is reachable",
        )
        assertEquals(0, builder.sessionRepository.updateSessionCoreFlagsCalls.size,
            "Without a sessionId the SaveManager helper short-circuits — no flag write reaches the server",
        )
    }

    // ── Launch-time crash recovery (rehearsalCrashPending intercept) ──

    @Test
    fun launchTimeCrashDismissNavigatesAwayInsteadOfStrandingTheUser() = runTest {
        // Launch-time intercept: sentinel is true, Sheet D shows, the
        // user picks "Just go back". There's no running emulator to
        // resume — we must signal navigation via requestExit so the
        // user doesn't end up on a blank EmulationScreen with no
        // affordance to leave.
        builder.sessionRepository.existingSessions = listOf(
            com.spela.player.domain.model.GameSession(
                id = "s1",
                gameId = "g1",
                name = "Crashed run",
                rehearsalCrashPending = true,
            ),
        )

        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame(gameId = "g1", sessionId = "s1"))
        builder.advanceTimeBy(500)
        // Precondition — Sheet D is visible, game never ran.
        assertTrue(
            vm.state.value.coreDecision is com.spela.player.presentation.state.CoreDecision.RehearsalCrashed,
        )
        assertFalse(vm.state.value.isRunning,
            "the game never reached the running state — this is the launch-time intercept path",
        )

        vm.onIntent(EmulationIntent.RehearsalCrashDismiss)
        builder.advanceTimeBy(100)

        assertNull(vm.state.value.coreDecision)
        assertTrue(vm.state.value.requestExit,
            "launch-time CrashDismiss must request an exit so the user navigates back instead of being stranded",
        )
    }

    @Test
    fun launchTimeCrashSentinelInterceptsStartGameAndShowsSheetD() = runTest {
        // Simulate a previous rehearsal run that died — the server
        // session record carries rehearsalCrashPending=true. When the
        // user taps Play, the VM must intercept BEFORE loadCore and
        // surface CoreDecision.RehearsalCrashed so the UI renders
        // Sheet D.
        builder.sessionRepository.existingSessions = listOf(
            com.spela.player.domain.model.GameSession(
                id = "s1",
                gameId = "g1",
                name = "Crashed run",
                rehearsalCrashPending = true,
            ),
        )

        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame(gameId = "g1", sessionId = "s1"))
        builder.advanceTimeBy(500)

        val decision = vm.state.value.coreDecision
        assertTrue(decision is com.spela.player.presentation.state.CoreDecision.RehearsalCrashed,
            "Launch with rehearsalCrashPending=true must surface Sheet D — got ${decision?.let { it::class.simpleName }}",
        )
        assertFalse(vm.state.value.isLoading,
            "Sheet D must clear the loading state so the user sees the prompt, not a spinner",
        )
        assertEquals(0, builder.libretroController.loadCoreCallCount,
            "VM must intercept BEFORE loadCore — the previous run might have crashed inside the core",
        )
    }

    // ── Crash-recovery sentinel lifecycle (PR 3c.iii) ────────────

    @Test
    fun keepNewClearsCrashSentinel() = runTest {
        val vm = builder.build()
        builder.mutableState.update { it.copy(sessionId = "s1") }

        vm.onIntent(EmulationIntent.RehearsalCompletedKeepNew)
        builder.advanceTimeBy(100)

        val clearCalls = builder.sessionRepository.updateSessionCoreFlagsCalls
            .filter { it.rehearsalCrashPending == false }
        assertEquals(1, clearCalls.size,
            "KeepNew must clear rehearsalCrashPending so the next launch doesn't re-route to Sheet D",
        )
        assertEquals("s1", clearCalls.single().sessionId)
    }

    @Test
    fun crashDismissClearsCrashSentinelAndExitsRehearsal() = runTest {
        val vm = builder.build()
        builder.mutableState.update { it.copy(sessionId = "s1") }
        builder.saveManager.rehearsalMode = true

        vm.onIntent(EmulationIntent.RehearsalCrashed("nestopia", "Nestopia UE"))
        builder.advanceTimeBy(50)
        vm.onIntent(EmulationIntent.RehearsalCrashDismiss)
        builder.advanceTimeBy(100)

        assertFalse(builder.saveManager.rehearsalMode,
            "CrashDismiss must exit rehearsal mode — the user explicitly acknowledged the crash",
        )
        val clearCalls = builder.sessionRepository.updateSessionCoreFlagsCalls
            .filter { it.rehearsalCrashPending == false }
        assertEquals(1, clearCalls.size,
            "CrashDismiss must clear rehearsalCrashPending so the next launch doesn't re-route to Sheet D",
        )
    }

    @Test
    fun crashDismissWithoutSessionIsNoOp() = runTest {
        val vm = builder.build()
        // No session bound on state — defensive path. Must not crash
        // and must not write any server flags.
        vm.onIntent(EmulationIntent.RehearsalCrashed("nestopia", "Nestopia UE"))
        builder.advanceTimeBy(50)
        vm.onIntent(EmulationIntent.RehearsalCrashDismiss)
        builder.advanceTimeBy(100)

        assertEquals(0, builder.sessionRepository.updateSessionCoreFlagsCalls.size,
            "CrashDismiss with no sessionId must not attempt a flag write",
        )
    }

    // ── Snackbar wiring (RehearsalSaveBlocked → statusMessage) ───

    @Test
    fun rehearsalSaveBlockedSurfacesSpecSnackCopyAsStatusMessage() = runTest {
        val vm = builder.build()
        builder.advanceTimeBy(50)
        assertNull(vm.state.value.statusMessage,
            "baseline: no rehearsal-blocked event has fired yet")

        // Drive a save attempt through the real SaveManager surface
        // while rehearsalMode is on. The VM's init-block collector
        // sees the resulting RehearsalSaveBlocked event and pushes
        // the canonical snackbar copy onto statusMessage.
        builder.saveManager.currentSessionId = "s1"
        builder.saveManager.currentCoreName = "nestopia"
        builder.saveManager.rehearsalMode = true
        builder.saveManager.saveState()
        builder.advanceTimeBy(100)

        assertEquals(
            "You're in a trial run — saves are paused until you " +
                "keep this version. Tap 'Did this work?' above to decide.",
            vm.state.value.statusMessage,
            "VM must surface the canonical core_upd.snack.rehearsal_save copy when SaveManager drops a save",
        )
    }
}
