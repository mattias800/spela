package com.spela.player.desktop.e2e

import androidx.compose.ui.test.*
import com.spela.player.domain.model.Cheat
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.ui.feature.ingame.SecondaryDashboardPage
import com.spela.player.presentation.ui.feature.ingame.SecondaryScreenContent
import com.spela.player.presentation.ui.feature.ingame.SecondarySaveSlotsPage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

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
        cheats: List<com.spela.player.domain.model.Cheat> = emptyList(),
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
                cheats = cheats,
                isFastForward = isFastForward,
                rewindEnabled = rewindEnabled,
                onSave = {},
                onLoad = {},
                onScreenshot = {},
                onToggleFastForward = {},
                onRewind = {},
                onToggleCheat = { _, _ -> },
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
        onNodeWithContentDescription("2 cheats active, tap to manage")
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
                saveSlots = emptyMap(),
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
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
                saveSlots = emptyMap(),
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
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
                saveSlots = emptyMap(),
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
            )
        }
        waitForIdle()

        onNodeWithText("Swipe up: save \u2022 Hold: load").assertExists()
    }

    @Test
    fun saveSlotsPageCallsOnSelectSlot() = runComposeUiTest {
        var selectedSlot = -1
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                saveSlots = emptyMap(),
                onSelectSlot = { selectedSlot = it },
                onSaveToSlot = {},
                onLoadFromSlot = {},
            )
        }
        waitForIdle()

        // Tap slot 3
        onNodeWithContentDescription("Save slot 3").performClick()
        waitForIdle()

        assert(selectedSlot == 3) { "Expected selectedSlot to be 3, was $selectedSlot" }
    }

    // -- Cheat Toggle Panel Tests ----------------------------------------------

    private val testCheats = listOf(
        Cheat(id = "c1", gameId = "1", index = 0, description = "Infinite Lives", code = "ABCD", enabled = false),
        Cheat(id = "c2", gameId = "1", index = 1, description = "Invincibility", code = "EFGH", enabled = true),
        Cheat(id = "c3", gameId = "1", index = 2, description = "Max Score", code = "IJKL", enabled = false),
    )

    @Test
    fun cheatsCardTapOpensPanel() = runComposeUiTest {
        renderDashboard(hasCheats = true, enabledCheatCount = 1, cheats = testCheats)

        // Tap the cheats card to open the panel
        onNodeWithContentDescription("1 cheats active, tap to manage").performClick()
        waitForIdle()

        // Verify the cheats panel is shown with the "Cheats" title
        onNodeWithText("Cheats").assertExists().assertIsDisplayed()

        // Verify individual cheat items are rendered
        onNodeWithContentDescription("Cheat: Infinite Lives, disabled")
            .assertExists()
            .assertIsDisplayed()
        onNodeWithContentDescription("Cheat: Invincibility, enabled")
            .assertExists()
            .assertIsDisplayed()
        onNodeWithContentDescription("Cheat: Max Score, disabled")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun cheatsPanelClosesOnCloseButton() = runComposeUiTest {
        renderDashboard(hasCheats = true, enabledCheatCount = 1, cheats = testCheats)

        // Open cheats panel
        onNodeWithContentDescription("1 cheats active, tap to manage").performClick()
        waitForIdle()

        // Verify panel is open
        onNodeWithContentDescription("Close cheats panel").assertExists()

        // Close the panel
        onNodeWithContentDescription("Close cheats panel").performClick()
        waitForIdle()

        // Verify we're back to the dashboard — stat cards should be visible again
        onNodeWithContentDescription("1 cheats active, tap to manage")
            .assertExists()
            .assertIsDisplayed()

        // Close button should no longer be visible
        onAllNodesWithContentDescription("Close cheats panel")
            .assertCountEquals(0)
    }

    @Test
    fun cheatsPanelToggleCallsCallback() = runComposeUiTest {
        var toggledCheatId: String? = null
        var toggledEnabled: Boolean? = null

        setContent {
            SecondaryDashboardPage(
                fps = 60f,
                frameTime = 16.7f,
                activeSlot = 1,
                hasCheats = true,
                enabledCheatCount = 1,
                cheats = testCheats,
                isFastForward = false,
                rewindEnabled = false,
                onSave = {},
                onLoad = {},
                onScreenshot = {},
                onToggleFastForward = {},
                onRewind = {},
                onToggleCheat = { id, enabled ->
                    toggledCheatId = id
                    toggledEnabled = enabled
                },
            )
        }

        // Open cheats panel
        onNodeWithContentDescription("1 cheats active, tap to manage").performClick()
        waitForIdle()

        // Find the switch within the "Infinite Lives" cheat row and toggle it
        // The SpSwitch is a child of the cheat row; clicking the row's switch toggles it
        onNodeWithContentDescription("Cheat: Infinite Lives, disabled")
            .onChildren()
            .filterToOne(isToggleable())
            .performClick()
        waitForIdle()

        assertTrue(toggledCheatId == "c1", "Expected cheat ID 'c1', got '$toggledCheatId'")
        assertTrue(toggledEnabled == true, "Expected enabled to be true, got $toggledEnabled")
    }

    @Test
    fun cheatsCardNotClickableWhenNoCheats() = runComposeUiTest {
        renderDashboard(hasCheats = false, enabledCheatCount = 0, cheats = emptyList())

        // "No cheats available" should be present (without "tap to manage")
        onNodeWithContentDescription("No cheats available")
            .assertExists()
            .assertIsDisplayed()

        // "tap to manage" content description should NOT be present
        onAllNodesWithContentDescription("0 cheats active, tap to manage")
            .assertCountEquals(0)

        // Tapping the card should not open the panel
        onNodeWithContentDescription("No cheats available").performClick()
        waitForIdle()

        // The cheats panel "Close cheats panel" button should not appear
        onAllNodesWithContentDescription("Close cheats panel")
            .assertCountEquals(0)

        // "No cheats" text should still be visible (dashboard is still showing)
        onNodeWithText("No cheats")
            .assertExists()
            .assertIsDisplayed()
    }

    // -- Save Slots Gesture Hint & Thumbnail Tests -----------------------------

    @Test
    fun saveSlotsHintTextShowsGestures() = runComposeUiTest {
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                saveSlots = emptyMap(),
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
            )
        }
        waitForIdle()

        // Verify the hint text shows save and load gesture instructions
        onNodeWithText("Swipe up: save \u2022 Hold: load")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun saveSlotsShowsFilledSlotInfo() = runComposeUiTest {
        val saveSlots = mapOf(
            2 to SaveSlotInfo(
                screenshotUrl = "https://example.com/screenshot.png",
                timestamp = "12:34",
                isFilled = true,
            ),
        )

        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 1,
                saveSlots = saveSlots,
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
            )
        }
        waitForIdle()

        // Filled slot 2 should have "filled" in its content description
        onNodeWithContentDescription("Save slot 2, filled, saved at 12:34")
            .assertExists()
            .assertIsDisplayed()

        // Empty slot 1 should NOT have "filled" — just "active"
        onNodeWithContentDescription("Save slot 1, active")
            .assertExists()
            .assertIsDisplayed()
    }

    @Test
    fun saveSlotsEmptySlotShowsPlaceholder() = runComposeUiTest {
        setContent {
            SecondarySaveSlotsPage(
                activeSlot = 3,
                saveSlots = emptyMap(),
                onSelectSlot = {},
                onSaveToSlot = {},
                onLoadFromSlot = {},
            )
        }
        waitForIdle()

        // All empty slots (including active) show "+" placeholder text
        onAllNodesWithText("+").assertCountEquals(10) // All 10 slots are empty

        // Non-active empty slots show "Empty" label text
        onAllNodesWithText("Empty").assertCountEquals(9) // 9 non-active empty slots

        // Active empty slot shows "Active" label
        onNodeWithContentDescription("Save slot 3, active").assertExists()
    }
}
