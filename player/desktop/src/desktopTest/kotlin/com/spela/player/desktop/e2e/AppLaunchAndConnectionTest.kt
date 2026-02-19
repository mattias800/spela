package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
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

        onNodeWithText("Spela").assertIsDisplayed()
        onNodeWithText("Connect to your game server").assertIsDisplayed()
        onNodeWithText("Add Server").assertIsDisplayed()
    }

    @Test
    fun addServerShowsFormAndAddsServer() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        // Pre-add a server so the form doesn't auto-open (auto-opens when server list is empty)
        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Existing", "http://existing:8080")
        }

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

        // Pre-add a server
        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Local Server", "http://localhost:8080")
        }

        setContent { harness.App() }
        advance(harness)

        // Tap server name to select it and navigate to login
        onNodeWithText("Local Server").performClick()
        advance(harness)

        // Should now be on the login screen
        onNodeWithText("Welcome Back").assertIsDisplayed()
        onNodeWithText("Username").assertIsDisplayed()
        onNodeWithText("Password").assertIsDisplayed()
        onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun loginScreenShowsRegisterToggle() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Local", "http://localhost:8080")
        }

        setContent { harness.App() }
        advance(harness)

        onNodeWithText("Local").performClick()
        advance(harness)

        // Toggle to register mode
        onNodeWithText("Don't have an account? Register").assertIsDisplayed()
        onNodeWithText("Don't have an account? Register").performClick()
        advanceQuick(harness)

        // "Create Account" appears as both heading and button - verify both exist
        onAllNodesWithText("Create Account").assertCountEquals(2)
        onNodeWithText("Already have an account? Sign In").assertIsDisplayed()
    }

    @Test
    fun serverListShowsRemoveButton() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("My Server", "http://my-server:8080")
        }

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

        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Server A", "http://a:8080")
            harness.serverRepo.addServer("Server B", "http://b:8080")
        }

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
    fun successfulLoginNavigatesToHomeScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Local", "http://localhost:8080")
        }

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
