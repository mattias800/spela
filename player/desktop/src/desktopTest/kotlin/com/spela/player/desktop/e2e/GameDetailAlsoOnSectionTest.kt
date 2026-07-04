package com.spela.player.desktop.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.domain.model.GamePlatform
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.feature.gamedetail.GameDetailAlsoOnTestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class GameDetailAlsoOnSectionTest {

    private fun createHarnessOnGameDetail(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun seedAlsoOnTargets(harness: SpelaTestHarness) {
        val platforms = listOf(
            GamePlatform(
                gameId = "1",
                consoleId = "nes",
                consoleName = "Nintendo Entertainment System",
            ),
            GamePlatform(
                gameId = "4",
                consoleId = "snes",
                consoleName = "Super Nintendo",
            ),
        )
        harness.gameRepo.games = harness.gameRepo.games.map { game ->
            if (game.id == "1") game.copy(platforms = platforms) else game
        }
    }

    private fun ComposeUiTest.scrollToAlsoOnSection() {
        onNodeWithTag("game_detail_content", useUnmergedTree = true)
            .performScrollToNode(hasTestTag(GameDetailAlsoOnTestTags.SECTION))
    }

    @Test
    fun alsoOnSectionHiddenWhenOnlyOnePlatformTarget() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        onAllNodesWithTag(
            GameDetailAlsoOnTestTags.SECTION,
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    @Test
    fun alsoOnSectionShowsPlatformsAndNavigatesToAlternate() = runComposeUiTest {
        val harness = createHarnessOnGameDetail()
        seedAlsoOnTargets(harness)

        setContent { harness.App() }
        navigateToGameDetail(harness, "1")

        onNodeWithText("Castlevania").assertIsDisplayed()
        scrollToAlsoOnSection()

        val currentPlatformTag = GameDetailAlsoOnTestTags.platform("1", "1")
        val alternatePlatformTag = GameDetailAlsoOnTestTags.platform("1", "4")

        onNodeWithTag(GameDetailAlsoOnTestTags.SECTION, useUnmergedTree = true)
            .assertIsDisplayed()
        onNodeWithContentDescription(
            "Current platform Nintendo Entertainment System",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithText("Current", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithContentDescription(
            "Open Castlevania on Super Nintendo",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        onNodeWithTag(currentPlatformTag, useUnmergedTree = true)
            .assert(hasNoClickAction())
        onNodeWithTag(alternatePlatformTag, useUnmergedTree = true)
            .assertHasClickAction()
            .performClick()
        advanceFully(harness)

        onNodeWithText("Chrono Trigger").assertIsDisplayed()
    }

    private fun hasNoClickAction(): SemanticsMatcher =
        SemanticsMatcher("has no click action") { node ->
            !node.config.contains(SemanticsActions.OnClick)
        }
}
