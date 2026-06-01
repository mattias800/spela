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
    fun cachedGameShowsPlayButton() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithTag("game_detail_play_button").assertIsDisplayed()
    }

    @Test
    fun uncachedGameShowsDownloadButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        // Game id="6" (FF6) is > 16 MB so it follows the legacy
        // Download-button path (#932 routes sub-threshold games to a
        // silent instant-download Play button instead).
        navigateToGameDetail(harness, "6")

        onNodeWithTag("game_detail_download_button").assertIsDisplayed()
    }

    @Test
    fun cachedGameSplitButtonShowsMoreOptions() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // The split button should have a "More options" trigger
        onNodeWithContentDescription("More options").assertIsDisplayed()
    }

    @Test
    fun splitButtonMenuShowsDeleteDownload() = runComposeUiTest {
        val harness = createLoggedInHarness()
        harness.downloadRepo.preCacheGame("1")

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the split button dropdown menu
        onNodeWithContentDescription("More options").performClick()
        advanceQuick(harness)

        // Should show "Delete Download" menu item
        onNodeWithText("Delete Download").assertIsDisplayed()
    }

    // ---- Actions menu tests ----

    @Test
    fun actionsMenuButtonIsVisible() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("More actions").assertIsDisplayed()
    }

    @Test
    fun actionsMenuShowsFavoriteItem() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the actions menu
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        onNodeWithText("Favorite").assertIsDisplayed()
    }

    @Test
    fun actionsMenuShowsPlayLaterItem() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the actions menu
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        onNodeWithText("Play Later").assertIsDisplayed()
    }

    @Test
    fun actionsMenuShowsAddToCollectionItem() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the actions menu
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        onNodeWithText("Add to Collection").assertIsDisplayed()
    }

    @Test
    fun favoriteToggleChangesMenuItemText() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the actions menu and click Favorite
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)
        onNodeWithText("Favorite").performClick()
        advanceQuick(harness)

        // Re-open the actions menu
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        // Should now show "Unfavorite"
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

    @Test
    fun uncachedGameActionsMenuHidesOpenDownloadFolder() = runComposeUiTest {
        // Not downloaded → nothing to reveal → item hidden.
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        onNodeWithText("Show in folder").assertDoesNotExist()
    }

    @Test
    fun actionsMenuDismissesOnItemClick() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        // Open the actions menu
        onNodeWithContentDescription("More actions").performClick()
        advanceQuick(harness)

        // Click a menu item
        onNodeWithText("Favorite").performClick()
        advanceQuick(harness)

        // Menu should be dismissed
        onNodeWithText("Play Later").assertDoesNotExist()
    }
}
