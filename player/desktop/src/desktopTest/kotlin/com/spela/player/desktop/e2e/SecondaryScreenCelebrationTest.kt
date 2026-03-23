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
 * - Common achievement unlock (default styling)
 * - Rare achievement unlock (tier-specific header and rarity badge)
 * - Game completion celebration (confetti + 100% indicator)
 */
@OptIn(ExperimentalTestApi::class)
class SecondaryScreenCelebrationTest {

    @Test
    fun commonAchievementShowsDefaultHeader() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
            title = "First Blood",
            description = "Defeat the first enemy",
            points = 10,
            rarityPercent = 75.0,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(achievementEvent = event)
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithContentDescription("Achievement unlocked: First Blood, 10 points")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("ACHIEVEMENT UNLOCKED")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("First Blood")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("Defeat the first enemy")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun rareAchievementShowsRareHeader() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
            title = "Speed Demon",
            description = "Complete the game in under 2 hours",
            points = 50,
            rarityPercent = 8.2,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(achievementEvent = event)
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("RARE ACHIEVEMENT")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("Speed Demon")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun ultraRareAchievementShowsUltraRareHeader() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
            title = "Perfectionist",
            description = "Complete without taking any damage",
            points = 100,
            rarityPercent = 2.5,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(achievementEvent = event)
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("ULTRA RARE ACHIEVEMENT")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun legendaryAchievementShowsLegendaryHeader() = runComposeUiTest {
        val event = AchievementEvent(
            type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
            title = "The Impossible",
            description = "Achieve the truly impossible",
            points = 200,
            rarityPercent = 0.3,
        )

        mainClock.autoAdvance = false

        setContent {
            SecondaryAchievementCelebration(achievementEvent = event)
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithText("LEGENDARY ACHIEVEMENT")
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
            SecondaryAchievementCelebration(achievementEvent = event)
        }

        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        onNodeWithContentDescription("Game completed celebration: Castlevania")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("100% COMPLETE")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithText("100%")
            .assertExists()
            .assertIsDisplayed()

        onAllNodesWithText("ACHIEVEMENT UNLOCKED")
            .assertCountEquals(0)
    }
}
