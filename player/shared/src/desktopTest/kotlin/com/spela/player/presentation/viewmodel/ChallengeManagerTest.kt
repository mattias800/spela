package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.state.EmulationState
import com.spela.player.presentation.viewmodel.emulation.StubChallengeRepository
import com.spela.player.presentation.viewmodel.emulation.StubLibretroController
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ChallengeManager] in isolation.
 *
 * These tests deliberately avoid the full `EmulationViewModelTestBuilder`
 * setup because the VM launches long-running background coroutines
 * (challenge elapsed-time loop, session heartbeat) that make scheduler
 * draining harder than it should be. The flaky
 * `EmulationViewModelChallengeTest.restartChallengeReloadsAndStartsNewAttempt`
 * history (PRs #355, #356, #357, #358, #361) is what motivated this file:
 * the race lived inside `ChallengeManager.restartChallenge`, so the right
 * place to test it is directly against `ChallengeManager` with a
 * controllable dispatcher.
 *
 * Uses `UnconfinedTestDispatcher` (runs eagerly on the calling thread up
 * to the first real suspension) rather than `StandardTestDispatcher`
 * (queues tasks and requires explicit drains). The `io → main` hop inside
 * `restartChallenge` therefore completes synchronously from the test's
 * point of view, and assertions are deterministic.
 *
 * IMPORTANT: any test that triggers `startChallengeTimer` (directly or
 * transitively via `restartChallenge`) must call `manager.cleanup()`
 * before the `runTest` block returns. The timer is an infinite
 * `while(isActive) { delay(100) }` loop, and `runTest`'s implicit
 * `advanceUntilIdle()` at the end of the block would otherwise spin
 * forever. The `@AfterTest` fallback runs too late — `runTest` has
 * already tried to drain the scheduler by then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChallengeManagerTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var scope: CoroutineScope
    private lateinit var state: MutableStateFlow<EmulationState>
    private lateinit var challengeRepository: StubChallengeRepository
    private lateinit var libretroController: StubLibretroController
    private lateinit var manager: ChallengeManager

    @BeforeTest
    fun setup() {
        testDispatcher = UnconfinedTestDispatcher()
        scope = CoroutineScope(testDispatcher + Job())
        state = MutableStateFlow(EmulationState())
        challengeRepository = StubChallengeRepository()
        libretroController = StubLibretroController()
        manager = ChallengeManager(
            challengeRepository = challengeRepository,
            libretroController = libretroController,
            screenshotCapture = null,
            _state = state,
            dispatchers = object : DispatcherProvider {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            },
            scope = scope,
        )
    }

    @AfterTest
    fun teardown() {
        manager.cleanup()
        scope.cancel(CancellationException("Test finished"))
    }

    @Test
    fun loadChallengeSaveStartsAttempt() = runTest(testDispatcher) {
        val saveData = byteArrayOf(1, 2, 3)
        state.update { it.copy(challengeId = "c1") }

        manager.loadChallengeSave("c1", saveData)

        assertEquals(1, challengeRepository.startAttemptCallCount)
        assertEquals("attempt-1", state.value.challengeAttemptId)
    }

    @Test
    fun restartChallengeAbandonsCurrentAttemptAndStartsNew() = runTest(testDispatcher) {
        // Prime state + load initial save so restartChallenge has the
        // saved bytes cached and a current attempt to abandon.
        // (isChallengeMode is derived from challengeId != null.)
        val saveData = byteArrayOf(1, 2, 3)
        state.update { it.copy(challengeId = "c1") }
        manager.loadChallengeSave("c1", saveData)
        // After loadChallengeSave the first attempt is "attempt-1" per
        // StubChallengeRepository.startAttemptResult. Swap in a second
        // attempt so we can tell the new one apart from the first.
        challengeRepository.startAttemptResult = Result.success(
            ChallengeAttempt(
                id = "attempt-2",
                challengeId = "c1",
                userId = "u1",
                username = "test",
                avatarUrl = null,
                status = "in_progress",
                startedAt = "",
                completedAt = null,
                durationMs = 0,
                isBest = false,
            ),
        )
        // Simulate some elapsed time so we can assert the counter is reset.
        state.update { it.copy(challengeElapsedMs = 12_345) }

        var resumeGameCalled = false
        manager.restartChallenge { resumeGameCalled = true }

        // With UnconfinedTestDispatcher the io → main hop completes
        // synchronously up to the challenge timer's first `delay(100)`,
        // so every assertion below is deterministic.
        assertEquals(1, challengeRepository.abandonAttemptCallCount)
        // Once for loadChallengeSave, once for restart.
        assertEquals(2, challengeRepository.startAttemptCallCount)
        assertEquals("attempt-2", state.value.challengeAttemptId)
        assertEquals(0L, state.value.challengeElapsedMs)
        assertTrue(resumeGameCalled, "restart should resume the game")

        // Stop the infinite challenge timer loop before runTest's
        // implicit advanceUntilIdle() at the end of the block.
        manager.cleanup()
    }

    @Test
    fun restartChallengeWithoutSaveDataIsNoop() = runTest(testDispatcher) {
        // No prior loadChallengeSave call — challengeSaveData is null, so
        // restart must bail out without touching the repository.
        state.update { it.copy(challengeId = "c1") }

        var resumeGameCalled = false
        manager.restartChallenge { resumeGameCalled = true }

        assertEquals(0, challengeRepository.abandonAttemptCallCount)
        assertEquals(0, challengeRepository.startAttemptCallCount)
        assertEquals(false, resumeGameCalled)
    }

    @Test
    fun restartChallengeWithoutChallengeIdIsNoop() = runTest(testDispatcher) {
        // challengeId == null (not in challenge mode) — restart bails out
        // before launching the io coroutine.
        var resumeGameCalled = false
        manager.restartChallenge { resumeGameCalled = true }

        assertEquals(0, challengeRepository.abandonAttemptCallCount)
        assertEquals(0, challengeRepository.startAttemptCallCount)
        assertEquals(false, resumeGameCalled)
    }

    @Test
    fun restartChallengeFailureSurfacesError() = runTest(testDispatcher) {
        state.update { it.copy(challengeId = "c1", challengeAttemptId = "attempt-1") }
        manager.loadChallengeSave("c1", byteArrayOf(1, 2, 3))

        // Second startAttempt call should fail.
        challengeRepository.startAttemptResult = Result.failure(RuntimeException("server down"))

        var resumeGameCalled = false
        manager.restartChallenge { resumeGameCalled = true }

        // The abandon still happened (the old attempt was cancelled), but
        // the new attempt errored and the error propagates to state.
        assertEquals(1, challengeRepository.abandonAttemptCallCount)
        assertEquals(false, resumeGameCalled)
        val error = state.value.error
        assertTrue(
            error != null && error.contains("Failed to restart"),
            "expected error to contain 'Failed to restart', got: $error",
        )
    }

    @Test
    fun cleanupCancelsTimerAndClearsSaveData() = runTest(testDispatcher) {
        state.update { it.copy(challengeId = "c1") }
        manager.loadChallengeSave("c1", byteArrayOf(1, 2, 3))
        manager.startChallengeTimer()

        manager.cleanup()

        // After cleanup, a subsequent restartChallenge should be a no-op
        // because the cached save data was cleared.
        var resumeGameCalled = false
        manager.restartChallenge { resumeGameCalled = true }

        // Should bail out on missing saveData.
        assertEquals(false, resumeGameCalled)
        // startAttempt was only called once (from the initial
        // loadChallengeSave), not again on the restart.
        assertEquals(1, challengeRepository.startAttemptCallCount)
    }

    @Test
    fun initChallengeCreationCapturesSaveState() = runTest(testDispatcher) {
        libretroController.serializeResult = byteArrayOf(7, 8, 9)

        var pauseGameCalled = false
        manager.initChallengeCreation { pauseGameCalled = true }

        assertTrue(pauseGameCalled)
        assertTrue(state.value.showChallengeCreation)
    }

    @Test
    fun dismissChallengeCreationClearsFlagsAndResumes() = runTest(testDispatcher) {
        state.update { it.copy(showChallengeCreation = true) }

        var resumeGameCalled = false
        manager.dismissChallengeCreation { resumeGameCalled = true }

        assertTrue(resumeGameCalled)
        assertEquals(false, state.value.showChallengeCreation)
        assertEquals(false, state.value.challengeCreationSuccess)
    }

    @Test
    fun giveUpChallengeAbandonsAndExits() = runTest(testDispatcher) {
        state.update {
            it.copy(
                challengeId = "c1",
                challengeAttemptId = "attempt-1",
                showGiveUpConfirm = true,
            )
        }

        var stopGameCalled = false
        manager.giveUpChallenge { stopGameCalled = true }

        assertEquals(1, challengeRepository.abandonAttemptCallCount)
        assertTrue(stopGameCalled)
        assertTrue(state.value.requestExit)
        assertEquals(false, state.value.showGiveUpConfirm)
    }

    @Test
    fun giveUpChallengeWithoutAttemptSkipsAbandon() = runTest(testDispatcher) {
        state.update { it.copy(challengeId = "c1", challengeAttemptId = null) }

        var stopGameCalled = false
        manager.giveUpChallenge { stopGameCalled = true }

        assertEquals(0, challengeRepository.abandonAttemptCallCount)
        assertTrue(stopGameCalled)
    }

    @Test
    fun completeChallengeSucceeds() = runTest(testDispatcher) {
        state.update { it.copy(challengeId = "c1", challengeAttemptId = "attempt-1", challengeElapsedMs = 9999) }
        challengeRepository.completeAttemptResult = Result.success(
            ChallengeAttempt(
                id = "attempt-1",
                challengeId = "c1",
                userId = "u1",
                username = "test",
                avatarUrl = null,
                status = "completed",
                startedAt = "",
                completedAt = "",
                durationMs = 9999,
                isBest = true,
            ),
        )

        var pauseGameCalled = false
        manager.completeChallenge { pauseGameCalled = true }

        // pauseGame is the caller's responsibility (the VM sets isPaused
        // inside its callback). ChallengeManager only stashes the attempt
        // and hides the overlay.
        assertTrue(pauseGameCalled)
        assertEquals(1, challengeRepository.completeAttemptCallCount)
        val completed = state.value.challengeCompletedAttempt
        assertTrue(completed != null && completed.isBest, "expected best-attempt to be set")
        assertEquals(false, state.value.showOverlay)
    }

    @Test
    fun completeChallengeFailureResumesGame() = runTest(testDispatcher) {
        state.update { it.copy(challengeId = "c1", challengeAttemptId = "attempt-1") }
        challengeRepository.completeAttemptResult = Result.failure(RuntimeException("offline"))

        var pauseGameCalled = false
        manager.completeChallenge { pauseGameCalled = true }

        assertTrue(pauseGameCalled)
        val error = state.value.error
        assertTrue(
            error != null && error.contains("Failed to submit"),
            "expected error to contain 'Failed to submit', got: $error",
        )
        // The game should be resumed after the failure path.
        assertEquals(false, state.value.isPaused)
        assertNull(state.value.challengeCompletedAttempt)

        // The failure path restarts the challenge timer, so stop it
        // before runTest tries to drain the scheduler.
        manager.cleanup()
    }
}
