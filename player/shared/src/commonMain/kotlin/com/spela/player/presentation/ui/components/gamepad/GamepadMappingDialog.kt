package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GamepadMappingState

/**
 * Desktop gamepad mapping editor (#1334, component C — gamepad mode). For the
 * current console it lists each physical [GamepadPosition] and the console
 * action it triggers, and lets the user reassign by position. Brand-neutral:
 * positions are physical ("Bottom button"), actions are the console's own
 * labels — no Xbox/Nintendo glyphs.
 *
 * @param state the effective position→action mapping + console outputs
 * @param onSetBinding assign a console action (RetroPad id) to a position
 * @param onResetToDefaults clear all overrides for this console
 * @param onDismiss close the editor
 */
@Composable
fun GamepadMappingDialog(
    state: GamepadMappingState,
    onSetBinding: (GamepadPosition, Int) -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingPosition by remember { mutableStateOf<GamepadPosition?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    // Cap to the available screen height so the action buttons are
                    // never clipped on short landscape screens like the AYN Thor
                    // (#1371). The position list below scrolls to fit.
                    .fillMaxHeight(0.9f)
                    .heightIn(max = 600.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(SpColor.SurfaceElevated)
                    .padding(SpSpacing.XLarge)
                    .testTag("gamepad_mapping_dialog"),
            ) {
                Text(
                    text = "Controller buttons — ${state.displayName}",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                )
                Text(
                    text = "Choose what each physical button does on this console.",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )

                Spacer(Modifier.height(SpSpacing.Medium))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Take the remaining space (between header and the pinned
                        // action buttons) and scroll within it (#1371).
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    GamepadPosition.entries.forEachIndexed { index, position ->
                        PositionRow(
                            position = position,
                            actionLabel = actionLabelFor(state, position),
                            onClick = { editingPosition = position },
                        )
                        if (index < GamepadPosition.entries.size - 1) {
                            SettingsDivider()
                        }
                    }
                }

                Spacer(Modifier.height(SpSpacing.Default))

                SpButton(
                    text = "Reset to defaults",
                    onClick = onResetToDefaults,
                    style = SpButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag("gamepad_mapping_reset"),
                )
                Spacer(Modifier.height(SpSpacing.Small))
                SpButton(
                    text = "Done",
                    onClick = onDismiss,
                    style = SpButtonStyle.Primary,
                    modifier = Modifier.fillMaxWidth().testTag("gamepad_mapping_done"),
                )
            }
        }
    }

    val editing = editingPosition
    if (editing != null) {
        GamepadActionPickerDialog(
            position = editing,
            state = state,
            onSelect = { retroButtonId ->
                onSetBinding(editing, retroButtonId)
                editingPosition = null
            },
            onDismiss = { editingPosition = null },
        )
    }
}

/** A settings-style row: physical position on the left, its console action on
 *  the right. Tapping opens the action picker. */
@Composable
private fun PositionRow(
    position: GamepadPosition,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.RadiusLarge),
                interactionSource = interactionSource,
                addFocusable = false,
            )
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .testTag("gamepad_pos_${position.name}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = position.displayName,
            style = SpTypography.BodyMedium,
            color = SpColor.OnCard,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = actionLabel,
            style = SpTypography.TitleMedium,
            color = if (actionLabel == "—") SpColor.OnBackgroundTertiary else SpColor.Primary,
            modifier = Modifier.testTag("gamepad_action_${position.name}"),
        )
    }
}

/** Picker of the console's actions to assign to a single physical position. */
@Composable
private fun GamepadActionPickerDialog(
    position: GamepadPosition,
    state: GamepadMappingState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = state.mapping[position]
    SpDialog(
        title = position.displayName,
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
        modifier = Modifier.testTag("gamepad_action_picker"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            state.outputs.forEachIndexed { index, output ->
                SpRadioOption(
                    title = output.label,
                    description = "Make the ${position.displayName.lowercase()} act as ${output.label}",
                    isSelected = current == output.retroButtonId,
                    onClick = { onSelect(output.retroButtonId) },
                )
                if (index < state.outputs.size - 1) {
                    SettingsDivider()
                }
            }
        }
    }
}

private fun actionLabelFor(state: GamepadMappingState, position: GamepadPosition): String {
    val retroId = state.mapping[position] ?: return "—"
    return state.outputs.firstOrNull { it.retroButtonId == retroId }?.label ?: "—"
}
