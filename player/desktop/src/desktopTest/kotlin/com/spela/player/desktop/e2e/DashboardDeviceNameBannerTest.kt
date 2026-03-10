package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * E2E tests for the dashboard device name banner.
 * The banner appears when the device has no name set, allowing users
 * to name their device without leaving the dashboard.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DashboardDeviceNameBannerTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun bannerAppearsWhenDeviceNameIsEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Device name defaults to empty in fresh database

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Name this device").assertIsDisplayed()
        onNodeWithText("Give this device a name so you can identify it across your account.")
            .assertIsDisplayed()
    }

    @Test
    fun bannerHasTextInputAndSaveButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Name this device").assertIsDisplayed()
        onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun bannerDisappearsAfterSavingDeviceName() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Banner should be visible initially
        onNodeWithText("Name this device").assertIsDisplayed()

        // Type a device name into the text field
        onNode(hasTestTag("device_name_banner")
            .and(hasAnyDescendant(hasText("Save"))))
            .assertIsDisplayed()

        // Find the text field within the banner and type
        onNodeWithText("My device").performTextInput("Living Room PC")
        advanceQuick(harness)

        // Click save
        onNodeWithText("Save").performClick()
        advanceQuick(harness)

        // Banner should be gone
        onNodeWithText("Name this device").assertDoesNotExist()

        // Device name should be saved in settings state
        assertEquals("Living Room PC", harness.settingsViewModel.state.value.deviceName)
    }

    @Test
    fun bannerDoesNotAppearWhenDeviceNameIsSet() = runComposeUiTest {
        val harness = createLoggedInHarness()
        // Pre-set a device name before the screen loads
        harness.deviceManager.setDeviceName("My Desktop")

        setContent { harness.App() }
        advance(harness)

        // Banner should NOT appear
        onNodeWithText("Name this device").assertDoesNotExist()
    }

    @Test
    fun saveButtonDisabledWhenInputIsEmpty() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Name this device").assertIsDisplayed()

        // Save button should be disabled when input is empty
        onNodeWithText("Save").assertIsNotEnabled()
    }
}
