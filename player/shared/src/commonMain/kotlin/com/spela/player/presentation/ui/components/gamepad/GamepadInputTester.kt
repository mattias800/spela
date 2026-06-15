package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.presentation.ui.gamepad.gamepadFocusable
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * Live input-layer tester (#1355). The whole panel is a SINGLE focusable
 * element: the user D-pad-navigates onto it, then presses face/shoulder/trigger/
 * stick buttons to see which canonical [GamepadPosition] each maps to — which
 * verifies the detected controller type without launching a game.
 *
 * Capture is scoped to focus: while this element is focused ([onActiveChange]
 * fires true), the input pipeline routes those buttons here and consumes them
 * (so A/B don't navigate). The **D-pad is never captured** — it always
 * navigates, so the user can move onto and off the panel. When unfocused,
 * nothing is captured and A/B behave normally everywhere else.
 *
 * D-pad positions are intentionally not shown (they don't vary by controller).
 */
@OptIn(ExperimentalLayoutApi::class)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .gamepadFocusable(
                shape = RoundedCornerShape(SpSpacing.RadiusMedium),
                interactionSource = interactionSource,
            )
            .padding(SpSpacing.Default)
            .testTag("input_tester"),
    ) {
        Text(
            text = if (focused) {
                "Press a button on your controller — the matching position lights up. " +
                    "Use the D-pad to move away. If the wrong position lights up, change the Type above."
            } else {
                "Navigate here with the D-pad, then press your buttons to see which position " +
                    "each maps to — to confirm the controller type is detected correctly."
            },
            style = SpTypography.BodySmall,
            color = SpColor.OnBackgroundTertiary,
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
        ) {
            // D-pad excluded — it isn't tested (positional-standard, drives navigation).
            GamepadPosition.entries.filterNot { it.isDpad }.forEach { position ->
                val active = position in pressedPositions
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                        .background(if (active) SpColor.Primary else SpColor.SurfaceElevated)
                        .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small)
                        .testTag("tester_pos_${position.name}")
                        .semantics {
                            contentDescription = position.displayName
                            stateDescription = if (active) "Pressed" else "Not pressed"
                        },
                ) {
                    Text(
                        text = position.displayName,
                        style = SpTypography.BodyMedium,
                        color = if (active) SpColor.OnPrimary else SpColor.OnCard,
                    )
                }
            }
        }
    }
}
