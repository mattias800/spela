package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.spela.player.domain.model.ShaderPreset
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.EmulationSurface
import com.spela.player.libretro.VulkanEmulationSurface
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

    // Use Vulkan surface for HW render cores (Vulkan init happens in surfaceCreated),
    // or when the GPU renderer is already active. Fall back to software otherwise.
    // On emulators, HW render is overridden to Angrylion (SwiftShader can't run
    // paraLLEl-RDP), so only trust gpuIsActive() — not isHwRenderEnabled().
    val useVulkanSurface = if (com.spela.player.util.isEmulator()) {
        androidController.gpuIsActive()
    } else {
        androidController.isHwRenderEnabled() || androidController.gpuIsActive()
    }
    if (useVulkanSurface && !isDualScreenSplit) {
        VulkanEmulationSurface(
            controller = androidController,
            selectedShader = selectedShader,
            modifier = modifier,
        )
    } else {
        EmulationSurface(
            controller = androidController,
            selectedShader = selectedShader,
            isDualScreenSplit = isDualScreenSplit,
            splitY = emulationState.dualScreenSplitY,
            modifier = modifier,
        )
    }
}
