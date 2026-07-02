package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.model.GameCollection
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.TestTags
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the standardised gamepad-mode screen heading (#1529).
 *
 * In gamepad mode SpTopBar is suppressed, so card/list destination screens
 * (Favorites, Play Later, All Games, Collections, Downloads) render an
 * SpScreenHeading with the screen title instead. In touch mode the heading
 * must not render — the SpTopBar (or tab bar) carries the context there.
 * The heading is asserted by test tag to distinguish it from the touch-mode
 * SpTopBar title, which uses the same text.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScreenHeadingTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun SpelaTestHarness.withFavorite() = apply {
        val games = gameRepo.games.toMutableList()
        games[0] = games[0].copy(isFavorite = true)
        gameRepo.games = games
    }

    private fun SpelaTestHarness.withPlayLater() = apply {
        val games = gameRepo.games.toMutableList()
        games[0] = games[0].copy(isInPlayLater = true)
        gameRepo.games = games
    }

    private fun SpelaTestHarness.withCollection() = apply {
        collectionRepo.myCollections = listOf(
            GameCollection(id = "c1", userId = "1", username = "player", name = "RPG Classics", gameCount = 3),
        )
    }

    private fun SpelaTestHarness.withDownload() = apply {
        downloadRepo.addDownloadedGame(
            DownloadedGame(
                gameId = "1",
                title = "Castlevania",
                consoleName = "NES",
                coverUrl = null,
                fileSizeBytes = 131072,
                downloadedAt = 1700000000000,
            )
        )
    }

    private fun ComposeUiTest.openInGamepadMode(harness: SpelaTestHarness, screen: SpScreen) {
        setContent { harness.App() }
        advance(harness)
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(screen))
        advance(harness)
    }

    private fun ComposeUiTest.assertHeading(text: String) {
        onNodeWithTag(TestTags.SCREEN_HEADING).assertExists().assertTextEquals(text)
    }

    // ---- Gamepad mode: each survey screen shows its heading ----

    @Test
    fun favoritesShowsHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness().withFavorite()
        openInGamepadMode(harness, SpScreen.Favorites)
        assertHeading("Favorites")
    }

    @Test
    fun playLaterShowsHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness().withPlayLater()
        openInGamepadMode(harness, SpScreen.PlayLater)
        assertHeading("Play Later")
    }

    @Test
    fun allGamesShowsHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness()
        openInGamepadMode(harness, SpScreen.AllGames)
        assertHeading("All Games")
    }

    @Test
    fun collectionsShowsHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness().withCollection()
        openInGamepadMode(harness, SpScreen.Collections)
        assertHeading("Collections")
    }

    @Test
    fun downloadsShowsHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness().withDownload()
        openInGamepadMode(harness, SpScreen.Downloads)
        assertHeading("Downloads")
    }

    // ---- Touch mode: no gamepad heading, SpTopBar carries the title ----

    @Test
    fun favoritesHidesGamepadHeadingInTouchMode() = runComposeUiTest {
        val harness = createHarness().withFavorite()

        setContent { harness.App() }
        advance(harness)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Favorites))
        advance(harness)

        onNodeWithTag(TestTags.SCREEN_HEADING).assertDoesNotExist()
        // The touch-mode SpTopBar still shows the title.
        onNodeWithText("Favorites").assertExists()
    }

    @Test
    fun collectionsHidesGamepadHeadingInTouchMode() = runComposeUiTest {
        val harness = createHarness().withCollection()

        setContent { harness.App() }
        advance(harness)
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Collections))
        advance(harness)

        onNodeWithTag(TestTags.SCREEN_HEADING).assertDoesNotExist()
    }

    // ---- Empty state: heading yields to the (self-describing) empty state ----

    @Test
    fun favoritesEmptyStateHasNoHeadingInGamepadMode() = runComposeUiTest {
        val harness = createHarness()
        openInGamepadMode(harness, SpScreen.Favorites)

        // The empty state replaces the grid entirely (and describes the screen
        // itself), so no heading is expected.
        onNodeWithTag(TestTags.SCREEN_HEADING).assertDoesNotExist()
    }
}
