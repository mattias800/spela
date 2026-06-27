package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for game detail split button and actions menu.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailActionsMenuTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ---- Split button tests ----

    @Test
    fun cachedGameShowsPlaySplitButtonAndDeleteDownloadAction() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
        onNodeWithContentDescription("More options").assertIsDisplayed()
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)
        onNodeWithText("Delete Download").assertIsDisplayed()
    }

    @Test
    fun uncachedGameShowsDownloadButtonAndHidesOpenDownloadFolderAction() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        // Game id="6" (FF6) is > 16 MB so it follows the legacy
        // Download-button path (#932 routes sub-threshold games to a
        // silent instant-download Play button instead).
        navigateToGameDetail(harness, "6")

        onNodeWithTag("game_detail_download_button").assertIsDisplayed()

        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)
        onNodeWithText("Show in folder").assertDoesNotExist()
    }

    // ---- Actions menu tests ----

    @Test
    fun actionsMenuShowsItemsTogglesFavoriteAndDismissesOnItemClick() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("More actions").assertIsDisplayed()
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)
        onNodeWithText("Favorite").assertIsDisplayed()
        onNodeWithText("Play Later").assertIsDisplayed()
        onNodeWithText("Add to Collection").assertIsDisplayed()

        onNodeWithText("Favorite").performClick()
        advanceQuick(harness)
        onNodeWithText("Play Later").assertDoesNotExist()

        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)
        onNodeWithText("Unfavorite").assertIsDisplayed()
    }

    @Test
    fun cachedGameActionsMenuShowsOpenDownloadFolder() = runComposeUiTest {
        // #1259: downloaded games on desktop expose "Show in folder" in the
        // actions menu (currentPlatform() is non-Android in the desktop test).
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        onNodeWithText("Show in folder").assertIsDisplayed()
    }
}
