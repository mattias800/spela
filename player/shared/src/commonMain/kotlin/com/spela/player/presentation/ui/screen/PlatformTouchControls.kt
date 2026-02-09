package com.spela.player.presentation.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.spela.player.presentation.viewmodel.LibretroController

/**
 * Platform-specific touch gamepad overlay.
 * On Android, renders semi-transparent on-screen controls (D-pad, A/B, Start/Select, L/R).
 * On Desktop, this is a no-op since keyboard/controller input is used instead.
 */
@Composable
expect fun PlatformTouchControls(
    controller: LibretroController,
    modifier: Modifier = Modifier,
)
