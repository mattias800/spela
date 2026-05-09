package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Regression test for the focus-restore-on-back bug:
 * - Open the Home dashboard with multiple Continue Playing items.
 * - Focus a non-first item (game #3).
 * - Navigate forward to its detail screen.
 * - Press back.
 * - The same Continue Playing item must regain focus.
 *
 * This exercises [SpCarousel]'s memoryKey/itemKey persistence and
 * is independent of input mode (works for keyboard and gamepad).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class HomeContinuePlayingFocusRestoreTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.deviceManager.setDeviceName("Test Device")
        val recents = listOf(
            game(id = "g1", title = "Castlevania"),
            game(id = "g2", title = "Super Mario Bros."),
            game(id = "g3", title = "Super Mario World"),
            game(id = "g4", title = "Mega Man 2"),
        )
        harness.gameRepo.recentGamesOverride = recents
        // Mirror into `games` so getGameDetail can find them — needed when
        // tests forward-navigate to GameDetail.
        harness.gameRepo.games = recents
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun game(
        id: String,
        title: String,
        isFavorite: Boolean = false,
    ): Game = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "SNES",
        description = "Test",
        developer = "Test",
        publisher = "Test",
        releaseDate = "1990",
        genre = "Action",
        // Non-zero file size so GameDetail's hero shows the Play split-button
        // (showCachedStyleButton requires either cached or instant-download-
        // candidate; we hit the latter at any non-zero size below the threshold).
        fileSize = 1024,
        fileName = "$id.smc",
        scrapeAttempts = 1,
        isFavorite = isFavorite,
    )

    @Test
    fun continuePlaying_focusedItemIsRestoredOnBackNav() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App() }
        advance(harness)

        val target = "Super Mario World, SNES"
        val targetCard = onNodeWithContentDescription(target)
        targetCard.assertExists()

        // Focus the third Continue Playing card.
        targetCard.requestFocus()
        advanceQuick(harness)
        targetCard.assert(isFocused())

        // Forward navigate to the game's detail screen.
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("g3"))
        )
        advance(harness)

        // Back to Home.
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)

        // The same card must regain focus.
        onNodeWithContentDescription(target).assert(isFocused())
    }

    /**
     * Real-world repro: animations ENABLED + keyboard-only navigation.
     * Mirrors the exact user path:
     *   1. Press Right arrow until Super Mario World is focused.
     *   2. Press Enter to open game detail.
     *   3. Press Escape to go back.
     *   4. Super Mario World must be focused again.
     *
     * Animations on is critical — the back transition runs AnimatedContent,
     * and the outgoing GameDetail is still composed/focused while Home's
     * focus restorer fires. Tests with `animationsEnabled = false` silently
     * miss timing bugs in this window.
     */
    @Test
    fun continuePlaying_focusRestoredAfterKeyboardEnterEscape() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App(animationsEnabled = true) }
        advance(harness)

        val target = "Super Mario World, SNES"
        // Castlevania is item 0 (the screen-default focus). Press Right
        // twice to land on Super Mario World (item 2 in the test data).
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
            pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
        }
        advanceQuick(harness)
        onNodeWithContentDescription(target).assert(isFocused())

        // Press Enter to navigate forward (clickable's keyboard activation).
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Enter)
        }
        advanceFully(harness)

        // Press Escape to go back (handled by the GamepadHandler wrapper).
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Escape)
        }
        advanceFully(harness)

        onNodeWithContentDescription(target).assert(isFocused())
    }

    /**
     * Two carousels on the Home screen, both with a previously-focused
     * item. The carousel that *most recently* owned focus must win the
     * restore — not the bottom-most one. Without the active-carousel gate,
     * each carousel's restore racing through `delay(120)` would let the
     * lower one (Favorites) clobber the upper one (Continue Playing).
     */
    @Test
    fun multiCarousel_lastFocusedCarouselWinsOnBackNav() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.deviceManager.setDeviceName("Test Device")
        harness.gameRepo.recentGamesOverride = listOf(
            game(id = "cp1", title = "Castlevania"),
            game(id = "cp2", title = "Super Mario World"),
        )
        harness.gameRepo.games = listOf(
            game(id = "fav1", title = "Chrono Trigger", isFavorite = true),
            game(id = "fav2", title = "Earthbound", isFavorite = true),
        )
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        setContent { harness.App() }
        advance(harness)

        // Step 1: focus a Favorites card and round-trip through detail.
        val favCard = onNodeWithContentDescription("Earthbound, SNES, favorited")
        favCard.assertExists()
        favCard.requestFocus()
        advanceQuick(harness)
        favCard.assert(isFocused())
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("fav2"))
        )
        advance(harness)
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)
        onNodeWithContentDescription("Earthbound, SNES, favorited").assert(isFocused())

        // Step 2: now focus a Continue Playing card. The active carousel
        // shifts. Round-trip again and Continue Playing must win — not
        // Favorites (which still has its own saved item).
        val cpCard = onNodeWithContentDescription("Super Mario World, SNES")
        cpCard.requestFocus()
        advanceQuick(harness)
        cpCard.assert(isFocused())
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("cp2"))
        )
        advance(harness)
        harness.navigationViewModel.onIntent(NavigationIntent.GoBack)
        advance(harness)
        onNodeWithContentDescription("Super Mario World, SNES").assert(isFocused())
    }
}
