package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
 * Live input-layer tester (#1355/#1448). The user navigates onto it and presses
 * the confirm button to **activate** it; once active, every button — including
 * the **D-pad** — and the analog sticks are captured and shown on the schematic,
 * and nothing navigates away, so the whole controller is testable.
 *
 * To **stop**, the user holds the confirm button for [HOLD_TO_STOP_MS] (a single
 * press no longer exits, so the confirm button is testable too); a progress bar
 * and countdown track the hold. Releasing early cancels. Deactivation happens on
 * *release after a full hold* — the stopping press is consumed while the tester is
 * still active, so it can't bounce back and re-activate. Touch users can also tap
 * to toggle. The confirm button is captured (never lights up) — its hold drives
 * the timer instead.
 *
 * Capture is scoped to the active state via [onActiveChange]; the platform input
 * layer keys off it (Android `captureTestInput`, desktop poller), reporting the
 * confirm button as [confirmHeld] rather than a lit position.
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
    val currentConfirmHeld by rememberUpdatedState(confirmHeld)
    var active by remember { mutableStateOf(false) }
    var holdComplete by remember { mutableStateOf(false) }
    // The hold-to-stop timer is "armed" only after the press that opened the tester
    // is released (#1448). On desktop the opening confirm press (a click on the
    // button's press edge) is still held when capture starts, so without this the
    // timer would begin the instant the tester opens. We disarm until that press
    // releases; touch opens with no confirm held, so the grace window below arms it.
    var armed by remember { mutableStateOf(false) }
    var sawHeldSinceActive by remember { mutableStateOf(false) }
    val holdProgress = remember { Animatable(0f) }

    LaunchedEffect(active) {
        onActiveChange(active)
        armed = false
        sawHeldSinceActive = false
        if (active) {
            // Give the platform a moment to report a carried-over opening press; if
            // confirm isn't held once it passes, there was none (touch / already
            // released) so arm immediately.
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
    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) SpColor.SurfaceBright else SpColor.SurfaceVariant)
            .border(2.dp, if (active) SpColor.Primary else SpColor.Divider, shape)
            .clickable(interactionSource = interactionSource, indication = null) { active = !active }
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(SpSpacing.Default)
            .testTag("input_tester"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (holding) {
            val secondsLeft = ceil((1f - holdProgress.value) * (HOLD_TO_STOP_MS / 1000f))
                .toInt().coerceAtLeast(0)
            Text(
                text = if (holdComplete) "Release to stop testing." else "Keep holding to stop… ${secondsLeft}s",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("tester_hold_label"),
            )
            Spacer(Modifier.height(SpSpacing.Small))
            HoldToStopBar(progress = holdProgress.value)
        } else {
            Text(
                text = if (active) {
                    "Testing — buttons, D-pad and sticks light up. " +
                        "Hold the confirm button (or tap) for 2s to stop."
                } else {
                    "Press the confirm button or tap to test your controller."
                },
                style = SpTypography.BodyMedium,
                color = if (active) SpColor.OnBackground else SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(SpSpacing.Medium))
        GamepadSchematic(highlighted = pressedPositions, sticks = sticks)
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
