package com.spela.player.desktop.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpGradientCard
import com.spela.player.presentation.ui.components.SpInnerCard
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1096 regression: SpCard / SpInnerCard / SpGradientCard previously stacked
 * `Modifier.clickable(...).gamepadFocusable(...)` on the same node.
 * `gamepadFocusable` added its own `.focusable()`, creating a second focus
 * target on top of `.clickable`'s built-in focus. On desktop with mouse
 * input, the very first click on an unfocused card was consumed by the
 * inner focusable as a focus-acquisition event, never reaching `onClick`.
 * The fix passes `addFocusable = false` to `gamepadFocusable` from the
 * three card variants, leaving `.clickable`'s focus target as the only one.
 */
@OptIn(ExperimentalTestApi::class)
class SpCardFirstClickTest {

    @Test
    fun spCard_firstClick_firesOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            SpCard(
                modifier = Modifier.testTag("card").size(120.dp),
                onClick = { clicks++ },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        onNodeWithTag("card").assertIsDisplayed()
        // First click — before #1096 fix this was swallowed by the duplicate
        // focusable; only the second click would fire onClick.
        onNodeWithTag("card").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun spInnerCard_firstClick_firesOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            SpInnerCard(
                modifier = Modifier.testTag("inner").size(120.dp),
                onClick = { clicks++ },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        onNodeWithTag("inner").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun spGradientCard_firstClick_firesOnClick() = runComposeUiTest {
        var clicks = 0
        setContent {
            SpGradientCard(
                modifier = Modifier.testTag("gradient").size(120.dp),
                onClick = { clicks++ },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        onNodeWithTag("gradient").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun spCard_hasSingleFocusTarget_notDuplicated() = runComposeUiTest {
        setContent {
            SpCard(
                modifier = Modifier.testTag("card").size(120.dp),
                onClick = { },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        // The bug was a double `.focusable()` from
        // `.clickable(...).gamepadFocusable(...)` — two focus targets sharing
        // one interaction source. With the fix there is exactly one node in
        // the semantics tree exposing the RequestFocus semantics action.
        val requestFocusMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus)
        val focusTargets = onAllNodes(requestFocusMatcher).fetchSemanticsNodes().size
        assertEquals(
            expected = 1,
            actual = focusTargets,
            message = "Expected exactly one RequestFocus target on SpCard; found $focusTargets — duplicate focus target would re-introduce #1096",
        )
    }

    @Test
    fun spInnerCard_hasSingleFocusTarget_notDuplicated() = runComposeUiTest {
        setContent {
            SpInnerCard(
                modifier = Modifier.testTag("inner").size(120.dp),
                onClick = { },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        val requestFocusMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus)
        val focusTargets = onAllNodes(requestFocusMatcher).fetchSemanticsNodes().size
        assertEquals(1, focusTargets, "duplicate RequestFocus targets re-introduce #1096")
    }

    @Test
    fun spGradientCard_hasSingleFocusTarget_notDuplicated() = runComposeUiTest {
        setContent {
            SpGradientCard(
                modifier = Modifier.testTag("gradient").size(120.dp),
                onClick = { },
            ) {
                Box(modifier = Modifier.size(120.dp))
            }
        }

        val requestFocusMatcher = SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus)
        val focusTargets = onAllNodes(requestFocusMatcher).fetchSemanticsNodes().size
        assertEquals(1, focusTargets, "duplicate RequestFocus targets re-introduce #1096")
    }
}
