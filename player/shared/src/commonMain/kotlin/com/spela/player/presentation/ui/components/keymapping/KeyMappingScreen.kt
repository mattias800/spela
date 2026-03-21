package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Direct-edit key mapping screen showing the controller visual with all current mappings.
 * Tapping any button enters single-button listening mode for just that button.
 *
 * Uses a responsive layout:
 * - Wide (>600dp): Controller visual on left, mapping list on right (side-by-side)
 * - Narrow (≤600dp): Controller visual on top, mapping list below (stacked)
 *
 * @param layout Console button layout
 * @param state Current key mapping state from the ViewModel
 * @param onButtonClick Called when a button on the controller is tapped (enters listening mode)
 * @param onStartWizard Called when the user wants to enter the full wizard flow
 * @param onResetToDefaults Called when the user wants to reset all bindings to defaults
 * @param keyNameResolver Converts a platform key code to a human-readable name
 */
/**
 * @param portLabel Optional subtitle showing which player/device is being configured
 *   (e.g., "Player 2 — Xbox Controller").
 */
@Composable
fun KeyMappingScreen(
    layout: ConsoleButtonLayout,
    state: KeyMappingState,
    onButtonClick: (Int) -> Unit,
    onStartWizard: () -> Unit,
    onResetToDefaults: () -> Unit,
    onLoadPreset: (() -> Unit)? = null,
    onCancelMapping: (() -> Unit)? = null,
    onClearBinding: (() -> Unit)? = null,
    keyNameResolver: (Int) -> String = { "Key $it" },
    portLabel: String? = null,
) {
    val buttonStates = remember(layout, state.currentBindings, state.currentMappingButton) {
        buildButtonStates(layout, state.currentBindings, state.currentMappingButton)
    }

    val mappingLabels = remember(state.currentBindings) {
        state.currentBindings.mapValues { (_, keyCode) -> keyNameResolver(keyCode) }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background)
            .padding(SpSpacing.ScreenHorizontal),
    ) {
        val isWide = maxWidth > 600.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(SpSpacing.Default))

            Text(
                text = layout.displayName,
                style = SpTypography.HeadlineMedium,
                color = SpColor.OnBackground,
            )

            if (portLabel != null) {
                Spacer(Modifier.height(SpSpacing.XSmall))
                Text(
                    text = portLabel,
                    style = SpTypography.TitleSmall,
                    color = SpColor.Primary,
                )
            }

            Spacer(Modifier.height(SpSpacing.Small))

            Text(
                text = "Tap a button to remap it",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundSecondary,
            )

            Spacer(Modifier.height(SpSpacing.Large))

            if (isWide) {
                // Side-by-side layout: controller left, list right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    // Controller visual
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
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

                    Spacer(Modifier.width(SpSpacing.Default))

                    // Mapping list
                    MappingListPanel(
                        layout = layout,
                        currentBindings = state.currentBindings,
                        highlightedButton = state.currentMappingButton,
                        onButtonClick = onButtonClick,
                        keyNameResolver = keyNameResolver,
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                // Stacked layout: controller on top, list below
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
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

                Spacer(Modifier.height(SpSpacing.Medium))

                // Mapping list takes remaining space
                MappingListPanel(
                    layout = layout,
                    currentBindings = state.currentBindings,
                    highlightedButton = state.currentMappingButton,
                    onButtonClick = onButtonClick,
                    keyNameResolver = keyNameResolver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }

            // Listening prompt (shown only when not in the mapping list context)
            if (state.currentMappingButton != null) {
                val buttonLabel = layout.buttons
                    .find { it.retroButtonId == state.currentMappingButton }
                    ?.label ?: "Button"

                Spacer(Modifier.height(SpSpacing.Small))

                Text(
                    text = "Press a key for: $buttonLabel",
                    style = SpTypography.HeadlineSmall,
                    color = SpColor.Primary,
                )

                Spacer(Modifier.height(SpSpacing.Small))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    if (onCancelMapping != null) {
                        SpButton(
                            text = "Cancel",
                            onClick = onCancelMapping,
                            style = SpButtonStyle.Ghost,
                        )
                    }
                    if (onClearBinding != null) {
                        SpSecondaryButton(
                            text = "Clear",
                            onClick = onClearBinding,
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.Medium))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                SpSecondaryButton(
                    text = "Start Wizard",
                    onClick = onStartWizard,
                    modifier = Modifier.weight(1f),
                )
                if (onLoadPreset != null) {
                    SpSecondaryButton(
                        text = "Load Preset",
                        onClick = onLoadPreset,
                        modifier = Modifier.weight(1f),
                    )
                }
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
}
