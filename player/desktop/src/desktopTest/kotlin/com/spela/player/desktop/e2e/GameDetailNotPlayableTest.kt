package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * #1255: a game for a console Spela can't emulate (playable = false) must
 * surface a "not playable" notice on the detail hero, instead of leaving
 * the absent Play button as the only signal. Playable games show no notice.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailNotPlayableTest {

    private val vitaConsole = Console(
        id = "psvita",
        name = "PlayStation Vita",
        abbreviation = "VITA",
        gameCount = 1,
        colorTheme = "#1e3a8a",
        saveStateSupport = false,
    )

    private val vitaGame = Game(
        id = "vita-1",
        title = "Unsupported Vita Title",
        consoleId = "psvita",
        consoleName = "PlayStation Vita",
        playable = false,
        fileSize = 524288,
        fileName = "game.vpk",
    )

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.gameRepo.consoles = harness.gameRepo.consoles + vitaConsole
        harness.gameRepo.games = harness.gameRepo.games + vitaGame
        return harness
    }

    @Test
    fun nonPlayableGameShowsNotice() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        navigateToGameDetail(harness, "vita-1")

        onNodeWithText("Unsupported Vita Title").assertIsDisplayed()
        onNodeWithTag("game_detail_not_playable_notice").assertIsDisplayed()
    }

    @Test
    fun playableGameShowsNoNotice() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        // Castlevania (id = 1) is a regular, playable seed game.
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onNodeWithTag("game_detail_not_playable_notice").assertDoesNotExist()
    }
}
