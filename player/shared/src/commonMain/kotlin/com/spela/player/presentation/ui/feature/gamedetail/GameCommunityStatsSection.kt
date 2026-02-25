package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GameStats
import com.spela.player.domain.model.TopPlayer
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpInnerCard
import com.spela.player.presentation.ui.components.SpTitledSection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

@Composable
internal fun GameCommunityStatsSection(
    stats: GameStats?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isLoading || stats == null || stats.totalPlayers == 0) return

    SpTitledSection(
        title = "Play Activity",
        icon = Icons.AutoMirrored.Outlined.TrendingUp,
        modifier = modifier.testTag("community_stats_section"),
    ) {
        // Stat cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            StatMiniCard(
                value = "${stats.totalPlayers}",
                label = if (stats.totalPlayers == 1) "player" else "players",
                modifier = Modifier.weight(1f),
            )
            StatMiniCard(
                value = formatPlayTime(stats.totalPlayTime),
                label = "total time",
                modifier = Modifier.weight(1f),
            )
            StatMiniCard(
                value = formatPlayTime(stats.averagePlayTime),
                label = "avg time",
                modifier = Modifier.weight(1f),
            )
        }

        // Top Players leaderboard
        if (stats.topPlayers.isNotEmpty()) {
            Spacer(Modifier.height(SpSpacing.Large))
            Text(
                text = "TOP PLAYERS",
                style = SpTypography.LabelMedium,
                color = SpColor.OnBackgroundSecondary,
            )
            Spacer(Modifier.height(SpSpacing.Small))

            val maxPlayTime = stats.topPlayers.maxOf { it.playTime }.coerceAtLeast(1)
            stats.topPlayers.forEachIndexed { index, player ->
                TopPlayerRow(
                    rank = index + 1,
                    player = player,
                    maxPlayTime = maxPlayTime,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XXSmall),
                )
            }
        }
    }
}

@Composable
private fun StatMiniCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    SpInnerCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(SpSpacing.Default),
        ) {
            Text(
                text = value,
                style = SpTypography.TitleLarge,
                color = SpColor.Accent,
            )
            Text(
                text = label,
                style = SpTypography.BodySmall,
                color = SpColor.OnBackgroundTertiary,
            )
        }
    }
}

@Composable
private fun TopPlayerRow(
    rank: Int,
    player: TopPlayer,
    maxPlayTime: Long,
    modifier: Modifier = Modifier,
) {
    val rankColor = when (rank) {
        1 -> SpColor.Gold
        2 -> SpColor.Silver
        3 -> SpColor.Bronze
        else -> SpColor.OnBackgroundTertiary
    }

    SpInnerCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$rank",
                style = SpTypography.LabelLarge,
                color = rankColor,
                modifier = Modifier.width(24.dp),
            )

            SpAvatar(
                username = player.username,
                avatarUrl = player.avatarUrl,
                size = 32.dp,
            )

            Spacer(Modifier.width(SpSpacing.Small))

            Text(
                text = player.username,
                style = SpTypography.BodyMedium,
                color = SpColor.OnCard,
                modifier = Modifier.width(80.dp),
            )

            Spacer(Modifier.width(SpSpacing.Small))

            // Progress bar
            val barFraction = if (maxPlayTime > 0) player.playTime.toFloat() / maxPlayTime else 0f
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SpColor.Accent.copy(alpha = 0.15f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barFraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SpColor.Accent),
                )
            }

            Spacer(Modifier.width(SpSpacing.Small))

            Text(
                text = formatPlayTime(player.playTime),
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.semantics {
                    contentDescription = "${player.username} played ${formatPlayTime(player.playTime)}"
                },
            )
        }
    }
}
