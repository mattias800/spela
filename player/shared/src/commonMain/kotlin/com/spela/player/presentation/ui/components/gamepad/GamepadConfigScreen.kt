package com.spela.player.presentation.ui.components.gamepad

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpSecondaryButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.domain.model.ControllerStyle
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GamepadConfigState
import com.spela.player.presentation.viewmodel.PortAssignmentUi

/**
 * Shows connected controllers with per-player configuration and live activity indicator.
 * Displays ports 1-4 by default (expandable).
 */
@Composable
fun GamepadConfigScreen(
    state: GamepadConfigState,
    onConfigurePort: (Int) -> Unit,
    onSwapUp: ((Int) -> Unit)? = null,
    onSwapDown: ((Int) -> Unit)? = null,
    onSetStyleOverride: ((Int, ControllerStyle?) -> Unit)? = null,
    /** Shows the per-port "Configure" (keyboard key-mapping) button. Hidden on
     *  Android, where gamepad input is positional and there's no keyboard. */
    showConfigureButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Port whose controller-type picker is open, or null when closed.
    var stylePickerPort by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(SpSpacing.Default),
    ) {
        Text(
            text = "Controllers",
            style = SpTypography.HeadlineSmall,
            color = SpColor.OnBackground,
            modifier = Modifier.semantics { contentDescription = "Controllers heading" },
        )

        Spacer(Modifier.height(SpSpacing.Medium))

        for (port in 0 until 4) {
            val assignment = state.portAssignments.find { it.port == port }
            ControllerRow(
                port = port,
                assignment = assignment,
                onConfigure = if (showConfigureButton) {
                    { onConfigurePort(port) }
                } else null,
                onSwapUp = if (port > 0 && assignment != null) {
                    { onSwapUp?.invoke(port) }
                } else null,
                onSwapDown = if (port < 3 && assignment != null) {
                    { onSwapDown?.invoke(port) }
                } else null,
                onPickStyle = if (assignment != null && onSetStyleOverride != null) {
                    { stylePickerPort = port }
                } else null,
            )
            if (port < 3) {
                Spacer(Modifier.height(SpSpacing.Small))
            }
        }
    }

    val pickerPort = stylePickerPort
    val pickerAssignment = pickerPort?.let { p -> state.portAssignments.find { it.port == p } }
    if (pickerPort != null && pickerAssignment != null && onSetStyleOverride != null) {
        ControllerStylePickerDialog(
            detectedStyle = pickerAssignment.detectedStyle,
            currentOverride = pickerAssignment.styleOverride,
            onSelect = { style ->
                onSetStyleOverride(pickerPort, style)
                stylePickerPort = null
            },
            onDismiss = { stylePickerPort = null },
        )
    }
}

@Composable
private fun ControllerRow(
    port: Int,
    assignment: PortAssignmentUi?,
    onConfigure: (() -> Unit)?,
    onSwapUp: (() -> Unit)?,
    onSwapDown: (() -> Unit)?,
    onPickStyle: (() -> Unit)? = null,
) {
    // Show the effective controller identity (e.g. "Xbox Controller"). For an
    // unrecognized pad fall back to the raw OS device name. (#1334)
    val identity = assignment?.let {
        if (it.style == ControllerStyle.Generic) it.deviceName else it.style.displayName
    }
    // Compact label for the type-override affordance: "Auto" when deferring to
    // detection, else the chosen style's short name (matches the picker wording,
    // e.g. Generic → "Gamepad").
    val styleLabel = assignment?.styleOverride?.shortLabel ?: "Auto"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(if (assignment != null) SpColor.SurfaceElevated else SpColor.SurfaceElevated.copy(alpha = 0.4f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .semantics {
                contentDescription = if (assignment != null) {
                    "Player ${port + 1}: $identity" +
                        if (assignment.isActive) ", active" else ""
                } else {
                    "Player ${port + 1}: No controller"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Player label
        Text(
            text = "P${port + 1}",
            style = SpTypography.TitleMedium,
            color = if (assignment != null) SpColor.Primary else SpColor.OnBackgroundTertiary,
        )

        Spacer(Modifier.width(SpSpacing.Medium))

        // Controller identity (detected/overridden style, else raw device name)
        // plus the per-controller type-override affordance.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = identity ?: "No controller",
                style = SpTypography.BodyMedium,
                color = if (assignment != null) SpColor.OnCard else SpColor.OnBackgroundTertiary,
            )
            if (assignment != null && onPickStyle != null) {
                SpButton(
                    text = "Type: $styleLabel",
                    onClick = onPickStyle,
                    style = SpButtonStyle.Ghost,
                    modifier = Modifier.semantics {
                        contentDescription = "Player ${port + 1} controller type"
                    },
                )
            }
        }

        // Activity indicator
        if (assignment != null) {
            ActivityIndicator(isActive = assignment.isActive)
            Spacer(Modifier.width(SpSpacing.Medium))
        }

        // Swap buttons
        if (onSwapUp != null || onSwapDown != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onSwapUp != null) {
                    SpButton(
                        text = "\u25B2",
                        onClick = onSwapUp,
                        style = SpButtonStyle.Ghost,
                    )
                }
                if (onSwapDown != null) {
                    SpButton(
                        text = "\u25BC",
                        onClick = onSwapDown,
                        style = SpButtonStyle.Ghost,
                    )
                }
            }
            Spacer(Modifier.width(SpSpacing.Small))
        }

        // Configure (keyboard key-mapping) button — omitted when onConfigure is null.
        if (assignment != null && onConfigure != null) {
            SpSecondaryButton(
                text = "Configure",
                onClick = onConfigure,
            )
        }
    }
}

@Composable
private fun ActivityIndicator(isActive: Boolean) {
    val targetColor = if (isActive) Color(0xFF4CAF50) else SpColor.OnBackgroundTertiary.copy(alpha = 0.3f)
    val color by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 150),
    )

    val animationsEnabled = com.spela.player.presentation.ui.components.LocalAnimationsEnabled.current
    val pulseAlpha = if (isActive && animationsEnabled) {
        val transition = rememberInfiniteTransition()
        val alpha by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        alpha
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(pulseAlpha)
            .clip(CircleShape)
            .background(color)
            .semantics {
                contentDescription = if (isActive) "Activity indicator active" else "Activity indicator inactive"
            },
    )
}
