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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.state.SaveSlotInfo
import com.spela.player.presentation.state.SlotPickerMode
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Slot-primary save/load picker that appears in-game on medium and
 * large console tiers (#804 phase 5). Replaces the historical
 * "free-form named manual save" affordance for these tiers because
 * for ~30+ MB save states the named-saves-unlimited UX silently
 * blows past the user's storage quota.
 *
 * Slot count tracks the tier:
 *   medium → 10 slots (PSX, N64, NDS-style states)
 *   large  → 5 slots (GameCube, Wii, PS2, 3DS-style states)
 *
 * The picker dismisses on scrim tap / back, on a slot-row tap (the
 * action commits then closes), or by an explicit Cancel.
 */
@Composable
fun InGameSlotPickerDialog(
    mode: SlotPickerMode,
    slotCount: Int,
    saveSlots: Map<Int, SaveSlotInfo>,
    onSaveToSlot: (Int) -> Unit,
    onLoadFromSlot: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (mode) {
        SlotPickerMode.Save -> "Save to slot"
        SlotPickerMode.Load -> "Load from slot"
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
            .testTag("in-game-slot-picker"),
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
                    onClick = {}, // Prevent click-through to scrim.
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = SpTypography.HeadlineSmall,
                color = SpColor.OnBackground,
            )

            // Slots laid out in rows of 5 so 10-slot (medium) shows
            // two rows and 5-slot (large) shows one. Compact enough
            // to fit on the in-game overlay without a scroller.
            val slots = (1..slotCount).toList()
            slots.chunked(5).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = SpSpacing.Medium),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    row.forEach { slot ->
                        SlotPickerCell(
                            slot = slot,
                            slotInfo = saveSlots[slot],
                            mode = mode,
                            onClick = {
                                when (mode) {
                                    SlotPickerMode.Save -> onSaveToSlot(slot)
                                    SlotPickerMode.Load -> onLoadFromSlot(slot)
                                }
                            },
                        )
                    }
                }
            }

            // Hint clarifies what tapping a filled slot does in Save
            // mode — the user might assume Save is destructive only
            // on selection, not on the slot row itself.
            Text(
                text = when (mode) {
                    SlotPickerMode.Save -> "Tap a slot to save here. Filled slots are overwritten."
                    SlotPickerMode.Load -> "Tap a filled slot to load."
                },
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .fillMaxWidth(),
            )

            SpSecondaryButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier
                    .padding(top = SpSpacing.Medium)
                    .testTag("in-game-slot-picker-cancel"),
            )
        }
    }
}

@Composable
private fun SlotPickerCell(
    slot: Int,
    slotInfo: SaveSlotInfo?,
    mode: SlotPickerMode,
    onClick: () -> Unit,
) {
    val filled = slotInfo?.isFilled == true
    // Load-from-empty is a no-op so we render those cells as inert
    // — clicking does nothing rather than firing onLoadFromSlot to
    // produce an error toast.
    val enabled = mode == SlotPickerMode.Save || filled
    val background = when {
        !enabled -> SpColor.Surface
        filled -> SpColor.Primary
        else -> SpColor.SurfaceElevated
    }
    val labelColor = when {
        !enabled -> SpColor.OnBackgroundTertiary
        filled -> SpColor.OnPrimary
        else -> SpColor.OnBackground
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(background)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .testTag("slot-picker-cell-$slot"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = slot.toString(),
                style = SpTypography.TitleMedium,
                color = labelColor,
            )
            if (slotInfo?.timestamp != null) {
                Text(
                    text = slotInfo.timestamp,
                    style = SpTypography.LabelSmall,
                    color = labelColor,
                )
            }
        }
    }
}
