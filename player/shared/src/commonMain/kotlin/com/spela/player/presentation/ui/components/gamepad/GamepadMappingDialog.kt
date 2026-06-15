package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spela.player.domain.model.ButtonInfo
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.feature.settings.SettingsDivider
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GamepadMappingState
import com.spela.player.presentation.viewmodel.GamepadMappingViewModel

/**
 * Desktop/Android gamepad mapping editor (#1334 component C; redesigned RetroArch-
 * style in #1377). Lists this **console's** buttons (NES = D-pad/Select/Start/B/A)
 * and which physical position currently triggers each. To rebind, the user picks a
 * console button and *holds* the physical button they want — held [HOLD_MS] it's
 * assigned 1:1; releasing resets; [ABORT_MS] idle aborts. Brand-neutral throughout:
 * the prompt names the held button by canonical position ("Bottom button"), never a
 * Xbox/Nintendo glyph.
 *
 * @param state effective position→action mapping, console outputs, + binding session
 * @param onStartBinding begin hold-to-bind for a console button
 * @param onBindKey report a captured gamepad press/release during a binding session
 *   (Android: the editor is a Dialog window, so its content captures keys itself)
 * @param onCancelBinding cancel the active hold-to-bind session
 * @param onResetToDefaults clear all overrides for this console
 * @param onDismiss close the editor
 */
@Composable
fun GamepadMappingDialog(
    state: GamepadMappingState,
    onStartBinding: (ButtonInfo) -> Unit,
    onBindKey: (GamepadPosition, Boolean) -> Unit,
    onCancelBinding: () -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = { if (state.bindingOutput != null) onCancelBinding() else onDismiss() },
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
                    // Cap to the available screen height so the action buttons are
                    // never clipped on short landscape screens like the AYN Thor
                    // (#1371). The button list below scrolls to fit.
                    .fillMaxHeight(0.9f)
                    .heightIn(max = 600.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(SpColor.SurfaceElevated)
                    .padding(SpSpacing.XLarge)
                    .testTag("gamepad_mapping_dialog"),
            ) {
                val binding = state.bindingOutput
                if (binding != null) {
                    BindingPrompt(
                        output = binding,
                        heldPosition = state.bindingHeldPosition,
                        tick = state.bindingTick,
                        onBindKey = onBindKey,
                        onCancel = onCancelBinding,
                    )
                } else {
                    MappingList(
                        state = state,
                        onStartBinding = onStartBinding,
                        onResetToDefaults = onResetToDefaults,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
    }
}

/** The default view: header + scrollable console-button list + pinned actions. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MappingList(
    state: GamepadMappingState,
    onStartBinding: (ButtonInfo) -> Unit,
    onResetToDefaults: () -> Unit,
    onDismiss: () -> Unit,
) {
    Text(
        text = "Controller buttons — ${state.displayName}",
        style = SpTypography.HeadlineMedium,
        color = SpColor.OnBackground,
    )
    Text(
        text = "Pick a console button, then hold the controller button you want it to use.",
        style = SpTypography.BodySmall,
        color = SpColor.OnBackgroundTertiary,
    )

    Spacer(Modifier.height(SpSpacing.Medium))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Take the remaining space (between header and the pinned action
            // buttons) and scroll within it (#1371).
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
        state.outputs.forEachIndexed { index, output ->
            OutputRow(
                output = output,
                positionLabel = boundPositionLabel(state, output.retroButtonId),
                onClick = { onStartBinding(output) },
            )
            if (index < state.outputs.size - 1) {
                SettingsDivider()
            }
        }
    }

    Spacer(Modifier.height(SpSpacing.Default))

    SpButton(
        text = "Reset to defaults",
        onClick = onResetToDefaults,
        style = SpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth().testTag("gamepad_mapping_reset"),
    )
    Spacer(Modifier.height(SpSpacing.Small))
    SpButton(
        text = "Done",
        onClick = onDismiss,
        style = SpButtonStyle.Primary,
        modifier = Modifier.fillMaxWidth().testTag("gamepad_mapping_done"),
    )
}

/** A settings-style row: console button on the left, the physical position that
 *  triggers it on the right. Tapping starts a hold-to-bind session. */
@Composable
private fun OutputRow(
    output: ButtonInfo,
    positionLabel: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.RadiusLarge),
                interactionSource = interactionSource,
                addFocusable = false,
            )
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .testTag("mapping_output_${output.retroButtonId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = output.label,
            style = SpTypography.BodyMedium,
            color = SpColor.OnCard,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = positionLabel,
            style = SpTypography.TitleMedium,
            color = if (positionLabel == UNBOUND_LABEL) SpColor.OnBackgroundTertiary else SpColor.Primary,
            modifier = Modifier.testTag("mapping_bound_${output.retroButtonId}"),
        )
    }
}

/** The hold-to-bind capture prompt shown while a session is active. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.BindingPrompt(
    output: ButtonInfo,
    heldPosition: GamepadPosition?,
    tick: Int,
    onBindKey: (GamepadPosition, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    // On Android the editor is a Dialog window, so MainActivity.onKeyDown never
    // sees these presses — without capturing here, a press just activates the
    // focused Cancel button and the prompt closes. onPreviewKeyEvent runs before
    // the focus system: it maps the key to a canonical position, feeds the binder,
    // and consumes it so it can't double as a click or navigation. The desktop
    // poller feeds the same binder signal directly. (#1377)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val position = keyToGamepadPosition(event.key) ?: return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> { onBindKey(position, true); true }
                    KeyEventType.KeyUp -> { onBindKey(position, false); true }
                    else -> false
                }
            }
            .testTag("binding_prompt"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Assign ${output.label}",
            style = SpTypography.HeadlineMedium,
            color = SpColor.OnBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SpSpacing.Large))

        if (heldPosition == null) {
            Text(
                text = "Hold the controller button you want to use for ${output.label}.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(SpSpacing.Default))
            CountdownLabel(
                tick = tick,
                durationMs = GamepadMappingViewModel.ABORT_MS,
                prefix = "Aborting in ",
                testTag = "binding_abort_countdown",
            )
        } else {
            Text(
                text = heldPosition.displayName,
                style = SpTypography.HeadlineSmall,
                color = SpColor.Primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("binding_held"),
            )
            Spacer(Modifier.height(SpSpacing.Default))
            HoldProgressBar(tick = tick)
            Spacer(Modifier.height(SpSpacing.Small))
            CountdownLabel(
                tick = tick,
                durationMs = GamepadMappingViewModel.HOLD_MS,
                prefix = "Keep holding… ",
                testTag = "binding_hold_countdown",
            )
        }
    }

    Spacer(Modifier.height(SpSpacing.Default))
    SpButton(
        text = "Cancel",
        onClick = onCancel,
        style = SpButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth().testTag("binding_cancel"),
    )
}

/** A linear fill over [GamepadMappingViewModel.HOLD_MS] that restarts on each
 *  [tick] change, visually tracking the hold the ViewModel is timing. */
@Composable
private fun HoldProgressBar(tick: Int) {
    androidx.compose.runtime.key(tick) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = GamepadMappingViewModel.HOLD_MS.toInt(),
                    easing = LinearEasing,
                ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SpSpacing.Small)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.SurfaceBright),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.value)
                    .height(SpSpacing.Small)
                    .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                    .background(SpColor.Primary),
            )
        }
    }
}

/** A whole-second countdown ("…3s, 2s, 1s") over [durationMs], restarting on each
 *  [tick] change so it stays in lock-step with the ViewModel's timer. */
@Composable
private fun CountdownLabel(tick: Int, durationMs: Long, prefix: String, testTag: String) {
    androidx.compose.runtime.key(tick) {
        val progress = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs.toInt(), easing = LinearEasing),
            )
        }
        val secondsLeft = ceil((1f - progress.value) * (durationMs / 1000f)).toInt().coerceAtLeast(0)
        Text(
            text = "$prefix${secondsLeft}s",
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/** Maps a Compose [Key] to its canonical [GamepadPosition], mirroring
 *  AndroidGamepadNormalizer's positional keycode mapping. Returns null for keys
 *  that aren't gamepad buttons. */
private fun keyToGamepadPosition(key: Key): GamepadPosition? = when (key) {
    Key.ButtonA -> GamepadPosition.SOUTH
    Key.ButtonB -> GamepadPosition.EAST
    Key.ButtonX -> GamepadPosition.WEST
    Key.ButtonY -> GamepadPosition.NORTH
    Key.DirectionUp -> GamepadPosition.DPAD_UP
    Key.DirectionDown -> GamepadPosition.DPAD_DOWN
    Key.DirectionLeft -> GamepadPosition.DPAD_LEFT
    Key.DirectionRight -> GamepadPosition.DPAD_RIGHT
    Key.ButtonL1 -> GamepadPosition.L1
    Key.ButtonR1 -> GamepadPosition.R1
    Key.ButtonL2 -> GamepadPosition.L2
    Key.ButtonR2 -> GamepadPosition.R2
    Key.ButtonThumbLeft -> GamepadPosition.L3
    Key.ButtonThumbRight -> GamepadPosition.R3
    Key.ButtonStart -> GamepadPosition.START
    Key.ButtonSelect -> GamepadPosition.SELECT
    else -> null
}

private const val UNBOUND_LABEL = "Unbound"

/** The positional name (e.g. "Bottom button") of the position currently bound to
 *  [retroButtonId], or [UNBOUND_LABEL] when nothing triggers this console button. */
private fun boundPositionLabel(state: GamepadMappingState, retroButtonId: Int): String {
    val position = state.mapping.entries
        .filter { it.value == retroButtonId }
        .minByOrNull { it.key.ordinal }
        ?.key
        ?: return UNBOUND_LABEL
    return position.displayName
}
