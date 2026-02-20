package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.KeyMappingPreset
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

enum class ControlsScope { DEFAULT, PER_CONSOLE }

@Composable
internal fun ControlsScopeTabs(
    selectedScope: ControlsScope,
    onScopeChanged: (ControlsScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("controls_scope_tabs"),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        ControlsScope.entries.forEach { scope ->
            val isSelected = scope == selectedScope
            val label = when (scope) {
                ControlsScope.DEFAULT -> "Default"
                ControlsScope.PER_CONSOLE -> "Per Console"
            }

            SpChip(
                text = label,
                isSelected = isSelected,
                onClick = { onScopeChanged(scope) },
            )
        }
    }
}

internal fun LazyListScope.controlsDefaultScopeItems(
    presets: List<KeyMappingPreset>,
    activePresetId: String?,
    onSelectPreset: (String) -> Unit,
    onOpenFullMapping: () -> Unit,
) {
    if (presets.isNotEmpty()) {
        item {
            SpCard(modifier = Modifier.testTag("controls_preset_card")) {
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
        SpButton(
            text = "Customize Bindings",
            onClick = onOpenFullMapping,
            style = SpButtonStyle.Outlined,
            modifier = Modifier.fillMaxWidth().testTag("customize_bindings_button"),
        )
    }
}

internal fun LazyListScope.controlsPerConsoleScopeItems(
    consoles: List<Console>,
    onNavigateToConsoleSettings: (String) -> Unit,
) {
    if (consoles.isEmpty()) {
        item {
            SpCard {
                Text(
                    text = "No consoles available",
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundTertiary,
                    modifier = Modifier.padding(SpSpacing.Default),
                )
            }
        }
    } else {
        items(
            items = consoles,
            key = { it.id },
        ) { console ->
            SpCard(
                onClick = { onNavigateToConsoleSettings(console.id) },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpSpacing.Default),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = console.name,
                            style = SpTypography.TitleMedium,
                            color = SpColor.OnCard,
                        )
                        Text(
                            text = "Custom key mapping",
                            style = SpTypography.BodySmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open console settings",
                        tint = SpColor.OnBackgroundTertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
