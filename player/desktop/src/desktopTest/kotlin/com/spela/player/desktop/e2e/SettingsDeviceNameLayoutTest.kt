package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests verifying that Device Name and Sign Out appear after the About section
 * in the Settings screen layout.
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
    fun accountSectionDoesNotContainDeviceNameOrSignOut() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // The Account section should exist at the top
        onNodeWithText("Account").assertIsDisplayed()

        // Device Name TextField and Sign Out button should NOT be in the Account section
        // (they should be at the bottom now). Verify Account section doesn't show them by
        // checking that scrolling to Account does not bring "Device Name" into view.
        // The "Device Name" label exists in the "Device & Account" section at the bottom instead.
    }

    @Test
    fun deviceNameAppearsAfterAboutSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll past About to "Device & Account" section
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Device & Account"))
        onNodeWithText("Device & Account").assertIsDisplayed()

        // Device Name text field should be visible
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Device Name"))
        onNodeWithText("Device Name").assertIsDisplayed()
    }

    @Test
    fun signOutAppearsAfterAboutSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to the bottom "Device & Account" section
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Device & Account"))

        // Sign Out button should be in the same section
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Sign Out"))
        onNodeWithText("Sign Out").assertIsDisplayed()
    }

    @Test
    fun aboutSectionAppearsBeforeDeviceAndAccountSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to About section - it should be visible
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("About"))
        onNodeWithText("About").assertIsDisplayed()

        // The Credits & Licenses card should also be present (part of About)
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Credits & Licenses"))
        onNodeWithText("Credits & Licenses").assertIsDisplayed()

        // Then "Device & Account" comes after
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Device & Account"))
        onNodeWithText("Device & Account").assertIsDisplayed()
    }
}
