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
    fun appLaunchShowsServerConnectionScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Nu spelar vi!", substring = true).assertIsDisplayed()
        onNodeWithText("Add Server").assertIsDisplayed()
    }

    @Test
    fun addServerShowsFormAndAddsServer() = runComposeUiTest {
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
    fun selectingServerNavigatesToLoginScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("Local Server", "http://localhost:8080")

        setContent { harness.App() }
        advance(harness)

        // Tap server name to select it and navigate to login
        onNodeWithText("Local Server").performClick()
        advance(harness)

        // Should now be on the login screen
        onNodeWithText("Username").assertIsDisplayed()
        onNodeWithText("Password").assertIsDisplayed()
        onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun loginScreenShowsRegisterToggle() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("Local", "http://localhost:8080")

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Local").performClick()
        advance(harness)

        // Toggle to register mode
        onNodeWithText("Don't have an account? Register").assertIsDisplayed()
        onNodeWithText("Don't have an account? Register").performClick()
        advanceQuick(harness)

        // "Create Account" button should be visible
        onNodeWithText("Create Account").assertIsDisplayed()
        onNodeWithText("Already have an account? Sign In").assertIsDisplayed()
    }

    @Test
    fun serverListShowsRemoveButton() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("My Server", "http://my-server:8080")

        setContent { harness.App() }
        advance(harness)

        // Server should be listed
        onNodeWithText("My Server").assertIsDisplayed()

        // Remove server button should be present with correct content description
        onNodeWithContentDescription("Remove server").assertIsDisplayed()
    }

    @Test
    fun removeServerRemovesItFromList() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("Server A", "http://a:8080")
        harness.serverRepo.preAddServer("Server B", "http://b:8080")

        setContent { harness.App() }
        advance(harness)

        // Both servers should be visible
        onNodeWithText("Server A").assertIsDisplayed()
        onNodeWithText("Server B").assertIsDisplayed()

        // Click the first remove button (removes Server A)
        onAllNodesWithContentDescription("Remove server").onFirst().performClick()
        advanceQuick(harness)

        // Server A should be gone, Server B remains
        onNodeWithText("Server A").assertDoesNotExist()
        onNodeWithText("Server B").assertIsDisplayed()
    }

    @Test
    fun addServerValidatesAndSavesOnSuccess() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent { harness.App() }
        advance(harness)

        // Form auto-opens when no servers exist — fill it in
        onNodeWithText("Server Name").performTextInput("Test Server")
        onNodeWithText("Server URL").performTextInput("http://localhost:8080")

        // Click Connect (first server uses "Connect" instead of "Add").
        // Use `advanceFully` here (not the standard `advance`) because
        // this path runs three async chains in sequence: validateServer
        // → repo.addServer → state-flow re-emission → form auto-close.
        // The standard 4-iteration `advance` was borderline under
        // parallel-fork CPU contention, leading to occasional flakes
        // where the form-closed assertion fired before the state had
        // propagated. 6 iterations leaves headroom without slowing
        // happy-path runs noticeably.
        onNodeWithText("Connect").performClick()
        advanceFully(harness)

        // Server should be added and form should close
        onNodeWithText("Test Server").assertIsDisplayed()
        onNodeWithText("Server Name").assertDoesNotExist()
    }

    @Test
    fun addServerShowsErrorOnValidationFailure() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.serverRepo.validateServerResult = false

        setContent { harness.App() }
        advance(harness)

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
    }

    @Test
    fun addServerRetryAfterFailure() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.serverRepo.validateServerResult = false

        setContent { harness.App() }
        advance(harness)

        // Fill form and try to add
        onNodeWithText("Server Name").performTextInput("My Server")
        onNodeWithText("Server URL").performTextInput("http://bad:9999")
        onNodeWithText("Connect").performClick()
        advance(harness)

        // Should show error
        onNodeWithText("Could not connect to server. Check the URL and try again.").assertIsDisplayed()

        // Fix the validation result and retry
        harness.serverRepo.validateServerResult = true
        onNodeWithText("Connect").performClick()
        advance(harness)

        // Server should now be added and form closed
        onNodeWithText("My Server").assertIsDisplayed()
        onNodeWithText("Server Name").assertDoesNotExist()
    }

    @Test
    fun successfulLoginNavigatesToHomeScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        harness.serverRepo.preAddServer("Local", "http://localhost:8080")

        setContent { harness.App() }
        advance(harness)

        // Select server
        onNodeWithText("Local").performClick()
        advance(harness)

        // Enter credentials
        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")

        // Submit login
        onNodeWithText("Sign In").performClick()
        advance(harness)

        // Should navigate to Home screen
        onNodeWithText("Spela").assertIsDisplayed()
    }
}
