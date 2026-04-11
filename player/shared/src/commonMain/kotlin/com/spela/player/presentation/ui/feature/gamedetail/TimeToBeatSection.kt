package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun TimeToBeatSection(
    game: Game,
    modifier: Modifier = Modifier,
) {
    val hastily = game.timeToBeatHastily
    val normally = game.timeToBeatNormally
    val completely = game.timeToBeatCompletely

    // Only render if at least one time value > 0
    if (hastily <= 0 && normally <= 0 && completely <= 0) return

    val maxSeconds = maxOf(hastily, normally, completely).coerceAtLeast(1)

    // Trigger animation after composition
    var animationStarted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationStarted = true }

    SpTitledSection(
        title = "How Long to Beat",
        icon = Icons.Filled.AccessTime,
        modifier = modifier.testTag("time_to_beat_section"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SpSpacing.Medium)) {
            if (hastily > 0) {
                TimeToBeatRow(
                    icon = Icons.Filled.FlashOn,
                    label = "Main Story",
                    seconds = hastily,
                    maxSeconds = maxSeconds,
                    color = Color.White.copy(alpha = 0.85f),
                    animationStarted = animationStarted,
                )
            }
            if (normally > 0) {
                TimeToBeatRow(
                    icon = Icons.Filled.SportsEsports,
                    label = "Main + Extras",
                    seconds = normally,
                    maxSeconds = maxSeconds,
                    color = Color.White.copy(alpha = 0.65f),
                    animationStarted = animationStarted,
                )
            }
            if (completely > 0) {
                TimeToBeatRow(
                    icon = Icons.Filled.EmojiEvents,
                    label = "Completionist",
                    seconds = completely,
                    maxSeconds = maxSeconds,
                    color = Color.White.copy(alpha = 0.50f),
                    animationStarted = animationStarted,
                )
            }
        }
    }
}

@Composable
private fun TimeToBeatRow(
    icon: ImageVector,
    label: String,
    seconds: Int,
    maxSeconds: Int,
    color: Color,
    animationStarted: Boolean,
) {
    val targetFraction = seconds.toFloat() / maxSeconds
    val animatedFraction by animateFloatAsState(
        targetValue = if (animationStarted) targetFraction else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "timeToBeatBar",
    )

    val hours = formatTimeToBeat(seconds)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(SpSpacing.IconDefault),
                )
                Text(
                    text = label,
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnBackgroundSecondary,
                )
            }
            Text(
                text = hours,
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
            )
        }
        Spacer(Modifier.height(SpSpacing.XSmall))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

/**
 * Formats time-to-beat seconds into a human-readable hours string.
 * Examples: 7200 -> "2h", 5400 -> "1.5h", 36000 -> "10h"
 */
internal fun formatTimeToBeat(seconds: Int): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600.0
    return if (hours == hours.toLong().toDouble()) {
        "${hours.toLong()}h"
    } else {
        "${"%.1f".format(hours)}h"
    }
}
