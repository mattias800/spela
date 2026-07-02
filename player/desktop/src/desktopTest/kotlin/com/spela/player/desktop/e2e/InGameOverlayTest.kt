package com.spela.player.desktop.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * E2E tests for in-game overlay interactions.
 * Tests: Overlay buttons (Save, Load, Screenshot, Fast Forward),
 *        Resume/Exit Game, and overlay toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class InGameOverlayTest {

    private val drawerActionLabels = listOf(
        "Save",
        "Load",
        "Screenshot",
        "Fast",
        "Challenge",
        "Controls",
        "Remap",
    )

    private fun createHarnessWithGameReady(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
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

        // Start game
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        // Open overlay (hidden by default on game start)
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
    }

    private fun ComposeUiTest.startGameInFixedRoot(harness: SpelaTestHarness) {
        setContent {
            Box(Modifier.width(1200.dp).height(800.dp).testTag("overlay_drawer_test_root")) {
                harness.App()
            }
        }
        advance(harness)

        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
    }

    private fun ComposeUiTest.boundsForText(text: String): Rect =
        onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?: error("Expected text node '$text'")

    private fun ComposeUiTest.boundsForContentDescription(description: String): Rect =
        onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?: error("Expected content-description node '$description'")

    private fun ComposeUiTest.textBoundsInsideBounds(text: String, bounds: Rect): Rect? =
        onAllNodesWithText(text, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .firstOrNull { textBounds ->
                textBounds.center.x in bounds.left..bounds.right &&
                    textBounds.center.y in bounds.top..bounds.bottom
            }

    private fun ComposeUiTest.assertTextInsideBounds(text: String, bounds: Rect) {
        val matchingTextBounds = textBoundsInsideBounds(text, bounds)

        assertTrue(
            matchingTextBounds != null,
            "Expected text '$text' inside overlay row bounds=$bounds",
        )
    }

    private fun ComposeUiTest.overlayTitleBounds(): Rect =
        onAllNodes(
            hasText("Castlevania") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInRoot
            ?: error("Expected overlay heading for Castlevania")

    private fun assertInsideLeftDrawer(label: String, bounds: Rect, rootBounds: Rect) {
        val drawerLimit = rootBounds.left + rootBounds.width * 0.52f
        assertTrue(
            bounds.right <= drawerLimit,
            "$label should stay inside the left drawer. bounds=$bounds drawerLimit=$drawerLimit",
        )
    }

    @Test
    fun overlayOpensAsDrawerPreservesContentAndDismisses() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGameInFixedRoot(harness)

        val rootBounds = onNodeWithTag("overlay_drawer_test_root", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot

        val titleBounds = overlayTitleBounds()
        assertInsideLeftDrawer("game title", titleBounds, rootBounds)
        assertTrue(
            titleBounds.left <= rootBounds.left + rootBounds.width * 0.30f,
            "game title should be anchored in the left drawer, not centered over the game. bounds=$titleBounds root=$rootBounds",
        )

        drawerActionLabels.forEach { label ->
            onNodeWithContentDescription(label, useUnmergedTree = true).assertIsDisplayed()
        }
        onNodeWithText("Exit Game", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("Continue", useUnmergedTree = true).assertIsDisplayed()

        val actionBounds = drawerActionLabels.map { label ->
            label to boundsForContentDescription(label)
        }
        val actionTextBounds = actionBounds.map { (label, bounds) ->
            label to (
                textBoundsInsideBounds(label, bounds)
                    ?: error("Expected text '$label' inside overlay row bounds=$bounds")
                )
        }
        actionBounds.forEach { (label, bounds) ->
            assertInsideLeftDrawer(label, bounds, rootBounds)
            assertTextInsideBounds(label, bounds)
            assertTrue(
                bounds.height < 48f,
                "$label drawer row should be compact. bounds=$bounds",
            )
        }
        assertInsideLeftDrawer("Exit Game", boundsForText("Exit Game"), rootBounds)
        assertInsideLeftDrawer("Continue", boundsForText("Continue"), rootBounds)

        val firstTextLeft = actionTextBounds.first().second.left
        actionTextBounds.drop(1).forEach { (label, textBounds) ->
            assertTrue(
                abs(textBounds.left - firstTextLeft) <= 2f,
                "$label text should align to the fixed drawer icon column. first=$firstTextLeft current=${textBounds.left}",
            )
        }

        actionBounds.zipWithNext().forEach { (above, below) ->
            assertTrue(
                below.second.center.y > above.second.center.y + 12f,
                "drawer actions should be a vertical list; ${above.first} and ${below.first} are not vertically ordered",
            )
        }

        onNodeWithContentDescription("Volume", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("100%", useUnmergedTree = true).assertIsDisplayed()
        val volumeIconBounds = boundsForContentDescription("Volume")
        val volumeValueBounds = boundsForText("100%")
        assertInsideLeftDrawer("Volume", volumeIconBounds, rootBounds)
        assertInsideLeftDrawer("100%", volumeValueBounds, rootBounds)
        assertTrue(
            abs(volumeIconBounds.center.y - volumeValueBounds.center.y) <= 24f,
            "volume should be a compact row with icon and value aligned. icon=$volumeIconBounds value=$volumeValueBounds",
        )

        val continueBounds = boundsForText("Continue")
        val exitBounds = boundsForText("Exit Game")
        assertTrue(
            exitBounds.top > continueBounds.bottom,
            "Exit Game should be the final drawer action below Continue. exit=$exitBounds continue=$continueBounds",
        )

        val fpsLabelBounds = boundsForText("FPS")
        assertInsideLeftDrawer("FPS", fpsLabelBounds, rootBounds)
        assertTrue(
            fpsLabelBounds.top > exitBounds.bottom,
            "performance metrics should sit at the bottom of the drawer after primary actions. fps=$fpsLabelBounds exit=$exitBounds",
        )

        onNodeWithText("Continue", useUnmergedTree = true).performClick()
        advanceQuick(harness)

        assertFalse(harness.emulationViewModel.state.value.showOverlay, "Continue should close the drawer")
        onNodeWithText("Exit Game").assertDoesNotExist()

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Exit Game", useUnmergedTree = true).assertIsDisplayed()

        onNodeWithTag("overlay_drawer_test_root", useUnmergedTree = true)
            .performTouchInput {
                click(Offset(rootBounds.width - 24f, rootBounds.height / 2f))
            }
        advanceQuick(harness)

        assertFalse(harness.emulationViewModel.state.value.showOverlay, "Backdrop should close the drawer")
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun overlayActionsSaveLoadFastForwardResumeAndExit() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        // The Load button downloads the session's auto-save. Seed a
        // session + auto-save so there's something to unserialize;
        // mirrors the state "a prior play left an auto-save behind".
        val sessionId = "session-1"
        harness.sessionRepo.sessions.add(
            com.spela.player.domain.model.GameSession(id = sessionId, gameId = "1", name = "Default"),
        )
        harness.sessionRepo.preSeedAutoSave(sessionId)

        startGame(harness)

        onNodeWithContentDescription("Save").assertIsDisplayed()
        onNodeWithContentDescription("Load").assertIsDisplayed()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()

        val saveCountBefore = harness.libretroController.saveCallCount
        onNodeWithContentDescription("Save").performClick()
        advance(harness)
        assertTrue(
            harness.libretroController.saveCallCount > saveCountBefore,
            "Save should have triggered serialization",
        )

        val loadCountBefore = harness.libretroController.loadCallCount
        onNodeWithContentDescription("Load").performClick()
        advance(harness)
        assertTrue(
            harness.libretroController.loadCallCount > loadCountBefore,
            "Load should have triggered unserialization",
        )

        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off initially")
        onNodeWithContentDescription("Fast").performClick()
        advanceQuick(harness)
        assertTrue(harness.libretroController.isFastForward, "Fast forward should be on")
        onNodeWithContentDescription("Normal").performClick()
        advanceQuick(harness)
        assertFalse(harness.libretroController.isFastForward, "Fast forward should be off again")

        onNodeWithText("Continue").assertIsDisplayed()
        onNodeWithText("Continue").performClick()
        advanceQuick(harness)

        onNodeWithText("Exit Game").assertDoesNotExist()
        assertTrue(harness.libretroController.isRunning)
        assertFalse(harness.libretroController.isPaused)

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Exit Game").performClick()
        advance(harness)
        assertFalse(harness.libretroController.isRunning, "Emulation should be stopped after exit")
        assertTrue(harness.libretroController.stopCallCount > 0, "Stop should have been called")
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun keyMappingOverlayShowsListAndSavesPerGameOverride() = runComposeUiTest {
        val harness = createHarnessWithGameReady()
        startGame(harness)

        harness.emulationViewModel.onIntent(EmulationIntent.ShowKeyMapping)
        advanceQuick(harness)

        // The editor now shows a per-console labeled mapping list (#1335), not a
        // pictorial controller diagram.
        onNodeWithTag("mapping_list").assertExists()
        onNodeWithTag("save_game_override").assertExists()

        onNodeWithTag("save_game_override").performClick()
        advanceQuick(harness)

        assertTrue(
            harness.keyMappingViewModel.state.value.hasGameOverride,
            "Saving in the overlay should create a per-game override",
        )

        // Tapping a button row enters single-button listening mode for it.
        onNodeWithTag("mapping_list").onChildren().onFirst().performClick()
        advanceQuick(harness)
        assertTrue(
            harness.keyMappingViewModel.state.value.currentMappingButton != null,
            "Tapping a mapping row should enter listening mode",
        )
    }

    @Test
    fun overlayHidesSaveLoadForNonSaveStateConsoles() = runComposeUiTest {
        val harness = createHarnessWithGameReady()

        // Configure controller to report no save state support (e.g. GameCube via Dolphin)
        harness.libretroController.supportsSaveStatesResult = false

        startGame(harness)

        onNodeWithContentDescription("Save").assertDoesNotExist()
        onNodeWithContentDescription("Load").assertDoesNotExist()
        onNodeWithContentDescription("Screenshot").assertIsDisplayed()
    }

}
