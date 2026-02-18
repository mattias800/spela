package com.spela.player.presentation.ui.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.NetplaySession
import com.spela.player.domain.model.NetplaySessionStatus
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography

@Composable
internal fun ContinuePlayingRow(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            ContinuePlayingCard(
                game = game,
                onClick = { onGameSelected(game.id) },
            )
        }
    }
}

@Composable
internal fun ContinuePlayingCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(280.dp)
            .semantics {
                contentDescription = "Continue playing ${game.title} on ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.size(width = 60.dp, height = 84.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = game.consoleName,
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                )
            }
            Spacer(Modifier.width(SpSpacing.Small))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SpColor.Primary)
                    .semantics {
                        contentDescription = "Play ${game.title}"
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = SpColor.OnPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
internal fun GameCarouselRow(
    games: List<Game>,
    onGameSelected: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = SpSpacing.ScreenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
    ) {
        items(games, key = { it.id }) { game ->
            GameCoverCard(
                game = game,
                onClick = { onGameSelected(game.id) },
            )
        }
    }
}

@Composable
internal fun GameCoverCard(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .width(SpSpacing.CoverMediumWidth)
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.fillMaxWidth(),
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
internal fun NetplaySessionCard(
    session: NetplaySession,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .semantics {
                contentDescription = when (session.status) {
                    NetplaySessionStatus.WAITING -> "${session.gameTitle} netplay session, waiting for player"
                    NetplaySessionStatus.IN_PROGRESS -> "${session.gameTitle} with ${session.clientUsername ?: "player"}, tap to rejoin"
                    NetplaySessionStatus.ENDED -> "${session.gameTitle} session ended"
                }
                role = Role.Button
            },
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpCoverArt(
                imageUrl = session.gameCoverUrl,
                contentDescription = "${session.gameTitle} cover",
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.gameTitle,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Text(
                    text = when (session.status) {
                        NetplaySessionStatus.WAITING -> "Waiting for player..."
                        NetplaySessionStatus.IN_PROGRESS -> "With ${session.clientUsername ?: session.hostUsername} -- Tap to rejoin"
                        NetplaySessionStatus.ENDED -> "Session ended"
                    },
                    style = SpTypography.BodySmall,
                    color = SpColor.OnBackgroundTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
