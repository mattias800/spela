package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.spela.player.libretro.DesktopAudioPlayer
import com.spela.player.libretro.DesktopEmulationSurface
import com.spela.player.libretro.DesktopLibretroController
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformEmulationSurface(
    controller: LibretroController,
    modifier: Modifier,
) {
    val desktopController = controller as? DesktopLibretroController ?: return

    val audioPlayer = remember(desktopController) {
        DesktopAudioPlayer(desktopController)
    }

    // Start audio when the surface enters composition, stop when it leaves
    DisposableEffect(desktopController) {
        val sampleRate = desktopController.getSampleRate()
        audioPlayer.start(sampleRate)

        onDispose {
            audioPlayer.stop()
        }
    }

    DesktopEmulationSurface(
        controller = desktopController,
        modifier = modifier,
    )
}
