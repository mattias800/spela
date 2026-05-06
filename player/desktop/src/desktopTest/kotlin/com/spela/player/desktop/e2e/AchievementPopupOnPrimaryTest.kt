package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.AchievementEvent
import com.spela.player.domain.model.AchievementEventType
import com.spela.player.domain.model.RACredentials
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Regression test for #1087: achievement unlocks must surface a top-anchored
 * banner on the primary in-game surface (not just the secondary screen).
 *
 * The mount lives in `InGameOverlay` and reads `state.achievementEvent`;
 * `EmulationViewModel.initAchievements` collects from the controller's
 * `events` flow into that field after RA is linked. The harness exposes
 * `achievementsRepo` and `achievementsCtrl` so each test can fake an
 * "RA linked, unlock fires" path without touching the network.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class AchievementPopupOnPrimaryTest {

    private fun createHarnessWithRALinked(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        // Default fake says "Not linked" — flipping to success enables
        // the achievement event collector when the game launches.
        harness.achievementsRepo.raTokenResult =
            Result.success(RACredentials(username = "tester", token = "tok"))
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(
            NavigationIntent.NavigateTo(SpScreen.GameDetail("1"))
        )
        return harness
    }

    private fun ComposeUiTest.startGame(harness: SpelaTestHarness) {
        setContent { harness.App() }
        advance(harness)

        // Launch the game. The pause overlay is hidden by default
        // immediately after launch, which matches the real flow:
        // the user is staring at the game frame, and that's the
        // surface we want the achievement banner to overlay.
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)
    }

    @Test
    fun achievementUnlockShowsBannerOnPrimarySurface() = runComposeUiTest {
        val harness = createHarnessWithRALinked()
        startGame(harness)

        harness.achievementsCtrl.emitEvent(
            AchievementEvent(
                type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
                achievementId = 42L,
                title = "Tutorial Complete",
                description = "Beat the first level",
                points = 10,
            ),
        )
        // advanceQuick (2 mainClock seconds) is well under the popup's
        // 4 s auto-dismiss timer, so the banner is still on screen.
        advanceQuick(harness)

        onNodeWithText("Achievement Unlocked!").assertIsDisplayed()
        onNodeWithText("Tutorial Complete").assertIsDisplayed()
        onNodeWithText("Beat the first level").assertIsDisplayed()
        onNodeWithText("10").assertIsDisplayed()
    }

    @Test
    fun gameCompletedEventShowsCompletionCopy() = runComposeUiTest {
        val harness = createHarnessWithRALinked()
        startGame(harness)

        harness.achievementsCtrl.emitEvent(
            AchievementEvent(
                type = AchievementEventType.GAME_COMPLETED,
                title = "Spela Quest",
                description = "All achievements unlocked",
            ),
        )
        advanceQuick(harness)

        onNodeWithText("Game Completed!").assertIsDisplayed()
        onNodeWithText("Spela Quest").assertIsDisplayed()
    }

    @Test
    fun dismissIntentClearsBanner() = runComposeUiTest {
        val harness = createHarnessWithRALinked()
        startGame(harness)

        harness.achievementsCtrl.emitEvent(
            AchievementEvent(
                type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
                title = "Speedrun",
                description = "Finished in under 5 minutes",
                points = 25,
            ),
        )
        advanceQuick(harness)
        onNodeWithText("Speedrun").assertIsDisplayed()

        // Tap-equivalent dismiss path: same intent the auto-dismiss timer
        // fires after 4 s. Asserting via the intent confirms the wiring
        // (state → popup → onDismiss → VM clears event) without depending
        // on mainClock advancement past the timer boundary, which would
        // race the harness's bounded advance loop.
        harness.emulationViewModel.onIntent(EmulationIntent.DismissAchievement)
        advanceQuick(harness)

        onNodeWithText("Speedrun").assertDoesNotExist()
    }

    @Test
    fun tappingBannerDismissesIt() = runComposeUiTest {
        val harness = createHarnessWithRALinked()
        startGame(harness)

        harness.achievementsCtrl.emitEvent(
            AchievementEvent(
                type = AchievementEventType.ACHIEVEMENT_TRIGGERED,
                title = "Combo x10",
                description = "Chained ten hits",
                points = 5,
            ),
        )
        advanceQuick(harness)
        onNodeWithText("Combo x10").assertIsDisplayed()

        // The banner's clickable wraps the inner Row with role=Button;
        // semantics merging surfaces the descendant text on the click
        // target, so clicking the title text invokes onDismiss.
        onNodeWithText("Combo x10").performClick()
        advanceQuick(harness)

        onNodeWithText("Combo x10").assertDoesNotExist()
    }
}
