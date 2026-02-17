package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.components.ShaderPreview
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.ShaderScope
import com.spela.player.presentation.viewmodel.SettingsState

@Composable
internal fun ShaderScopeTabs(
    selectedScope: ShaderScope,
    onScopeChanged: (ShaderScope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
    ) {
        ShaderScope.entries.forEach { scope ->
            val isSelected = scope == selectedScope
            val label = when (scope) {
                ShaderScope.DEFAULT -> "Default"
                ShaderScope.PER_CONSOLE -> "Per Console"
            }
            val backgroundColor = if (isSelected) {
                SpColor.Primary.copy(alpha = 0.2f)
            } else {
                SpColor.SurfaceVariant
            }
            val textColor = if (isSelected) SpColor.Primary else SpColor.OnBackgroundSecondary

            Box(
                modifier = Modifier
                    .spFocusRing(shape = RoundedCornerShape(SpSpacing.RadiusPill))
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(backgroundColor)
                    .clickable { onScopeChanged(scope) }
                    .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Small),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = SpTypography.LabelMedium,
                    color = textColor,
                )
            }
        }
    }
}

internal fun LazyListScope.shaderDefaultScopeItems(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    item {
        SpCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SpSpacing.Small),
            ) {
                ShaderPreset.entries.forEachIndexed { index, shader ->
                    SpRadioOption(
                        title = shader.displayName,
                        description = shader.description,
                        isSelected = state.selectedShader == shader,
                        onClick = { viewModel.onIntent(SettingsIntent.SelectShader(shader)) },
                    )
                    if (index < ShaderPreset.entries.size - 1) {
                        SettingsDivider()
                    }
                }
            }
        }
    }

    item {
        val previewConsoleId = state.consoles.firstOrNull()?.id
        if (previewConsoleId != null) {
            ShaderPreview(
                imageUrl = "${state.serverUrl}/api/consoles/${previewConsoleId}/preview-screenshot",
                shader = state.selectedShader,
                onClick = { viewModel.onIntent(SettingsIntent.ShowShaderPreviewFullscreen(previewConsoleId)) },
                modifier = Modifier
                    .padding(horizontal = SpSpacing.Default)
                    .widthIn(max = 360.dp),
            )
        }
    }
}

internal fun LazyListScope.shaderPerConsoleScopeItems(
    state: SettingsState,
    onNavigateToConsoleSettings: (String) -> Unit,
) {
    if (state.consoles.isEmpty()) {
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
            items = state.consoles,
            key = { it.id },
        ) { console ->
            val consoleShader = state.consoleShaders[console.id]
                ?: state.selectedShader
            val hasOverride = state.deviceShaderOverrides.containsKey(console.id)
            val effectiveShader = state.deviceShaderOverrides[console.id]
                ?: consoleShader

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
                            text = effectiveShader.displayName +
                                if (hasOverride) " (device)" else "",
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
