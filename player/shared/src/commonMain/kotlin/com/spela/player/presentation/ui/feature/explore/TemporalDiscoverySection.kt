package com.spela.player.presentation.ui.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.AnniversaryItem
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

private val CardWidth = 140.dp

@Composable
fun OnThisDaySection(
    games: List<Game>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("on_this_day_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .semantics {
                        val year = game.releaseDate?.take(4) ?: ""
                        contentDescription = "${game.title}, released $year"
                    },
            ) {
                ExploreGameCard(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                val year = game.releaseDate?.take(4) ?: ""
                if (year.isNotEmpty()) {
                    Text(
                        text = "Released $year",
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("on_this_day_year_${game.id}"),
                    )
                }
            }
        }
    }
}

@Composable
fun AnniversariesSection(
    anniversaries: List<AnniversaryItem>,
    onGameSelected: (gameId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("anniversaries_row"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(anniversaries, key = { "${it.game.id}_${it.yearsAgo}" }) { item ->
            Column(
                modifier = Modifier
                    .width(CardWidth)
                    .semantics {
                        contentDescription = "${item.game.title}, ${item.yearsAgo} year${if (item.yearsAgo != 1) "s" else ""} ago"
                    },
            ) {
                ExploreGameCard(
                    game = item.game,
                    onClick = { onGameSelected(item.game.id) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${item.yearsAgo} year${if (item.yearsAgo != 1) "s" else ""} ago",
                    style = SpTypography.LabelSmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("anniversary_years_${item.game.id}"),
                )
            }
        }
    }
}

@Composable
fun TemporalSectionSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    LazyRow(
        modifier = modifier.testTag("temporal_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            SpShimmer(
                modifier = Modifier
                    .width(CardWidth)
                    .clip(RoundedCornerShape(SpSpacing.CardCornerRadius)),
                width = CardWidth,
                height = (CardWidth.value * 1.33f).dp,
            )
        }
    }
}
