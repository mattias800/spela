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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
 * Direct-edit key mapping screen: a per-console labeled list of buttons and their
 * current bindings (#1335). Tapping any row enters single-button listening mode for
 * that button. (Replaced the old pictorial controller diagram, which rendered the
 * wrong controller for most consoles; an approximate positional visual is a separate
 * follow-up.)
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
    /** When non-null, a game is loaded and the current bindings can be saved as a
     *  per-game override (#1336). */
    onSaveGameOverride: (() -> Unit)? = null,
    /** When non-null and [hasGameOverride], the per-game override can be cleared. */
    onClearGameOverride: (() -> Unit)? = null,
    hasGameOverride: Boolean = false,
    keyNameResolver: (Int) -> String = { "Key $it" },
    portLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
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

            // Per-console mapping as a labeled list (#1335): each row is a button
            // and its current binding, tappable to remap. Replaces the old pictorial
            // controller diagram (only 5 outlines for ~14 consoles, drifting hotspots,
            // sticks drawn as disconnected dots). An approximate positional visual is
            // a separate follow-up.
            MappingListPanel(
                layout = layout,
                currentBindings = state.currentBindings,
                highlightedButton = state.currentMappingButton,
                onButtonClick = onButtonClick,
                keyNameResolver = keyNameResolver,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .weight(1f),
            )

            // Listening prompt — shown below the list while a button is selected.
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

            // Action buttons — capped to the list width so they stay aligned on wide screens.
            Row(
                modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
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

            // Per-game override affordance (#1336): only when a game is loaded.
            if (onSaveGameOverride != null) {
                Spacer(Modifier.height(SpSpacing.Medium))
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    SpButton(
                        text = "Save for this game",
                        onClick = onSaveGameOverride,
                        modifier = Modifier.weight(1f).testTag("save_game_override"),
                    )
                    if (hasGameOverride && onClearGameOverride != null) {
                        SpButton(
                            text = "Clear game override",
                            onClick = onClearGameOverride,
                            style = SpButtonStyle.Ghost,
                            modifier = Modifier.weight(1f).testTag("clear_game_override"),
                        )
                    }
                }
            }

            Spacer(Modifier.height(SpSpacing.Default))
        }
    }
}
