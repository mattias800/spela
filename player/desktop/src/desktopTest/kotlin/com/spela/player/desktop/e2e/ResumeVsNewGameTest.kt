package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.GameSession
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.PendingLaunch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for the resume-vs-new-game split button menu on the game detail screen.
 *
 * When a game has existing sessions (saves), the split button shows:
 * - Primary button: "Resume" (loads last save)
 * - Menu item: "Continue from Title Screen" (skips auto-load, reuses session)
 * - Menu item: "Start fresh playthrough" (skips auto-load, forces new session — was
 *   "New Game" before #900; renamed to disambiguate from the top-button label
 *   which now also says "New game" when no session exists)
 *
 * When no sessions exist, only "New game" and "Delete Download" appear.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ResumeVsNewGameTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun SpelaTestHarness.addSessionForGame(gameId: String = "1") {
        sessionRepo.sessions.add(
            GameSession(
                id = "session-1",
                gameId = gameId,
                name = "Default",
            )
        )
    }

    private fun ComposeUiTest.showGameDetail(harness: SpelaTestHarness) {
        navigateToGameDetail(harness, "1")
    }

    private fun ComposeUiTest.openMoreOptions(harness: SpelaTestHarness) {
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)
    }

    @Test
    fun menuReflectsGameWithSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        showGameDetail(harness)

        onNodeWithText("Resume").assertIsDisplayed()

        openMoreOptions(harness)

        onNodeWithText("Continue from Title Screen").assertIsDisplayed()
        onNodeWithText("Keep your in-game save, start from the beginning").assertIsDisplayed()
        onNodeWithText("Start fresh playthrough").assertIsDisplayed()
        onNodeWithText("Keep your existing saves, start a separate playthrough from scratch").assertIsDisplayed()
        onNodeWithText("Delete Download").assertIsDisplayed()
    }

    @Test
    fun menuReflectsGameWithoutSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        showGameDetail(harness)

        onNodeWithText("New game").assertIsDisplayed()

        openMoreOptions(harness)

        onNodeWithText("Continue from Title Screen").assertDoesNotExist()
        onNodeWithText("Start fresh playthrough").assertDoesNotExist()
        onNodeWithText("Delete Download").assertIsDisplayed()
    }

    @Test
    fun resumeButtonDispatchesLaunchWithAutoLoad() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        showGameDetail(harness)

        onNodeWithText("Resume").performClick()
        advance(harness)

        val resumeLaunch = capturedLaunch
        assertTrue(resumeLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", resumeLaunch.gameId)
        assertFalse(resumeLaunch.skipAutoLoad, "skipAutoLoad should be false")
        assertFalse(resumeLaunch.forceNewSession, "forceNewSession should be false")
    }

    @Test
    fun continueFromTitleScreenDispatchesLaunchWithoutAutoLoad() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        showGameDetail(harness)

        openMoreOptions(harness)
        onNodeWithText("Continue from Title Screen").performClick()
        advance(harness)

        val titleScreenLaunch = capturedLaunch
        assertTrue(titleScreenLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", titleScreenLaunch.gameId)
        assertTrue(titleScreenLaunch.skipAutoLoad, "skipAutoLoad should be true")
        assertFalse(titleScreenLaunch.forceNewSession, "forceNewSession should be false")
    }

    @Test
    fun startFreshPlaythroughDispatchesLaunchForNewSession() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        showGameDetail(harness)

        openMoreOptions(harness)
        onNodeWithText("Start fresh playthrough").performClick()
        advance(harness)

        val newGameLaunch = capturedLaunch
        assertTrue(newGameLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", newGameLaunch.gameId)
        assertTrue(newGameLaunch.skipAutoLoad, "skipAutoLoad should be true")
        assertTrue(newGameLaunch.forceNewSession, "forceNewSession should be true")
    }
}
