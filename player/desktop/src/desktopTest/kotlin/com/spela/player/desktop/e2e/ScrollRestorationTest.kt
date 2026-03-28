package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for scroll position restoration on back navigation.
 * When navigating forward then back, the scroll position should be
 * preserved so the user returns to where they were.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ScrollRestorationTest {

    private fun createHarnessWithManyConsoles(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        // Override with 10 consoles to force scrolling
        harness.gameRepo.consoles = listOf(
            Console("nes", "Nintendo Entertainment System", "NES", 3, "#e53e3e"),
            Console("snes", "Super Nintendo", "SNES", 2, "#3182ce"),
            Console("gba", "Game Boy Advance", "GBA", 1, "#5a1f9e"),
            Console("gen", "Sega Genesis", "GEN", 1, "#0060a8"),
            Console("n64", "Nintendo 64", "N64", 1, "#009e42"),
            Console("psx", "PlayStation", "PSX", 1, "#003087"),
            Console("gb", "Game Boy", "GB", 1, "#9bbc0f"),
            Console("gbc", "Game Boy Color", "GBC", 1, "#6b4fa0"),
            Console("dc", "Dreamcast", "DC", 1, "#ff6600"),
            Console("sat", "Sega Saturn", "SAT", 1, "#333333"),
        )
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun consoleListScrollPositionRestoredOnBack() = runComposeUiTest {
        val harness = createHarnessWithManyConsoles()
        setContent { harness.App() }
        advance(harness)

        // Navigate to console list (10 consoles — requires scrolling)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Consoles)
        )
        advance(harness)

        // NES should be visible at the top
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsDisplayed()

        // Scroll to Sega Saturn (last item, definitely off-screen)
        val saturnCard = onNodeWithContentDescription("Sega Saturn, 1 games")
        saturnCard.performScrollTo()
        advanceQuick(harness)
        saturnCard.assertIsDisplayed()

        // NES should now be off-screen
        onNodeWithContentDescription("Nintendo Entertainment System, 3 games")
            .assertIsNotDisplayed()

        // Navigate forward to Saturn console detail
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Console("sat"))
        )
        advance(harness)

        // Navigate back
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // Saturn should still be visible (scroll position restored)
        onNodeWithContentDescription("Sega Saturn, 1 games")
            .assertIsDisplayed()
    }
}
