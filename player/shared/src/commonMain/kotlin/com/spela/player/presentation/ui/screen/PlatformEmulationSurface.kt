package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Platform-specific composable that renders emulation video frames.
 * On Android, this renders Bitmaps from the native bridge.
 * On Desktop, this will use a different rendering approach.
 */
@Composable
expect fun PlatformEmulationSurface(
    controller: LibretroController,
    modifier: Modifier = Modifier,
)
