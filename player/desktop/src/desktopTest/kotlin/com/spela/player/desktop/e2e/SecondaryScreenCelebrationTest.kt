package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementEventType
import com.spela.player.presentation.ui.feature.ingame.SecondaryAchievementCelebration
import kotlin.test.Test

/**
 * E2E tests for the SecondaryAchievementCelebration overlay.
 *
 * Tests cover:
 * - Achievement unlock celebration (ACHIEVEMENT_TRIGGERED event)
 * - Game completion celebration (GAME_COMPLETED event) with distinct visuals
 *
 * The SecondaryAchievementCelebration is a stateless composable rendered
 * directly with explicit parameters. No ViewModel is needed.
 */
@OptIn(ExperimentalTestApi::class)
class SecondaryScreenCelebrationTest {

    @Test
    fun achievementCelebrationShowsOnEvent() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
            title = "First Blood",
            description = "Defeat the first enemy",
            points = 10,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(
                achievementEvent = event,
            )
        }

        // Advance clock to let the LaunchedEffect enqueue and promote the event.
        // Keep autoAdvance disabled because the celebration has an infinite glow
        // animation that would cause waitForIdle() to hang.
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        // The celebration overlay should show with correct content description
        onNodeWithContentDescription("Achievement unlocked: First Blood, 10 points")
            .assertExists()
            .assertIsDisplayed()

        // "ACHIEVEMENT UNLOCKED" header text should be visible
        onNodeWithText("ACHIEVEMENT UNLOCKED")
            .assertExists()
            .assertIsDisplayed()

        // Achievement title should be visible
        onNodeWithText("First Blood")
            .assertExists()
            .assertIsDisplayed()

        // Achievement description should be visible
        onNodeWithText("Defeat the first enemy")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun gameCompletedCelebrationShowsDistinctly() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.GAME_COMPLETED,
            title = "Castlevania",
            description = "All achievements unlocked!",
            points = 0,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(
                achievementEvent = event,
            )
        }

        // Advance clock to let the LaunchedEffect enqueue and promote the event.
        // Keep autoAdvance disabled because the celebration has an infinite glow
        // animation that would cause waitForIdle() to hang.
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        // The celebration overlay should show with game completed content description
        onNodeWithContentDescription("Game completed celebration: Castlevania")
            .assertExists()
            .assertIsDisplayed()

        // "GAME COMPLETED" header text should be visible (distinct from "ACHIEVEMENT UNLOCKED")
        onNodeWithText("GAME COMPLETED")
            .assertExists()
            .assertIsDisplayed()

        // "100%" completion indicator should be visible
        onNodeWithText("100%")
            .assertExists()
            .assertIsDisplayed()

        // "ACHIEVEMENT UNLOCKED" should NOT be present
        onAllNodesWithText("ACHIEVEMENT UNLOCKED")
            .assertCountEquals(0)
    }
}
