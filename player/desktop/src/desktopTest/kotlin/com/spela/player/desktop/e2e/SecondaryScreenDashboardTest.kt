package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.ui.feature.ingame.SecondaryDashboardPage
import com.spela.player.presentation.ui.feature.ingame.SecondaryScreenContent
import com.spela.player.presentation.ui.feature.ingame.SecondarySaveSlotsPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * E2E tests for the Second Screen Companion dashboard page and pager infrastructure.
 *
 * Tests cover:
 * - Dashboard stat cards (FPS, save slot, cheats)
 * - Quick action buttons (Save, Load, Screenshot, Fast Forward, Rewind)
 * - Conditional rendering (rewind button, fast forward toggle state, cheats display)
 * - Companion header with game title
 * - Page indicator dots
 *
 * The SecondaryDashboardPage is a stateless composable so most tests render it
 * directly with explicit parameters. Tests for the companion header and page
 * indicator dots use SecondaryScreenContent through the test harness.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class SecondaryScreenDashboardTest {

    // -- Helpers ---------------------------------------------------------------

    private fun ComposeUiTest.advanceSeconds(harness: SpelaTestHarness, seconds: Int) {
        mainClock.autoAdvance = false
        repeat(seconds) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
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

    private fun ComposeUiTest.startGameAndRenderSecondary(
        harness: SpelaTestHarness,
        gameId: String = "1",
    ) {
        harness.emulationViewModel.onIntent(EmulationIntent.StartGame(gameId = gameId))
        mainClock.autoAdvance = false
        repeat(4) {
            harness.testDispatcher.scheduler.advanceTimeBy(1_000)
            harness.testDispatcher.scheduler.runCurrent()
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

    /**
     * Render the dashboard page directly with the given parameters.
     * Since SecondaryDashboardPage is a stateless composable, no ViewModel is needed.
     */
    private fun ComposeUiTest.renderDashboard(
        fps: Float = 60f,
        frameTime: Float = 16.7f,
        activeSlot: Int = 1,
        hasCheats: Boolean = false,
        enabledCheatCount: Int = 0,
        isFastForward: Boolean = false,
        rewindEnabled: Boolean = false,
    ) {
        setContent {
            SecondaryDashboardPage(
                fps = fps,
                frameTime = frameTime,
                activeSlot = activeSlot,
                hasCheats = hasCheats,
                enabledCheatCount = enabledCheatCount,
                isFastForward = isFastForward,
                rewindEnabled = rewindEnabled,
                onSave = {},
                onLoad = {},
                onScreenshot = {},
                onToggleFastForward = {},
                onRewind = {},
            )
        }
    }

    // -- Dashboard stat cards --------------------------------------------------

    @Test
    fun dashboardStatCardsRenderCorrectly() = runComposeUiTest {
        renderDashboard(fps = 60f, frameTime = 16.7f, activeSlot = 3)

        // FPS card with frame time
        onNodeWithContentDescription("60 FPS, 16.7 ms frame time")
            .assertExists()
            .assertIsDisplayed()

        // Save slot card
        onNodeWithContentDescription("Active save slot 3")
            .assertExists()
            .assertIsDisplayed()

        // Cheats card (no cheats by default)
        onNodeWithContentDescription("No cheats available")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun dashboardWithCheatsActive() = runComposeUiTest {
        renderDashboard(hasCheats = true, enabledCheatCount = 2)

        // Cheats card should show active count
        onNodeWithContentDescription("2 cheats active")
            .assertExists()
            .assertIsDisplayed()

        // "2 active" text visible in the card
        onNodeWithText("2 active")
            .assertExists()

        // No cheats description should NOT be present
        onAllNodesWithContentDescription("No cheats available")
            .assertCountEquals(0)
    }

    @Test
    fun dashboardWithNoCheats() = runComposeUiTest {
        renderDashboard(hasCheats = false, enabledCheatCount = 0)

        // No cheats available description
        onNodeWithContentDescription("No cheats available")
            .assertExists()
            .assertIsDisplayed()

        // "No cheats" text visible in the card
        onNodeWithText("No cheats")
            .assertExists()

        // Active cheats description should NOT be present
        onAllNodesWithContentDescription("0 cheats active")
            .assertCountEquals(0)
    }

    // -- Quick action buttons --------------------------------------------------

    @Test
    fun dashboardQuickActionsPresent() = runComposeUiTest {
        renderDashboard()

        // All standard quick action buttons should exist
        onNodeWithContentDescription("Save")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithContentDescription("Load")
            .assertExists()
            .assertIsDisplayed()

        onNodeWithContentDescription("Screenshot")
            .assertExists()
            .assertIsDisplayed()

        // Fast forward button (shows "Fast" label when not active)
        onNodeWithContentDescription("Fast")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun dashboardRewindButtonShownWhenEnabled() = runComposeUiTest {
        renderDashboard(rewindEnabled = true)

        onNodeWithContentDescription("Rewind")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun dashboardRewindButtonHiddenWhenDisabled() = runComposeUiTest {
        renderDashboard(rewindEnabled = false)

        onAllNodesWithContentDescription("Rewind")
            .assertCountEquals(0)
    }

    @Test
    fun dashboardFastForwardToggleShowsNormalWhenActive() = runComposeUiTest {
        renderDashboard(isFastForward = true)

        // When fast forward is active, button label is "Normal" (to indicate tapping returns to normal)
        onNodeWithContentDescription("Normal")
            .assertExists()
            .assertIsDisplayed()

        // "Fast" label should not be present
        onAllNodesWithContentDescription("Fast")
            .assertCountEquals(0)
    }

    @Test
    fun dashboardFastForwardToggleShowsFastWhenInactive() = runComposeUiTest {
        renderDashboard(isFastForward = false)

        // When fast forward is inactive, button label is "Fast"
        onNodeWithContentDescription("Fast")
            .assertExists()
            .assertIsDisplayed()

        // "Normal" label should not be present
        onAllNodesWithContentDescription("Normal")
            .assertCountEquals(0)
    }

    // -- Companion header (via SecondaryScreenContent) -------------------------

    @Test
    fun companionHeaderRendersWithGameTitle() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Companion header should show the game title in its semantic description
        onNodeWithContentDescription("Now playing: Castlevania")
            .assertExists()
            .assertIsDisplayed()
    }

    // -- Page indicator dots (via SecondaryScreenContent) ----------------------

    @Test
    fun pageIndicatorDotsPresent() = runComposeUiTest {
        val harness = createHarnessWithNesGame()
        startGameAndRenderSecondary(harness)

        // Art page should be active (initial page)
        onNodeWithContentDescription("Art, 1 of 4, active")
            .assertExists()

        // Other pages should be present but not active
        onNodeWithContentDescription("Controls, 2 of 4")
            .assertExists()
        onNodeWithContentDescription("Dashboard, 3 of 4")
            .assertExists()
        onNodeWithContentDescription("Save Slots, 4 of 4")
            .assertExists()
    }

    // --- Save Slots Page Tests ---

    @Test
    fun saveSlotsPageRendersAllSlots() = runComposeUiTest {
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                onSelectSlot = {},
            )
        }
        waitForIdle()

        // Title
        onNodeWithText("Save Slots").assertExists()

        // Active slot 1
        onNodeWithContentDescription("Save slot 1, active").assertExists()

        // A few inactive slots
        onNodeWithContentDescription("Save slot 2").assertExists()
        onNodeWithContentDescription("Save slot 5").assertExists()
        onNodeWithContentDescription("Save slot 10").assertExists()
    }

    @Test
    fun saveSlotsPageHighlightsActiveSlot() = runComposeUiTest {
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 7,
                onSelectSlot = {},
            )
        }
        waitForIdle()

        // Slot 7 should be active
        onNodeWithContentDescription("Save slot 7, active").assertExists()

        // Slot 1 should NOT be active
        onNodeWithContentDescription("Save slot 1, active").assertDoesNotExist()
        onNodeWithContentDescription("Save slot 1").assertExists()
    }

    @Test
    fun saveSlotsPageShowsHintText() = runComposeUiTest {
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                onSelectSlot = {},
            )
        }
        waitForIdle()

        onNodeWithText("Tap to select active slot").assertExists()
    }

    @Test
    fun saveSlotsPageCallsOnSelectSlot() = runComposeUiTest {
        var selectedSlot = -1
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                onSelectSlot = { selectedSlot = it },
            )
        }
        waitForIdle()

        // Tap slot 3
        onNodeWithContentDescription("Save slot 3").performClick()
        waitForIdle()

        assert(selectedSlot == 3) { "Expected selectedSlot to be 3, was $selectedSlot" }
    }
}
