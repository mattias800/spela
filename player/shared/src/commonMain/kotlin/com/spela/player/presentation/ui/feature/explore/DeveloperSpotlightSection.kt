package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

@Composable
fun DeveloperSpotlightSection(
    spotlight: DeveloperSpotlight,
    onDeveloperSelected: (name: String) -> Unit,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("developer_spotlight"),
    ) {
        // Developer info header
        SpCard(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("developer_spotlight_card")
                .semantics {
                    contentDescription = "${spotlight.name}, ${spotlight.gameCount} games, rating ${formatRating(spotlight.avgRating)}"
                    role = Role.Button
                },
            onClick = { onDeveloperSelected(spotlight.name) },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SpColor.PrimaryDark,
                                Color(0xFF1A1A4E),
                                SpColor.AccentDark.copy(alpha = 0.6f),
                            ),
                        ),
                    ),
            ) {
                // Section title inside banner
                Text(
                    text = "Developer Spotlight",
                    style = SpTypography.LabelMedium,
                    color = SpColor.Accent.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(SpSpacing.XLarge),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(SpSpacing.XLarge),
                ) {
                    Text(
                        text = spotlight.name,
                        style = SpTypography.HeadlineMedium,
                        color = SpColor.OnBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                    ) {
                        Text(
                            text = "${spotlight.gameCount} games",
                            style = SpTypography.BodyMedium,
                            color = SpColor.OnBackgroundSecondary,
                        )
                        if (spotlight.avgRating > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = SpColor.Rating,
                                    modifier = Modifier.size(SpSpacing.IconSmall),
                                )
                                Text(
                                    text = formatRating(spotlight.avgRating),
                                    style = SpTypography.BodyMedium,
                                    color = SpColor.OnBackgroundSecondary,
                                )
                            }
                        }
                    }
                    if (spotlight.consoles.isNotEmpty()) {
                        Spacer(Modifier.height(SpSpacing.XSmall))
                        Text(
                            text = spotlight.consoles.joinToString(" · "),
                            style = SpTypography.BodySmall,
                            color = SpColor.Accent.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        // Top games row
        if (spotlight.topGames.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.testTag("developer_spotlight_games"),
                contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
            ) {
                items(spotlight.topGames, key = { it.id }) { game ->
                    SpotlightGameCard(
                        game = game,
                        onClick = { onGameSelected(game.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightGameCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .testTag("developer_spotlight_game_${game.id}")
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = game.coverAspectRatio,
            )
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
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = game.consoleName,
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DeveloperSpotlightSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag("developer_spotlight_skeleton")) {
        SpShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
            width = 400.dp,
            height = 100.dp,
        )
        Spacer(Modifier.height(SpSpacing.Medium))
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            items(4) {
                SpGameCardSkeleton(modifier = Modifier.width(SpSpacing.CoverMediumWidth))
            }
        }
    }
}
