package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests verifying that Device Name and Sign Out appear in the About category.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SettingsDeviceNameLayoutTest {

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
    fun categoryListShowsAllCategories() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // All categories should be visible in the left panel
        onNodeWithContentDescription("General").assertExists()
        onNodeWithContentDescription("Emulation").assertExists()
        onNodeWithContentDescription("Controls").assertExists()
        onNodeWithContentDescription("Achievements").assertExists()
        onNodeWithContentDescription("Storage & Sync").assertExists()
        onNodeWithContentDescription("About").assertExists()
    }

    @Test
    fun deviceNameAppearsInAboutCategory() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Click About category
        onNodeWithContentDescription("About").performClick()
        advanceQuick(harness)

        onNodeWithText("Device & Account").assertIsDisplayed()
        onNodeWithText("Device Name").assertIsDisplayed()
    }

    @Test
    fun signOutAppearsInAboutCategory() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Click About category
        onNodeWithContentDescription("About").performClick()
        advanceQuick(harness)

        onNodeWithText("Sign Out").assertIsDisplayed()
    }

    @Test
    fun aboutCategoryShowsCreditsAndLicenses() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Click About category
        onNodeWithContentDescription("About").performClick()
        advanceQuick(harness)

        onNodeWithText("Credits & Licenses").assertIsDisplayed()
    }
}
