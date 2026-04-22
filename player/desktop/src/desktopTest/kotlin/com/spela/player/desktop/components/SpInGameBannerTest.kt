package com.spela.player.desktop.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.ui.components.SpDecisionAction
import com.spela.player.presentation.ui.components.SpInGameBanner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for [SpInGameBanner] — the thin over-emulator
 * banner used by the #672 rehearsal flow ("Trying {core} — your save
 * is untouched. Did this work?").
 */
@OptIn(ExperimentalTestApi::class)
class SpInGameBannerTest {

    // Minimal throwaway ImageVector — the component renders whatever
    // icon the parent passes, and these tests care about layout /
    // click wiring rather than the specific glyph. Avoids a
    // test-module dependency on material-icons.
    private val stubIcon: ImageVector = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).build()

    @Test
    fun rendersTextWhenNoActions() = runComposeUiTest {
        setContent {
            SpInGameBanner(
                text = "Trying Nestopia UE — your save is untouched.",
                icon = stubIcon,
                testTagName = "rehearsal-banner",
            )
        }

        onNodeWithTag("rehearsal-banner").assertIsDisplayed()
        onNodeWithText("Trying Nestopia UE — your save is untouched.")
            .assertIsDisplayed()
    }

    @Test
    fun rendersPrimaryActionAndFiresOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            SpInGameBanner(
                text = "Trying Nestopia UE",
                icon = stubIcon,
                primaryAction = SpDecisionAction(
                    label = "Did this work?",
                    onClick = { clicks++ },
                ),
            )
        }

        onNodeWithText("Did this work?").assertIsDisplayed()
        onNodeWithTag("sp-banner-primary").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun rendersSecondaryActionAndFiresOnClick() = runComposeUiTest {
        var compareClicks = 0
        setContent {
            SpInGameBanner(
                text = "Trying Nestopia UE",
                icon = stubIcon,
                secondaryAction = SpDecisionAction(
                    label = "Switch to older version for comparison",
                    onClick = { compareClicks++ },
                ),
            )
        }

        onNodeWithText("Switch to older version for comparison")
            .assertIsDisplayed()
        onNodeWithTag("sp-banner-secondary").performClick()
        assertEquals(1, compareClicks)
    }

    @Test
    fun bothActionsRenderTogether() = runComposeUiTest {
        setContent {
            SpInGameBanner(
                text = "Locked to version v-a3f9",
                icon = stubIcon,
                primaryAction = SpDecisionAction("Did this work?", {}),
                secondaryAction = SpDecisionAction(
                    "Switch to older version for comparison",
                    {},
                ),
            )
        }

        onNodeWithTag("sp-banner-primary").assertIsDisplayed()
        onNodeWithTag("sp-banner-secondary").assertIsDisplayed()
    }
}
