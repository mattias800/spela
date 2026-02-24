package com.spela.player.presentation.ui.components.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
fun StarRatingRow(
    currentRating: Int?,
    averageRating: Double,
    ratingCount: Long,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 32.dp,
) {
    Column(modifier = modifier) {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(SpSpacing.Small),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics {
                contentDescription = if (currentRating != null) {
                    "Your rating: $currentRating out of 5 stars"
                } else {
                    "Tap to rate this game"
                }
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall)) {
                for (star in 1..5) {
                    val isFilled = currentRating != null && star <= currentRating
                    Icon(
                        imageVector = if (isFilled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "$star star",
                        tint = if (isFilled) SpColor.Warning else SpColor.OnBackgroundTertiary,
                        modifier = Modifier
                            .size(starSize)
                            .semantics { role = Role.Button }
                            .clickable { onRate(star) },
                    )
                }
            }

            if (ratingCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = "%.1f".format(averageRating),
                        style = SpTypography.TitleLarge,
                        color = SpColor.OnBackground,
                    )
                    Text(
                        text = "($ratingCount)",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }
            }
        }
    }
}

@Composable
fun CompactStarRating(
    averageRating: Double,
    ratingCount: Long,
    modifier: Modifier = Modifier,
) {
    if (ratingCount == 0L) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = SpColor.Warning,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = "%.1f".format(averageRating),
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundSecondary,
        )
    }
}
