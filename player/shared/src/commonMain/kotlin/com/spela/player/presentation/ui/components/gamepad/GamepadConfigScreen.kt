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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.theme.SpColor
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
    modifier: Modifier = Modifier,
) {
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
                onConfigure = { onConfigurePort(port) },
                onSwapUp = if (port > 0 && assignment != null) {
                    { onSwapUp?.invoke(port) }
                } else null,
                onSwapDown = if (port < 3 && assignment != null) {
                    { onSwapDown?.invoke(port) }
                } else null,
            )
            if (port < 3) {
                Spacer(Modifier.height(SpSpacing.Small))
            }
        }
    }
}

@Composable
private fun ControllerRow(
    port: Int,
    assignment: PortAssignmentUi?,
    onConfigure: () -> Unit,
    onSwapUp: (() -> Unit)?,
    onSwapDown: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
            .background(if (assignment != null) SpColor.SurfaceElevated else SpColor.SurfaceElevated.copy(alpha = 0.4f))
            .padding(horizontal = SpSpacing.Default, vertical = SpSpacing.Medium)
            .semantics {
                contentDescription = if (assignment != null) {
                    "Player ${port + 1}: ${assignment.deviceName}" +
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

        // Device name
        Text(
            text = assignment?.deviceName ?: "No controller",
            style = SpTypography.BodyMedium,
            color = if (assignment != null) SpColor.OnCard else SpColor.OnBackgroundTertiary,
            modifier = Modifier.weight(1f),
        )

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

        // Configure button
        if (assignment != null) {
            SpButton(
                text = "Configure",
                onClick = onConfigure,
                style = SpButtonStyle.Outlined,
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
