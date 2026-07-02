package com.spela.player.desktop.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.WidescreenMode
import com.spela.player.presentation.intent.EmulationIntent
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.gamepad.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression test for #1410: the in-game overlay menu must anchor d-pad focus
 * on itself when it opens.
 *
 * Before the fix, the panel had no `focusGroup()` and the open-time focus
 * request was unreliable, so the menu had no focus anchor — focus stayed
 * `<none>` and the first d-pad press escaped through the scrim into the
 * game-detail screen behind it (rating stars, metadata, that screen's
 * buttons), leaving the overlay's own actions unreachable.
 *
 * This asserts the anchor (focus lands on the first drawer action on open) — the reliably
 * testable half; it is `<none>` on the unfixed code. The full d-pad sweep
 * across every action is verified on hardware (the focus-ring/centre-on-focus
 * animations make repeated in-test d-pad traversal flaky — see the QA issue),
 * and the containment itself comes from the `focusGroup()` added in the fix.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class OverlayDpadNavTest {

    private fun ComposeUiTest.focusedLabels(): List<String> =
        onAllNodes(isFocused()).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
        }

    private fun ComposeUiTest.startGameWithOverlayOpen(harness: SpelaTestHarness) {
        harness.downloadRepo.preCacheGame("1")
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.GameDetail("1")))
        setContent { harness.App() }
        advance(harness)
        onNodeWithTag("game_detail_play_button").performClick()
        advance(harness)

        openOverlay(harness)
    }

    private fun configureGameOneAsWii(harness: SpelaTestHarness) {
        harness.gameRepo.consoles = harness.gameRepo.consoles + Console(
            id = "wii",
            name = "Nintendo Wii",
            abbreviation = "Wii",
            gameCount = 1,
        )
        harness.gameRepo.games = harness.gameRepo.games.map { game ->
            if (game.id == "1") {
                game.copy(
                    consoleId = "wii",
                    consoleName = "Nintendo Wii",
                    fileName = "castlevania.wbfs",
                )
            } else {
                game
            }
        }
    }

    private fun ComposeUiTest.openOverlay(harness: SpelaTestHarness) {
        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithContentDescription("Save").assertExists()
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
    }

    private fun ComposeUiTest.tapScrimOutsideDrawer(harness: SpelaTestHarness) {
        val rootBounds = onRoot().fetchSemanticsNode().boundsInRoot
        onRoot().performTouchInput {
            click(Offset(rootBounds.width - 24f, rootBounds.height / 2f))
        }
        advanceQuick(harness)
    }

    private fun ComposeUiTest.pressOverlayKey(key: Key, harness: SpelaTestHarness) {
        onAllNodes(isRoot()).onFirst().performKeyInput { pressKey(key) }
        advanceQuick(harness)
    }

    @Test
    fun overlayAnchorsFocusOnOpen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        // The menu anchors focus on the first action when it opens. On the unfixed
        // code this was <none> (the focus request never landed), so the first
        // d-pad press escaped into the screen behind the scrim.
        assertEquals(
            listOf("Save"),
            focusedLabels(),
            "overlay must anchor focus on its first drawer action when it opens",
        )
    }

    @Test
    fun overlayDpadMovesVerticallyStaysContainedAndEscapeCloses() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Load"), focusedLabels())

        pressOverlayKey(Key.DirectionRight, harness)
        assertEquals(listOf("Load"), focusedLabels(), "right should not escape the drawer")

        pressOverlayKey(Key.DirectionLeft, harness)
        assertEquals(listOf("Load"), focusedLabels(), "left should not escape the drawer")

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Screenshot"), focusedLabels())

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Load"), focusedLabels())

        pressOverlayKey(Key.Escape, harness)

        assertEquals(false, harness.emulationViewModel.state.value.showOverlay)
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun overlayReanchorsFocusAfterMixedCloseAndReopen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)
        assertEquals(listOf("Save"), focusedLabels())

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Load"), focusedLabels())

        pressOverlayKey(Key.Escape, harness)
        assertEquals(false, harness.emulationViewModel.state.value.showOverlay)

        openOverlay(harness)
        assertEquals(
            listOf("Save"),
            focusedLabels(),
            "overlay should reset focus to the first action after keyboard dismissal",
        )

        tapScrimOutsideDrawer(harness)
        assertEquals(false, harness.emulationViewModel.state.value.showOverlay)

        openOverlay(harness)
        assertEquals(
            listOf("Save"),
            focusedLabels(),
            "overlay should reset focus to the first action after touch dismissal",
        )

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Load"), focusedLabels(), "d-pad should work after reopening")
    }

    @Test
    fun overlayVolumeShortcutIsKeyboardOperable() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Volume"), focusedLabels())
        onNodeWithText("Volume").assertIsDisplayed()

        pressOverlayKey(Key.Enter, harness)
        onNodeWithContentDescription("Volume slider", useUnmergedTree = true).assertIsDisplayed()

        val initialVolume = harness.emulationViewModel.state.value.volume
        pressOverlayKey(Key.DirectionDown, harness)
        val loweredByDown = harness.emulationViewModel.state.value.volume
        assertTrue(
            loweredByDown < initialVolume,
            "down should lower volume. before=$initialVolume after=$loweredByDown",
        )

        pressOverlayKey(Key.DirectionUp, harness)
        val raisedByUp = harness.emulationViewModel.state.value.volume
        assertTrue(
            raisedByUp > loweredByDown,
            "up should raise volume. before=$loweredByDown after=$raisedByUp",
        )

        pressOverlayKey(Key.DirectionLeft, harness)
        val loweredByLeft = harness.emulationViewModel.state.value.volume
        assertTrue(
            loweredByLeft < raisedByUp,
            "left should lower volume. before=$raisedByUp after=$loweredByLeft",
        )

        pressOverlayKey(Key.DirectionRight, harness)
        val raisedByRight = harness.emulationViewModel.state.value.volume
        assertTrue(
            raisedByRight > loweredByLeft,
            "right should raise volume. before=$loweredByLeft after=$raisedByRight",
        )

        pressOverlayKey(Key.Escape, harness)
        onNodeWithContentDescription("Volume slider", useUnmergedTree = true).assertDoesNotExist()

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Save"), focusedLabels(), "d-pad should remain inside the drawer after closing volume")
    }

    @Test
    fun overlayWidescreenShortcutIsKeyboardOperableForSupportedConsole() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        configureGameOneAsWii(harness)
        startGameWithOverlayOpen(harness)

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Widescreen"), focusedLabels())

        pressOverlayKey(Key.DirectionLeft, harness)
        assertEquals(listOf("Volume"), focusedLabels(), "left should move from Widescreen to Volume")

        pressOverlayKey(Key.DirectionRight, harness)
        assertEquals(listOf("Widescreen"), focusedLabels(), "right should move from Volume to Widescreen")
        onNodeWithText("Widescreen").assertIsDisplayed()
        assertEquals(WidescreenMode.STRETCH, harness.emulationViewModel.state.value.widescreenMode)

        pressOverlayKey(Key.Enter, harness)
        onNodeWithText("Stretch (Full)", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Stretch (Full)" in focusedLabels())

        pressOverlayKey(Key.DirectionDown, harness)
        assertTrue("Zoom" in focusedLabels())

        pressOverlayKey(Key.Enter, harness)
        assertEquals(WidescreenMode.ZOOM, harness.emulationViewModel.state.value.widescreenMode)
        onNodeWithText("Stretch (Full)", useUnmergedTree = true).assertDoesNotExist()

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Save"), focusedLabels(), "d-pad should remain inside the drawer after closing widescreen")
    }

    @Test
    fun overlayConfirmActivatesFocusedDrawerAction() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        repeat(8) {
            pressOverlayKey(Key.DirectionDown, harness)
        }
        assertEquals(listOf("Exit Game"), focusedLabels())

        pressOverlayKey(Key.Enter, harness)

        assertEquals(false, harness.libretroController.isRunning)
        assertTrue(
            harness.libretroController.stopCallCount > 0,
            "Enter on focused Exit Game should stop emulation",
        )
    }
}
