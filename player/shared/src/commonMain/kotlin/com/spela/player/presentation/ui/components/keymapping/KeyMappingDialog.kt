package com.spela.player.presentation.ui.components.keymapping

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
import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * A dialog wrapper that shows [KeyMappingScreen] as a modal.
 * Used by the in-game overlay and potentially by per-console settings.
 *
 * @param layout Console button layout
 * @param state Current key mapping state
 * @param onButtonClick Called when a button on the controller is tapped
 * @param onStartWizard Called to enter the wizard flow
 * @param onResetToDefaults Called to reset all bindings
 * @param onDismiss Called when the dialog should close
 * @param keyNameResolver Converts a platform key code to a human-readable name
 */
@Composable
fun KeyMappingDialog(
    layout: ConsoleButtonLayout,
    state: KeyMappingState,
    onButtonClick: (Int) -> Unit,
    onStartWizard: () -> Unit,
    onResetToDefaults: () -> Unit,
    onLoadPreset: (() -> Unit)? = null,
    onCancelMapping: (() -> Unit)? = null,
    onClearBinding: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    keyNameResolver: (Int) -> String = { "Key $it" },
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
                KeyMappingScreen(
                    layout = layout,
                    state = state,
                    onButtonClick = onButtonClick,
                    onStartWizard = onStartWizard,
                    onResetToDefaults = onResetToDefaults,
                    onLoadPreset = onLoadPreset,
                    onCancelMapping = onCancelMapping,
                    onClearBinding = onClearBinding,
                    keyNameResolver = keyNameResolver,
                )
            }
        }
    }
}
