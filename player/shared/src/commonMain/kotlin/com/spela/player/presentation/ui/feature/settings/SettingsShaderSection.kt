package com.spela.player.presentation.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.ui.components.ShaderPreview
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpRadioOption
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.SettingsIntent
import com.spela.player.presentation.viewmodel.SettingsViewModel
import com.spela.player.presentation.viewmodel.SettingsState

internal fun LazyListScope.shaderDefaultScopeItems(
    state: SettingsState,
    viewModel: SettingsViewModel,
) {
    item {
        SpCard(onGradient = true) {
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
                        modifier = Modifier.testTag("shader_option_${shader.apiId}"),
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

