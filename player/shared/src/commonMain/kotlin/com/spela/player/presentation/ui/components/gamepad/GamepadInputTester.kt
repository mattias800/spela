package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.libretro.GamepadTestSticks
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Live input-layer tester (#1355/#1448). The user navigates onto it and presses
 * the confirm button to **activate** it; once active, every button — including
 * the **D-pad** — is captured and lights up on the schematic, and nothing
 * navigates away, so the whole controller is testable (the D-pad isn't, in the
 * passive focus-capture model, because that's how you'd leave). Pressing confirm
 * again stops it. The confirm button is the toggle, so it's the one button not
 * shown lighting up.
 *
 * Capture is scoped to the active state via [onActiveChange]; the platform input
 * layer keys off it (Android `captureTestInput`, desktop poller) and exempts the
 * confirm button so it can toggle here as a click.
 */
@Composable
fun GamepadInputTester(
    pressedPositions: Set<GamepadPosition>,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sticks: GamepadTestSticks = GamepadTestSticks(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    var active by remember { mutableStateOf(false) }

    LaunchedEffect(active) { onActiveChange(active) }
    // Release capture whenever the tester leaves composition (e.g. navigating
    // away mid-test) so it never leaks.
    DisposableEffect(Unit) { onDispose { onActiveChange(false) } }

    val shape = RoundedCornerShape(SpSpacing.RadiusMedium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (active) SpColor.OnGradientFill else SpColor.SurfaceVariant)
            .border(2.dp, if (active) SpColor.PrimaryLight else Color.Transparent, shape)
            .clickable(interactionSource = interactionSource, indication = null) { active = !active }
            .gamepadFocusable(shape = shape, interactionSource = interactionSource, addFocusable = false)
            .padding(SpSpacing.Default)
            .testTag("input_tester"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (active) {
                "Testing — buttons, D-pad and sticks all light up. " +
                    "Press the confirm button or tap again to stop."
            } else {
                "Press the confirm button or tap to test your controller."
            },
            style = SpTypography.BodySmall,
            color = if (active) SpColor.PrimaryLight else SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        GamepadSchematic(highlighted = pressedPositions, sticks = sticks)
    }
}
