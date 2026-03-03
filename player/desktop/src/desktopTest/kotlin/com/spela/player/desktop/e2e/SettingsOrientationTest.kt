package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the orientation lock setting in the Display section.
 *
 * Verifies:
 * - Display section renders with three radio options (Auto, Landscape, Portrait)
 * - "Auto" is selected by default
 * - Clicking an option updates the ViewModel state
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SettingsOrientationTest {

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
    fun displaySectionShowsOrientationOptions() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        // Scroll to Display section header
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Display"))
        onNodeWithText("Display").assertIsDisplayed()

        // Scroll to each orientation option
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Follow system orientation"))
        onNodeWithText("Auto").assertExists()

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Lock to landscape orientation"))
        onNodeWithText("Landscape").assertExists()

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Lock to portrait orientation"))
        onNodeWithText("Portrait").assertExists()
    }

    @Test
    fun autoIsSelectedByDefault() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        assertEquals("auto", harness.settingsViewModel.state.value.orientationLock)
    }

    @Test
    fun selectingLandscapeUpdatesState() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Lock to landscape orientation"))

        onNodeWithText("Landscape").performClick()
        advance(harness)

        assertEquals("landscape", harness.settingsViewModel.state.value.orientationLock)
        assertEquals("landscape", harness.preferencesRepo.getOrientationLock())
    }

    @Test
    fun selectingPortraitUpdatesState() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Lock to portrait orientation"))

        onNodeWithText("Portrait").performClick()
        advance(harness)

        assertEquals("portrait", harness.settingsViewModel.state.value.orientationLock)
        assertEquals("portrait", harness.preferencesRepo.getOrientationLock())
    }

    @Test
    fun selectingAutoAfterLandscapeRestoresDefault() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettings(harness)

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Lock to landscape orientation"))

        // First select Landscape
        onNodeWithText("Landscape").performClick()
        advance(harness)
        assertEquals("landscape", harness.settingsViewModel.state.value.orientationLock)

        // Then switch back to Auto
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Follow system orientation"))
        onNodeWithText("Auto").performClick()
        advance(harness)
        assertEquals("auto", harness.settingsViewModel.state.value.orientationLock)
    }
}
