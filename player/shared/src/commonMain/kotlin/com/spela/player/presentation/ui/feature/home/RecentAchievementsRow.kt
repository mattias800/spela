package com.spela.player.presentation.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.RecentAchievement
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun RecentAchievementsRow(
    achievements: List<RecentAchievement>,
    modifier: Modifier = Modifier,
) {
    SpCarousel(
        modifier = modifier,
    ) {
        items(achievements.take(5), key = { it.achievementRaId }) { achievement ->
            AchievementCard(achievement = achievement)
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: RecentAchievement,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(SpSpacing.RadiusLarge))
            .background(SpColor.SurfaceElevated)
            .semantics {
                contentDescription = "${achievement.title}, ${achievement.points} points, from ${achievement.gameTitle}"
            },
    ) {
        Column(
            modifier = Modifier.padding(SpSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Badge image
            if (achievement.badgeUrl != null) {
                SubcomposeAsyncImage(
                    model = achievement.badgeUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(SpSpacing.RadiusMedium)),
                    contentScale = ContentScale.Crop,
                    loading = {
                        SpShimmer(modifier = Modifier.size(48.dp))
                    },
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(SpSpacing.RadiusMedium))
                        .background(SpColor.SurfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = SpColor.Gold,
                        modifier = Modifier.size(SpSpacing.IconDefault),
                    )
                }
            }

            Spacer(Modifier.height(SpSpacing.Small))

            // Achievement title
            Text(
                text = achievement.title,
                style = SpTypography.TitleSmall,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Game title
            Text(
                text = achievement.gameTitle,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(SpSpacing.XSmall))

            // Points badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${achievement.points} pts",
                    style = SpTypography.LabelSmall,
                    color = SpColor.Gold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(SpSpacing.RadiusSmall))
                        .background(SpColor.Gold.copy(alpha = 0.15f))
                        .padding(horizontal = SpSpacing.Small, vertical = SpSpacing.XXSmall),
                )
            }
        }
    }
}
