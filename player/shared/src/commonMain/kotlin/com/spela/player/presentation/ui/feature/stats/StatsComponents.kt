package com.spela.player.presentation.ui.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.ui.components.SpAvatar
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.util.formatPlayTime

@Composable
internal fun RankBadge(rank: Int) {
    val badgeColor = when (rank) {
        1 -> SpColor.Gold
        2 -> SpColor.Silver
        3 -> SpColor.Bronze
        else -> SpColor.OnBackgroundTertiary
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (rank <= 3) badgeColor.copy(alpha = 0.2f)
                else SpColor.SurfaceVariant
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$rank",
            style = SpTypography.LabelLarge.copy(fontWeight = FontWeight.Bold),
            color = badgeColor,
        )
    }
}

@Composable
internal fun MostPlayedGameItem(
    rank: Int,
    item: MostPlayedGame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank $rank: ${item.game.title}, ${formatPlayTime(item.totalPlayTime)} play time"
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
            RankBadge(rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            SpCoverArt(
                imageUrl = item.game.coverUrl,
                contentDescription = "${item.game.title} cover",
                modifier = Modifier.height(64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
                aspectRatio = item.game.coverAspectRatio,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.game.title,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.game.consoleName.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = item.game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                ) {
                    StatLabel(label = "Play time", value = formatPlayTime(item.totalPlayTime))
                    StatLabel(label = "Players", value = "${item.totalPlayers}")
                }
            }
        }
    }
}

@Composable
internal fun ActivePlayerItem(
    rank: Int,
    item: ActivePlayer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank $rank: ${item.username}, ${formatPlayTime(item.totalPlayTime)} play time"
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
            RankBadge(rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            SpAvatar(
                username = item.username,
                avatarUrl = item.avatarUrl,
                size = 48.dp,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.username,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.Default),
                ) {
                    StatLabel(label = "Play time", value = formatPlayTime(item.totalPlayTime))
                    StatLabel(label = "Games", value = "${item.gamesPlayed}")
                }
            }
        }
    }
}

/**
 * A federated (mesh) leaderboard row — leaner than the local rows since the mesh
 * aggregate is metadata-only (label + playtime + optional player count; no cover
 * art or avatar). [players] is null for the player metric. Focusable (so gamepad
 * d-pad can scroll the list) but display-only for now — A-press is a no-op;
 * linking a mesh game to its import page is a follow-up.
 */
@Composable
internal fun MeshStatItem(
    rank: Int,
    label: String,
    playTimeSeconds: Long,
    players: Long?,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank $rank: $label, ${formatPlayTime(playTimeSeconds)} play time"
                role = Role.Button
            },
        onClick = {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            Text(
                text = label,
                style = SpTypography.TitleLarge,
                color = SpColor.OnCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            StatLabel(label = "Play time", value = formatPlayTime(playTimeSeconds))
            if (players != null) {
                Spacer(Modifier.width(SpSpacing.Default))
                StatLabel(label = "Players", value = "$players")
            }
        }
    }
}

/**
 * A federated "top achievers" leaderboard row — a player's achievement-unlock
 * count, with the connected-server label when remote. Focusable (so the d-pad
 * can scroll the list) but display-only for now.
 */
@Composable
internal fun MeshAchieverItem(
    rank: Int,
    username: String,
    count: Long,
    serverName: String,
    modifier: Modifier = Modifier,
) {
    SpCard(
        onGradient = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Rank $rank: $username, $count achievements"
                role = Role.Button
            },
        onClick = {},
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SpSpacing.Default),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RankBadge(rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            Text(
                text = username,
                style = SpTypography.TitleLarge,
                color = SpColor.OnCard,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            StatLabel(label = "Achievements", value = "$count")
            if (serverName.isNotBlank()) {
                Spacer(Modifier.width(SpSpacing.Default))
                StatLabel(label = "Server", value = serverName)
            }
        }
    }
}

@Composable
internal fun StatLabel(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = SpTypography.LabelMedium,
            color = SpColor.OnCard,
        )
        Text(
            text = label,
            style = SpTypography.LabelSmall,
            color = SpColor.OnBackgroundTertiary,
        )
    }
}

