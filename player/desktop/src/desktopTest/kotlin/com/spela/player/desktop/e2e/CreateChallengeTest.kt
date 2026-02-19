package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.SaveState
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for Create Challenge dialog on Game Detail page:
 * - Create button in challenges section
 * - Dialog opens with form fields
 * - Save state picker works
 * - Validation prevents submit without save state
 * - Cancel dismisses dialog
 * - Successful challenge creation
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class CreateChallengeTest {

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.navigateToGameDetail(harness: SpelaTestHarness) =
        navigateToGameDetail(harness, "1")

    private fun ComposeUiTest.scrollToSection(matcher: SemanticsMatcher) {
        onNodeWithTag("game_detail_content").performScrollToNode(matcher)
    }

    // --- Create Challenge Button ---

    @Test
    fun challengesSectionShowsCreateButton() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").assertIsDisplayed()
    }

    @Test
    fun createButtonOpensDialog() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        onNodeWithTag("create_challenge_dialog").assertIsDisplayed()
        onNodeWithText("Create Challenge").assertIsDisplayed()
    }

    // --- Dialog Form Fields ---

    @Test
    fun dialogShowsTitleField() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        // SpTextField merges semantics, so use unmerged tree for tag lookup
        onNodeWithTag("challenge_title_field", useUnmergedTree = true).assertExists()
    }

    @Test
    fun dialogShowsTypeSelector() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        // Verify type chips are present (use unmerged tree since parent clickable merges)
        onNodeWithTag("challenge_type_selector", useUnmergedTree = true).assertExists()
        onNodeWithText("Completion", useUnmergedTree = true).assertExists()
        onNodeWithText("Speedrun", useUnmergedTree = true).assertExists()
        onNodeWithText("Survival", useUnmergedTree = true).assertExists()
    }

    @Test
    fun dialogShowsDifficultySelector() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        // Verify difficulty chips are present (use unmerged tree since parent clickable merges)
        onNodeWithTag("challenge_difficulty_selector", useUnmergedTree = true).assertExists()
        onNodeWithText("Easy", useUnmergedTree = true).assertExists()
        onNodeWithText("Medium", useUnmergedTree = true).assertExists()
        onNodeWithText("Hard", useUnmergedTree = true).assertExists()
    }

    @Test
    fun dialogShowsNoSavesMessage() = runComposeUiTest {
        val harness = createHarness()
        // No save states = default empty list

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        onNodeWithText("No save states available", substring = true).assertIsDisplayed()
    }

    @Test
    fun dialogShowsSaveStatePicker() = runComposeUiTest {
        val harness = createHarness()
        harness.saveRepo.saves["1"] = mutableListOf(
            SaveState(1, 1, "Boss Fight"),
            SaveState(2, 1, "Level Start"),
        )

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        // Check save state picker exists within the dialog
        val dialog = onNodeWithTag("create_challenge_dialog")
        dialog.assertIsDisplayed()
        onNodeWithTag("save_state_picker", useUnmergedTree = true).assertExists()
        // Verify the text "Save State" label is visible (distinguishes from save states section)
        onNodeWithText("Save State", useUnmergedTree = true).assertExists()
    }

    @Test
    fun submitDisabledWithoutSaveState() = runComposeUiTest {
        val harness = createHarness()
        // No save states

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        onNodeWithTag("create_challenge_submit").assertIsNotEnabled()
    }

    @Test
    fun cancelDismissesDialog() = runComposeUiTest {
        val harness = createHarness()

        setContent { harness.App() }
        navigateToGameDetail(harness)

        scrollToSection(hasTestTag("create_challenge_button"))
        onNodeWithTag("create_challenge_button").performClick()
        advance(harness)

        onNodeWithTag("create_challenge_dialog").assertIsDisplayed()

        onNodeWithText("Cancel").performClick()
        advance(harness)

        onNodeWithTag("create_challenge_dialog").assertDoesNotExist()
    }
}
