package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GameRating
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

@Composable
internal fun GameReviewsSection(
    reviews: List<GameRating>,
    reviewsTotal: Long,
    reviewsPage: Int,
    isLoading: Boolean,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpTitledSection(
        title = "Reviews",
        modifier = modifier.testTag("reviews_section"),
        titleTrailing = if (reviewsTotal > 0) {
            {
                Text(
                    text = "($reviewsTotal)",
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
        } else null,
    ) {
        if (reviews.isEmpty() && !isLoading) {
            Text(
                text = "No reviews yet. Rate this game and add a review to share your thoughts.",
                style = SpTypography.BodyMedium,
                color = SpColor.OnBackgroundTertiary,
                modifier = Modifier.testTag("reviews_empty"),
            )
        } else {
            reviews.forEach { rating ->
                ReviewItem(
                    rating = rating,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.XXSmall),
                )
            }

            val hasMore = reviewsPage * 10 < reviewsTotal
            if (hasMore) {
                Spacer(Modifier.height(SpSpacing.Medium))
                SpButton(
                    text = if (isLoading) "Loading..." else "Load More",
                    onClick = onLoadMore,
                    style = SpButtonStyle.Secondary,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth().testTag("load_more_reviews"),
                )
            }
        }
    }
}

@Composable
private fun ReviewItem(
    rating: GameRating,
    modifier: Modifier = Modifier,
) {
    SpCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.Top,
        ) {
            SpAvatar(
                username = rating.username,
                avatarUrl = rating.avatarUrl,
                size = 32.dp,
            )

            Spacer(Modifier.width(SpSpacing.Medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Small),
                ) {
                    Text(
                        text = rating.username,
                        style = SpTypography.TitleSmall,
                        color = SpColor.OnCard,
                    )

                    // Star rating
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "${rating.rating} out of 5 stars"
                        },
                    ) {
                        for (star in 1..5) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (star <= rating.rating) SpColor.Warning else SpColor.OnBackgroundTertiary.copy(alpha = 0.3f),
                                modifier = Modifier.height(14.dp).width(14.dp),
                            )
                        }
                    }

                    Text(
                        text = formatRelativeTime(rating.createdAt),
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
                }

                if (rating.review.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = rating.review,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                    )
                }
            }
        }
    }
}

private fun formatRelativeTime(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val now = Clock.System.now()
        val duration: Duration = now - instant
        val seconds = duration.inWholeSeconds
        when {
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            seconds < 604800 -> "${seconds / 86400}d ago"
            else -> "${seconds / 604800}w ago"
        }
    } catch (_: Exception) {
        ""
    }
}
