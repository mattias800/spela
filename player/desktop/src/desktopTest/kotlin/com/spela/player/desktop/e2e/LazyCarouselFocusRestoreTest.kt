package com.spela.player.desktop.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import com.spela.player.domain.model.Game
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test

/**
 * Regression test for the lazy-carousel focus-restore guarantee
 * introduced in #1168 PR B (SpCarousel -> LazyRow).
 *
 * SpCarousel now composes items lazily — only items in (or near) the
 * visible window are in the composition tree. A naive implementation
 * would break focus restoration for any item the user had previously
 * scrolled to: after back-nav the LazyRow starts at index 0 and the
 * previously-focused FocusRequester isn't bound to any layout node, so
 * focusRestoreItem's `requestFocus` is a silent no-op.
 *
 * The fix: SpCarousel reads LocalFocusMemory at composition and, if the
 * saved key matches one of its items, `scrollToItem(targetIndex)` runs
 * before focusRestoreItem's ~120 ms layout-settle delay fires. By the
 * time `requestFocus` runs the item has been composed and its
 * FocusRequester is attached.
 *
 * This test walks the realistic user path: enough DirectionRight presses
 * to land on an item that's almost certainly outside the initial
 * viewport, forward-nav, back-nav, verify focus restored.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class LazyCarouselFocusRestoreTest {

    private fun game(id: String, title: String): Game = Game(
        id = id,
        title = title,
        consoleId = "snes",
        consoleName = "SNES",
        description = "Test",
        developer = "Test",
        publisher = "Test",
        releaseDate = "1990",
        genre = "Action",
        fileSize = 1024,
        fileName = "$id.smc",
        scrapeAttempts = 1,
    )

    private fun createHarness(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.deviceManager.setDeviceName("Test Device")
        // HomeScreen caps Continue Playing at .take(6), so 6 items is
        // the carousel's actual size in this test. With the desktop
        // test viewport only ~3 items are visible at a time — items at
        // index 3+ are guaranteed off-screen on cold remount, which is
        // the scenario the test needs to exercise.
        val recents = (1..6).map { i ->
            game(id = "g$i", title = "Recents Game $i")
        }
        harness.gameRepo.recentGamesOverride = recents
        harness.gameRepo.games = recents
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Home))
        return harness
    }

    private fun ComposeUiTest.awaitFocusedContentDescription(
        harness: SpelaTestHarness,
        contentDescription: String,
    ) {
        repeat(4) {
            if (onAllNodesWithContentDescription(contentDescription)
                    .fetchSemanticsNodes()
                    .any { node -> node.config.getOrNull(SemanticsProperties.Focused) == true }
            ) {
                return
            }
            advanceQuick(harness)
        }
        onNodeWithContentDescription(contentDescription).assert(isFocused())
    }

    @Test
    fun smoke_dPadAdvancesTwoPositions() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App(animationsEnabled = true) }
        advance(harness)

        // Same shape as continuePlaying_focusRestoredAfterKeyboardEnterEscape
        // but with the larger recents list. Two right presses should land
        // on Recents Game 3.
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
            pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
        }
        advanceFully(harness)
        awaitFocusedContentDescription(harness, "Recents Game 3, SNES")
    }

    @Test
    fun offScreenItem_focusRestoredAfterForwardAndBackNav() = runComposeUiTest {
        val harness = createHarness()
        setContent { harness.App(animationsEnabled = true) }
        advance(harness)

        // Walk right with the d-pad until focused on the last item
        // ("Recents Game 6", index 5). On a 6-item carousel with the
        // desktop test viewport showing ~3 items, the leftmost items
        // are disposed when the focused item is at index 5 — and on
        // back-nav the LazyRow remounts at index 0, so item 5 is
        // disposed unless SpCarousel's restoration logic actively
        // scrolls back to it.
        //
        // All presses go through a single performKeyInput block — the
        // intervening "rapid" window (<100 ms wall clock) tells
        // SpCarousel to snap-scroll instead of animating.
        val pressCount = 5
        onRoot().performKeyInput {
            repeat(pressCount) {
                pressKey(androidx.compose.ui.input.key.Key.DirectionRight)
            }
        }
        advanceFully(harness)

        val targetTitle = "Recents Game ${pressCount + 1}, SNES"
        awaitFocusedContentDescription(harness, targetTitle)

        // Forward to game detail.
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Enter)
        }
        advanceFully(harness)

        // Back to Home. The lazy carousel must scroll the saved item
        // into view and restore focus to it.
        onRoot().performKeyInput {
            pressKey(androidx.compose.ui.input.key.Key.Escape)
        }
        advanceFully(harness)

        awaitFocusedContentDescription(harness, targetTitle)
    }
}
