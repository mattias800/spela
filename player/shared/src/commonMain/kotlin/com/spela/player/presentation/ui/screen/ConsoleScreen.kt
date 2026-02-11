package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.spela.player.domain.model.Game
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyStates
import com.spela.player.presentation.ui.components.SpLoadingIndicator
import com.spela.player.presentation.ui.components.SpSearchField
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.gamepad.spFocusRing
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScreen(
    consoleId: String,
    viewModel: GameListViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
    onNavigateToConsoleSettings: () -> Unit = {},
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()

    val consoleName = state.consoles
        .firstOrNull { it.id == consoleId }?.name ?: "Games"

    LaunchedEffect(consoleId) {
        viewModel.onIntent(GameListIntent.SelectConsole(consoleId))
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpColor.Background),
    ) {
        SpTopBar(
            title = consoleName,
            showBack = true,
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .spFocusRing(shape = CircleShape)
                        .clip(CircleShape)
                        .background(SpColor.SurfaceVariant)
                        .clickable(onClick = onNavigateToConsoleSettings)
                        .focusable()
                        .semantics {
                            contentDescription = "Console settings"
                            role = Role.Button
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = SpColor.OnSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )

        SpSearchField(
            value = state.searchQuery,
            onValueChange = { viewModel.onIntent(GameListIntent.Search(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
            placeholder = "Search $consoleName games...",
        )

        if (state.isLoading && state.games.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpLoadingIndicator(message = "Loading games...")
            }
        } else {
            PullToRefreshBox(
                isRefreshing = state.isLoading,
                onRefresh = { viewModel.onIntent(GameListIntent.SelectConsole(consoleId)) },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.games.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.searchQuery.length >= 2) {
                            SpEmptyStates.NoSearchResults(query = state.searchQuery)
                        } else {
                            SpEmptyStates.NoGamesInConsole(consoleName = consoleName)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = SpSpacing.ScreenHorizontal,
                            vertical = SpSpacing.Default,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    ) {
                        items(state.games, key = { it.id }) { game ->
                            GameGridItem(
                                game = game,
                                onClick = { onGameSelected(game.id) },
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
                onAction = { viewModel.onIntent(GameListIntent.DismissError) },
            )
        },
        onDismiss = { viewModel.onIntent(GameListIntent.DismissError) },
        modifier = Modifier.align(Alignment.BottomCenter),
    )
    } // outer Box
}

@Composable
private fun GameGridItem(
    game: Game,
    onClick: () -> Unit,
) {
    SpCard(
        onClick = onClick,
        modifier = Modifier.semantics {
            contentDescription = "${game.title}${game.genre?.let { ", $it" } ?: ""}"
            role = Role.Button
        },
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
                if (game.genre != null) {
                    Text(
                        text = game.genre,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
