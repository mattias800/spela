package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ShowcaseAchievement
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

/**
 * CONTENT component -- large achievement card for profile showcase.
 *
 * Shows a prominent achievement badge, title, game title, points, and rarity chip.
 * Fixed width for use in horizontal scrolling LazyRow.
 */
@Composable
fun SpAchievementShowcaseCard(
    achievement: ShowcaseAchievement,
    modifier: Modifier = Modifier,
) {
    SpCard(modifier = modifier.width(160.dp)) {
        Column(
            modifier = Modifier.padding(SpSpacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
        ) {
            SpAchievementBadge(
                badgeUrl = achievement.badgeUrl,
                rarityPercent = achievement.rarityPercent,
                size = 64.dp,
            )

            Spacer(Modifier.height(SpSpacing.XSmall))

            Text(
                text = achievement.title,
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Text(
                text = achievement.gameTitle,
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "${achievement.points} pts",
                style = SpTypography.LabelSmall,
                color = SpColor.OnBackgroundSecondary,
                textAlign = TextAlign.Center,
            )

            if (achievement.rarityPercent > 0) {
                SpRarityChip(rarityPercent = achievement.rarityPercent)
            }
        }
    }
}
