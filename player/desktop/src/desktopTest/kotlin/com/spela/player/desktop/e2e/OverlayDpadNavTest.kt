package com.spela.player.desktop.e2e

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
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
 * This asserts the anchor (focus lands on Continue on open) — the reliably
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

        harness.emulationViewModel.onIntent(EmulationIntent.ToggleOverlay)
        advanceQuick(harness)
        onNodeWithText("Continue").assertExists()
        harness.gamepadPortManager.setInputMode(InputMode.GAMEPAD)
        advanceQuick(harness)
    }

    private fun ComposeUiTest.pressOverlayKey(key: Key, harness: SpelaTestHarness) {
        onRoot().performKeyInput { pressKey(key) }
        advanceQuick(harness)
    }

    @Test
    fun overlayAnchorsFocusOnOpen() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        // The menu anchors focus on Continue when it opens. On the unfixed
        // code this was <none> (the focus request never landed), so the first
        // d-pad press escaped into the screen behind the scrim.
        assertEquals(
            listOf("Continue"),
            focusedLabels(),
            "overlay must anchor focus on its Continue button when it opens",
        )
    }

    @Test
    fun overlayDpadMovesVerticallyStaysContainedAndEscapeCloses() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Exit Game"), focusedLabels())

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Remap"), focusedLabels())

        pressOverlayKey(Key.DirectionRight, harness)
        assertEquals(listOf("Remap"), focusedLabels(), "right should not escape the drawer")

        pressOverlayKey(Key.DirectionLeft, harness)
        assertEquals(listOf("Remap"), focusedLabels(), "left should not escape the drawer")

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Exit Game"), focusedLabels())

        pressOverlayKey(Key.DirectionDown, harness)
        assertEquals(listOf("Continue"), focusedLabels())

        pressOverlayKey(Key.Escape, harness)

        assertEquals(false, harness.emulationViewModel.state.value.showOverlay)
        onNodeWithText("Exit Game").assertDoesNotExist()
    }

    @Test
    fun overlayConfirmActivatesFocusedDrawerAction() = runComposeUiTest {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        startGameWithOverlayOpen(harness)

        pressOverlayKey(Key.DirectionUp, harness)
        assertEquals(listOf("Exit Game"), focusedLabels())

        pressOverlayKey(Key.Enter, harness)

        assertEquals(false, harness.libretroController.isRunning)
        assertTrue(
            harness.libretroController.stopCallCount > 0,
            "Enter on focused Exit Game should stop emulation",
        )
    }
}
