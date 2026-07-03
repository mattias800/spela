package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.ingame.SecondaryScreenContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for OLED burn-in protection on the secondary screen.
 *
 * The secondary screen fades to black after 15s of inactivity for single-screen
 * console games (NES, SNES, etc.) to prevent OLED burn-in. Dual-screen games
 * (DS/3DS) are exempt since the touch screen is actively used during gameplay.
 *
 * These tests render SecondaryScreenContent directly (the desktop App() does not
 * render it since secondary display is a no-op on desktop).
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenBurnInTest {

    /**
     * Advance both test dispatcher and Compose clock by [seconds] seconds,
     * using bounded 1-second steps to avoid hanging on perpetual coroutines
     * and to match the existing advanceN pattern.
     */
    private fun ComposeUiTest.advanceSeconds(harness: SpelaTestHarness, seconds: Int) {
        mainClock.autoAdvance = false
        repeat(seconds) {
            advanceHarnessSchedulerByOnUiThread(harness, 1_000)
            mainClock.advanceTimeBy(1_000)
            waitForIdle()
        }
        mainClock.autoAdvance = true
    }

    private fun createHarnessWithNesGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.downloadRepo.preCacheGame("1")
        return harness
    }

    private fun createHarnessWithNdsGame(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        val ndsGame = Game(
            id = "99",
            title = "Mario Kart DS",
            consoleId = "nds",
            consoleName = "NDS",
            description = "Racing on the DS.",
            developer = "Nintendo",
            publisher = "Nintendo",
            releaseDate = "2005",
            genre = "Racing",
            fileSize = 67108864,
            fileName = "mariokartds.nds",
            scrapeAttempts = 1,
        )
        harness.gameRepo.games = harness.gameRepo.games + ndsGame
        harness.downloadRepo.preCacheGame("99")
        return harness
    }

    private fun ComposeUiTest.startGameAndRenderSecondary(
        harness: SpelaTestHarness,
        gameId: String = "1",
    ) {
        // Start emulation with bounded advance (not advanceUntilIdle which hangs
        // on perpetual coroutines like the session timer)
        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = gameId))
        mainClock.autoAdvance = false
        repeat(4) {
            advanceHarnessSchedulerByOnUiThread(harness, 1_000)
        }
        mainClock.autoAdvance = true

        setContent {
            SecondaryScreenContent(
                viewModel = harness.emulationViewModel,
                controller = harness.libretroController,
            )
        }
        advanceSeconds(harness, 1)
    }

    @Test
    fun secondaryScreenVisibleInitially() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Game title should be visible in the persistent header
        onNodeWithContentDescription("Now playing: Castlevania").assertIsDisplayed()

        // Dimming overlay should NOT exist yet
        onAllNodesWithContentDescription("Screen dimmed for OLED protection")
            .assertCountEquals(0)
    }

    @Test
    fun screenDimsAfterIdleTimeout() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Advance past the 15s idle timeout + 5s fade duration
        advanceSeconds(harness, 21)

        // Dimming overlay should now exist
        onNodeWithContentDescription("Screen dimmed for OLED protection")
            .assertExists()
    }

    @Test
    fun touchRestoresDimmedScreen() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Advance past the idle timeout + fade
        advanceSeconds(harness, 21)

        // Verify dimming overlay exists
        onNodeWithContentDescription("Screen dimmed for OLED protection")
            .assertExists()

        // Touch the dimming overlay — parent Initial handler resets timer
        onNodeWithContentDescription("Screen dimmed for OLED protection")
            .performClick()
        advanceSeconds(harness, 1)

        // Dimming overlay should be gone (alpha restored via snap animation)
        onAllNodesWithContentDescription("Screen dimmed for OLED protection")
            .assertCountEquals(0)
    }

    @Test
    fun dualScreenGameNeverDims() = runComposeUiTest {
        val harness = createHarnessWithNdsGame()
        startGameAndRenderSecondary(harness, gameId = "99")

        // Advance well past the idle timeout + fade
        advanceSeconds(harness, 30)

        // Dimming overlay should never appear for DS games
        onAllNodesWithContentDescription("Screen dimmed for OLED protection")
            .assertCountEquals(0)
    }

    @Test
    fun pauseResetsDimTimer() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Advance 13 seconds (setup already used 1s, so total 14s < 15s timeout)
        advanceSeconds(harness, 13)

        // Verify not dimmed yet
        onAllNodesWithContentDescription("Screen dimmed for OLED protection")
            .assertCountEquals(0)

        // Pause the game — this resets the timer via LaunchedEffect key change
        harness.emulationViewModel.onIntent(EmulationIntent.PauseGame)
        advanceSeconds(harness, 1)

        // Advance another 13 seconds (total 14s since pause reset, still < 15s)
        advanceSeconds(harness, 13)

        // Should NOT be dimmed since the timer was reset by pause
        onAllNodesWithContentDescription("Screen dimmed for OLED protection")
            .assertCountEquals(0)
    }
}
