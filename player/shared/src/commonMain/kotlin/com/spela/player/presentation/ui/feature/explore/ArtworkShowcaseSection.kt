package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.ArtworkItem
import androidx.compose.ui.focus.focusRequester
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val ArtworkCardWidth = 280.dp
private const val ARTWORK_ASPECT_RATIO = 16f / 9f

@Composable
fun ArtworkShowcaseSection(
    artworks: List<ArtworkItem>,
    onGameSelected: (gameId: String) -> Unit,
    onGallerySelected: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("artwork_showcase"),
    ) {
        SpCarousel(
            itemCount = artworks.size,
            modifier = Modifier.testTag("artwork_showcase_row"),
        ) { index, focusRequester ->
            Box(modifier = Modifier.focusRequester(focusRequester)) {
                ArtworkCard(
                    artwork = artworks[index],
                    onClick = { onGameSelected(artworks[index].gameId) },
                )
            }
        }
    }
}

@Composable
private fun ArtworkCard(
    artwork: ArtworkItem,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(SpSpacing.CardCornerRadius)

    SpCard(
        modifier = Modifier
            .width(ArtworkCardWidth)
            .testTag("artwork_card_${artwork.gameId}")
            .semantics {
                contentDescription = "${artwork.gameTitle}, ${artwork.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
        ) {
            // Artwork image
            SubcomposeAsyncImage(
                model = artwork.url,
                contentDescription = "${artwork.gameTitle} artwork",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ARTWORK_ASPECT_RATIO),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ARTWORK_ASPECT_RATIO)
                            .background(SpColor.SurfaceBright.copy(alpha = 0.25f)),
                    )
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ARTWORK_ASPECT_RATIO)
                            .background(
                                Brush.linearGradient(
                                    listOf(SpColor.SurfaceVariant, SpColor.SurfaceElevated),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = artwork.gameTitle.take(2).uppercase(),
                            style = SpTypography.DisplaySmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                },
            )

            // Gradient overlay at bottom for text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ARTWORK_ASPECT_RATIO)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SpSpacing.Medium),
                ) {
                    Text(
                        text = artwork.gameTitle,
                        style = SpTypography.TitleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("artwork_title_${artwork.gameId}"),
                    )
                    Text(
                        text = artwork.consoleAbbreviation.uppercase(),
                        style = SpTypography.LabelSmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun ArtworkShowcaseSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 3,
) {
    LazyRow(
        modifier = modifier.testTag("artwork_showcase_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            SpShimmer(
                modifier = Modifier
                    .width(ArtworkCardWidth)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = ArtworkCardWidth,
                height = (ArtworkCardWidth.value / ARTWORK_ASPECT_RATIO).dp,
            )
        }
    }
}
