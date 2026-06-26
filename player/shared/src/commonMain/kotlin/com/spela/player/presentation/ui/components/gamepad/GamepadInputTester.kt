package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpSpacing

/**
 * Live input-layer tester (#1355): a single focusable gamepad schematic. While
 * it's focused ([onActiveChange] fires true) the input pipeline routes face/
 * shoulder/trigger/stick buttons here and consumes them (so A/B don't navigate)
 * and the pressed [GamepadPosition] lights up on the schematic; the **D-pad is
 * never captured** so the user can move on and off the panel.
 *
 * Intentionally minimal (#1448): just the visual — no position-label chips and
 * no instructional text. Callers that want guidance (the Settings page) render
 * their own copy above the tester; the wizard relies on its page subtitle.
 */
@Composable
fun GamepadInputTester(
    pressedPositions: Set<GamepadPosition>,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // Drive capture from this element's focus, and always release on exit.
    LaunchedEffect(focused) { onActiveChange(focused) }
    DisposableEffect(Unit) { onDispose { onActiveChange(false) } }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.RadiusMedium),
                interactionSource = interactionSource,
            )
            .padding(SpSpacing.Default)
            .testTag("input_tester"),
    ) {
        GamepadSchematic(highlighted = pressedPositions)
    }
}
