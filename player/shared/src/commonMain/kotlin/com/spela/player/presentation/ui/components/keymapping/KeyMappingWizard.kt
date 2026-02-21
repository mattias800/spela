package com.spela.player.presentation.ui.components.keymapping

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.ButtonInfo
import com.spela.player.domain.model.ConsoleButtonLayout
import com.spela.player.presentation.state.KeyMappingState
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

/**
 * Full-screen wizard that steps through each button on a console controller,
 * prompting the user to press the key/button they want to bind.
 *
 * @param layout Console button layout with the buttons to map
 * @param state Current key mapping state from the ViewModel
 * @param onSkip Called when the user skips the current button
 * @param onResetAll Called when the user wants to reset all bindings
 * @param onDone Called when the user finishes the wizard
 * @param onDismiss Called when the dialog should close
 * @param keyNameResolver Converts a platform key code to a human-readable name
 */
@Composable
fun KeyMappingWizard(
    layout: ConsoleButtonLayout,
    state: KeyMappingState,
    onSkip: () -> Unit,
    onClearBinding: (() -> Unit)? = null,
    onResetAll: () -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
    keyNameResolver: (Int) -> String = { "Key $it" },
) {
    // Track success flash
    var showSuccess by remember { mutableStateOf(false) }
    var lastMappedStep by remember { mutableStateOf(-1) }

    // Detect when a new binding was captured (step advanced)
    LaunchedEffect(state.mappingStep) {
        if (state.mappingStep > 1 && state.mappingStep != lastMappedStep) {
            showSuccess = true
            lastMappedStep = state.mappingStep
            delay(500)
            showSuccess = false
        }
    }

    val successBg by animateColorAsState(
        targetValue = if (showSuccess) SpColor.Success.copy(alpha = 0.15f) else SpColor.SurfaceElevated,
        animationSpec = tween(200),
        label = "wizardSuccessBg",
    )

    // Build button states for the controller visual
    val buttonStates = remember(layout, state.currentBindings, state.currentMappingButton) {
        buildButtonStates(layout, state.currentBindings, state.currentMappingButton)
    }

    val mappingLabels = remember(state.currentBindings) {
        state.currentBindings.mapValues { (_, keyCode) -> keyNameResolver(keyCode) }
    }

    val currentButton: ButtonInfo? = layout.buttons.find { it.retroButtonId == state.currentMappingButton }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .widthIn(max = 600.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(successBg)
                    .padding(SpSpacing.XLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Title
                Text(
                    text = "Controller Mapping",
                    style = SpTypography.HeadlineMedium,
                    color = SpColor.OnBackground,
                )

                Spacer(Modifier.height(SpSpacing.Default))

                // Controller visual (takes ~40% of height)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                ) {
                    ConsoleControllerVisual(
                        layout = layout,
                        buttonStates = buttonStates,
                        highlightedButton = state.currentMappingButton,
                        mappingLabels = mappingLabels,
                        onButtonClick = { /* In wizard mode, buttons are not directly clickable */ },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.height(SpSpacing.XLarge))

                // Current mapping prompt
                if (currentButton != null) {
                    Text(
                        text = "Map: ${currentButton.label}",
                        style = SpTypography.HeadlineSmall,
                        color = SpColor.OnBackground,
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = "Press a button on your gamepad...",
                        style = SpTypography.BodyMedium,
                        color = SpColor.OnBackgroundSecondary,
                    )
                } else {
                    Text(
                        text = "All buttons mapped",
                        style = SpTypography.HeadlineSmall,
                        color = SpColor.Success,
                    )
                }

                Spacer(Modifier.height(SpSpacing.Default))

                // Condensed mapping progress list
                MappingListCondensed(
                    layout = layout,
                    currentBindings = state.currentBindings,
                    highlightedButton = state.currentMappingButton,
                    keyNameResolver = keyNameResolver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )

                Spacer(Modifier.height(SpSpacing.Small))

                // Progress indicator
                Text(
                    text = "${state.mappingStep} of ${state.totalSteps} buttons mapped",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )

                Spacer(Modifier.height(SpSpacing.Medium))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    SpButton(
                        text = "Skip",
                        onClick = onSkip,
                        style = SpButtonStyle.Ghost,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClearBinding != null) {
                        SpButton(
                            text = "Clear",
                            onClick = onClearBinding,
                            style = SpButtonStyle.Ghost,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SpButton(
                        text = "Reset All",
                        onClick = onResetAll,
                        style = SpButtonStyle.Outlined,
                        modifier = Modifier.weight(1f),
                    )
                    SpButton(
                        text = "Done",
                        onClick = onDone,
                        style = SpButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
