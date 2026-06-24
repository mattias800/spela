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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #1441: the console card stacks three fixed-proportion regions — photo over
 * logo over game count — so that, for any two cards in the same row, the photo
 * centres line up and the logo centres line up regardless of each console's
 * image aspect ratio or game-count label. This mirrors the web console card.
 *
 * The region geometry is what guarantees the alignment, so the test asserts on
 * the region bounds (photo / logo testTags) rather than the images, which don't
 * decode in the headless desktop test.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class ConsoleCardLayoutTest {

    private fun harnessWithTwoConsoles(): SpelaTestHarness {
        val harness = SpelaTestHarness(StandardTestDispatcher())
        harness.authRepo.preSetTokens()
        harness.gameRepo.consoles = listOf(
            Console(
                id = "console1", name = "Console One", abbreviation = "C1",
                gameCount = 5, colorTheme = "#333333",
                iconUrl = "/icon1.png", logoUrl = "/logo1.svg", photoUrl = "/photo1.png",
            ),
            Console(
                id = "console2", name = "Console Two", abbreviation = "C2",
                gameCount = 1, colorTheme = "#444444",
                iconUrl = "/icon2.png", logoUrl = "/logo2.svg", photoUrl = "/photo2.png",
            ),
        )
        harness.navigationViewModel.onIntent(NavigationIntent.NavigateTo(SpScreen.Consoles))
        return harness
    }

    @Test
    fun photoAndLogoRegionsAlignAcrossCardsInARow() = runComposeUiTest {
        val harness = harnessWithTwoConsoles()
        // Wide viewport so both consoles land in the same (first) row.
        setContent {
            Box(modifier = Modifier.width(2400.dp).height(1080.dp)) {
                harness.App()
            }
        }
        advance(harness)

        // Footer renders the game count, pluralised per card.
        onNodeWithText("5 games", useUnmergedTree = true).assertExists()
        onNodeWithText("1 game", useUnmergedTree = true).assertExists()

        fun bounds(tag: String) =
            onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        val card1 = bounds(TestTags.consoleCard("console1"))
        val card2 = bounds(TestTags.consoleCard("console2"))
        val photo1 = bounds(TestTags.consoleCardPhoto("console1"))
        val photo2 = bounds(TestTags.consoleCardPhoto("console2"))
        val logo1 = bounds(TestTags.consoleCardLogo("console1"))
        val logo2 = bounds(TestTags.consoleCardLogo("console2"))

        // Each region occupies the same fraction of its card, so two equally
        // sized cards in a row line up photo-to-photo and logo-to-logo. We
        // compare fractions (not absolute pixels) because the default-focused
        // card is transiently scaled up — a uniform scale preserves the
        // fractions, which is exactly the alignment guarantee the design makes.
        fun relTop(child: androidx.compose.ui.geometry.Rect, card: androidx.compose.ui.geometry.Rect) =
            (child.top - card.top) / card.height
        fun relHeight(child: androidx.compose.ui.geometry.Rect, card: androidx.compose.ui.geometry.Rect) =
            child.height / card.height

        assertTrue(
            abs(relTop(photo1, card1) - relTop(photo2, card2)) <= 0.01f,
            "photo region offset differs: ${relTop(photo1, card1)} vs ${relTop(photo2, card2)}",
        )
        assertTrue(
            abs(relHeight(photo1, card1) - relHeight(photo2, card2)) <= 0.01f,
            "photo region height fraction differs",
        )
        assertTrue(
            abs(relTop(logo1, card1) - relTop(logo2, card2)) <= 0.01f,
            "logo region offset differs: ${relTop(logo1, card1)} vs ${relTop(logo2, card2)}",
        )
        assertTrue(
            abs(relHeight(logo1, card1) - relHeight(logo2, card2)) <= 0.01f,
            "logo region height fraction differs",
        )

        // Photo region sits above the logo region within a card.
        assertTrue(
            relTop(photo1, card1) < relTop(logo1, card1),
            "photo should be above logo (${relTop(photo1, card1)} vs ${relTop(logo1, card1)})",
        )
    }
}
