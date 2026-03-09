package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Second Screen default page selector in Settings.
 *
 * Verifies:
 * - Settings screen shows a "Second Screen" section
 * - All four page options are rendered (Art Display, Controls, Dashboard, Save Slots)
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SettingsSecondScreenTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToSettings(harness: SpelaTestHarness) {
        harness.settingsViewModel.onIntent(SettingsIntent.LoadSettings)
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Settings)
        )
        advanceFully(harness)
    }

    @Test
    fun settingsShowsSecondScreenPageSelector() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to the "Second Screen" section header
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Second Screen"))
        onNodeWithText("Second Screen").assertIsDisplayed()

        // Verify all four radio options are present
        // SpRadioOption uses the title as contentDescription with role RadioButton
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Art Display"))
        onNodeWithText("Art Display").assertIsDisplayed()
        onNodeWithText("Game artwork from SteamGridDB").assertIsDisplayed()

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Dashboard"))
        onNodeWithText("Dashboard").assertIsDisplayed()
        onNodeWithText("Stats and quick actions").assertIsDisplayed()

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Save Slots"))
        onNodeWithText("Save Slots").assertIsDisplayed()
        onNodeWithText("Visual save slot management").assertIsDisplayed()

        // Controls option (scroll back up if needed)
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Touch gamepad controls"))
        onNodeWithText("Controls").assertIsDisplayed()
        onNodeWithText("Touch gamepad controls").assertIsDisplayed()
    }
}
