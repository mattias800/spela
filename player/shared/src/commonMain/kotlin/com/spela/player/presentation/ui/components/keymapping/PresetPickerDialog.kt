package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.presentation.ui.components.SpDialog
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun PresetPickerDialog(
    presets: List<KeyMappingPreset>,
    activePresetId: String?,
    onSelectPreset: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SpDialog(
        title = "Controller Presets",
        onDismiss = onDismiss,
        confirmText = "Done",
        onConfirm = onDismiss,
        modifier = Modifier.testTag("preset_picker_dialog"),
    ) {
        if (presets.isEmpty()) {
            Text(
                text = "No presets available for this platform.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.padding(vertical = SpSpacing.Default),
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                presets.forEachIndexed { index, preset ->
                    SpRadioOption(
                        title = preset.displayName,
                        description = preset.description,
                        isSelected = preset.id == activePresetId,
                        onClick = { onSelectPreset(preset.id) },
                    )
                    if (index < presets.size - 1) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}
