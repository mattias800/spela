package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.EmulationSurface
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformEmulationSurface(
    controller: LibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier,
    onEscapePressed: (() -> Unit)?,
) {
    val androidController = controller as? AndroidLibretroController ?: return
    EmulationSurface(
        controller = androidController,
        selectedShader = selectedShader,
        modifier = modifier,
    )
}
