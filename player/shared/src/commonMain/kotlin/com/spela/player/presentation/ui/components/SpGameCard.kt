package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

/**
 * Standard game card — the ONE component for displaying a game with
 * cover art, title, subtitle, and optional rating.
 *
 * This is a ROLE component. It defines WHAT is shown (a game), not
 * HOW it looks (that's SpCard + SpCoverArt). All game cards across
 * the app must use this component for visual consistency.
 *
 * Does NOT accept a modifier parameter — its layout is fixed.
 * The parent controls sizing via the width parameter.
 */
@Composable
fun SpGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    coverAspectRatio: Float = 0.75f,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    width: Dp = SpSpacing.CoverMediumWidth,
    testTag: String? = null,
) {
    SpCard(
        modifier = Modifier
            .width(width)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .semantics {
                val extras = buildString {
                    if (isFavorite) append(", favorited")
                    if (isInPlayLater) append(", in play later")
                }
                contentDescription = "$title, $subtitle$extras"
                role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Column {
            SpCoverArt(
                imageUrl = coverUrl,
                contentDescription = "$title cover art",
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = coverAspectRatio,
            )
            Column(
                modifier = Modifier.padding(SpSpacing.Small),
            ) {
                // Title
                Text(
                    text = title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                // Subtitle (console name)
                Spacer(Modifier.height(SpSpacing.Small))
                Text(
                    text = subtitle,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Rating (third line)
                if (rating > 0) {
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(SpSpacing.IconXSmall),
                        )
                        Text(
                            text = formatRating(rating),
                            style = SpTypography.BodySmall,
                            color = SpColor.OnBackgroundSecondary,
                        )
                    }
                }

                // Favorite / Play Later indicators
                if (isFavorite || isInPlayLater) {
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                    ) {
                        if (isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favorited",
                                tint = SpColor.Favorite,
                                modifier = Modifier.size(SpSpacing.IconXSmall),
                            )
                        }
                        if (isInPlayLater) {
                            Icon(
                                imageVector = Icons.Filled.WatchLater,
                                contentDescription = "In Play Later",
                                tint = SpColor.OnBackgroundTertiary,
                                modifier = Modifier.size(SpSpacing.IconXSmall),
                            )
                        }
                    }
                }
            }
        }
    }
}
