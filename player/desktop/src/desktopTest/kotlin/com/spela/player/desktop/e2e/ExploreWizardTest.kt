package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.WizardResults
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Desktop E2E tests for Phase 14: Decision Wizard.
 *
 * Covers:
 * - Wizard screen renders with first step
 * - Selecting an option advances to next step
 * - Completing all 3 steps shows results
 * - Wizard back button works
 * - Empty results show empty state
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ExploreWizardTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private val sampleResults = listOf(
        Game(
            id = "g1",
            title = "Action Hero",
            consoleId = "snes",
            consoleName = "SNES",
            coverUrl = null,
            rating = 90.0,
            genre = "Action",
        ),
        Game(
            id = "g2",
            title = "Run and Gun",
            consoleId = "nes",
            consoleName = "NES",
            coverUrl = null,
            rating = 85.0,
            genre = "Shooter",
        ),
    )

    @Test
    fun wizardScreenRendersFirstStep() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
        )
        advance(harness)

        onNodeWithTag("wizard_screen").assertIsDisplayed()
        onNodeWithTag("wizard_step_title").assertIsDisplayed()
        onNodeWithText("What are you in the mood for?").assertIsDisplayed()
        onNodeWithTag("wizard_option_action").assertIsDisplayed()
        onNodeWithText("Action & Excitement").assertIsDisplayed()
        onNodeWithTag("wizard_option_chill").assertIsDisplayed()
    }

    @Test
    fun selectingOptionAdvancesToNextStep() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
        )
        advance(harness)

        onNodeWithTag("wizard_option_action").performClick()
        advance(harness)

        onNodeWithText("Pick an era").assertIsDisplayed()
        onNodeWithTag("wizard_option_80s").assertIsDisplayed()
    }

    @Test
    fun completingAllStepsShowsResults() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.wizardResults = WizardResults(sampleResults, "Action-Packed Picks")

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
        )
        advance(harness)

        // Step 1: mood
        onNodeWithTag("wizard_option_action").performClick()
        advance(harness)

        // Step 2: era
        onNodeWithTag("wizard_option_80s").performClick()
        advance(harness)

        // Step 3: vibe
        onNodeWithTag("wizard_option_solo").performClick()
        advance(harness)

        onNodeWithTag("wizard_results_title").assertIsDisplayed()
    }

    @Test
    fun wizardBackButtonGoesToPreviousStep() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
        )
        advance(harness)

        // Go to step 2
        onNodeWithTag("wizard_option_action").performClick()
        advance(harness)
        onNodeWithText("Pick an era").assertIsDisplayed()

        // Go back
        onNodeWithTag("wizard_back_button").performClick()
        advance(harness)

        onNodeWithText("What are you in the mood for?").assertIsDisplayed()
    }

    @Test
    fun emptyResultsShowEmptyState() = runComposeUiTest {
        val harness = createHarness()
        harness.exploreRepo.wizardResults = WizardResults(emptyList(), "No Picks")

        setContent { harness.App() }
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.ExploreWizard)
        )
        advance(harness)

        // Complete wizard
        onNodeWithTag("wizard_option_action").performClick()
        advance(harness)
        onNodeWithTag("wizard_option_80s").performClick()
        advance(harness)
        onNodeWithTag("wizard_option_solo").performClick()
        advance(harness)

        onNodeWithTag("wizard_empty_state").assertIsDisplayed()
    }
}
