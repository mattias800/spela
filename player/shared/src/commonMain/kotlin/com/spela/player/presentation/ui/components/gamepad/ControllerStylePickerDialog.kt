package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.feature.settings.SettingsDivider

/**
 * Per-controller style override picker (#1334, component D). Lets the user
 * correct the detected controller type for a connected pad. "Auto" clears the
 * override and defers to detection; any explicit choice (including Gamepad /
 * Generic) is stored device-local.
 *
 * Brand-neutral by design: the entries are plain text identities, no glyphs.
 *
 * @param detectedStyle the auto-detected style, surfaced as the "Auto" subtitle
 * @param currentOverride the stored override, or null when Auto is in effect
 * @param onSelect invoked with the chosen style, or null to clear to Auto
 */
@Composable
fun ControllerStylePickerDialog(
    detectedStyle: ControllerStyle,
    currentOverride: ControllerStyle?,
    onSelect: (ControllerStyle?) -> Unit,
    onDismiss: () -> Unit,
) {
    SpDialog(
        title = "Controller type",
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
        modifier = Modifier.testTag("controller_style_picker"),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SpRadioOption(
                title = "Auto",
                description = "Use the detected type (${detectedStyle.displayName})",
                isSelected = currentOverride == null,
                onClick = { onSelect(null) },
            )
            SettingsDivider()
            ControllerStyle.entries.forEachIndexed { index, style ->
                SpRadioOption(
                    title = style.displayName,
                    description = "Treat this controller as ${style.displayName}",
                    isSelected = currentOverride == style,
                    onClick = { onSelect(style) },
                )
                if (index < ControllerStyle.entries.size - 1) {
                    SettingsDivider()
                }
            }
        }
    }
}
