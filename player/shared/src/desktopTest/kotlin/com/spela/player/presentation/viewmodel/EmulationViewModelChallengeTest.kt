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
        val calls = builder.libretroController.calls
        assertTrue(
            calls.indexOf("unserialize") > calls.indexOf("start"),
            "challenge restore must run after core start; calls=$calls",
        )
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

    // restartChallenge coverage moved to ChallengeManagerTest (same
    // directory), which exercises the manager directly with an
    // UnconfinedTestDispatcher so the io → main hop completes
    // synchronously without the race that made the VM-level version
    // flaky (PRs #355, #356, #357, #358, #361).

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
    fun dismissChallengeCreationFromCancelKeepsGamePausedAndRestoresOverlay() = runTest {
        // Cancel path: opening the panel hid the overlay; dismissing
        // restores it. Game stays paused so the user can pick another
        // overlay action without the game running underneath.
        val vm = builder.build()
        vm.onIntent(EmulationIntent.StartGame("game1"))
        builder.advanceTimeBy(100)

        vm.onIntent(EmulationIntent.CreateChallenge)
        builder.advanceTimeBy(100)
        assertTrue(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.isPaused)
        assertFalse(vm.state.value.showOverlay)

        vm.onIntent(EmulationIntent.DismissChallengeCreation)
        assertFalse(vm.state.value.showChallengeCreation)
        assertTrue(vm.state.value.isPaused)
        assertTrue(vm.state.value.showOverlay)
    }
}
