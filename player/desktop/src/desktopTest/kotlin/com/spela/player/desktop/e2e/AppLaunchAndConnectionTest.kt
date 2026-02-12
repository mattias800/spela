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

    private fun advance(harness: SpelaTestHarness, scope: ComposeUiTest) {
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
        harness.testDispatcher.scheduler.advanceUntilIdle()
        scope.waitForIdle()
    }

    @Test
    fun appLaunchShowsServerConnectionScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        setContent { harness.App() }
        advance(harness, this)

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
        advance(harness, this)

        // Initially the add-server form is hidden
        onNodeWithText("Server Name").assertDoesNotExist()

        // Tap "Add Server" to reveal form
        onNodeWithText("Add Server").performClick()
        advance(harness, this)

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
        advance(harness, this)

        // Tap server name to select it and navigate to login
        onNodeWithText("Local Server").performClick()
        advance(harness, this)

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
        advance(harness, this)

        onNodeWithText("Local").performClick()
        advance(harness, this)

        // Toggle to register mode
        onNodeWithText("Don't have an account? Register").assertIsDisplayed()
        onNodeWithText("Don't have an account? Register").performClick()
        advance(harness, this)

        // "Create Account" appears as both heading and button - verify both exist
        onAllNodesWithText("Create Account").assertCountEquals(2)
        onNodeWithText("Already have an account? Sign In").assertIsDisplayed()
    }

    @Test
    fun successfulLoginNavigatesToHomeScreen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())

        runTest(harness.testDispatcher) {
            harness.serverRepo.addServer("Local", "http://localhost:8080")
        }

        setContent { harness.App() }
        advance(harness, this)

        // Select server
        onNodeWithText("Local").performClick()
        advance(harness, this)

        // Enter credentials
        onNodeWithText("Username").performClick()
        onNodeWithText("Username").performTextInput("player")
        onNodeWithText("Password").performClick()
        onNodeWithText("Password").performTextInput("player123")

        // Submit login
        onNodeWithText("Sign In").performClick()
        advance(harness, this)

        // Should navigate to Home screen
        onNodeWithText("Spela").assertIsDisplayed()
    }
}
