package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import com.spela.player.domain.model.WiiControlScheme
import com.spela.player.domain.model.WiiIrSource
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the per-game Wii control scheme section on the game
 * detail screen (#1559). The in-game drawer path is covered by
 * EmulationViewModel lifecycle tests (running-session E2E is not
 * desktop-fakeable at the drawer level).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class WiiControlSchemeTest {

    private fun createHarness(wiiGame: Boolean): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        if (wiiGame) {
            val games = harness.gameRepo.games.toMutableList()
            games[0] = games[0].copy(consoleId = "wii", consoleName = "Nintendo Wii")
            harness.gameRepo.games = games
        }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun wiiGameDetailShowsSchemeSectionWithNunchukDefault() = runComposeUiTest {
        val harness = createHarness(wiiGame = true)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        onNodeWithTag("wii-control-scheme-section").assertExists()
        onNodeWithTag("wii-scheme-option-nunchuk", useUnmergedTree = true).assertExists()
        // Motion expectations hint (#1585).
        onNodeWithTag("wii-motion-hint", useUnmergedTree = true).assertExists()
    }

    @Test
    fun selectingSchemePersistsPerGame() = runComposeUiTest {
        val harness = createHarness(wiiGame = true)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        onNodeWithTag("wii-scheme-option-classic", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        advanceQuick(harness)

        assertEquals(
            WiiControlScheme.CLASSIC_CONTROLLER,
            harness.preferencesRepo.wiiControlSchemes["1"],
        )
    }

    @Test
    fun selectingIrSourcePersistsPerGame() = runComposeUiTest {
        val harness = createHarness(wiiGame = true)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        onNodeWithTag("wii-ir-source-option-touch_pointer", useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        advanceQuick(harness)

        assertEquals(
            WiiIrSource.TOUCH_POINTER,
            harness.preferencesRepo.wiiIrSources["1"],
        )
    }

    @Test
    fun storedIrSourceIsPreselectedOnReturn() = runComposeUiTest {
        val harness = createHarness(wiiGame = true)
        harness.preferencesRepo.wiiIrSources["1"] = WiiIrSource.TOUCH_POINTER

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        onNodeWithTag("wii-ir-source-option-touch_pointer", useUnmergedTree = true)
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))
    }

    @Test
    fun storedSchemeIsPreselectedOnReturn() = runComposeUiTest {
        val harness = createHarness(wiiGame = true)
        harness.preferencesRepo.wiiControlSchemes["1"] = WiiControlScheme.WIIMOTE_SIDEWAYS

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        // SpRadioOption expresses selection via stateDescription, not the
        // Selected semantics property.
        onNodeWithTag("wii-scheme-option-wiimote_sideways", useUnmergedTree = true)
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))
    }

    @Test
    fun nonWiiGameDetailHasNoSchemeSection() = runComposeUiTest {
        val harness = createHarness(wiiGame = false)

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        advance(harness)

        onNodeWithTag("wii-control-scheme-section").assertDoesNotExist()
    }
}
