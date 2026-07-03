package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for ROM verification chip on game detail screen.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class VerificationChipTest {

    private fun createLoggedInHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    @Test
    fun gameDetailShowsVerifiedChipWhenVerified() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Set game "1" to verified
        harness.gameRepo.games = harness.gameRepo.games.map {
            if (it.id == "1") it.copy(verificationStatus = "verified") else it
        }

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        onNodeWithText("Verified").assertIsDisplayed()
        onNodeWithContentDescription("ROM verification: Verified").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsUnverifiedChipWhenUnverified() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Set game "1" to unverified with no tag
        harness.gameRepo.games = harness.gameRepo.games.map {
            if (it.id == "1") it.copy(verificationStatus = "unverified") else it
        }

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        onNodeWithText("Unverified").assertIsDisplayed()
        onNodeWithContentDescription("ROM verification: Unverified").assertIsDisplayed()
    }

    @Test
    fun gameDetailShowsTagTextWhenUnverifiedWithTag() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Set game "1" to unverified with a specific tag
        harness.gameRepo.games = harness.gameRepo.games.map {
            if (it.id == "1") it.copy(
                verificationStatus = "unverified",
                verificationTag = "Bad Dump",
            ) else it
        }

        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        onNodeWithText("Bad Dump").assertIsDisplayed()
        onNodeWithContentDescription("ROM verification: Bad Dump").assertIsDisplayed()
    }

    @Test
    fun gameDetailDoesNotShowVerificationChipWhenStatusNull() = runComposeUiTest {
        val harness = createLoggedInHarness()

        // Default games have null verificationStatus
        setContent { harness.App() }

        navigateToGameDetail(harness, gameId = "1")

        // Neither verified nor unverified chip should appear
        onAllNodesWithText("Verified").assertCountEquals(0)
        onAllNodesWithText("Unverified").assertCountEquals(0)
    }
}
