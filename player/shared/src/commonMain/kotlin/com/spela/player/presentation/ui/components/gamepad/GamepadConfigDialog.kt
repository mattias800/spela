package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GamepadConfigState

/**
 * Dialog wrapper for [GamepadConfigScreen], used in the in-game overlay.
 */
@Composable
fun GamepadConfigDialog(
    state: GamepadConfigState,
    onConfigurePort: (Int) -> Unit,
    onSwapUp: ((Int) -> Unit)? = null,
    onSwapDown: ((Int) -> Unit)? = null,
    onSetStyleOverride: ((Int, ControllerStyle?) -> Unit)? = null,
    showConfigureButton: Boolean = true,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Scrim),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(SpColor.SurfaceElevated)
                    .padding(SpSpacing.Default),
            ) {
                GamepadConfigScreen(
                    state = state,
                    onConfigurePort = onConfigurePort,
                    onSwapUp = onSwapUp,
                    onSwapDown = onSwapDown,
                    onSetStyleOverride = onSetStyleOverride,
                    showConfigureButton = showConfigureButton,
                )
            }
        }
    }
}
