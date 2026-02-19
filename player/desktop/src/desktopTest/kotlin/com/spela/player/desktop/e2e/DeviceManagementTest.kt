package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.viewmodel.SettingsIntent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * E2E tests for Phase 8: Device Management in Settings.
 * Tests: Device section visibility, empty device list, delete confirmation dialog.
 *
 * Note: The "Devices" section is near the bottom of the Settings LazyColumn.
 * Since the mock API returns no devices, the section shows "No registered devices".
 * The delete confirmation dialog lives inside the DeviceManagementSection composable
 * which is a lazy item — it must be in the viewport for the dialog to render.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class DeviceManagementTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    /**
     * Navigate to Settings and scroll the LazyColumn to the "Devices" section.
     */
    private fun ComposeUiTest.navigateToSettingsAndScrollToDevices(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Settings)
        )
        advance(harness)

        // Scroll to bring "Devices" section into view
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("Devices"))
    }

    @Test
    fun settingsScreenShowsDevicesSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToDevices(harness)

        onNodeWithText("Devices").assertIsDisplayed()
    }

    @Test
    fun emptyDeviceListShowsMessage() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToDevices(harness)

        // With mock API, no real devices
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No registered devices"))
        onNodeWithText("No registered devices").assertIsDisplayed()
    }

    @Test
    fun deleteDeviceConfirmDialogAppears() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToDevices(harness)

        // Scroll to "No registered devices" to ensure the DeviceManagementSection lazy item is composed
        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No registered devices"))

        // Trigger delete confirmation programmatically while section is in viewport
        harness.settingsViewModel.onIntent(SettingsIntent.ShowDeleteDeviceConfirm(1))
        // Use bounded advance to avoid scrolling the item off-screen
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        // Confirmation dialog should appear
        onNodeWithText("Remove Device").assertIsDisplayed()
        onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun deleteDeviceConfirmDialogDismisses() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToDevices(harness)

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No registered devices"))

        harness.settingsViewModel.onIntent(SettingsIntent.ShowDeleteDeviceConfirm(1))
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        onNodeWithText("Remove Device").assertIsDisplayed()

        // Dismiss
        harness.settingsViewModel.onIntent(SettingsIntent.DismissDeleteDeviceConfirm)
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        val state = harness.settingsViewModel.state.value
        assertNull(state.showDeleteDeviceConfirm)
    }

    @Test
    fun deleteDeviceConfirmButtonDismissesDialog() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToDevices(harness)

        onNodeWithTag("settings_list")
            .performScrollToNode(hasText("No registered devices"))

        harness.settingsViewModel.onIntent(SettingsIntent.ShowDeleteDeviceConfirm(1))
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        onNodeWithText("Remove Device").assertIsDisplayed()

        // Tap "Remove" button
        onNodeWithText("Remove").performClick()
        harness.testDispatcher.scheduler.advanceTimeBy(1_000)
        harness.testDispatcher.scheduler.runCurrent()
        waitForIdle()

        val state = harness.settingsViewModel.state.value
        assertNull(state.showDeleteDeviceConfirm)
    }
}
