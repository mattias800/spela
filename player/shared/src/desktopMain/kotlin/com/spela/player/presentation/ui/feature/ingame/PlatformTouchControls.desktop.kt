package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformTouchControls(
    controller: LibretroController,
    modifier: Modifier,
    port: Int,
) {
    // No-op on desktop: keyboard and physical controller input is used instead.
}
