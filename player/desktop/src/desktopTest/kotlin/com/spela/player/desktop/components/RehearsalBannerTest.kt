package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.feature.ingame.RehearsalBanner
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for the #672 rehearsal banner role composable.
 * Pins copy verbatim against `core_upd.banner.trying` /
 * `core_upd.banner.did_work_btn` keys in the spec, and the
 * "Did this work?" callback wiring.
 */
@OptIn(ExperimentalTestApi::class)
class RehearsalBannerTest {

    private val decision = CoreDecision.RehearsalPrompt(
        coreName = "nestopia",
        coreDisplayName = "Nestopia UE",
        usingNewSha = true,
    )

    @Test
    fun rendersBannerWithCoreDisplayNameAndAction() = runComposeUiTest {
        setContent {
            RehearsalBanner(
                decision = decision,
                onDidThisWork = {},
            )
        }

        onNodeWithTag("core-upgrade-rehearsal-banner").assertIsDisplayed()
        onNodeWithText("Trying Nestopia UE — your save is untouched.").assertIsDisplayed()
        onNodeWithText("Did this work?").assertIsDisplayed()
    }

    @Test
    fun fallsBackToCoreNameWhenDisplayNameIsEmpty() = runComposeUiTest {
        setContent {
            RehearsalBanner(
                decision = decision.copy(coreDisplayName = ""),
                onDidThisWork = {},
            )
        }

        onNodeWithText("Trying nestopia — your save is untouched.").assertIsDisplayed()
    }

    @Test
    fun didThisWorkButtonFiresCallback() = runComposeUiTest {
        var clicks = 0
        setContent {
            RehearsalBanner(
                decision = decision,
                onDidThisWork = { clicks++ },
            )
        }

        onNodeWithTag("sp-banner-primary").performClick()
        assertEquals(1, clicks)
    }
}
