package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Direct-edit key mapping screen showing the controller visual with all current mappings.
 * Tapping any button enters single-button listening mode for just that button.
 *
 * Can be used standalone (from settings) or embedded in a modal dialog.
 *
 * @param layout Console button layout
 * @param state Current key mapping state from the ViewModel
 * @param onButtonClick Called when a button on the controller is tapped (enters listening mode)
 * @param onStartWizard Called when the user wants to enter the full wizard flow
 * @param onResetToDefaults Called when the user wants to reset all bindings to defaults
 * @param keyNameResolver Converts a platform key code to a human-readable name
 */
@Composable
fun KeyMappingScreen(
    layout: ConsoleButtonLayout,
    state: KeyMappingState,
    onButtonClick: (Int) -> Unit,
    onStartWizard: () -> Unit,
    onResetToDefaults: () -> Unit,
    keyNameResolver: (Int) -> String = { "Key $it" },
) {
    val buttonStates = buildMap {
        layout.buttons.forEach { btn ->
            val isMapped = state.currentBindings.containsKey(btn.retroButtonId)
            val isListening = state.currentMappingButton == btn.retroButtonId
            put(btn.retroButtonId, when {
                isListening -> ButtonVisualState.HIGHLIGHTED
                isMapped -> ButtonVisualState.MAPPED
                else -> ButtonVisualState.UNMAPPED
            })
        }
    }

    val mappingLabels = state.currentBindings.mapValues { (_, keyCode) -> keyNameResolver(keyCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .padding(SpSpacing.ScreenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(SpSpacing.Default))

        Text(
            text = layout.displayName,
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
        )

        Spacer(Modifier.height(SpSpacing.Small))

        Text(
            text = "Tap a button to remap it",
            style = SpTypography.BodyMedium,
            color = SpColor.OnBackgroundSecondary,
        )

        Spacer(Modifier.height(SpSpacing.XLarge))

        // Controller visual
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            ConsoleControllerVisual(
                layout = layout,
                buttonStates = buttonStates,
                highlightedButton = state.currentMappingButton,
                mappingLabels = mappingLabels,
                onButtonClick = onButtonClick,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Listening prompt
        if (state.currentMappingButton != null) {
            val buttonLabel = layout.buttons
                .find { it.retroButtonId == state.currentMappingButton }
                ?.label ?: "Button"

            Spacer(Modifier.height(SpSpacing.Large))

            Text(
                text = "Press a key for: $buttonLabel",
                style = SpTypography.HeadlineSmall,
                color = SpColor.Primary,
            )

            Text(
                text = "Press Escape to cancel",
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }

        Spacer(Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            SpButton(
                text = "Start Wizard",
                onClick = onStartWizard,
                style = SpButtonStyle.Outlined,
                modifier = Modifier.weight(1f),
            )
            SpButton(
                text = "Reset to Defaults",
                onClick = onResetToDefaults,
                style = SpButtonStyle.Ghost,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(SpSpacing.Default))
    }
}
