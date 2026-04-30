package com.spela.player.desktop.components

import androidx.compose.ui.test.*
import com.spela.player.presentation.ui.feature.ingame.InGameNamedSaveDialog
import com.spela.player.presentation.ui.feature.ingame.InGameSlotPickerDialog
import com.spela.player.presentation.state.SlotPickerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * UI coverage for the medium-tier "Save with name…" affordance shipped
 * in #830. Two surfaces:
 *
 *   InGameSlotPickerDialog — renders the link only when the host
 *     passes a non-null onSaveWithName. Hidden on large tier (passes
 *     null per #804 phase 5 spec) and on Load mode.
 *   InGameNamedSaveDialog — text input, blank submission rejected,
 *     Cancel / scrim dismiss without saving.
 */
@OptIn(ExperimentalTestApi::class)
class NamedSaveDialogTest {

    // ── Slot picker — link visibility ──────────────────────────────

    @Test
    fun slotPickerShowsSaveWithNameLinkInSaveModeWhenCallbackProvided() = runComposeUiTest {
        setContent {
            InGameSlotPickerDialog(
                mode = SlotPickerMode.Save,
                slotCount = 10,
                saveSlots = emptyMap(),
                onSaveToSlot = {},
                onLoadFromSlot = {},
                onDismiss = {},
                onSaveWithName = {},
            )
        }

        onNodeWithTag("in-game-slot-picker-save-with-name").assertIsDisplayed()
        onNodeWithText("Save with name…").assertIsDisplayed()
    }

    @Test
    fun slotPickerHidesSaveWithNameLinkWhenCallbackOmitted() = runComposeUiTest {
        // Large tier: the host doesn't pass a callback, so the link
        // is hidden — slot-only by spec.
        setContent {
            InGameSlotPickerDialog(
                mode = SlotPickerMode.Save,
                slotCount = 5,
                saveSlots = emptyMap(),
                onSaveToSlot = {},
                onLoadFromSlot = {},
                onDismiss = {},
                onSaveWithName = null,
            )
        }

        onNodeWithTag("in-game-slot-picker-save-with-name").assertDoesNotExist()
    }

    @Test
    fun slotPickerHidesSaveWithNameLinkInLoadMode() = runComposeUiTest {
        // Even on medium tier with a callback, Load mode hides the
        // link — naming a save isn't relevant when loading.
        setContent {
            InGameSlotPickerDialog(
                mode = SlotPickerMode.Load,
                slotCount = 10,
                saveSlots = emptyMap(),
                onSaveToSlot = {},
                onLoadFromSlot = {},
                onDismiss = {},
                onSaveWithName = {},
            )
        }

        onNodeWithTag("in-game-slot-picker-save-with-name").assertDoesNotExist()
    }

    @Test
    fun slotPickerSaveWithNameLinkRoutesToCallback() = runComposeUiTest {
        var clicks = 0
        setContent {
            InGameSlotPickerDialog(
                mode = SlotPickerMode.Save,
                slotCount = 10,
                saveSlots = emptyMap(),
                onSaveToSlot = {},
                onLoadFromSlot = {},
                onDismiss = {},
                onSaveWithName = { clicks++ },
            )
        }

        onNodeWithTag("in-game-slot-picker-save-with-name").performClick()

        assertEquals(1, clicks)
    }

    // ── Named-save dialog ──────────────────────────────────────────

    @Test
    fun namedSaveDialogRendersTitleAndCopy() = runComposeUiTest {
        setContent {
            InGameNamedSaveDialog(onConfirm = {}, onDismiss = {})
        }

        onNodeWithText("Save with name").assertIsDisplayed()
        onNodeWithTag("named-save-input", useUnmergedTree = true).assertExists()
    }

    @Test
    fun namedSaveDialogConfirmIsDisabledForBlankName() = runComposeUiTest {
        var captured: String? = null
        setContent {
            InGameNamedSaveDialog(
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }

        // Confirm without typing — should be inert.
        onNodeWithTag("named-save-confirm").assertIsNotEnabled()
        assertNull(captured)
    }

    @Test
    fun namedSaveDialogConfirmDispatchesTypedName() = runComposeUiTest {
        var captured: String? = null
        setContent {
            InGameNamedSaveDialog(
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }

        onNode(hasSetTextAction()).performTextInput("Before final boss")
        onNodeWithTag("named-save-confirm").performClick()

        assertEquals("Before final boss", captured)
    }

    @Test
    fun namedSaveDialogCancelDismissesWithoutFiringConfirm() = runComposeUiTest {
        var confirmCount = 0
        var dismissCount = 0
        setContent {
            InGameNamedSaveDialog(
                onConfirm = { confirmCount++ },
                onDismiss = { dismissCount++ },
            )
        }

        onNode(hasSetTextAction()).performTextInput("Will be discarded")
        onNodeWithTag("named-save-cancel").performClick()

        assertEquals(0, confirmCount)
        assertEquals(1, dismissCount)
    }
}
