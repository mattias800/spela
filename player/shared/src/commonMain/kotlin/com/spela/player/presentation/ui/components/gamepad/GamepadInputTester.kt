package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.libretro.GamepadTestSticks
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay
import kotlin.math.ceil

/** How long the confirm button must be held to stop the tester (#1448). */
private const val HOLD_TO_STOP_MS = 2000

/** Grace after the tester opens before the hold-to-stop timer can arm, long enough
 *  for the platform to report a carried-over opening press (#1448). */
private const val ACTIVATION_GRACE_MS = 150L

/**
 * Live input-layer tester (#1355/#1448). The element behaves like any other
 * focusable item in the screen: navigating onto it (D-pad) or tapping it (touch)
 * only **focuses** it — it does not start capturing — so the user can pass over it
 * freely. Pressing the **confirm** button while it's focused **activates** it.
 *
 * Once active, every button — including the **D-pad** and confirm itself — and the
 * analog sticks are captured and shown on the schematic, and nothing navigates
 * away. To **stop**, the user holds the confirm button for [HOLD_TO_STOP_MS] (a
 * single press no longer exits, so the confirm button is testable too); a progress
 * bar and countdown below the schematic track the hold, and stopping happens on
 * *release after a full hold* so the stopping press can't bounce back to
 * re-activate.
 *
 * Capture is scoped to the active state via [onActiveChange]; the platform input
 * layer keys off it (Android `captureTestInput`, desktop poller), reporting the
 * confirm button via [confirmHeld] (for the timer) in addition to lighting it up.
 */
@Composable
fun GamepadInputTester(
    pressedPositions: Set<GamepadPosition>,
    confirmHeld: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sticks: GamepadTestSticks = GamepadTestSticks(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    val focused by interactionSource.collectIsFocusedAsState()
    val currentConfirmHeld by rememberUpdatedState(confirmHeld)
    var active by remember { mutableStateOf(false) }
    var holdComplete by remember { mutableStateOf(false) }
    // The hold-to-stop timer is "armed" only after the press that opened the tester
    // is released (#1448). On desktop the opening confirm press (a click on the
    // button's press edge) is still held when capture starts, so without this the
    // timer would begin the instant the tester opens. We disarm until that press
    // releases; touch/D-pad focus then a fresh confirm press arms via the grace.
    var armed by remember { mutableStateOf(false) }
    var sawHeldSinceActive by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(active) {
        onActiveChange(active)
        armed = false
        sawHeldSinceActive = false
        if (active) {
            delay(ACTIVATION_GRACE_MS)
            if (!currentConfirmHeld) armed = true
        }
    }
    // Release capture whenever the tester leaves composition (e.g. navigating
    // away mid-test) so it never leaks.
    DisposableEffect(Unit) { onDispose { onActiveChange(false) } }

    // Hold-to-stop timer. Once armed, holding confirm fills the bar over
    // HOLD_TO_STOP_MS; if confirmHeld flips to false the animateTo is cancelled (the
    // LaunchedEffect re-keys), so an early release resets without stopping. Stopping
    // happens on release *after* a full hold, not on timer completion, so the
    // confirm press that stops the tester is consumed while still active and can't
    // bounce back to re-activate.
    LaunchedEffect(active, confirmHeld) {
        if (active) {
            if (confirmHeld) sawHeldSinceActive = true else if (sawHeldSinceActive) armed = true
        }
        if (active && confirmHeld && armed) {
            holdProgress.snapTo(0f)
            holdComplete = false
            holdProgress.animateTo(1f, tween(HOLD_TO_STOP_MS, easing = LinearEasing))
            holdComplete = true
        } else {
            if (active && holdComplete) active = false
            holdProgress.snapTo(0f)
            holdComplete = false
        }
    }

    val holding = active && confirmHeld && armed
    val secondsLeft = ceil((1f - holdProgress.value) * (HOLD_TO_STOP_MS / 1000f)).toInt().coerceAtLeast(0)
    val statusText = when {
        holding && holdComplete -> "Release to stop testing."
        holding -> "Keep holding to stop… ${secondsLeft}s"
        active -> "Hold the confirm button to stop testing."
        focused -> "Press the confirm button to test your controller."
        else -> "Select the input tester to test your controller."
    }

    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            // While focused-but-inactive, the confirm button activates the tester.
            // It reaches Compose as DPAD center (Android) or Enter (desktop) once
            // resolved by the convention layer; consume both edges so the clickable
            // doesn't also fire. While active the platform captures confirm (for the
            // hold-to-stop timer) so it never reaches here (#1448).
            .onPreviewKeyEvent { event ->
                val isConfirm = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter || event.key == Key.NumPadEnter
                when {
                    active || !isConfirm -> false
                    event.type == KeyEventType.KeyUp -> { active = true; true }
                    else -> true
                }
            }
            .clip(shape)
            .background(if (active) SpColor.SurfaceBright else SpColor.SurfaceVariant)
            .border(2.dp, if (active) SpColor.Primary else SpColor.Divider, shape)
            // A tap (touch/mouse) only focuses — it doesn't capture — so the tester
            // behaves like a normal navigable item; activation is the confirm press
            // above (#1448).
            .clickable(interactionSource = interactionSource, indication = null) {
                runCatching { focusRequester.requestFocus() }
            }
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(SpSpacing.Default)
            .testTag("input_tester"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GamepadSchematic(highlighted = pressedPositions, sticks = sticks)
        Spacer(Modifier.height(SpSpacing.Medium))
        // Status + hold bar below the schematic, at a static height so the layout
        // never jumps when the bar appears: two reserved text lines + a reserved
        // bar slot (#1448).
        Text(
            text = statusText,
            style = SpTypography.BodyMedium,
            color = if (active || focused) SpColor.OnBackground else SpColor.OnBackgroundSecondary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("tester_status"),
        )
        Spacer(Modifier.height(SpSpacing.Small))
        Box(Modifier.fillMaxWidth().height(SpSpacing.Small)) {
            if (holding) HoldToStopBar(progress = holdProgress.value)
        }
    }
}

/** A linear fill over the hold, on a high-contrast dark track. */
@Composable
private fun HoldToStopBar(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SpSpacing.Small)
            .clip(RoundedCornerShape(SpSpacing.RadiusPill))
            .background(SpColor.Background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(SpSpacing.Small)
                .clip(RoundedCornerShape(SpSpacing.RadiusPill))
                .background(SpColor.PrimaryLight)
                .testTag("tester_hold_progress"),
        )
    }
}
