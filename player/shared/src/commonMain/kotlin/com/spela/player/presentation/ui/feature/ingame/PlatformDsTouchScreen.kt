package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Platform-specific DS bottom screen renderer with touch input.
 * On Android, renders the bottom half of the emulation framebuffer and maps
 * touch events to libretro pointer input.
 * On Desktop, this is a no-op (secondary display is not supported).
 *
 * @param controller The libretro controller for touch input forwarding.
 * @param splitY The Y pixel where the framebuffer splits (192 for DS).
 * @param selectedShader Shader to apply to the rendered frame.
 */
@Composable
expect fun PlatformDsTouchScreen(
    controller: LibretroController,
    splitY: Int,
    selectedShader: ShaderPreset = ShaderPreset.NONE,
    bottomScreenWidth: Int = 0,
    bottomScreenOffsetX: Int = 0,
    modifier: Modifier = Modifier,
)
