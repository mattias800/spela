package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.TouchGamepadOverlay
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformTouchControls(
    controller: LibretroController,
    modifier: Modifier,
) {
    val androidController = controller as? AndroidLibretroController ?: return
    TouchGamepadOverlay(
        controller = androidController,
        modifier = modifier,
    )
}
