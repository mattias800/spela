package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for offline indicator banner (Phase 8).
 * Tests: banner visibility when online/offline.
 *
 * NOTE: The offline banner observes ConnectivityMonitor.isOnline via koinInject
 * in SpelaApp. Since the test harness uses a real ConnectivityMonitor backed by
 * the mock API client (which always returns 200 OK for health checks), the monitor
 * defaults to online. The banner should NOT be visible when online.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class OfflineIndicatorTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun offlineBannerNotVisibleWhenOnline() = runComposeUiTest {
        val harness = createLoggedInHarness()

        setContent { harness.App() }
        advance(harness)

        // Banner text should not be visible when online
        onNodeWithText("Offline").assertDoesNotExist()
    }
}
