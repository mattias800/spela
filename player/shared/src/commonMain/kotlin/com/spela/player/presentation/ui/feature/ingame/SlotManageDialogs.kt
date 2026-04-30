package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Bottom-sheet-style modal that opens on long-press of a filled slot
 * cell on the in-game slot picker (#831). Lets the user rename or
 * delete the slot without leaving the game.
 *
 * Rename / Delete are routed through the parent (EmulationViewModel
 * intents) — this composable just decides which action the user picked.
 * Cancel just dismisses.
 */
@Composable
fun InGameSlotActionsSheet(
    slot: Int,
    slotInfo: SaveSlotInfo?,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title = buildString {
        append("Slot ")
        append(slot)
        if (!slotInfo?.name.isNullOrBlank()) {
            append(" — ")
            append(slotInfo!!.name)
        } else if (slotInfo?.timestamp != null) {
            append(" — ")
            append(slotInfo.timestamp)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("in-game-slot-actions-sheet"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            SpButton(
                text = "Rename",
                onClick = onRename,
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .testTag("slot-actions-rename"),
            )
            SpButton(
                text = "Delete",
                onClick = onDelete,
                modifier = Modifier
                    .padding(top = SpSpacing.Small)
                    .testTag("slot-actions-delete"),
            )
            SpSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier
                    .padding(top = SpSpacing.Small)
                    .testTag("slot-actions-cancel"),
            )
        }
    }
}

/**
 * Rename dialog — text input pre-filled with the slot's existing name.
 * Empty submission is allowed (clears the user-supplied name; server
 * keeps the slot's "Slot N" auto-label). See #831.
 */
@Composable
fun InGameSlotRenameDialog(
    slot: Int,
    slotInfo: SaveSlotInfo?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(slot) { mutableStateOf(slotInfo?.name.orEmpty()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("in-game-slot-rename-dialog"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Rename slot $slot",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            SpTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = "Slot $slot",
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth()
                    .testTag("slot-rename-input"),
            )
            Row(
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    SpSpacing.Small,
                    Alignment.CenterHorizontally,
                ),
            ) {
                SpSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("slot-rename-cancel"),
                )
                SpButton(
                    text = "Save",
                    onClick = { onConfirm(name) },
                    modifier = Modifier.testTag("slot-rename-confirm"),
                )
            }
        }
    }
}

/**
 * Two-step delete: a confirmation dialog before the irreversible
 * DELETE call hits the server. See #831.
 */
@Composable
fun InGameSlotDeleteConfirmDialog(
    slot: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .testTag("in-game-slot-delete-confirm"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(SpSpacing.RadiusXLarge))
                .background(SpColor.SurfaceElevated)
                .padding(SpSpacing.XLarge)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Delete slot $slot?",
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )
            Text(
                text = "This can't be undone.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SpSpacing.Small)
                    .fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    SpSpacing.Small,
                    Alignment.CenterHorizontally,
                ),
            ) {
                SpSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.testTag("slot-delete-cancel"),
                )
                SpButton(
                    text = "Delete",
                    onClick = onConfirm,
                    modifier = Modifier.testTag("slot-delete-confirm"),
                )
            }
        }
    }
}
