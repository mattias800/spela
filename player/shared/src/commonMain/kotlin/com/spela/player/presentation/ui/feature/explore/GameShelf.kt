package com.spela.player.presentation.ui.feature.explore

import com.spela.player.presentation.ui.components.rememberResolvedCoverUrl
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCarouselGameCard
import com.spela.player.presentation.ui.components.SpChip
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatRating

@Composable
fun GameShelf(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.testTag("game_shelf"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            ExploreGameCard(
                game = game,
                onClick = { onGameSelected(game.id) },
            )
        }
    }
}

/** ROLE component — a game card in the Explore page shelves. Delegates to [SpCarouselGameCard]. */
@Composable
internal fun ExploreGameCard(
    game: Game,
    onClick: () -> Unit,
) {
    val resolvedCoverUrl = rememberResolvedCoverUrl(gameId = game.id, coverUrl = game.coverUrl, scrapeAttempts = game.scrapeAttempts)
    SpCarouselGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = resolvedCoverUrl,
        onClick = onClick,
        rating = game.rating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        testTag = "explore_game_card_${game.id}",
    )
}

@Composable
fun GameShelfSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    LazyRow(
        modifier = modifier.testTag("game_shelf_skeleton"),
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(count) {
            SpGameCardSkeleton(modifier = Modifier.width(SpSpacing.CoverMediumWidth))
        }
    }
}
