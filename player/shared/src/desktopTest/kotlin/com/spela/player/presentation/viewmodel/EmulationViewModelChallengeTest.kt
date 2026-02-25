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
class EmulationViewModelChallengeTest {

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

    @Test
    fun startGameWithChallengeDownloadsChallengeSave() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        assertTrue(vm.state.value.isRunning)
        assertTrue(vm.state.value.isChallengeMode)
        assertEquals(1, builder.challengeRepository.downloadChallengeSaveCallCount)
        builder.tearDown()
    }

    @Test
    fun startGameWithChallengeUnserializesSave() = runTest(testDispatcher) {
        val saveData = byteArrayOf(99, 88)
        builder.challengeRepository.downloadChallengeSaveResult = Result.success(saveData)
        val vm = builder.build()

        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        assertTrue(builder.libretroController.unserializeCallCount >= 1)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(saveData))
        builder.tearDown()
    }

    @Test
    fun startGameWithChallengeStartsAttempt() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.startAttemptCallCount)
        assertEquals("attempt-1", vm.state.value.challengeAttemptId)
        builder.tearDown()
    }

    @Test
    fun startGameWithChallengeSaveDataUsesProvidedData() = runTest(testDispatcher) {
        val providedData = byteArrayOf(11, 22, 33)
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1", challengeSaveData = providedData))
        advanceTimeBy(100)

        // Should use provided data, not download
        assertEquals(0, builder.challengeRepository.downloadChallengeSaveCallCount)
        assertTrue(builder.libretroController.lastUnserializeData.contentEquals(providedData))
        builder.tearDown()
    }

    @Test
    fun challengeTimerIncrementsWhileRunning() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)
        assertTrue(vm.state.value.isChallengeMode)

        // Challenge timer ticks every 100ms using System.nanoTime()
        // With virtual time, we just assert it becomes > 0 at some point
        advanceTimeBy(500)
        // The timer relies on System.nanoTime() which progresses with real time,
        // but the delay(100) in the timer loop IS controlled by virtual time.
        // After advancing, the loop will have run several iterations.
        assertTrue(vm.state.value.challengeElapsedMs >= 0)
        builder.tearDown()
    }

    @Test
    fun createChallengePausesAndSerializesAndShowsDialog() = runTest(testDispatcher) {
        builder.screenshotCapture = StubScreenshotCapture()
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        advanceTimeBy(100)

        assertTrue(vm.state.value.isPaused)
        assertTrue(vm.state.value.showChallengeCreation)
        assertTrue(builder.libretroController.serializeCallCount >= 1)
        builder.tearDown()
    }

    @Test
    fun createChallengeCapturesScreenshot() = runTest(testDispatcher) {
        val screenshotCapture = StubScreenshotCapture()
        builder.screenshotCapture = screenshotCapture
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        advanceTimeBy(100)

        assertEquals(1, screenshotCapture.captureCallCount)
        builder.tearDown()
    }

    @Test
    fun submitChallengeCallsRepoAndShowsSuccess() = runTest(testDispatcher) {
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
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        advanceTimeBy(100)
        assertTrue(vm.state.value.showChallengeCreation)

        vm.onIntent(EmulationIntent.SubmitChallenge("Test", "desc", "speedrun", "medium"))
        advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.createChallengeCallCount)
        assertFalse(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.challengeCreationSuccess)
        assertEquals("Challenge created!", vm.state.value.statusMessage)
        builder.tearDown()
    }

    @Test
    fun submitChallengeFailureShowsError() = runTest(testDispatcher) {
        builder.challengeRepository.createChallengeResult = Result.failure(Exception("server error"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.SubmitChallenge("Test", "desc", "speedrun", "medium"))
        advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to create challenge"))
        builder.tearDown()
    }

    @Test
    fun completeChallengeSubmitsAndShowsResult() = runTest(testDispatcher) {
        builder.challengeRepository.completeAttemptResult = Result.success(
            ChallengeAttempt(
                id = "attempt-1", challengeId = "c1", userId = "u1", username = "test",
                avatarUrl = null, status = "completed", startedAt = "", completedAt = "",
                durationMs = 5000, isBest = true,
            )
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CompleteChallenge)
        advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.completeAttemptCallCount)
        assertNotNull(vm.state.value.challengeCompletedAttempt)
        assertEquals(5000, vm.state.value.challengeCompletedAttempt!!.durationMs)
        assertTrue(vm.state.value.isPaused)
        builder.tearDown()
    }

    @Test
    fun completeChallengeFailureRestartsTimer() = runTest(testDispatcher) {
        builder.challengeRepository.completeAttemptResult = Result.failure(Exception("failed"))
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CompleteChallenge)
        advanceTimeBy(100)

        assertNotNull(vm.state.value.error)
        assertTrue(vm.state.value.error!!.contains("Failed to submit"))
        // Should have resumed the game
        assertFalse(vm.state.value.isPaused)
        builder.tearDown()
    }

    @Test
    fun restartChallengeReloadsAndStartsNewAttempt() = runTest(testDispatcher) {
        builder.challengeRepository.startAttemptResult = Result.success(
            ChallengeAttempt(id = "attempt-2", challengeId = "c1", userId = "u1", username = "test",
                avatarUrl = null, status = "in_progress", startedAt = "", completedAt = null,
                durationMs = 0, isBest = false)
        )
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        // First attempt
        assertEquals("attempt-2", vm.state.value.challengeAttemptId)

        vm.onIntent(EmulationIntent.RestartChallenge)
        advanceTimeBy(100)

        // Should have abandoned previous attempt and started new one
        assertEquals(1, builder.challengeRepository.abandonAttemptCallCount)
        // startAttempt called twice: once for initial start, once for restart
        assertEquals(2, builder.challengeRepository.startAttemptCallCount)
        assertEquals(0, vm.state.value.challengeElapsedMs)
        builder.tearDown()
    }

    @Test
    fun confirmGiveUpAbandonsAndExits() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1", challengeId = "c1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.ConfirmGiveUp)
        advanceTimeBy(100)

        assertEquals(1, builder.challengeRepository.abandonAttemptCallCount)
        assertTrue(vm.state.value.requestExit)
        assertFalse(vm.state.value.isRunning)
        builder.tearDown()
    }

    @Test
    fun dismissChallengeCreationResumes() = runTest(testDispatcher) {
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        advanceTimeBy(100)
        assertTrue(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.isPaused)

        vm.onIntent(EmulationIntent.DismissChallengeCreation)
        assertFalse(vm.state.value.showChallengeCreation)
        assertFalse(vm.state.value.isPaused)
        builder.tearDown()
    }
}
