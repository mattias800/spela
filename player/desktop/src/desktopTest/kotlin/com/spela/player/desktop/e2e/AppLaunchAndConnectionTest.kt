package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for app launch and server connection flow.
 * Tests: App launch, server connection screen, adding a server, selecting a server.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AppLaunchAndConnectionTest {

    @Test
    fun addServerFormCanBeOpenedWhenServersExist() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        // Pre-add a server so the form doesn't auto-open (auto-opens when server list is empty)
        harness.serverRepo.preAddServer("Existing", "http://existing:8080")

        setContent { harness.App() }
        advance(harness)

        // Initially the add-server form is hidden
        onNodeWithText("Server Name").assertDoesNotExist()

        // Tap "Add Server" to reveal form
        onNodeWithText("Add Server").performClick()
        advanceQuick(harness)

        onNodeWithText("Server Name").assertIsDisplayed()
        onNodeWithText("Server URL").assertIsDisplayed()
    }

    @Test
    fun serverListSupportsRemovalSelectionRegisterToggleAndLogin() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("Server A", "http://a:8080")
        harness.serverRepo.preAddServer("Server B", "http://b:8080")

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Server A").assertIsDisplayed()
        onNodeWithText("Server B").assertIsDisplayed()
        onAllNodesWithContentDescription("Remove server").assertCountEquals(2)

        onAllNodesWithContentDescription("Remove server").onFirst().performClick()
        advanceQuick(harness)

        onNodeWithText("Server A").assertDoesNotExist()
        onNodeWithText("Server B").assertIsDisplayed()

        onNodeWithText("Server B").performClick()
        advance(harness)

        onNodeWithText("Username").assertIsDisplayed()
        onNodeWithText("Password").assertIsDisplayed()
        onNodeWithText("Sign In").assertIsDisplayed()
        onNodeWithText("Don't have an account? Register").assertIsDisplayed()

        onNodeWithText("Don't have an account? Register").performClick()
        advanceQuick(harness)
        onNodeWithText("Create Account").assertIsDisplayed()
        onNodeWithText("Already have an account? Sign In").assertIsDisplayed()

        onNodeWithText("Already have an account? Sign In").performClick()
        advanceQuick(harness)
        onNodeWithText("Sign In").assertIsDisplayed()

        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")
        onNodeWithText("Sign In").performClick()
        advance(harness)

        onNodeWithText("Spela").assertIsDisplayed()
    }

    @Test
    fun appLaunchShowsConnectionScreenAndCanRetryServerValidation() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.serverRepo.validateServerResult = false

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Nu spelar vi!", substring = true).assertIsDisplayed()
        onNodeWithText("Add Server").assertIsDisplayed()

        // Form auto-opens — fill it in
        onNodeWithText("Server Name").performTextInput("Bad Server")
        onNodeWithText("Server URL").performTextInput("http://bad-url:9999")

        // Click Connect
        onNodeWithText("Connect").performClick()
        advance(harness)

        // Error should appear, form should stay open with entered values
        onNodeWithText("Could not connect to server. Check the URL and try again.").assertIsDisplayed()
        onNodeWithText("Server Name").assertIsDisplayed()
        onNodeWithText("Server URL").assertIsDisplayed()

        // Fix the validation result and retry. `advanceFully` (not the
        // standard `advance`) because the success retry path runs
        // validateServer → addServer → state-flow re-emission →
        // form auto-close, and the standard 4-iteration helper is
        // borderline under parallel-fork CPU contention.
        harness.serverRepo.validateServerResult = true
        onNodeWithText("Connect").performClick()
        advanceFully(harness)

        // Server should now be added and form closed
        onNodeWithText("Bad Server").assertIsDisplayed()
        onNodeWithText("Server Name").assertDoesNotExist()
    }
}
