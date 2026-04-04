package com.spela.player.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

/** Default aspect ratio for carousel cards when the game doesn't specify one. */
private const val CAROUSEL_DEFAULT_ASPECT_RATIO = 0.75f

/**
 * CONTENT component — defines how a game looks on a card.
 *
 * Layer 2 in the component hierarchy (Design → Content → Role).
 * Composes [SpCard] + [SpCoverArt] into a fixed layout:
 * cover art → title → subtitle → rating → variant count → favorite/play later indicators.
 *
 * Does NOT accept a modifier parameter — the layout is strict.
 * Sizing modes (mutually exclusive):
 * - Default: fixed [width], fixed [coverAspectRatio] — for carousels with uniform cards
 * - Grid: [fillWidth] = true, [coverAspectRatio] = null — fills parent cell, dynamic height
 * - Carousel (uncropped): [coverHeight] set, [coverAspectRatio] = null — fixed cover height, dynamic width
 *
 * Role components ([SpGridGameCard], [SpCarouselGameCard]) should
 * delegate to this — screens never use SpGameCard directly.
 */
@Composable
fun SpGameCard(
    title: String,
    subtitle: String,
    coverUrl: String?,
    onClick: () -> Unit,
    coverAspectRatio: Float? = 0.75f,
    rating: Double = 0.0,
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    width: Dp = SpSpacing.CoverMediumWidth,
    fillWidth: Boolean = false,
    coverHeight: Dp? = null,
    variantCount: Int = 0,
    testTag: String? = null,
    coverBadge: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // In carousel mode, compute width from the cover height and actual image ratio.
    // Start with a default ratio (0.75) and update when the image loads.
    var resolvedAspectRatio by remember { mutableFloatStateOf(coverAspectRatio ?: CAROUSEL_DEFAULT_ASPECT_RATIO) }
    val cardWidth = when {
        fillWidth -> null
        coverHeight != null -> coverHeight * resolvedAspectRatio
        else -> width
    }
    val sizeModifier = if (cardWidth != null) modifier.width(cardWidth) else modifier.fillMaxWidth()
    SpCard(
        modifier = sizeModifier
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
            Box {
                SpCoverArt(
                    imageUrl = coverUrl,
                    contentDescription = "$title cover art",
                    modifier = if (coverHeight != null) Modifier.height(coverHeight) else Modifier.fillMaxWidth(),
                    aspectRatio = if (coverHeight != null) null else coverAspectRatio,
                    onAspectRatioResolved = if (coverHeight != null) { ratio ->
                        resolvedAspectRatio = ratio
                    } else null,
                )
                if (coverBadge != null) {
                    Box(modifier = Modifier.align(Alignment.BottomStart).padding(SpSpacing.XSmall)) {
                        coverBadge()
                    }
                }
            }
            Column(
                modifier = Modifier.padding(SpSpacing.Small),
            ) {
                // Title
                Text(
                    text = title,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Subtitle (console name)
                if (subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(SpSpacing.Small))
                    Text(
                        text = subtitle,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

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

                // Variant count
                if (variantCount > 1) {
                    Spacer(Modifier.height(SpSpacing.XSmall))
                    Text(
                        text = "$variantCount versions",
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                    )
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
