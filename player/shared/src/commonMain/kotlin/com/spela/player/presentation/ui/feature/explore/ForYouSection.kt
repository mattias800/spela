package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ForYouRow
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
fun ForYouSection(
    rows: List<ForYouRow>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag("for_you_section"),
        verticalArrangement = Arrangement.spacedBy(SpSpacing.Large),
    ) {
        rows.forEach { row ->
            if (row.games.isNotEmpty()) {
                ForYouRowSection(
                    row = row,
                    onGameSelected = onGameSelected,
                )
            }
        }
    }
}

@Composable
private fun ForYouRowSection(
    row: ForYouRow,
    onGameSelected: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("for_you_row_${row.type}"),
    ) {
        // Row header with title (and source game thumbnail for "because_you_played")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.type == "because_you_played" && row.sourceGame != null) {
                SpCoverArt(
                    imageUrl = row.sourceGame.coverUrl,
                    contentDescription = "${row.sourceGame.title} cover",
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("for_you_source_game_${row.sourceGame.id}"),
                    aspectRatio = row.sourceGame.coverAspectRatio,
                )
                Spacer(Modifier.width(SpSpacing.Small))
            }
            Text(
                text = row.title,
                style = SpTypography.TitleMedium,
                color = SpColor.OnBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(SpSpacing.Medium))

        // Horizontal game shelf
        LazyRow(
            contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
        ) {
            items(row.games, key = { it.id }) { game ->
                ForYouGameCard(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }
    }
}

@Composable
private fun ForYouGameCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .testTag("for_you_game_${game.id}")
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = game.consoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )

                    if (game.rating > 0) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(SpSpacing.IconXSmall),
                        )
                        Text(
                            text = formatRating(game.rating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ForYouSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 2,
) {
    Column(modifier = modifier.testTag("for_you_skeleton")) {
        repeat(rowCount) {
            Column(modifier = Modifier.fillMaxWidth()) {
                SpShimmer(
                    modifier = Modifier
                        .padding(horizontal = SpSpacing.ScreenHorizontal)
                        .clip(RoundedCornerShape(4.dp)),
                    width = 200.dp,
                    height = 20.dp,
                )
                Spacer(Modifier.height(SpSpacing.Small))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                ) {
                    items(5) {
                        SpGameCardSkeleton(modifier = Modifier.width(SpSpacing.CoverMediumWidth))
                    }
                }
                Spacer(Modifier.height(SpSpacing.Default))
            }
        }
    }
}
