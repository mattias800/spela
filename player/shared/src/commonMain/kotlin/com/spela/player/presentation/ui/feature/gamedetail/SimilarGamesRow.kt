package com.spela.player.presentation.ui.feature.gamedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.SimilarGame
import com.spela.player.presentation.ui.components.SpAvailabilityGameCard
import com.spela.player.presentation.ui.components.SpCarousel
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpTitledSection
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun SimilarGamesSection(
    games: List<SimilarGame>,
    onGameSelected: (String) -> Unit,
) {
    if (games.isEmpty()) return

    SpTitledSection(
        title = "Similar Games",
        icon = Icons.Outlined.Explore,
        edgeToEdgeContent = true,
    ) {
        SpCarousel(
            contentPadding = PaddingValues(horizontal = SpSpacing.XLarge),
        ) {
            items(games, key = { it.igdbGameId }) { game ->
                SimilarGameCard(
                    game = game,
                    onClick = if (game.localGameId != null) {
                        { onGameSelected(game.localGameId) }
                    } else null,
                )
            }
        }
    }
}

/** ROLE component — a similar game card with library availability. Delegates to [SpAvailabilityGameCard]. */
@Composable
private fun SimilarGameCard(
    game: SimilarGame,
    onClick: (() -> Unit)?,
) {
    SpAvailabilityGameCard(
        title = game.name,
        subtitle = "",
        coverUrl = game.coverUrl,
        onClick = onClick ?: {},
        available = game.localGameId != null,
        coverHeight = SpSpacing.CoverMediumHeight,
        rating = game.rating,
    )
}
