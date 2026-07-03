package com.spela.player.desktop.e2e

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.presentation.navigation.NavigationIntent
import com.spela.player.presentation.navigation.SpScreen
import com.spela.player.presentation.ui.TestTags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #1082 regression test: console cards must never stretch past 280 dp wide
 * regardless of viewport size, so the design's intended 4:5 portrait aspect
 * ratio (#1441) stays a compact size on AYN Thor landscape and any windowed
 * desktop. Pre-fix the grid stayed at 3 columns past 700 dp and each card
 * grew unbounded with the viewport (≥ 600 dp at 1920 dp containers).
 *
 * The breakpoint logic itself is unit-tested in
 * `ConsoleColumnsForWidthTest`; this test is the UI-level cap assertion.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class LibraryConsolesTabWidthCapTest {

    private fun createHarnessWithConsoles(count: Int): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.gameRepo.consoles = (1..count).map { i ->
            Console(
                id = "console$i",
                name = "Console $i",
                abbreviation = "C$i",
                gameCount = i,
                colorTheme = "#333333",
            )
        }
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        return harness
    }

    @Test
    fun cardWidthCappedAtWideViewport() = runComposeUiTest {
        // 12 consoles spans multiple rows at any column count from 1 to 5,
        // so the assertion catches a cap regression in any branch of the
        // breakpoint `when`.
        val harness = createHarnessWithConsoles(12)

        // Force a wide viewport so the test actually exercises the
        // ≥1500 dp branch where the per-card widthIn cap is the only
        // thing stopping cards from stretching past 340 dp. Without an
        // explicit width the test ran at whatever the default Compose
        // desktop test window happens to be (often narrow enough that
        // cards naturally fit under the cap), which would let a cap
        // regression slip through.
        setContent {
            Box(
                modifier = Modifier
                    .width(2400.dp)
                    .height(1080.dp),
            ) {
                harness.App()
            }
        }
        advance(harness)

        // Every console card on screen must respect the 280 dp width cap.
        // Asserting on each card individually (vs. just the first) so a
        // partial-row regression in the existing weight(1f) Spacer logic
        // is also caught.
        //
        // SemanticsNode.size is pixels; the density() helper on
        // ComposeUiTest converts the dp ceiling into the equivalent pixel
        // budget for comparison. Tolerance of 1 px absorbs sub-pixel
        // rounding in Compose's measurement pipeline.
        val maxWidthPx = with(density) { 280.dp.toPx() }
        repeat(12) { i ->
            val node = onNodeWithTag(TestTags.consoleCard("console${i + 1}"))
                .fetchSemanticsNode()
            val widthPx = node.size.width.toFloat()
            assertTrue(
                widthPx <= maxWidthPx + 1f,
                "console${i + 1} width was ${widthPx} px, expected ≤ ${maxWidthPx} px (280 dp)",
            )
        }
    }
}
