package com.spela.player.presentation.ui.feature.home

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun TopRatedRow(
    games: List<TopRatedGame>,
    onGameSelected: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
) {
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.rank }) { game ->
            TopRatedCard(
                game = game,
                onClick = if (game.localGameId != null) {
                    { onGameSelected(game.localGameId) }
                } else null,
            )
        }
    }
}

@Composable
private fun TopRatedCard(
    game: TopRatedGame,
    onClick: (() -> Unit)?,
) {
    val alpha = if (game.localGameId != null) 1f else 0.5f
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .alpha(alpha)
            .semantics {
                contentDescription = "${game.name}, rated ${game.rating.toInt()}"
                if (onClick != null) role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.name} cover art",
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(
                    horizontal = SpSpacing.Small,
                    vertical = SpSpacing.Small,
                ),
            ) {
                Text(
                    text = game.name,
                    style = SpTypography.TitleSmall,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = SpColor.Gold,
                    )
                    Spacer(Modifier.width(SpSpacing.XXSmall))
                    Text(
                        text = "${game.rating.toInt()}",
                        style = SpTypography.LabelSmall,
                        color = SpColor.Gold,
                    )
                }
            }
        }
    }
}
