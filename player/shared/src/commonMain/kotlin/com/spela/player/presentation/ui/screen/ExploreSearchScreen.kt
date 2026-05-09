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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.CompositionLocalProvider
import com.spela.player.presentation.ui.gamepad.LocalFocusMemory
import com.spela.player.presentation.ui.gamepad.focusRestoreItem
import com.spela.player.presentation.ui.gamepad.rememberFocusMemoryState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.explore.GameFilterPanel
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.ui.theme.SpTypography
import com.spela.player.presentation.viewmodel.ExploreViewModel
import com.spela.player.util.formatRating

@Composable
fun ExploreSearchScreen(
    viewModel: ExploreViewModel,
    onGameSelected: (String) -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val searchState by viewModel.gameSearchState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSavedSearches()
    }

    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD
    val focusMemory = rememberFocusMemoryState()

    SpScreen(modifier = Modifier.testTag("explore_search_screen")) {
        CompositionLocalProvider(LocalFocusMemory provides focusMemory) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Advanced Search",
                    showBack = true,
                    onBack = onBack,
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleFilterPanel() },
                            modifier = Modifier.testTag("toggle_filter_panel_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FilterList,
                                contentDescription = "Toggle filters",
                                tint = if (searchState.showFilterPanel) SpColor.Primary else SpColor.OnBackgroundSecondary,
                            )
                        }
                    },
                )
            }

            if (searchState.showFilterPanel) {
                GameFilterPanel(
                    filters = searchState.filters,
                    savedSearches = searchState.savedSearches,
                    isLoading = searchState.isLoading,
                    isSaving = searchState.isSaving,
                    onFiltersChanged = { viewModel.updateFilters(it) },
                    onApplyFilters = { viewModel.applyFilters() },
                    onClearFilters = { viewModel.clearFilters() },
                    onSaveSearch = { name -> viewModel.saveCurrentSearch(name) },
                    onDeleteSavedSearch = { id -> viewModel.deleteSavedSearch(id) },
                    onApplySavedSearch = { saved -> viewModel.applySavedSearch(saved) },
                    modifier = Modifier
                        .focusRestoreItem(key = "explore_search_filters", isDefault = true)
                        .fillMaxWidth()
                        .padding(horizontal = SpSpacing.ScreenHorizontal),
                )
                Spacer(Modifier.height(SpSpacing.Medium))
            }

            when {
                searchState.isLoading && searchState.results.isEmpty() -> {
                    SpLazyVerticalGrid(
                        columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_loading"),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    ) {
                        items(8) {
                            SpGameCardSkeleton()
                        }
                    }
                }

                searchState.results.isEmpty() && !searchState.isLoading && searchState.filters.isEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Search,
                            title = "Search your library",
                            message = "Use the filters above to find games by console, genre, developer, rating, and more.",
                            modifier = Modifier.testTag("search_empty_state"),
                        )
                    }
                }

                searchState.results.isEmpty() && !searchState.isLoading && !searchState.filters.isEmpty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Search,
                            title = "No results found",
                            message = "Try adjusting your filters to find more games.",
                            modifier = Modifier.testTag("search_no_results"),
                        )
                    }
                }

                else -> {
                    Text(
                        text = "${searchState.results.size} results",
                        style = SpTypography.LabelMedium,
                        color = SpColor.OnBackgroundSecondary,
                        modifier = Modifier
                            .padding(horizontal = SpSpacing.ScreenHorizontal)
                            .testTag("search_results_count"),
                    )
                    Spacer(Modifier.height(SpSpacing.Small))
                    SpLazyVerticalGrid(
                        columns = GridCells.Adaptive(SpSpacing.GridCellMinWidth),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("search_results_grid"),
                        contentPadding = PaddingValues(SpSpacing.ScreenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                        verticalArrangement = Arrangement.spacedBy(SpSpacing.GridSpacing),
                    ) {
                        itemsIndexed(searchState.results, key = { _, g -> g.id }) { index, game ->
                            SearchResultGameCard(
                                game = game,
                                onClick = { onGameSelected(game.id) },
                                modifier = Modifier.focusRestoreItem(
                                    key = "explore_search_${game.id}",
                                    isDefault = index == 0,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Error snackbar
        SpSnackbar(
            data = searchState.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissSearchError() },
                )
            },
            onDismiss = { viewModel.dismissSearchError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        } // CompositionLocalProvider
    }
}

@Composable
private fun SearchResultGameCard(
    game: Game,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SpCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag("search_result_game_${game.id}")
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
