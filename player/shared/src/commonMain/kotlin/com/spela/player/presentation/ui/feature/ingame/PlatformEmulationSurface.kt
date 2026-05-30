package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Platform-specific composable that renders emulation video frames.
 * On Android, this renders Bitmaps from the native bridge.
 * On Desktop, this will use a different rendering approach.
 *
 * [selectedShader] controls the video filter applied to the rendered frame.
 * [onEscapePressed] is invoked on Desktop when the user presses the Escape key.
 * On Android this parameter is unused (back is handled by PlatformBackHandler).
 * [overlayVisible] is true while the in-game pause overlay is shown; on Desktop
 * the surface then yields keyboard/gamepad input and focus so the overlay menu
 * can be navigated (#1211). Unused on Android.
 */
@Composable
expect fun PlatformEmulationSurface(
    controller: LibretroController,
    selectedShader: ShaderPreset = ShaderPreset.NONE,
    modifier: Modifier = Modifier,
    onEscapePressed: (() -> Unit)? = null,
    overlayVisible: Boolean = false,
)
