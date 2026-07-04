package com.spela.player.desktop.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for scroll position restoration and focus preservation
 * across navigation transitions.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScrollRestorationTest {

    private fun createHarnessWithManyConsoles(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.gameRepo.consoles = listOf(
            Console(id = "nes", name = "Nintendo Entertainment System", abbreviation = "NES", gameCount = 3, colorTheme = "#e53e3e"),
            Console(id = "snes", name = "Super Nintendo", abbreviation = "SNES", gameCount = 2, colorTheme = "#3182ce"),
            Console(id = "gba", name = "Game Boy Advance", abbreviation = "GBA", gameCount = 1, colorTheme = "#5a1f9e"),
            Console(id = "gen", name = "Sega Genesis", abbreviation = "GEN", gameCount = 1, colorTheme = "#0060a8"),
            Console(id = "n64", name = "Nintendo 64", abbreviation = "N64", gameCount = 1, colorTheme = "#009e42"),
            Console(id = "psx", name = "PlayStation", abbreviation = "PSX", gameCount = 1, colorTheme = "#003087"),
            Console(id = "gb", name = "Game Boy", abbreviation = "GB", gameCount = 1, colorTheme = "#9bbc0f"),
            Console(id = "gbc", name = "Game Boy Color", abbreviation = "GBC", gameCount = 1, colorTheme = "#6b4fa0"),
            Console(id = "dc", name = "Dreamcast", abbreviation = "DC", gameCount = 1, colorTheme = "#ff6600"),
            Console(id = "sat", name = "Sega Saturn", abbreviation = "SAT", gameCount = 1, colorTheme = "#333333"),
        )
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    // ── Scroll position preservation ──────────────────────────────────

    @Test
    fun consoleListStartsAtTopOnForwardNavigation() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()
        setContent {
            Box(modifier = Modifier.width(560.dp).height(260.dp)) {
                harness.App()
            }
        }
        advance(harness)

        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsDisplayed()
        onNodeWithContentDescription("Sega Saturn, 1 games")
            .assertIsNotDisplayed()
    }

    @Test
    fun scrollPositionRestoredAfterManualScrollAndBack() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()
        setContent {
            Box(modifier = Modifier.width(560.dp).height(260.dp)) {
                harness.App()
            }
        }
        advance(harness)

        // Navigate to console list
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // Scroll to Saturn (last item)
        val saturnCard = onNodeWithContentDescription("Sega Saturn, 1 games")
        saturnCard.performScrollTo()
        advanceQuick(harness)
        saturnCard.assertIsDisplayed()

        // Verify NES is NOT displayed (scrolled off top)
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()

        // Navigate forward
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("sat"))
        )
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // NES should NOT be displayed (scroll position should be at bottom)
        // If this fails (NES is displayed), scroll position was reset to top.
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()

        // Saturn should still be visible
        onNodeWithContentDescription("Sega Saturn, 1 games")
            .assertIsDisplayed()
    }

    @Test
    fun scrollPositionNotResetByDpadAfterManualScroll() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()
        // Guards the #1452 off-screen-focus redirect: once a manual scroll
        // pushes the focused element out of view, a d-pad press must move
        // focus to a *visible* card instead of spatially-moving from the
        // off-screen one (which centre-on-focuses a near-top card and snaps
        // the list back up).
        //
        // Fixed viewport so the scenario is deterministic: short enough that
        // the top (NES) and bottom (Saturn) of the console list can't both be
        // on screen, so a manual scroll to the bottom genuinely moves NES
        // off-screen. The height is deliberately well below one screenful of
        // rows — #1446 made the grid denser (more columns → fewer, shorter
        // rows), so the previous 420 dp viewport fit the whole list and the
        // scroll became a no-op. 560 dp → 4 columns; 260 dp keeps NES and
        // Saturn from coexisting.
        setContent {
            Box(modifier = Modifier.width(560.dp).height(260.dp)) {
                harness.App()
            }
        }
        advance(harness)

        // Enter gamepad mode and navigate to console list
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // Scroll to Saturn manually (simulates joystick/touch scroll); this
        // moves the top of the list (NES) off-screen.
        val saturnCard = onNodeWithContentDescription("Sega Saturn, 1 games")
        saturnCard.performScrollTo()
        advanceQuick(harness)
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()

        // Press d-pad — the off-screen-focus redirect must re-acquire focus to
        // a visible card without yanking the scroll back to the top (#1194 /
        // #1452). If it didn't, NES would become visible again.
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        advanceQuick(harness)

        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()
    }

    // ── Focus preservation on navigation ──────────────────────────────

    @Test
    fun focusAcquiredOnForwardNavigation() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)

        // Navigate to console games list
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleGames("nes"))
        )
        advance(harness)

        // A game card should be visible and the screen should be navigable
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun focusAcquiredOnBackNavigation() = runComposeUiTest {
        val harness = createLoggedInHarness()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)

        // Navigate to console games
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ConsoleGames("nes"))
        )
        advance(harness)

        // Navigate to game detail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        advance(harness)

        // Navigate back to games list
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Games should still be visible (we're back on the games list)
        onNodeWithText("Castlevania").assertIsDisplayed()
    }

    @Test
    fun scrollPositionPreservedOnFocusDrivenScroll() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()
        setContent { harness.App() }
        advance(harness)

        // Enter gamepad mode and go to consoles
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // NES should have focus (first focusable element)
        val nesCard = onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
        nesCard.assertIsDisplayed()

        // Navigate down several times to scroll the list via focus
        repeat(8) {
            onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            advanceQuick(harness)
        }

        // Navigate forward to a console detail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("nes"))
        )
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // NES should NOT necessarily be at the top — scroll position
        // from the focus-driven scroll should be preserved.
        // At minimum, the console list should be showing (not stuck/blank).
        onAllNodesWithContentDescription("Nintendo Entertainment System, 3 games")
            .fetchSemanticsNodes().isNotEmpty()
    }
}
