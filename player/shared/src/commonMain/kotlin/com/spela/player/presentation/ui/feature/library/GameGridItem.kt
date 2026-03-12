package com.spela.player.presentation.ui.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun GameGridItem(
    game: Game,
    onClick: () -> Unit,
    onRequestScrape: ((Game) -> Unit)? = null,
) {
    // Trigger scrape for games with no cover art that haven't been scraped yet
    if (onRequestScrape != null && game.coverUrl == null && game.scrapeAttempts == 0) {
        LaunchedEffect(game.id) {
            onRequestScrape(game)
        }
    }

    SpCard(
        onClick = onClick,
        onGradient = true,
        modifier = Modifier.semantics {
            contentDescription = "${game.title}${game.genre?.let { ", $it" } ?: ""}${if (game.isFavorite) ", favorited" else ""}"
            role = Role.Button
        },
    ) {
        Column {
            // Cover art with favorite badge overlay and scraping shimmer
            Box {
                SpCoverArt(
                    imageUrl = game.coverUrl,
                    contentDescription = "${game.title} cover art",
                    modifier = Modifier.fillMaxWidth(),
                    aspectRatio = game.coverAspectRatio,
                    isLoading = game.coverUrl == null && game.scrapeAttempts == 0,
                )
                if (game.isFavorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(SpSpacing.XSmall)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Favorite,
                            contentDescription = "Favorited",
                            tint = SpColor.Favorite,
                            modifier = Modifier.size(SpSpacing.IconXSmall),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(
                    horizontal = SpSpacing.Small,
                    vertical = SpSpacing.Small,
                ),
            ) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.genre != null) {
                    Text(
                        text = game.genre,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )
                }
                // Star rating — only shown when there are community ratings
                if (game.averageRating >= 1.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                        modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(SpSpacing.IconXSmall),
                        )
                        Text(
                            text = "%.1f".format(game.averageRating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
                // Variant count badge
                if (game.variantCount > 1) {
                    Text(
                        text = "${game.variantCount} versions",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        modifier = Modifier.padding(top = SpSpacing.XXSmall),
                    )
                }
            }
        }
    }
}
