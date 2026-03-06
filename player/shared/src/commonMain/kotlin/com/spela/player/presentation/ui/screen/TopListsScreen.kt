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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.TopListGame
import com.spela.player.presentation.intent.TopListsIntent
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.stats.RankBadge
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.TopListsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopListsScreen(
    viewModel: TopListsViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onIntent(TopListsIntent.LoadTopLists)
    }

    Box(modifier = Modifier.fillMaxSize().testTag("top_lists_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SpColor.Background),
        ) {
            SpTopBar(
                title = "Top Lists",
                showBack = true,
                onBack = onBack,
            )

            if (state.isLoading && state.games.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SpLoadingIndicator(message = "Loading top lists...")
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.onIntent(TopListsIntent.LoadTopLists) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.games.isEmpty() && !state.isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize().testTag("top_lists_empty"),
                            contentAlignment = Alignment.Center,
                        ) {
                            SpEmptyState(
                                icon = Icons.Filled.EmojiEvents,
                                title = "No top rated games yet",
                                message = "Top rated games from your library will appear here",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("top_lists_content"),
                            contentPadding = PaddingValues(vertical = SpSpacing.Default),
                        ) {
                            itemsIndexed(
                                state.games,
                                key = { _, item -> "toplist-${item.gameId}" },
                            ) { _, item ->
                                TopListGameItem(
                                    game = item,
                                    onClick = { onGameSelected(item.gameId) },
                                )
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
                    onAction = { viewModel.onIntent(TopListsIntent.DismissError) },
                )
            },
            onDismiss = { viewModel.onIntent(TopListsIntent.DismissError) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TopListGameItem(
    game: TopListGame,
    onClick: () -> Unit,
) {
    SpCard(
        onGradient = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XSmall)
            .testTag("top_list_item_${game.gameId}")
            .semantics {
                contentDescription = "Rank ${game.rank}: ${game.name}, rated %.1f".format(game.rating)
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
            RankBadge(game.rank)
            Spacer(Modifier.width(SpSpacing.Medium))
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.name} cover",
                modifier = Modifier.height(64.dp),
                cornerRadius = SpSpacing.RadiusMedium,
            )
            Spacer(Modifier.width(SpSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = SpTypography.TitleLarge,
                    color = SpColor.OnCard,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (game.consoleName.isNotBlank()) {
                    Spacer(Modifier.height(SpSpacing.XXSmall))
                    Text(
                        text = game.consoleName,
                        style = SpTypography.BodySmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(SpSpacing.XSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XXSmall),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = SpColor.Warning,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "%.1f".format(game.rating),
                        style = SpTypography.LabelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpColor.OnCard,
                    )
                }
            }
        }
    }
}
