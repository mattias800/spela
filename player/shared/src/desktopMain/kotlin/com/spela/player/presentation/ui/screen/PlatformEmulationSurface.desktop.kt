package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.DesktopAudioPlayer
import com.spela.player.libretro.DesktopEmulationSurface
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformEmulationSurface(
    controller: LibretroController,
    selectedShader: ShaderPreset,
    modifier: Modifier,
    onEscapePressed: (() -> Unit)?,
) {
    val desktopController = controller as? DesktopLibretroController ?: return

    val audioPlayer = remember(desktopController) {
        DesktopAudioPlayer(desktopController)
    }

    // Start audio when the surface enters composition, stop when it leaves.
    // The audio thread polls for a valid sample rate internally, so it is
    // safe to start before the game has finished loading.
    DisposableEffect(desktopController) {
        audioPlayer.start()

        onDispose {
            audioPlayer.stop()
        }
    }

    DesktopEmulationSurface(
        controller = desktopController,
        selectedShader = selectedShader,
        modifier = modifier,
        onEscapePressed = onEscapePressed,
    )
}
