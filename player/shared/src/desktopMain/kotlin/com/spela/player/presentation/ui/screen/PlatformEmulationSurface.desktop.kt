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
import com.spela.player.libretro.MetalEmulationSurface
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

    // Check if GPU rendering is active (Metal on macOS, Vulkan on Linux/Windows)
    val gpuActive = desktopController.gpuIsActive()

    if (gpuActive && isMacOs()) {
        MetalEmulationSurface(
            controller = desktopController,
            selectedShader = selectedShader,
            modifier = modifier,
        )
    } else {
        // Software fallback (or GPU not yet initialized -- it will be initialized
        // by the surface itself on first composition)

        // Load key mapping from repository based on current console
        val keyMappingRepo: KeyMappingRepository = koinInject()
        val emulationViewModel: EmulationViewModel = koinInject()
        val consoleId = emulationViewModel.state.value.consoleId

        var keyMapping by remember { mutableStateOf<Map<Int, Int>?>(null) }

        LaunchedEffect(consoleId) {
            if (consoleId.isNotEmpty()) {
                val retroMapping = keyMappingRepo.getEffectiveMapping(consoleId)
                keyMapping = retroMapping.entries.associate { (retro, platform) -> platform to retro }
            }
        }

        // Try GPU surface first (Metal on macOS), fall back to software
        if (isMacOs()) {
            MetalEmulationSurface(
                controller = desktopController,
                selectedShader = selectedShader,
                modifier = modifier,
            )
        } else {
            DesktopEmulationSurface(
                controller = desktopController,
                selectedShader = selectedShader,
                modifier = modifier,
                onEscapePressed = onEscapePressed,
                keyMapping = keyMapping,
            )
        }
    }
}

/** Detect macOS at runtime. */
private fun isMacOs(): Boolean {
    val os = System.getProperty("os.name", "").lowercase()
    return os.contains("mac") || os.contains("darwin")
}
