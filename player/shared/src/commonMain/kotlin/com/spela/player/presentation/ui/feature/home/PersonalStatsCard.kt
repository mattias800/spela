package com.spela.player.presentation.ui.feature.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.UserStats
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

@Composable
internal fun PersonalStatsCard(
    stats: UserStats,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            StatMiniCard(
                icon = Icons.Filled.Timer,
                iconColor = SpColor.Accent,
                label = "Play Time",
                value = formatPlayTime(stats.totalPlayTime),
                modifier = Modifier.weight(1f),
            )
            StatMiniCard(
                icon = Icons.Filled.SportsEsports,
                iconColor = SpColor.Primary,
                label = "Games Played",
                value = "${stats.gamesPlayed}",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            StatMiniCard(
                icon = Icons.Filled.LocalFireDepartment,
                iconColor = SpColor.Warning,
                label = "Streak",
                value = "${stats.currentStreak} day${if (stats.currentStreak != 1) "s" else ""}",
                modifier = Modifier.weight(1f),
            )
            StatMiniCard(
                icon = Icons.Filled.EmojiEvents,
                iconColor = SpColor.Gold,
                label = "Best Streak",
                value = "${stats.longestStreak} day${if (stats.longestStreak != 1) "s" else ""}",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.SurfaceVariant)
            .padding(SpSpacing.Default)
            .semantics { contentDescription = "$label: $value" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(SpSpacing.IconLarge),
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column {
                Text(
                    text = value,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnBackground,
                )
                Text(
                    text = label,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        }
    }
}
