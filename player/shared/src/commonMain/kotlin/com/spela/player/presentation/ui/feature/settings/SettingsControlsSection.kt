package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.theme.SpSpacing

internal fun LazyListScope.controlsDefaultScopeItems(
    presets: List<KeyMappingPreset>,
    activePresetId: String?,
    onSelectPreset: (String) -> Unit,
    onOpenFullMapping: () -> Unit,
) {
    if (presets.isNotEmpty()) {
        item {
            SpCard(onGradient = true, modifier = Modifier.testTag("controls_preset_card")) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.Small),
                ) {
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

    item {
        SpSecondaryButton(
            text = "Customize Bindings",
            onClick = onOpenFullMapping,
            modifier = Modifier.fillMaxWidth().testTag("customize_bindings_button"),
        )
    }
}

