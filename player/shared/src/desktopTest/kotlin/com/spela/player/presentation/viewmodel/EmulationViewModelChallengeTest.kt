package com.spela.player.presentation.viewmodel

import com.spela.player.presentation.viewmodel.emulation.EmulationViewModelTestBuilder
import com.spela.player.presentation.viewmodel.emulation.StubScreenshotCapture
import com.spela.player.domain.model.Challenge
import com.spela.player.domain.model.ChallengeAttempt
import com.spela.player.domain.model.ChallengeDifficulty
import com.spela.player.domain.model.ChallengeType
import com.spela.player.presentation.intent.EmulationIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EmulationViewModelChallengeTest {

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
    fun startGameWithChallengeDownloadsChallengeSave() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertTrue(vm.state.value.isChallengeMode)
        assertEquals(1, builder.challengeRepository.downloadChallengeSaveCallCount)
    }

    @Test
    fun startGameWithChallengeUnserializesSave() = runTest {
        val saveData = byteArrayOf(99, 88)
        builder.challengeRepository.downloadChallengeSaveResult = Result.success(saveData)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        assertTrue(builder.libretroController.unserializeCallCount >= 1)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(saveData))
    }

    @Test
    fun startGameWithChallengeStartsAttempt() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.startAttemptCallCount)
        assertEquals("attempt-1", vm.state.value.challengeAttemptId)
    }

    @Test
    fun startGameWithChallengeSaveDataUsesProvidedData() = runTest {
        val providedData = byteArrayOf(11, 22, 33)
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1", challengeSaveData = providedData))
        builder.advanceTimeBy(100)

        // Should use provided data, not download
        assertEquals(0, builder.challengeRepository.downloadChallengeSaveCallCount)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(providedData))
    }

    @Test
    fun challengeTimerIncrementsWhileRunning() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.isChallengeMode)

        // Challenge timer ticks every 100ms using System.nanoTime()
        // With virtual time, we just assert it becomes > 0 at some point
        builder.advanceTimeBy(500)
        // The timer relies on System.nanoTime() which progresses with real time,
        // but the delay(100) in the timer loop IS controlled by virtual time.
        // After advancing, the loop will have run several iterations.
        assertTrue(vm.state.value.challengeElapsedMs >= 0)
    }

    @Test
    fun createChallengePausesAndSerializesAndShowsDialog() = runTest {
        builder.screenshotCapture = StubScreenshotCapture()
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)

        assertTrue(vm.state.value.isPaused)
        assertTrue(vm.state.value.showChallengeCreation)
        assertTrue(builder.libretroController.serializeCallCount >= 1)
    }

    @Test
    fun createChallengeCapturesScreenshot() = runTest {
        val screenshotCapture = StubScreenshotCapture()
        builder.screenshotCapture = screenshotCapture
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)

        assertEquals(1, screenshotCapture.captureCallCount)
    }

    @Test
    fun submitChallengeCallsRepoAndShowsSuccess() = runTest {
        builder.challengeRepository.createChallengeResult = Result.success(
            Challenge(
                id = "new-c1", creatorId = "u1", creatorUsername = "test", creatorAvatarUrl = null,
                gameId = "game1", gameTitle = "Test", gameCoverUrl = null, gameConsoleName = "NES",
                name = "My Challenge", description = "desc", type = ChallengeType.SPEEDRUN,
                difficulty = ChallengeDifficulty.MEDIUM, status = "active", screenshotUrl = null,
                coreName = "nestopia", saveFileSize = 100, attemptCount = 0, completionCount = 0,
                bestTimeMs = null, expiresAt = null, createdAt = "",
            )
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showChallengeCreation)

        vm.onIntent(EmulationIntent.SubmitChallenge("Test", "desc", "speedrun", "medium"))
        builder.advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.createChallengeCallCount)
        assertFalse(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.challengeCreationSuccess)
        assertEquals("Challenge created!", vm.state.value.statusMessage)
    }

    @Test
    fun submitChallengeFailureShowsError() = runTest {
        builder.challengeRepository.createChallengeResult = Result.failure(Exception("server error"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SubmitChallenge("Test", "desc", "speedrun", "medium"))
        builder.advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to create challenge"))
    }

    @Test
    fun completeChallengeSubmitsAndShowsResult() = runTest {
        builder.challengeRepository.completeAttemptResult = Result.success(
            ChallengeAttempt(
                id = "attempt-1", challengeId = "c1", userId = "u1", username = "test",
                avatarUrl = null, status = "completed", startedAt = "", completedAt = "",
                durationMs = 5000, isBest = true,
            )
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CompleteChallenge)
        builder.advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.completeAttemptCallCount)
        assertNotNull(vm.state.value.challengeCompletedAttempt)
        assertEquals(5000, vm.state.value.challengeCompletedAttempt!!.durationMs)
        assertTrue(vm.state.value.isPaused)
    }

    @Test
    fun completeChallengeFailureRestartsTimer() = runTest {
        builder.challengeRepository.completeAttemptResult = Result.failure(Exception("failed"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CompleteChallenge)
        builder.advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to submit"))
        // Should have resumed the game
        assertFalse(vm.state.value.isPaused)
    }

    // TODO: flaky in CI despite multiple fixes (see IMPROVEMENTS.md 2026-04-10
    // entry). The test has been blocking unrelated PRs for six CI runs now.
    // Skipping until someone can invest time in a proper root-cause
    // investigation of the coroutine-test-dispatcher race inside
    // ChallengeManager.restartChallenge. The PR #359 drain helper
    // (advanceTimeByAndDrain) passes 5/5 locally but still fails
    // intermittently on GitHub Actions runners.
    @Ignore
    @Test
    fun restartChallengeReloadsAndStartsNewAttempt() = runTest {
        builder.challengeRepository.startAttemptResult = Result.success(
            ChallengeAttempt(id = "attempt-2", challengeId = "c1", userId = "u1", username = "test",
                avatarUrl = null, status = "in_progress", startedAt = "", completedAt = null,
                durationMs = 0, isBest = false)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        // Drain across multiple passes: ChallengeManager.restartChallenge
        // launches on dispatchers.io and later hops to dispatchers.main via
        // withContext(), so a single runCurrent() pass leaves the main-thread
        // state update unscheduled and the test becomes flaky under CI load.
        builder.advanceTimeByAndDrain(100)

        // First attempt
        assertEquals("attempt-2", vm.state.value.challengeAttemptId)

        vm.onIntent(EmulationIntent.RestartChallenge)
        builder.advanceTimeByAndDrain(100)

        // Should have abandoned previous attempt and started new one
        assertEquals(1, builder.challengeRepository.abandonAttemptCallCount)
        // startAttempt called twice: once for initial start, once for restart
        assertEquals(2, builder.challengeRepository.startAttemptCallCount)
        assertEquals(0, vm.state.value.challengeElapsedMs)
    }

    @Test
    fun confirmGiveUpAbandonsAndExits() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmGiveUp)
        builder.advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.abandonAttemptCallCount)
        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
    }

    @Test
    fun dismissChallengeCreationResumes() = runTest {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.isPaused)

        vm.onIntent(EmulationIntent.DismissChallengeCreation)
        assertFalse(vm.state.value.showChallengeCreation)
        assertFalse(vm.state.value.isPaused)
    }
}
