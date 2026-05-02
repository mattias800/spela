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

    /** Add a session for game "1" so hasSaves becomes true. */
    private fun SpelaTestHarness.addSessionForGame(gameId: String = "1") {
        sessionRepo.sessions.add(
            GameSession(
                id = "session-1",
                gameId = gameId,
                name = "Default",
            )
        )
    }

    // ---- "Continue from Title Screen" visibility ----

    @Test
    fun continueFromTitleScreenAppearsWhenGameHasSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // "Continue from Title Screen" should be visible
        onNodeWithText("Continue from Title Screen").assertIsDisplayed()
    }

    @Test
    fun continueFromTitleScreenDoesNotAppearWhenNoSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        // No sessions added — hasSaves is false

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // "Continue from Title Screen" should NOT exist
        onNodeWithText("Continue from Title Screen").assertDoesNotExist()
    }

    // ---- "New Game" visibility ----

    @Test
    fun newGameMenuItemAppearsWhenGameHasSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // "New Game" should be visible
        onNodeWithText("Start fresh playthrough").assertIsDisplayed()
    }

    @Test
    fun newGameMenuItemDoesNotAppearWhenNoSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        // No sessions — hasSaves is false

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // "New Game" should NOT exist
        onNodeWithText("Start fresh playthrough").assertDoesNotExist()
    }

    // ---- Primary button label changes ----

    @Test
    fun primaryButtonShowsResumeWhenGameHasSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Primary button should read "Resume"
        onNodeWithText("Resume").assertIsDisplayed()
    }

    @Test
    fun primaryButtonShowsNewGameWhenNoSaves() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        // No sessions

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Primary button should read "New game" (was "Play" before #900;
        // we say "New game" so the user knows there's no save state to
        // resume — same verb as the menu's fresh-playthrough action,
        // matching honestly because the action IS the same when no
        // session exists yet).
        onNodeWithText("New game").assertIsDisplayed()
    }

    // ---- Intent dispatch verification ----

    @Test
    fun continueFromTitleScreenDispatchesCorrectIntent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open menu and click "Continue from Title Screen"
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)
        onNodeWithText("Continue from Title Screen").performClick()
        advance(harness)

        // Should dispatch PrepareLaunch with skipAutoLoad=true, forceNewSession=false
        val titleScreenLaunch = capturedLaunch
        assertTrue(titleScreenLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", titleScreenLaunch.gameId)
        assertTrue(titleScreenLaunch.skipAutoLoad, "skipAutoLoad should be true")
        assertFalse(titleScreenLaunch.forceNewSession, "forceNewSession should be false")
    }

    @Test
    fun newGameDispatchesCorrectIntent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open menu and click "New Game"
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)
        onNodeWithText("Start fresh playthrough").performClick()
        advance(harness)

        // Should dispatch PrepareLaunch with skipAutoLoad=true, forceNewSession=true
        val newGameLaunch = capturedLaunch
        assertTrue(newGameLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", newGameLaunch.gameId)
        assertTrue(newGameLaunch.skipAutoLoad, "skipAutoLoad should be true")
        assertTrue(newGameLaunch.forceNewSession, "forceNewSession should be true")
    }

    @Test
    fun resumeButtonDispatchesCorrectIntent() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        var capturedLaunch: PendingLaunch? = null
        harness.scope.launch {
            harness.emulationViewModel.launchReady.collect { capturedLaunch = it }
        }

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Click the primary "Resume" button
        onNodeWithText("Resume").performClick()
        advance(harness)

        // Should dispatch PrepareLaunch with skipAutoLoad=false, forceNewSession=false
        val resumeLaunch = capturedLaunch
        assertTrue(resumeLaunch != null, "Expected a PendingLaunch to be emitted")
        assertEquals("1", resumeLaunch.gameId)
        assertFalse(resumeLaunch.skipAutoLoad, "skipAutoLoad should be false")
        assertFalse(resumeLaunch.forceNewSession, "forceNewSession should be false")
    }

    // ---- Menu item descriptions ----

    @Test
    fun continueFromTitleScreenShowsDescription() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // The subtitle description should be visible
        onNodeWithText("Keep your in-game save, start from the beginning").assertIsDisplayed()
    }

    @Test
    fun newGameShowsDescription() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // The subtitle description should be visible
        onNodeWithText("Keep your existing saves, start a separate playthrough from scratch").assertIsDisplayed()
    }

    // ---- Delete Download still present ----

    @Test
    fun deleteDownloadAlwaysPresentInMenu() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")
        harness.addSessionForGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // "Delete Download" should still be in the menu alongside the new items
        onNodeWithText("Delete Download").assertIsDisplayed()
    }
}
