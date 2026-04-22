package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.state.CoreDecision
import com.spela.player.presentation.ui.feature.ingame.RehearsalCrashedDecisionSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for the #672 Sheet D composable. Pins copy
 * verbatim against `core_upd.sheet_d.*` keys in
 * `design-proposals/core-upgrade-decision-spec.md` and the routing
 * for the four actions.
 */
@OptIn(ExperimentalTestApi::class)
class RehearsalCrashedDecisionSheetTest {

    private val decision = CoreDecision.RehearsalCrashed(
        coreName = "nestopia",
        coreDisplayName = "Nestopia UE",
    )

    @Test
    fun rendersSpecTitleAndBodyWithCoreDisplayName() = runComposeUiTest {
        setContent {
            RehearsalCrashedDecisionSheet(
                decision = decision,
                onLockOldVersion = {},
                onStartFresh = {},
                onReport = {},
                onTryLater = {},
            )
        }

        onNodeWithTag("core-upgrade-sheet-d").assertIsDisplayed()
        onNodeWithText("That didn't go well").assertIsDisplayed()
        onNodeWithText(
            "Nestopia UE ran into a problem while loading your save. " +
                "Your save itself is fine — we'll go back to the older version.",
        ).assertIsDisplayed()
    }

    @Test
    fun fallsBackToCoreNameWhenDisplayNameIsEmpty() = runComposeUiTest {
        setContent {
            RehearsalCrashedDecisionSheet(
                decision = decision.copy(coreDisplayName = ""),
                onLockOldVersion = {},
                onStartFresh = {},
                onReport = {},
                onTryLater = {},
            )
        }

        onNodeWithText(
            "nestopia ran into a problem while loading your save. " +
                "Your save itself is fine — we'll go back to the older version.",
        ).assertIsDisplayed()
    }

    @Test
    fun primaryFiresOnLockOldVersion() = runComposeUiTest {
        var lockClicks = 0
        setContent {
            RehearsalCrashedDecisionSheet(
                decision = decision,
                onLockOldVersion = { lockClicks++ },
                onStartFresh = {},
                onReport = {},
                onTryLater = {},
            )
        }

        onNodeWithText("Lock to the older version").assertIsDisplayed()
        onNodeWithTag("sp-decision-primary").performClick()
        assertEquals(1, lockClicks)
    }

    @Test
    fun secondaryFiresOnStartFresh() = runComposeUiTest {
        var freshClicks = 0
        setContent {
            RehearsalCrashedDecisionSheet(
                decision = decision,
                onLockOldVersion = {},
                onStartFresh = { freshClicks++ },
                onReport = {},
                onTryLater = {},
            )
        }

        onNodeWithText("Start fresh on the new version").assertIsDisplayed()
        onNodeWithTag("sp-decision-secondary").performClick()
        assertEquals(1, freshClicks)
    }

    @Test
    fun moreOptionsExposesReportAndTryLater() = runComposeUiTest {
        var reportClicks = 0
        var laterClicks = 0
        setContent {
            RehearsalCrashedDecisionSheet(
                decision = decision,
                onLockOldVersion = {},
                onStartFresh = {},
                onReport = { reportClicks++ },
                onTryLater = { laterClicks++ },
            )
        }

        onNodeWithTag("sp-decision-more-toggle").performClick()
        onNodeWithText("Report this to the server admin").assertIsDisplayed()
        onNodeWithText("Just go back — I'll try again later").assertIsDisplayed()

        onNodeWithTag("sp-decision-more-0").performClick()
        onNodeWithTag("sp-decision-more-1").performClick()

        assertEquals(1, reportClicks)
        assertEquals(1, laterClicks)
    }
}
