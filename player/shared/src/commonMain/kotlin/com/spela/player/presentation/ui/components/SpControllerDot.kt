package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor

/**
 * A single controller status dot.
 *
 * Three visual states:
 * - **Disconnected** (`connected = false`): hollow ring, dim
 * - **Connected idle** (`connected = true, active = false`): solid green, subtle glow
 * - **Active input** (`connected = true, active = true`): white, bright glow — fades back to green
 *
 * This is a design-layer component. No labels, no outer spacing.
 */
@Composable
fun SpControllerDot(
    connected: Boolean,
    active: Boolean,
    port: Int,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val connectedColor = Color(0xFF4ADE80) // green-400
    val activeColor = Color.White
    val disconnectedColor = SpColor.OnBackgroundTertiary.copy(alpha = 0.3f)
    val disconnectedBorderColor = SpColor.OnBackgroundTertiary.copy(alpha = 0.5f)

    val dotColor by animateColorAsState(
        targetValue = when {
            active -> activeColor
            connected -> connectedColor
            else -> disconnectedColor
        },
        animationSpec = tween(durationMillis = if (active) 50 else 300),
        label = "dotColor",
    )

    val glowColor = when {
        active -> Color.White.copy(alpha = 0.4f)
        connected -> connectedColor.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val description = when {
        active -> "Player ${port + 1} active"
        connected -> "Player ${port + 1} connected"
        else -> "Player ${port + 1} not connected"
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (!connected) {
                    // Hollow ring for disconnected
                    Modifier
                        .clip(CircleShape)
                        .background(Color.Transparent)
                        .drawBehind {
                            drawCircle(
                                color = disconnectedBorderColor,
                                radius = this.size.minDimension / 2,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                ),
                            )
                        }
                } else {
                    // Solid circle with glow for connected/active
                    Modifier
                        .drawBehind {
                            // Glow
                            drawCircle(
                                color = glowColor,
                                radius = this.size.minDimension * 0.9f,
                            )
                        }
                        .clip(CircleShape)
                        .background(dotColor)
                }
            )
            .semantics { contentDescription = description },
    )
}
