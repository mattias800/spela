package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GamepadPosition
import com.spela.player.libretro.GamepadTestSticks
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlin.math.abs

/**
 * Brand-neutral, approximate schematic of a generic gamepad (#1366) — drawn from
 * primitives (circles + labels), with each canonical [GamepadPosition] placed at
 * its rough physical location: shoulders/triggers across the top, the D-pad on the
 * left, the face-button diamond on the right, Select/Start in the centre, and the
 * stick clicks below. It is an at-a-glance orientation aid, NOT a per-console
 * replica; the labelled mapping stays authoritative.
 *
 * Positions in [highlighted] render filled (e.g. currently pressed, or the button
 * being bound). The L3/R3 wells additionally show live analog deflection from
 * [sticks] (#1448): the dot slides toward the stick's direction, so the user can
 * test stick travel, not just the click. A single reusable layout — driven by the
 * canonical position set, not per-console art.
 */
@Composable
fun GamepadSchematic(
    highlighted: Set<GamepadPosition>,
    modifier: Modifier = Modifier,
    sticks: GamepadTestSticks = GamepadTestSticks(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.SurfaceVariant)
            .padding(SpSpacing.Default),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Default),
    ) {
        // Top row: shoulders/triggers with Select/Start centred between them —
        // L2 L1 · SE ST · R1 R2.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                Pip(GamepadPosition.L2, highlighted)
                Pip(GamepadPosition.L1, highlighted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                Pip(GamepadPosition.SELECT, highlighted)
                Pip(GamepadPosition.START, highlighted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                Pip(GamepadPosition.R1, highlighted)
                Pip(GamepadPosition.R2, highlighted)
            }
        }

        // Main body: D-pad cross (left) · stick clicks L3 R3 (centre, aligned
        // with the crosses' inner arms) · face diamond (right). Dropping the
        // separate stick-click row keeps this compact.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cross(
                up = GamepadPosition.DPAD_UP,
                down = GamepadPosition.DPAD_DOWN,
                left = GamepadPosition.DPAD_LEFT,
                right = GamepadPosition.DPAD_RIGHT,
                highlighted = highlighted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small)) {
                StickPip(GamepadPosition.L3, sticks.leftX, sticks.leftY, highlighted)
                StickPip(GamepadPosition.R3, sticks.rightX, sticks.rightY, highlighted)
            }
            Cross(
                up = GamepadPosition.NORTH,
                down = GamepadPosition.SOUTH,
                left = GamepadPosition.WEST,
                right = GamepadPosition.EAST,
                highlighted = highlighted,
            )
        }
    }
}

/** A 3×3 cross with the four directional positions on the arms (centre empty). */
@Composable
private fun Cross(
    up: GamepadPosition,
    down: GamepadPosition,
    left: GamepadPosition,
    right: GamepadPosition,
    highlighted: Set<GamepadPosition>,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(up, highlighted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pip(left, highlighted)
            Spacer(Modifier.size(PIP_SIZE.dp))
            Pip(right, highlighted)
        }
        Pip(down, highlighted)
    }
}

/** A single labelled button. Filled when [position] is in [highlighted]. */
@Composable
private fun Pip(position: GamepadPosition, highlighted: Set<GamepadPosition>) {
    val active = position in highlighted
    Box(
        modifier = Modifier
            .size(PIP_SIZE.dp)
            .clip(CircleShape)
            .background(if (active) SpColor.Primary else SpColor.SurfaceElevated)
            .border(1.dp, if (active) SpColor.Primary else SpColor.OnBackgroundTertiary, CircleShape)
            .padding(SpSpacing.XXSmall)
            .testTag("schematic_${position.name}")
            .semantics {
                contentDescription = position.displayName
                stateDescription = if (active) "Active" else "Inactive"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = schematicLabel(position),
            style = SpTypography.LabelSmall,
            color = if (active) SpColor.OnPrimary else SpColor.OnBackgroundTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A stick well (L3/R3): a ring whose inner dot slides toward the live analog
 * deflection ([dx], [dy] in -1..1), and that fills when the stick is clicked
 * (position in [highlighted]). Shares the [Pip] testTag/semantics so existing
 * tests and accessibility keep working.
 */
@Composable
private fun StickPip(
    position: GamepadPosition,
    dx: Float,
    dy: Float,
    highlighted: Set<GamepadPosition>,
) {
    val clicked = position in highlighted
    val deflected = abs(dx) > 0.15f || abs(dy) > 0.15f
    val active = clicked || deflected
    val travel = ((PIP_SIZE - DOT_SIZE) / 2 - 1).dp
    Box(
        modifier = Modifier
            .size(PIP_SIZE.dp)
            .clip(CircleShape)
            .background(if (clicked) SpColor.Primary else SpColor.SurfaceElevated)
            .border(1.dp, if (active) SpColor.Primary else SpColor.OnBackgroundTertiary, CircleShape)
            .testTag("schematic_${position.name}")
            .semantics {
                contentDescription = position.displayName
                stateDescription = if (active) "Active" else "Inactive"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .offset(x = travel * dx.coerceIn(-1f, 1f), y = travel * dy.coerceIn(-1f, 1f))
                .size(DOT_SIZE.dp)
                .clip(CircleShape)
                .background(if (clicked) SpColor.OnPrimary else SpColor.OnBackgroundTertiary),
        )
    }
}

private const val PIP_SIZE = 34
private const val DOT_SIZE = 12

/** Short glyph/label for the schematic pip — positional, brand-neutral. */
private fun schematicLabel(position: GamepadPosition): String = when (position) {
    GamepadPosition.DPAD_UP -> "↑"
    GamepadPosition.DPAD_DOWN -> "↓"
    GamepadPosition.DPAD_LEFT -> "←"
    GamepadPosition.DPAD_RIGHT -> "→"
    GamepadPosition.NORTH -> "▲"
    GamepadPosition.SOUTH -> "▼"
    GamepadPosition.WEST -> "◀"
    GamepadPosition.EAST -> "▶"
    GamepadPosition.L1 -> "L1"
    GamepadPosition.R1 -> "R1"
    GamepadPosition.L2 -> "L2"
    GamepadPosition.R2 -> "R2"
    GamepadPosition.L3 -> "L3"
    GamepadPosition.R3 -> "R3"
    GamepadPosition.START -> "ST"
    GamepadPosition.SELECT -> "SE"
}
