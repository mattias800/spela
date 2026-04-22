package com.spela.player.desktop.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.spela.player.presentation.ui.feature.ingame.CoreMismatchDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Post-port coverage for [CoreMismatchDialog] — the post-load save
 * state mismatch dialog, ported from a raw `Box + Scrim + clickable +
 * SurfaceElevated` pattern to [SpDecisionSheet] in #672 PR 3c.iii.
 * Pins the new tone-aligned copy (no "warning", no "corruption") and
 * the three-action routing.
 */
@OptIn(ExperimentalTestApi::class)
class CoreMismatchDialogTest {

    @Test
    fun rendersRefreshedCopyWithCoreNames() = runComposeUiTest {
        setContent {
            CoreMismatchDialog(
                saveCoreName = "snes9x",
                currentCoreName = "nestopia",
                onTryAnyway = {},
                onGameSaveOnly = {},
                onStartFresh = {},
            )
        }

        onNodeWithTag("core-mismatch-dialog").assertIsDisplayed()
        onNodeWithText("We couldn't load that save state").assertIsDisplayed()
        onNodeWithText(
            "This save was made with snes9x — we're now running nestopia. " +
                "Loading might still work, but you can also start from your " +
                "in-game save or begin a fresh run.",
        ).assertIsDisplayed()
    }

    @Test
    fun fallsBackToGenericCoreLabelsWhenNamesAreEmpty() = runComposeUiTest {
        // Defensive: if the VM couldn't resolve one of the core names,
        // the dialog must still produce a readable body — "the
        // original core" / "the current core" placeholders.
        setContent {
            CoreMismatchDialog(
                saveCoreName = "",
                currentCoreName = "",
                onTryAnyway = {},
                onGameSaveOnly = {},
                onStartFresh = {},
            )
        }

        onNodeWithText(
            "This save was made with the original core — we're now running " +
                "the current core. Loading might still work, but you can also " +
                "start from your in-game save or begin a fresh run.",
        ).assertIsDisplayed()
    }

    @Test
    fun primaryFiresOnTryAnyway() = runComposeUiTest {
        var clicks = 0
        setContent {
            CoreMismatchDialog(
                saveCoreName = "snes9x",
                currentCoreName = "nestopia",
                onTryAnyway = { clicks++ },
                onGameSaveOnly = {},
                onStartFresh = {},
            )
        }

        onNodeWithText("Try loading anyway").assertIsDisplayed()
        onNodeWithTag("sp-decision-primary").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun secondaryFiresOnGameSaveOnly() = runComposeUiTest {
        var clicks = 0
        setContent {
            CoreMismatchDialog(
                saveCoreName = "snes9x",
                currentCoreName = "nestopia",
                onTryAnyway = {},
                onGameSaveOnly = { clicks++ },
                onStartFresh = {},
            )
        }

        onNodeWithText("Start with game save only").assertIsDisplayed()
        onNodeWithTag("sp-decision-secondary").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun tertiaryFiresOnStartFresh() = runComposeUiTest {
        var clicks = 0
        setContent {
            CoreMismatchDialog(
                saveCoreName = "snes9x",
                currentCoreName = "nestopia",
                onTryAnyway = {},
                onGameSaveOnly = {},
                onStartFresh = { clicks++ },
            )
        }

        onNodeWithText("Start fresh").assertIsDisplayed()
        onNodeWithTag("sp-decision-tertiary").performClick()
        assertEquals(1, clicks)
    }
}
