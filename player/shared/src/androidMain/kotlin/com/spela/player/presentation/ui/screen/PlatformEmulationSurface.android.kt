package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.EmulationSurface
import com.spela.player.presentation.viewmodel.EmulationViewModel
import com.spela.player.presentation.viewmodel.LibretroController
import org.koin.compose.koinInject

@Composable
actual fun PlatformEmulationSurface(
    controller: LibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier,
    onEscapePressed: (() -> Unit)?,
) {
    val androidController = controller as? AndroidLibretroController ?: return
    val emulationViewModel: EmulationViewModel = koinInject()
    val emulationState by emulationViewModel.state.collectAsState()

    val isDualScreenSplit = emulationState.isDualScreenConsole && emulationState.secondaryDisplayActive

    EmulationSurface(
        controller = androidController,
        selectedShader = selectedShader,
        isDualScreenSplit = isDualScreenSplit,
        splitY = emulationState.dualScreenSplitY,
        modifier = modifier,
    )
}
