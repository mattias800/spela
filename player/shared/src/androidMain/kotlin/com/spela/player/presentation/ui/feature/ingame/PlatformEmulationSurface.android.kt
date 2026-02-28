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
    val isEmu = com.spela.player.util.isEmulator()
    val hwRenderEnabled = emulationState.isHwRenderEnabled
    val gpuActive = androidController.gpuIsActive()

    val useVulkanSurface = if (isEmu) {
        gpuActive
    } else {
        hwRenderEnabled || gpuActive
    }

    val isOverlayVisible = emulationState.showOverlay || emulationState.showKeyMapping || emulationState.showGamepadConfig

    if (isDualScreenSplit) {
        // Dual-screen DS/3DS: always use software surface (split rendering)
        EmulationSurface(
            controller = androidController,
            selectedShader = selectedShader,
            isDualScreenSplit = true,
            splitY = emulationState.dualScreenSplitY,
            modifier = modifier,
        )
    } else {
        // Always include VulkanEmulationSurface in the tree so the SurfaceView
        // is present from the first composition. Dynamically swapping it in later
        // (when isHwRenderEnabled becomes true) causes the SurfaceView to never
        // receive a surfaceCreated callback from the Android window manager.
        //
        // The SurfaceView starts transparent (TRANSLUCENT format). GPU rendering
        // is deferred until isHwRenderEnabled becomes true. For software-only
        // cores, the EmulationSurface Canvas renders beneath the transparent surface.
        VulkanEmulationSurface(
            controller = androidController,
            selectedShader = selectedShader,
            isHwRenderEnabled = hwRenderEnabled,
            isOverlayVisible = isOverlayVisible,
            modifier = modifier,
        )

        // Software fallback: render via Canvas when GPU renderer isn't active.
        // Covers both non-HW cores (entire session) and the initial frames of
        // HW cores before gpuInit completes.
        if (!useVulkanSurface) {
            EmulationSurface(
                controller = androidController,
                selectedShader = selectedShader,
                isDualScreenSplit = false,
                splitY = 0,
                modifier = modifier,
            )
        }
    }
}
