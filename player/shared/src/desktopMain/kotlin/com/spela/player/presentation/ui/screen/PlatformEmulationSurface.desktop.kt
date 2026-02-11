package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.domain.repository.KeyMappingRepository
import com.spela.player.libretro.DesktopAudioPlayer
import com.spela.player.libretro.DesktopEmulationSurface
import com.spela.player.libretro.DesktopLibretroController
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
    val desktopController = controller as? DesktopLibretroController ?: return

    val audioPlayer = remember(desktopController) {
        DesktopAudioPlayer(desktopController)
    }

    // Start audio when the surface enters composition, stop when it leaves.
    DisposableEffect(desktopController) {
        audioPlayer.start()
        onDispose {
            audioPlayer.stop()
        }
    }

    // Load key mapping from repository based on current console
    val keyMappingRepo: KeyMappingRepository = koinInject()
    val emulationViewModel: EmulationViewModel = koinInject()
    val consoleId = emulationViewModel.state.value.consoleId

    var keyMapping by remember { mutableStateOf<Map<Int, Int>?>(null) }

    LaunchedEffect(consoleId) {
        if (consoleId.isNotEmpty()) {
            // getEffectiveMapping returns retroButtonId -> platformKeyCode
            // DesktopEmulationSurface expects platformKeyCode -> retroButtonId
            val retroMapping = keyMappingRepo.getEffectiveMapping(consoleId)
            keyMapping = retroMapping.entries.associate { (retro, platform) -> platform to retro }
        }
    }

    DesktopEmulationSurface(
        controller = desktopController,
        selectedShader = selectedShader,
        modifier = modifier,
        onEscapePressed = onEscapePressed,
        keyMapping = keyMapping,
    )
}
