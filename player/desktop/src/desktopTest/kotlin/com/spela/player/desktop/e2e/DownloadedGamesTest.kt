package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the Downloaded Games list on the Downloads screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DownloadedGamesTest {

    @Test
    fun downloadsScreenShowsDownloadedGamesList() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.downloadRepo.addDownloadedGame(
            DownloadedGame(
                gameId = "1",
                title = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
                fileSizeBytes = 131072,
                downloadedAt = 1700000000000,
            )
        )
        harness.downloadRepo.addDownloadedGame(
            DownloadedGame(
                gameId = "4",
                title = "Chrono Trigger",
                consoleName = "SNES",
                coverUrl = null,
                fileSizeBytes = 4194304,
                downloadedAt = 1700001000000,
            )
        )

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Downloads))

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Downloaded Games").assertIsDisplayed()
        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithText("Chrono Trigger").assertIsDisplayed()
    }

    @Test
    fun tappingDownloadedGameNavigatesToGameDetail() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.downloadRepo.addDownloadedGame(
            DownloadedGame(
                gameId = "1",
                title = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
                fileSizeBytes = 131072,
                downloadedAt = 1700000000000,
            )
        )

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Downloads))

        setContent { harness.App() }
        advance(harness)

        // Tap on the downloaded game
        onNodeWithText("Castlevania").performClick()
        advance(harness)

        // Should navigate to GameDetail screen
        val currentScreen = harness.navigationViewModel.state.value.currentScreen
        assertEquals(SpScreen.GameDetail("1"), currentScreen)
    }

    @Test
    fun deleteRemovesGameFromList() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.downloadRepo.addDownloadedGame(
            DownloadedGame(
                gameId = "1",
                title = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
                fileSizeBytes = 131072,
                downloadedAt = 1700000000000,
            )
        )

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Downloads))

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Castlevania").assertIsDisplayed()

        // Click Delete button
        onNodeWithText("Delete").performClick()
        advance(harness)

        // Game should be removed
        onNodeWithText("Castlevania").assertDoesNotExist()
    }

    @Test
    fun emptyStateWhenNoDownloads() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Downloads))

        setContent { harness.App() }
        advance(harness)

        // No "Downloaded Games" heading should be visible
        onNodeWithText("Downloaded Games").assertDoesNotExist()
    }
}
