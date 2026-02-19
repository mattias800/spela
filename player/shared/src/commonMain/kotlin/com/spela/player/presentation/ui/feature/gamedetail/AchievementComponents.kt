package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.AchievementPlayerRanking
import com.spela.player.domain.model.AchievementProgress
import com.spela.player.domain.model.AchievementTimelineEntry
import com.spela.player.domain.model.GameAchievement
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

/**
 * A single achievement card used in the grid view.
 * Shows badge placeholder, title, description, points, and locked/unlocked state.
 */
@Composable
internal fun AchievementCard(
    achievement: GameAchievement,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (isUnlocked) 1f else 0.5f

    SpCard(modifier = modifier.alpha(alpha)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Badge placeholder (colored circle)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
                    .background(
                        if (isUnlocked) SpColor.Gold.copy(alpha = 0.2f)
                        else SpColor.SurfaceBright,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${achievement.points}",
                    style = SpTypography.LabelMedium,
                    color = if (isUnlocked) SpColor.Gold else SpColor.OnBackgroundTertiary,
                )
            }

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = achievement.title,
                    style = SpTypography.TitleMedium,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = achievement.description,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * A single timeline entry showing an unlocked achievement with timestamp.
 * Used in the timeline view, rendered as a vertical timeline with colored nodes.
 */
@Composable
internal fun TimelineEntryRow(
    entry: AchievementTimelineEntry,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Timeline node + line
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(20.dp)
                .fillMaxHeight(),
        ) {
            Spacer(Modifier.height(SpSpacing.Medium))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(SpColor.Primary),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(SpColor.Primary.copy(alpha = 0.3f)),
            )
        }

        Spacer(Modifier.width(SpSpacing.Small))

        // Content card
        SpCard(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = SpSpacing.XSmall),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SpSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Badge placeholder
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(SpSpacing.RadiusDefault))
                        .background(SpColor.Gold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${entry.points}",
                        style = SpTypography.LabelSmall,
                        color = SpColor.Gold,
                    )
                }

                Spacer(Modifier.width(SpSpacing.Small))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = SpTypography.TitleMedium,
                        color = SpColor.OnCard,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.points} pts",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                    entry.playTimeAtUnlock?.let { playTime ->
                        Text(
                            text = "after ${formatPlayTime(playTime)}",
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }

                if (entry.isHardcore) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                            .background(SpColor.Gold.copy(alpha = 0.15f))
                            .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall),
                    ) {
                        Text(
                            text = "HC",
                            style = SpTypography.LabelSmall,
                            color = SpColor.Gold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single row in the achievement leaderboard.
 * Shows rank with gold/silver/bronze styling, username, unlock count, and points.
 */
@Composable
internal fun LeaderboardRow(
    rank: Int,
    player: AchievementPlayerRanking,
    totalAchievements: Int,
    modifier: Modifier = Modifier,
) {
    val rankColor = when (rank) {
        1 -> SpColor.Gold
        2 -> SpColor.Silver
        3 -> SpColor.Bronze
        else -> SpColor.OnBackgroundTertiary
    }

    val completionPercent = if (totalAchievements > 0) {
        (player.unlockedCount * 100) / totalAchievements
    } else 0

    SpCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.Medium, vertical = SpSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank
            Text(
                text = "#$rank",
                style = SpTypography.LabelLarge,
                color = rankColor,
                modifier = Modifier.width(36.dp),
            )

            SpAvatar(
                username = player.username,
                avatarUrl = player.avatarUrl,
                size = 32.dp,
            )

            Spacer(Modifier.width(SpSpacing.Small))

            // Username + unlock info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.username,
                    style = SpTypography.BodyMedium,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${player.unlockedCount}/$totalAchievements unlocked",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }

            Spacer(Modifier.width(SpSpacing.Small))

            // Points + completion
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${player.earnedPoints} pts",
                    style = SpTypography.LabelMedium,
                    color = SpColor.OnCard,
                )
                Text(
                    text = "$completionPercent%",
                    style = SpTypography.LabelSmall,
                    color = if (completionPercent == 100) SpColor.Gold else SpColor.OnBackgroundTertiary,
                    modifier = Modifier.semantics {
                        contentDescription = "$completionPercent percent complete"
                    },
                )
            }
        }
    }
}
