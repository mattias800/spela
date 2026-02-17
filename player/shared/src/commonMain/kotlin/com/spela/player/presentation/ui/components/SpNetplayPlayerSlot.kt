package com.spela.player.presentation.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun SpNetplayPlayerSlot(
    username: String?,
    avatarUrl: String?,
    isHost: Boolean,
    latencyMs: Int?,
    modifier: Modifier = Modifier,
) {
    val isFilled = username != null

    val borderModifier = if (!isFilled) {
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
            .border(1.dp, SpColor.OnBackgroundTertiary.copy(alpha = 0.3f), RoundedCornerShape(SpSpacing.CardCornerRadius))
    } else {
        modifier.fillMaxWidth()
    }

    SpCard(
        modifier = borderModifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default)
                .semantics {
                    contentDescription = if (isFilled) {
                        val hostLabel = if (isHost) " (host)" else ""
                        val pingLabel = latencyMs?.let { ", ping: $it milliseconds" } ?: ""
                        "Player: $username$hostLabel$pingLabel"
                    } else {
                        "Waiting for player to join"
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) SpColor.Primary.copy(alpha = 0.2f) else SpColor.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isFilled) SpColor.Primary else SpColor.OnBackgroundTertiary,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(Modifier.width(SpSpacing.Medium))

            if (isFilled) {
                Text(
                    text = username!!,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                    modifier = Modifier.weight(1f),
                )

                if (isHost) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Host",
                        tint = SpColor.Warning,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(SpSpacing.Small))
                }

                if (latencyMs != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(pingColor(latencyMs)),
                        )
                        Text(
                            text = "${latencyMs}ms",
                            style = SpTypography.LabelSmall,
                            color = pingColor(latencyMs),
                        )
                    }
                }
            } else {
                WaitingForPlayerText(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WaitingForPlayerText(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "waitingPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "waitingAlpha",
    )

    Text(
        text = "Waiting for player...",
        style = SpTypography.BodyMedium,
        color = SpColor.OnBackgroundTertiary.copy(alpha = alpha),
        modifier = modifier,
    )
}
