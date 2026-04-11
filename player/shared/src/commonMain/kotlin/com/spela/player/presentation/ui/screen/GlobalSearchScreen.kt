package com.spela.player.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.presentation.ui.components.PlatformBackHandler
import com.spela.player.presentation.ui.components.SpButton
import com.spela.player.presentation.ui.components.SpButtonStyle
import com.spela.player.presentation.ui.components.SpMainContentPadding
import com.spela.player.presentation.ui.components.SpScreen
import com.spela.player.presentation.ui.components.SpScreenTopSpacer
import com.spela.player.presentation.ui.components.SpEmptyState
import com.spela.player.presentation.ui.components.SpSnackbar
import com.spela.player.presentation.ui.components.SpSnackbarData
import com.spela.player.presentation.ui.components.SpSnackbarType
import com.spela.player.presentation.ui.components.SpTextField
import com.spela.player.presentation.ui.components.SpTopBar
import com.spela.player.presentation.ui.feature.search.RecentSearchesSection
import com.spela.player.presentation.ui.feature.search.SearchResultSkeleton
import com.spela.player.presentation.ui.feature.search.SearchResultsList
import com.spela.player.presentation.ui.gamepad.InputMode
import com.spela.player.presentation.ui.gamepad.LocalInputMode
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing
import com.spela.player.presentation.viewmodel.GlobalSearchViewModel

@Composable
fun GlobalSearchScreen(
    viewModel: GlobalSearchViewModel,
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit,
    onCollectionSelected: (String) -> Unit,
    onSeriesSelected: (String, String) -> Unit,
    onFranchiseSelected: (String, String) -> Unit,
    onAdvancedFiltersSelected: () -> Unit,
    onBack: () -> Unit,
) {
    PlatformBackHandler { onBack() }

    val state by viewModel.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val isGamepad = LocalInputMode.current == InputMode.GAMEPAD

    // Focus the text field on mount. Use a delay longer than the
    // GamepadHandler retry loop's initial check (100ms) so it fires
    // after GamepadHandler has already focused something, then we
    // override it with the text field.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

    SpScreen(modifier = Modifier.testTag("global_search_screen")) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isGamepad) {
                SpScreenTopSpacer()
            } else {
                SpTopBar(
                    title = "Search",
                    showBack = true,
                    onBack = onBack,
                )
            }
            SpMainContentPadding {

            // Search input
            SpTextField(
                value = state.query,
                onValueChange = { viewModel.updateQuery(it) },
                placeholder = "Search games, consoles, developers...",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = SpColor.OnBackgroundSecondary,
                    )
                },
                trailingIcon = if (state.query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = { viewModel.clearSearch() },
                            modifier = Modifier.testTag("search_clear_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = SpColor.OnBackgroundSecondary,
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("global_search_input"),
            )

            // Advanced Filters button
            SpButton(
                text = "Advanced Filters",
                onClick = onAdvancedFiltersSelected,
                style = SpButtonStyle.Ghost,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(SpSpacing.IconDefault),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("global_search_advanced_filters"),
            )

            // Content area
            when {
                state.showRecentSearches -> {
                    RecentSearchesSection(
                        recentSearches = state.recentSearches,
                        onRecentSearchSelected = { viewModel.selectRecentSearch(it) },
                        onRemoveRecentSearch = { viewModel.removeRecentSearch(it) },
                        onClearAll = { viewModel.clearRecentSearches() },
                    )
                }

                state.showPlaceholder -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.TravelExplore,
                            title = "Find anything",
                            message = "Search across games, consoles, developers, publishers, collections, series, and franchises.",
                            modifier = Modifier.testTag("search_placeholder"),
                        )
                    }
                }

                state.showHint -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Search,
                            title = "Type at least 2 characters",
                            message = "Search across games, consoles, developers, and more.",
                            modifier = Modifier.testTag("search_hint"),
                        )
                    }
                }

                state.isLoading && !state.hasResults -> {
                    SearchResultSkeleton(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = SpSpacing.ScreenHorizontal,
                                top = SpSpacing.Default,
                                end = SpSpacing.ScreenHorizontal,
                            )
                            .testTag("search_loading"),
                    )
                }

                state.showNoResults -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        SpEmptyState(
                            icon = Icons.Filled.Search,
                            title = "No results found",
                            message = "Try a different search term or use Advanced Filters for more options.",
                            modifier = Modifier.testTag("search_no_results"),
                        )
                    }
                }

                state.hasResults -> {
                    SearchResultsList(
                        results = state.results!!,
                        isLoading = state.isLoading,
                        suggestions = state.suggestions,
                        expandedCategories = state.expandedCategories,
                        expandedResults = state.expandedResults,
                        onExpandCategory = { viewModel.expandCategory(it) },
                        onCollapseCategory = { viewModel.collapseCategory(it) },
                        onGameSelected = onGameSelected,
                        onConsoleSelected = onConsoleSelected,
                        onDeveloperSelected = onDeveloperSelected,
                        onPublisherSelected = onPublisherSelected,
                        onCollectionSelected = onCollectionSelected,
                        onSeriesSelected = onSeriesSelected,
                        onFranchiseSelected = onFranchiseSelected,
                    )
                }
            }
        } // SpMainContentPadding
        } // Column

        // Error snackbar
        SpSnackbar(
            data = state.error?.let {
                SpSnackbarData(
                    message = it,
                    type = SpSnackbarType.Error,
                    actionLabel = "Dismiss",
                    onAction = { viewModel.dismissError() },
                )
            },
            onDismiss = { viewModel.dismissError() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
