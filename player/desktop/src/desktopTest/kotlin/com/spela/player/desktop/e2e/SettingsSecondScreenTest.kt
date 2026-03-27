package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Second Screen default page selector in Settings > Emulation.
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

        // Click Emulation category (Second Screen is under Emulation)
        onNodeWithContentDescription("Emulation").performClick()
        advanceQuick(harness)

        onNodeWithText("Second Screen").assertIsDisplayed()
        onNodeWithText("Art Display").assertIsDisplayed()
        onNodeWithText("Game artwork from SteamGridDB").assertIsDisplayed()
        onNodeWithText("Dashboard").assertIsDisplayed()
        onNodeWithText("Save Slots").assertIsDisplayed()
        onNodeWithText("Touch gamepad controls").assertIsDisplayed()
    }
}
