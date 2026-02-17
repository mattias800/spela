package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.spela.player.domain.model.ActivePlayer
import com.spela.player.domain.model.MostPlayedGame
import com.spela.player.presentation.intent.StatsIntent
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSectionHeader
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.StatsViewModel
import com.spela.player.util.formatPlayTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onGameSelected: (String) -> Unit,
    onUserSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(StatsIntent.LoadStats)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = "Stats",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoading && state.mostPlayedGames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading stats...")
                }
            } else {
                val isEmpty = state.mostPlayedGames.isEmpty() && state.activePlayers.isEmpty()

                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(StatsIntent.LoadStats) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isEmpty && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyState(
                                icon = Icons.Filled.BarChart,
                                title = "No stats yet",
                                message = "Play some games and stats will appear here",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = SpSpacing.Default),
                        ) {
                            // Most Played Games section
                            if (state.mostPlayedGames.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "Most Played Games",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }

                                itemsIndexed(
                                    state.mostPlayedGames,
                                    key = { _, item -> "game-${item.game.id}" },
                                ) { index, item ->
                                    MostPlayedGameItem(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onGameSelected(item.game.id) },
                                    )
                                }

                                item {
                                    Spacer(Modifier.height(SpSpacing.XXLarge))
                                }
                            }

                            // Most Active Players section
                            if (state.activePlayers.isNotEmpty()) {
                                item {
                                    SpSectionHeader(
                                        title = "Most Active Players",
                                        modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal),
                                    )
                                    Spacer(Modifier.height(SpSpacing.Small))
                                }

                                itemsIndexed(
                                    state.activePlayers,
                                    key = { _, item -> "player-${item.userId}" },
                                ) { index, item ->
                                    ActivePlayerItem(
                                        rank = index + 1,
                                        item = item,
                                        onClick = { onUserSelected(item.userId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.onIntent(StatsIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(StatsIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun RankBadge(rank: Int) {
    val badgeColor = when (rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
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
private fun MostPlayedGameItem(
    rank: Int,
    item: MostPlayedGame,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
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
                modifier = Modifier.size(width = 48.dp, height = 64.dp),
                cornerRadius = 8.dp,
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
private fun ActivePlayerItem(
    rank: Int,
    item: ActivePlayer,
    onClick: () -> Unit,
) {
    SpCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
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
            PlayerAvatar(
                avatarUrl = item.avatarUrl,
                username = item.username,
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

@Composable
private fun PlayerAvatar(
    avatarUrl: String?,
    username: String,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            SubcomposeAsyncImage(
                model = avatarUrl,
                contentDescription = "$username avatar",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SpColor.SurfaceVariant),
                    )
                },
                error = {
                    AvatarPlaceholder(username)
                },
            )
        } else {
            AvatarPlaceholder(username)
        }
    }
}

@Composable
private fun AvatarPlaceholder(username: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.PrimaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = username.take(1).uppercase(),
            style = SpTypography.TitleLarge,
            color = SpColor.PrimaryLight,
        )
    }
}

@Composable
private fun StatLabel(label: String, value: String) {
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

