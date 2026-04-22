package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.ui.feature.ingame.RehearsalCompleteDecisionSheet
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behaviour coverage for the #672 Sheet C composable. Pins copy
 * verbatim against `core_upd.sheet_c.*` keys in
 * `design-proposals/core-upgrade-decision-spec.md` and the routing
 * for the three actions.
 */
@OptIn(ExperimentalTestApi::class)
class RehearsalCompleteDecisionSheetTest {

    @Test
    fun rendersSpecTitleAndBody() = runComposeUiTest {
        setContent {
            RehearsalCompleteDecisionSheet(
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onTryLonger = {},
            )
        }

        onNodeWithTag("core-upgrade-sheet-c").assertIsDisplayed()
        onNodeWithText("Did your save load correctly?").assertIsDisplayed()
        onNodeWithText(
            "If the screen looks right and the controls feel normal, the new version works.",
        ).assertIsDisplayed()
    }

    @Test
    fun primaryFiresOnKeepNewVersion() = runComposeUiTest {
        var keepClicks = 0
        setContent {
            RehearsalCompleteDecisionSheet(
                onKeepNewVersion = { keepClicks++ },
                onLockOldVersion = {},
                onTryLonger = {},
            )
        }

        onNodeWithText("Yes, keep the new version").assertIsDisplayed()
        onNodeWithTag("sp-decision-primary").performClick()
        assertEquals(1, keepClicks)
    }

    @Test
    fun secondaryFiresOnLockOldVersion() = runComposeUiTest {
        var lockClicks = 0
        setContent {
            RehearsalCompleteDecisionSheet(
                onKeepNewVersion = {},
                onLockOldVersion = { lockClicks++ },
                onTryLonger = {},
            )
        }

        onNodeWithText("No, lock to the older version").assertIsDisplayed()
        onNodeWithTag("sp-decision-secondary").performClick()
        assertEquals(1, lockClicks)
    }

    @Test
    fun tryLongerIsDirectlyVisibleAsTertiaryAction() = runComposeUiTest {
        var longerClicks = 0
        setContent {
            RehearsalCompleteDecisionSheet(
                onKeepNewVersion = {},
                onLockOldVersion = {},
                onTryLonger = { longerClicks++ },
            )
        }

        // Spec keeps "Let me try a bit longer" as a peer ghost action,
        // not inside a "More options" expander — user must see it on
        // first render so the escape hatch is always discoverable.
        onNodeWithText("Let me try a bit longer").assertIsDisplayed()
        onNodeWithTag("sp-decision-tertiary").performClick()
        assertEquals(1, longerClicks)
    }
}
