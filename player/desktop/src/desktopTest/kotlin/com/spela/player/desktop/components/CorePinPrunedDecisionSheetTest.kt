package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.feature.ingame.CorePinPrunedDecisionSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for the #672 Sheet B composable. Pins the copy
 * contract from `design-proposals/core-upgrade-decision-spec.md`
 * §"Sheet B — The older version isn't available anymore" and the
 * callback routing from its three actions.
 */
@OptIn(ExperimentalTestApi::class)
class CorePinPrunedDecisionSheetTest {

    private val decision = CoreDecision.PinPruned(
        coreName = "nestopia",
        coreDisplayName = "Nestopia UE",
        gameTitle = "Castlevania",
        prunedSha = "abcd1234".repeat(8),
    )

    @Test
    fun rendersSpecTitleAndBody() = runComposeUiTest {
        setContent {
            CorePinPrunedDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onStartFresh = {},
                onRemindLater = {},
            )
        }

        onNodeWithTag("core-upgrade-sheet-b").assertIsDisplayed()
        onNodeWithText("The older version isn't available anymore").assertIsDisplayed()
        // Spec body — verbatim against `core_upd.sheet_b.body`. Note the
        // body does NOT interpolate the game title (that's Sheet A's job).
        onNodeWithText(
            "We used to keep the exact core version your save was " +
                "made with, but it's been rotated out of the server's history. " +
                "We'll use the latest Nestopia UE instead. Try your save first — " +
                "if it looks wrong you can start fresh.",
        ).assertIsDisplayed()
    }

    @Test
    fun fallsBackToCoreNameWhenDisplayNameIsEmpty() = runComposeUiTest {
        // Defence in depth: the server occasionally ships cores with no
        // display-name set (pre-backfill rows). We must not emit an
        // empty " " in the body — fall back to the libretro id.
        setContent {
            CorePinPrunedDecisionSheet(
                decision = decision.copy(coreDisplayName = ""),
                onTryWithMySave = {},
                onStartFresh = {},
                onRemindLater = {},
            )
        }

        onNodeWithText(
            "We used to keep the exact core version your save was " +
                "made with, but it's been rotated out of the server's history. " +
                "We'll use the latest nestopia instead. Try your save first — " +
                "if it looks wrong you can start fresh.",
        ).assertIsDisplayed()
    }

    @Test
    fun primaryFiresOnTryWithMySave() = runComposeUiTest {
        var tryClicks = 0
        setContent {
            CorePinPrunedDecisionSheet(
                decision = decision,
                onTryWithMySave = { tryClicks++ },
                onStartFresh = {},
                onRemindLater = {},
            )
        }

        onNodeWithText("Try with my save").assertIsDisplayed()
        onNodeWithTag("sp-decision-primary").performClick()
        assertEquals(1, tryClicks)
    }

    @Test
    fun secondaryFiresOnStartFresh() = runComposeUiTest {
        var freshClicks = 0
        setContent {
            CorePinPrunedDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onStartFresh = { freshClicks++ },
                onRemindLater = {},
            )
        }

        onNodeWithText("Start fresh anyway").assertIsDisplayed()
        onNodeWithTag("sp-decision-secondary").performClick()
        assertEquals(1, freshClicks)
    }

    @Test
    fun moreOptionsExposesRemindLater() = runComposeUiTest {
        var remindClicks = 0
        setContent {
            CorePinPrunedDecisionSheet(
                decision = decision,
                onTryWithMySave = {},
                onStartFresh = {},
                onRemindLater = { remindClicks++ },
            )
        }

        onNodeWithTag("sp-decision-more-toggle").performClick()
        onNodeWithText("Remind me the next time I play this").assertIsDisplayed()
        onNodeWithTag("sp-decision-more-0").performClick()
        assertEquals(1, remindClicks)
    }
}
