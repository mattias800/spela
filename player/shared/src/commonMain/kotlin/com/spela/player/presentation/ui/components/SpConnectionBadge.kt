package com.spela.player.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
}

@Composable
fun SpConnectionBadge(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val dotColor by animateColorAsState(
        targetValue = when (status) {
            ConnectionStatus.CONNECTED -> SpColor.Success
            ConnectionStatus.CONNECTING -> SpColor.Warning
            ConnectionStatus.DISCONNECTED -> SpColor.Error
        },
        label = "connectionDotColor",
    )

    val statusText = label ?: when (status) {
        ConnectionStatus.CONNECTED -> "Connected"
        ConnectionStatus.CONNECTING -> "Connecting..."
        ConnectionStatus.DISCONNECTED -> "Disconnected"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SpColor.SurfaceVariant)
            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall)
            .semantics { contentDescription = "Connection status: $statusText" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
    ) {
        if (status == ConnectionStatus.CONNECTED) {
            PulsingDot(color = dotColor)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        }
        Text(
            text = statusText,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDot")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulsingDotAlpha",
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** Latency below this threshold is considered good (green). */
const val LATENCY_GOOD_MS = 50

/** Latency below this threshold is considered moderate (yellow); above is bad (red). */
const val LATENCY_MODERATE_MS = 150

/**
 * Returns the appropriate ping color based on latency thresholds:
 * - Green: < [LATENCY_GOOD_MS]
 * - Yellow: [LATENCY_GOOD_MS]-[LATENCY_MODERATE_MS]
 * - Red: > [LATENCY_MODERATE_MS]
 */
fun pingColor(latencyMs: Int): Color = when {
    latencyMs < LATENCY_GOOD_MS -> SpColor.Success
    latencyMs < LATENCY_MODERATE_MS -> SpColor.Warning
    else -> SpColor.Error
}
