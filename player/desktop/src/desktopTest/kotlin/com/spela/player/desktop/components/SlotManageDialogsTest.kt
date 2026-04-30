package com.spela.player.desktop.components

import androidx.compose.ui.test.*
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.ui.feature.ingame.InGameSlotActionsSheet
import com.spela.player.presentation.ui.feature.ingame.InGameSlotDeleteConfirmDialog
import com.spela.player.presentation.ui.feature.ingame.InGameSlotRenameDialog
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * UI coverage for the slot-manage modals shown on long-press of a
 * filled slot cell on the in-game slot picker (#831). Tests the
 * three composables in isolation — long-press wiring is exercised
 * by the slot picker test class. Each modal:
 *
 *   InGameSlotActionsSheet    — Rename / Delete / Cancel routing
 *   InGameSlotRenameDialog    — text input + confirm/cancel
 *   InGameSlotDeleteConfirmDialog — two-step destructive confirm
 */
@OptIn(ExperimentalTestApi::class)
class SlotManageDialogsTest {

    // ── InGameSlotActionsSheet ─────────────────────────────────────

    @Test
    fun actionsSheetRendersTitleWithSlotAndTimestampWhenNoName() = runComposeUiTest {
        setContent {
            InGameSlotActionsSheet(
                slot = 7,
                slotInfo = SaveSlotInfo(timestamp = "13:42", isFilled = true, saveId = "abc"),
                onRename = {}, onDelete = {}, onDismiss = {},
            )
        }

        onNodeWithText("Slot 7 — 13:42").assertIsDisplayed()
        onNodeWithTag("slot-actions-rename").assertIsDisplayed()
        onNodeWithTag("slot-actions-delete").assertIsDisplayed()
        onNodeWithTag("slot-actions-cancel").assertIsDisplayed()
    }

    @Test
    fun actionsSheetTitlePrefersUserNameOverTimestamp() = runComposeUiTest {
        setContent {
            InGameSlotActionsSheet(
                slot = 3,
                slotInfo = SaveSlotInfo(
                    timestamp = "10:00",
                    name = "Before final boss",
                    isFilled = true,
                    saveId = "id",
                ),
                onRename = {}, onDelete = {}, onDismiss = {},
            )
        }

        onNodeWithText("Slot 3 — Before final boss").assertIsDisplayed()
    }

    @Test
    fun actionsSheetRenameButtonRoutesToOnRename() = runComposeUiTest {
        var renamed = 0
        setContent {
            InGameSlotActionsSheet(
                slot = 1,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id"),
                onRename = { renamed++ },
                onDelete = {},
                onDismiss = {},
            )
        }

        onNodeWithTag("slot-actions-rename").performClick()

        assertEquals(1, renamed)
    }

    @Test
    fun actionsSheetDeleteButtonRoutesToOnDelete() = runComposeUiTest {
        var deleted = 0
        setContent {
            InGameSlotActionsSheet(
                slot = 1,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id"),
                onRename = {},
                onDelete = { deleted++ },
                onDismiss = {},
            )
        }

        onNodeWithTag("slot-actions-delete").performClick()

        assertEquals(1, deleted)
    }

    @Test
    fun actionsSheetCancelDismisses() = runComposeUiTest {
        var dismissed = 0
        setContent {
            InGameSlotActionsSheet(
                slot = 1,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id"),
                onRename = {},
                onDelete = {},
                onDismiss = { dismissed++ },
            )
        }

        onNodeWithTag("slot-actions-cancel").performClick()

        assertEquals(1, dismissed)
    }

    // ── InGameSlotRenameDialog ─────────────────────────────────────

    @Test
    fun renameDialogPrefillsExistingName() = runComposeUiTest {
        setContent {
            InGameSlotRenameDialog(
                slot = 5,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id", name = "Existing"),
                onConfirm = {},
                onDismiss = {},
            )
        }

        onNodeWithText("Rename slot 5").assertIsDisplayed()
        onNodeWithText("Existing").assertIsDisplayed()
    }

    @Test
    fun renameDialogConfirmDispatchesEditedText() = runComposeUiTest {
        var captured: String? = null
        setContent {
            InGameSlotRenameDialog(
                slot = 5,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id", name = ""),
                onConfirm = { captured = it },
                onDismiss = {},
            )
        }

        onNode(hasSetTextAction()).performTextInput("New name")
        onNodeWithTag("slot-rename-confirm").performClick()

        assertEquals("New name", captured)
    }

    @Test
    fun renameDialogCancelDismissesWithoutFiringConfirm() = runComposeUiTest {
        var confirmCount = 0
        var dismissCount = 0
        setContent {
            InGameSlotRenameDialog(
                slot = 5,
                slotInfo = SaveSlotInfo(isFilled = true, saveId = "id", name = "x"),
                onConfirm = { confirmCount++ },
                onDismiss = { dismissCount++ },
            )
        }

        onNodeWithTag("slot-rename-cancel").performClick()

        assertEquals(0, confirmCount)
        assertEquals(1, dismissCount)
    }

    // ── InGameSlotDeleteConfirmDialog ──────────────────────────────

    @Test
    fun deleteConfirmRendersSlotInTitle() = runComposeUiTest {
        setContent {
            InGameSlotDeleteConfirmDialog(slot = 7, onConfirm = {}, onDismiss = {})
        }

        onNodeWithText("Delete slot 7?").assertIsDisplayed()
        onNodeWithText("This can't be undone.").assertIsDisplayed()
    }

    @Test
    fun deleteConfirmConfirmFiresOnConfirm() = runComposeUiTest {
        var confirmed = 0
        setContent {
            InGameSlotDeleteConfirmDialog(
                slot = 7,
                onConfirm = { confirmed++ },
                onDismiss = {},
            )
        }

        onNodeWithTag("slot-delete-confirm").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun deleteConfirmCancelDismissesWithoutFiringConfirm() = runComposeUiTest {
        var confirmed = 0
        var dismissed = 0
        setContent {
            InGameSlotDeleteConfirmDialog(
                slot = 7,
                onConfirm = { confirmed++ },
                onDismiss = { dismissed++ },
            )
        }

        onNodeWithTag("slot-delete-cancel").performClick()

        assertEquals(0, confirmed)
        assertEquals(1, dismissed)
    }
}
