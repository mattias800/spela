package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.TestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop E2E tests for the console detail screen changes from #1095:
 * - Terminal "Library" browse section renders for >15-game libraries
 * - Terminal section is hidden for ≤15-game libraries
 * - Admin overflow menu (MoreVert) is visible for admin users
 * - Admin overflow menu is hidden for non-admin users
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ConsoleDetailBrowseSectionTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun SpelaTestHarness.setupLargeLibrary(consoleId: String = "nes", gameCount: Int = 20) {
        val existingConsole = gameRepo.consoles.find { it.id == consoleId }!!
        gameRepo.consoles = gameRepo.consoles.map {
            if (it.id == consoleId) it.copy(gameCount = gameCount) else it
        }
        gameRepo.games = (1..gameCount).map { index ->
            Game(
                id = "large_$index",
                title = "Game $index",
                consoleId = consoleId,
                consoleName = existingConsole.name,
                developer = "Dev",
                publisher = "Pub",
                releaseDate = "2000",
                genre = "Action",
                fileSize = 131072,
                fileName = "game_$index.nes",
                scrapeAttempts = 1,
            )
        }
    }

    private fun SpelaTestHarness.setupSmallLibrary(consoleId: String = "nes", gameCount: Int = 3) {
        gameRepo.consoles = gameRepo.consoles.map {
            if (it.id == consoleId) it.copy(gameCount = gameCount) else it
        }
        gameRepo.games = gameRepo.games.filter { it.consoleId == consoleId }.take(gameCount)
    }

    // ─────────────────────────────────────────────────
    // Terminal browse section: large library (>15 games)
    // ─────────────────────────────────────────────────

    @Test
    fun terminalBrowseSectionVisibleForLargeLibrary() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 20)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        onNodeWithTag(TestTags.CONSOLE_BROWSE_ALL_SECTION).assertIsDisplayed()
        onNodeWithTag(TestTags.CONSOLE_BROWSE_ALL_CTA).assertIsDisplayed()
    }

    @Test
    fun terminalBrowseSectionContainsGameCount() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 20)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        onNodeWithText("Browse all 20", substring = true).assertIsDisplayed()
    }

    @Test
    fun terminalBrowseSectionHiddenForSmallLibrary() = runComposeUiTest {
        val harness = createHarness()
        harness.setupSmallLibrary(gameCount = 3)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        onAllNodesWithTag(TestTags.CONSOLE_BROWSE_ALL_SECTION).assertCountEquals(0)
        onAllNodesWithTag(TestTags.CONSOLE_BROWSE_ALL_CTA).assertCountEquals(0)
    }

    @Test
    fun terminalBrowseSectionHiddenForExactlyFifteenGames() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 15)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        onAllNodesWithTag(TestTags.CONSOLE_BROWSE_ALL_SECTION).assertCountEquals(0)
    }

    @Test
    fun terminalBrowseSectionVisibleForSixteenGames() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 16)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        onNodeWithTag(TestTags.CONSOLE_BROWSE_ALL_SECTION).assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────
    // Admin overflow menu visibility
    // ─────────────────────────────────────────────────

    @Test
    fun adminMenuButtonVisibleForAdminUser() = runComposeUiTest {
        val harness = createHarness()
        harness.authRepo.simulateAdminLoggedIn()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onNodeWithTag(TestTags.CONSOLE_ADMIN_MENU_BUTTON).assertIsDisplayed()
    }

    @Test
    fun adminMenuButtonHiddenForNonAdminUser() = runComposeUiTest {
        val harness = createHarness()
        // Default FakeAuthRepository returns role "player" — non-admin

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onAllNodesWithTag(TestTags.CONSOLE_ADMIN_MENU_BUTTON).assertCountEquals(0)
    }

    @Test
    fun adminMenuOpensWithSettingsItem() = runComposeUiTest {
        val harness = createHarness()
        harness.authRepo.simulateAdminLoggedIn()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onNodeWithTag(TestTags.CONSOLE_ADMIN_MENU_BUTTON).performClick()
        advanceQuick(harness)

        onNodeWithTag(TestTags.CONSOLE_ADMIN_MENU_SETTINGS).assertIsDisplayed()
    }

    // ─────────────────────────────────────────────────
    // Console settings button (available to all users)
    // ─────────────────────────────────────────────────

    @Test
    fun consoleSettingsButtonVisibleForNonAdmin() = runComposeUiTest {
        val harness = createHarness()
        // Default FakeAuthRepository returns role "player" — non-admin

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onNodeWithTag(TestTags.CONSOLE_SETTINGS_BUTTON).assertIsDisplayed()
    }

    @Test
    fun consoleSettingsButtonNavigatesToConsoleSettings() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onNodeWithTag(TestTags.CONSOLE_SETTINGS_BUTTON).performClick()
        advance(harness)

        assertEquals(
            SpScreen.ConsoleSettings("nes"),
            harness.navigationViewModel.state.value.currentScreen,
            "Console settings button should navigate to ConsoleSettings(\"nes\")",
        )
    }

    // ─────────────────────────────────────────────────
    // Banner is clean — no action buttons
    // ─────────────────────────────────────────────────

    // ─────────────────────────────────────────────────
    // First-entry focus claims the Browse-all CTA (#1166 follow-up)
    // ─────────────────────────────────────────────────

    /**
     * The Library section now sits at the top of the screen-content list
     * (directly under the banner) and its "Browse all …" SpButton is
     * marked `focusRestoreItem(isDefault = true)`. On first entry to the
     * console-detail screen, the focus-memory primitive should claim
     * focus on that button so the d-pad has somewhere to land.
     *
     * Without this guarantee the user sees "nothing focused" on entry
     * and the first d-pad press resorts to `moveFocus(Next)` which —
     * depending on composition order — can leapfrog past the curated
     * carousels (the original symptom reported alongside #1166).
     */
    @Test
    fun browseAllCtaIsFocusedOnFirstEntryForLargeLibrary() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 20)

        setContent { harness.App(animationsEnabled = true) }
        advance(harness)

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advanceFully(harness)

        onNodeWithTag(TestTags.CONSOLE_BROWSE_ALL_CTA).assert(isFocused())
    }

    @Test
    fun heroBannerHasNoBrowseGamesButton() = runComposeUiTest {
        val harness = createHarness()
        harness.setupLargeLibrary(gameCount = 20)

        setContent { harness.App() }

        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Console("nes")))
        advance(harness)

        // The old banner browse button used consoleBrowseGames tag — should be gone
        onAllNodesWithTag(TestTags.consoleBrowseGames("nes")).assertCountEquals(0)
    }
}
