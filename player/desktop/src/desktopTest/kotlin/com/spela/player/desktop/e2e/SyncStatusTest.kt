package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Sync Status in Settings screen (Phase 8).
 * Tests: sync section visibility, online/offline status, sync button state.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SyncStatusTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToSettingsAndScrollToSync(harness: SpelaTestHarness) {
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.Settings)
        )
        advance(harness)

        // Sync lives under the "Storage & Sync" category since the
        // settings screen was split into categorised sub-pages.
        onNodeWithContentDescription("Storage & Sync").performClick()
        advanceQuick(harness)
    }

    @Test
    fun settingsScreenShowsSyncSection() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToSync(harness)

        onNodeWithText("Sync").assertIsDisplayed()
    }

    @Test
    fun syncSectionShowsOnlineStatus() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToSync(harness)

        onNodeWithText("Online").assertIsDisplayed()
    }

    @Test
    fun syncSectionShowsSyncNowButton() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        navigateToSettingsAndScrollToSync(harness)

        onNodeWithText("Sync Now").assertIsDisplayed()
    }
}
