package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformDsTouchScreen(
    controller: LibretroController,
    splitY: Int,
    selectedShader: ShaderPreset,
    modifier: Modifier,
) {
    // No-op on desktop: secondary display is not supported.
}
