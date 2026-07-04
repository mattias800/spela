package com.spela.player.presentation.ui.feature.explore

import com.spela.player.presentation.ui.components.rememberResolvedCoverUrl
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import com.spela.player.presentation.ui.components.SpCarousel
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.focusRequester
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.SpCarouselGameCard
import com.spela.player.presentation.ui.components.gamePlatformPillContent
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun GameShelf(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    memoryKey: String? = null,
) {
    SpCarousel(
        itemCount = games.size,
        modifier = modifier.testTag("game_shelf"),
        memoryKey = memoryKey,
        itemKey = if (memoryKey != null) ({ games[it].id }) else null,
    ) { index, focusRequester ->
        Box(modifier = Modifier.focusRequester(focusRequester)) {
            ExploreGameCard(
                game = games[index],
                onPlatformSelected = onGameSelected,
                onClick = { onGameSelected(games[index].id) },
            )
        }
    }
}

/** ROLE component — a game card in the Explore page shelves. Delegates to [SpCarouselGameCard]. */
@Composable
internal fun ExploreGameCard(
    game: Game,
    onClick: () -> Unit,
    onPlatformSelected: ((String) -> Unit)? = null,
) {
    val resolvedCoverUrl = rememberResolvedCoverUrl(gameId = game.id, coverUrl = game.coverUrl, scrapeAttempts = game.scrapeAttempts)
    SpCarouselGameCard(
        title = game.title,
        subtitle = game.consoleName,
        coverUrl = resolvedCoverUrl,
        onClick = onClick,
        rating = game.communityRating,
        isFavorite = game.isFavorite,
        isInPlayLater = game.isInPlayLater,
        testTag = "explore_game_card_${game.id}",
        platformContent = gamePlatformPillContent(game, onPlatformSelected),
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
