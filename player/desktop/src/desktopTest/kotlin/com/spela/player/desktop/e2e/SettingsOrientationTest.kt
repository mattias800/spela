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
 * E2E tests for the orientation lock setting in the General > Display section.
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

        // General is selected by default, Display section should be visible
        onNodeWithText("Display").assertIsDisplayed()
        onNodeWithText("Auto").assertExists()
        onNodeWithText("Landscape").assertExists()
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

        onNodeWithText("Landscape").performClick()
        advance(harness)
        assertEquals("landscape", harness.settingsViewModel.state.value.orientationLock)

        onNodeWithText("Auto").performClick()
        advance(harness)
        assertEquals("auto", harness.settingsViewModel.state.value.orientationLock)
    }
}
