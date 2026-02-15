package com.spela.player.presentation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlinx.coroutines.delay

@Composable
fun SpNetplayHud(
    peerUsername: String,
    latencyMs: Int,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fade to 0.3 opacity after 5s of stable connection
    var stable by remember { mutableStateOf(false) }
    LaunchedEffect(latencyMs) {
        stable = false
        delay(5000)
        stable = true
    }

    val targetAlpha = if (stable && latencyMs < 200) 0.3f else 1f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(1000),
        label = "hudAlpha",
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(),
        exit = fadeOut(),
    ) {
        Row(
            modifier = modifier
                .alpha(alpha)
                .clip(RoundedCornerShape(8.dp))
                .background(SpColor.Scrim)
                .clickable(onClick = onClick)
                .focusable()
                .semantics {
                    contentDescription = "$peerUsername, ping: $latencyMs milliseconds. Tap to open game menu."
                    role = Role.Button
                }
                .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        ) {
            // Colored ping dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(pingColor(latencyMs)),
            )

            Text(
                text = peerUsername,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackground,
            )

            Text(
                text = "${latencyMs}ms",
                style = SpTypography.LabelSmall,
                color = pingColor(latencyMs),
            )

            // Warning icon at > 200ms
            if (latencyMs > 200) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "High latency warning",
                    tint = SpColor.Warning,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
