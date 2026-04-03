package com.spela.player.presentation.ui.feature.ingame

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.spela.player.libretro.AndroidLibretroController
import com.spela.player.libretro.TouchGamepadOverlay
import com.spela.player.presentation.viewmodel.LibretroController

@Composable
actual fun PlatformTouchControls(
    controller: LibretroController,
    modifier: Modifier,
    port: Int,
) {
    val androidController = controller as? AndroidLibretroController ?: return

    // Auto-hide touch controls when a physical controller is actively being used
    val physicalControllerActive by androidController.physicalControllerActive.collectAsState()

    val animEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
    AnimatedVisibility(
        visible = !physicalControllerActive,
        enter = if (animEnabled) fadeIn() else androidx.compose.animation.EnterTransition.None,
        exit = if (animEnabled) fadeOut() else androidx.compose.animation.ExitTransition.None,
    ) {
        TouchGamepadOverlay(
            controller = androidController,
            modifier = modifier,
            port = port,
        )
    }
}
