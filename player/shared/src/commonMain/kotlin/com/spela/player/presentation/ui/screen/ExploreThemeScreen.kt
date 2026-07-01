package com.spela.player.presentation.ui.screen

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
import androidx.compose.foundation.lazy.grid.GridCells
import com.spela.player.presentation.ui.components.SpLazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.Game
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpCard
import com.spela.player.presentation.ui.components.SpCoverArt
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpGameCardSkeleton
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.components.sectionPillClearance
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating

@Composable
fun ExploreThemeScreen(
    themeId: String,
    themeName: String,
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.themeDetailState.collectAsState()

    LaunchedEffect(themeId) {
        viewModel.loadThemeGames(themeId, themeName)
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(modifier = Modifier.testTag("explore_theme_screen")) {
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (!isGamepad) {
                SpTopBar(
                    title = themeName,
                    showBack = true,
                    onBack = onBack,
                )
            }

            when {
                state.isLoading && state.games.isEmpty() -> {
                    SpLazyVerticalGrid(
                        columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("theme_games_loading"),
                        contentPadding = PaddingValues(
                            start = SpSpacing.ScreenHorizontal,
                            end = SpSpacing.ScreenHorizontal,
                            top = sectionPillClearance() + SpSpacing.ScreenHorizontal,
                            bottom = SpSpacing.ScreenHorizontal,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    ) {
                        items(8) {
                            SpGameCardSkeleton()
                        }
                    }
                }

                state.games.isEmpty() && !state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Category,
                            title = "No games found",
                            message = "No games match this theme yet.",
                            modifier = Modifier.testTag("theme_empty_state"),
                        )
                    }
                }

                else -> {
                    SpLazyVerticalGrid(
                        columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("theme_games_grid"),
                        contentPadding = PaddingValues(
                            start = SpSpacing.ScreenHorizontal,
                            end = SpSpacing.ScreenHorizontal,
                            top = sectionPillClearance() + SpSpacing.ScreenHorizontal,
                            bottom = SpSpacing.ScreenHorizontal,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    ) {
                        items(state.games, key = { it.id }) { game ->
                            ThemeGameCard(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                                modifier = Modifier.focusRestoreItem(
                                    key = "theme_${themeId}_${game.id}",
                                    isDefault = game == state.games.firstOrNull(),
                                ),
                            )
                        }
                    }
                }
            }
        }

        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissThemeDetailError() },
                )
            },
            onDismiss = { viewModel.dismissThemeDetailError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // CompositionLocalProvider
    }
}

@Composable
private fun ThemeGameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("theme_game_card_${game.id}")
            .semantics {
                contentDescription = "${game.title}, ${game.consoleName}"
                role = Role.Button
            },
        onClick = onClick,
        onGradient = true,
    ) {
        Column {
            SpCoverArt(
                imageUrl = game.coverUrl,
                contentDescription = "${game.title} cover art",
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = game.coverAspectRatio,
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
                Spacer(Modifier.height(SpSpacing.XXSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SpSpacing.XSmall),
                ) {
                    Text(
                        text = game.consoleName,
                        style = SpTypography.LabelSmall,
                        color = SpColor.OnBackgroundTertiary,
                        maxLines = 1,
                    )
                    if (game.igdbCriticsRating > 0) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = SpColor.Rating,
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = formatRating(game.igdbCriticsRating),
                            style = SpTypography.LabelSmall,
                            color = SpColor.OnBackgroundTertiary,
                        )
                    }
                }
            }
        }
    }
}
