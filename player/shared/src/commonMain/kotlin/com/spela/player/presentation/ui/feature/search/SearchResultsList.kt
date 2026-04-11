package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.domain.model.SearchCategory
import com.spela.player.domain.model.SearchSuggestion
import com.spela.player.presentation.ui.components.SpShimmer
import com.spela.player.presentation.ui.theme.SpSpacing

private object SearchCategoryNames {
    const val GAMES = "Games"
    const val CONSOLES = "Consoles"
    const val DEVELOPERS = "Developers"
    const val PUBLISHERS = "Publishers"
    const val COLLECTIONS = "Collections"
    const val SERIES = "Series"
    const val FRANCHISES = "Franchises"
}

@Composable
fun SearchResultsList(
    results: GlobalSearchResult,
    isLoading: Boolean,
    suggestions: List<SearchSuggestion> = emptyList(),
    expandedCategories: Set<String> = emptySet(),
    expandedResults: GlobalSearchResult? = null,
    onExpandCategory: (String) -> Unit = {},
    onCollapseCategory: (String) -> Unit = {},
    onGameSelected: (String) -> Unit,
    onConsoleSelected: (String) -> Unit,
    onDeveloperSelected: (String) -> Unit,
    onPublisherSelected: (String) -> Unit,
    onCollectionSelected: (String) -> Unit,
    onSeriesSelected: (String, String) -> Unit,
    onFranchiseSelected: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_results_list"),
    ) {
        // Loading skeleton at top when re-searching
        if (isLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = SpSpacing.ScreenHorizontal,
                            vertical = SpSpacing.Small,
                        ),
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = SpSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SpSpacing.Medium),
                        ) {
                            SpShimmer(width = 40.dp, height = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                SpShimmer(width = 140.dp, height = 14.dp)
                                Spacer(Modifier.height(SpSpacing.XSmall))
                                SpShimmer(width = 80.dp, height = 12.dp)
                            }
                        }
                    }
                }
            }
        }

        // Quick results section
        if (suggestions.isNotEmpty()) {
            item(key = "quick_results") {
                QuickResultsSection(
                    suggestions = suggestions,
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

        // Category sections
        searchSection(
            name = SearchCategoryNames.GAMES,
            category = results.games,
            expandedCategory = expandedResults?.games,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "game_${it.id}" },
        ) { game ->
            GameSearchResultItem(
                game = game,
                onClick = { onGameSelected(game.id) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.CONSOLES,
            category = results.consoles,
            expandedCategory = expandedResults?.consoles,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "console_${it.id}" },
        ) { console ->
            ConsoleSearchResultItem(
                console = console,
                onClick = { onConsoleSelected(console.id) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.DEVELOPERS,
            category = results.developers,
            expandedCategory = expandedResults?.developers,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "developer_${it.name}" },
        ) { developer ->
            CompanySearchResultItem(
                name = developer.name,
                gameCount = developer.gameCount,
                avgRating = developer.avgRating,
                label = "Developer",
                onClick = { onDeveloperSelected(developer.name) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.PUBLISHERS,
            category = results.publishers,
            expandedCategory = expandedResults?.publishers,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "publisher_${it.name}" },
        ) { publisher ->
            CompanySearchResultItem(
                name = publisher.name,
                gameCount = publisher.gameCount,
                avgRating = publisher.avgRating,
                label = "Publisher",
                onClick = { onPublisherSelected(publisher.name) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.COLLECTIONS,
            category = results.collections,
            expandedCategory = expandedResults?.collections,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "collection_${it.id}" },
        ) { collection ->
            CollectionSearchResultItem(
                collection = collection,
                onClick = { onCollectionSelected(collection.id) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.SERIES,
            category = results.series,
            expandedCategory = expandedResults?.series,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "series_${it.id}" },
        ) { series ->
            SeriesSearchResultItem(
                series = series,
                onClick = { onSeriesSelected(series.id, series.name) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        searchSection(
            name = SearchCategoryNames.FRANCHISES,
            category = results.franchises,
            expandedCategory = expandedResults?.franchises,
            expandedCategories = expandedCategories,
            onExpand = onExpandCategory,
            onCollapse = onCollapseCategory,
            itemKey = { "franchise_${it.id}" },
        ) { franchise ->
            FranchiseSearchResultItem(
                franchise = franchise,
                onClick = { onFranchiseSelected(franchise.id, franchise.name) },
                modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.XXSmall),
            )
        }

        // Bottom spacer
        item { Spacer(Modifier.height(SpSpacing.XLarge)) }
    }
}

/**
 * Adds a search section (header + items) to the LazyColumn if the category
 * has results. Handles expand/collapse resolution via [resolveCategory].
 */
private fun <T> LazyListScope.searchSection(
    name: String,
    category: SearchCategory<T>,
    expandedCategory: SearchCategory<T>?,
    expandedCategories: Set<String>,
    onExpand: (String) -> Unit,
    onCollapse: (String) -> Unit,
    itemKey: (T) -> String,
    itemContent: @Composable (T) -> Unit,
) {
    val isExpanded = name in expandedCategories
    val resolved = resolveCategory(category, expandedCategory, isExpanded)
    if (resolved.results.isEmpty()) return

    item {
        SearchSectionHeader(
            title = name,
            total = resolved.total,
            displayedCount = resolved.results.size,
            isExpanded = isExpanded,
            onSeeAll = { onExpand(name) },
            onShowLess = { onCollapse(name) },
            modifier = Modifier.padding(horizontal = SpSpacing.ScreenHorizontal, vertical = SpSpacing.Small),
        )
    }
    items(
        count = resolved.results.size,
        key = { itemKey(resolved.results[it]) },
    ) { index ->
        itemContent(resolved.results[index])
    }
}

/**
 * Resolves which category data to display. When a category is expanded
 * and expanded results are available, use those. Otherwise use the
 * default (limit=5) results.
 */
private fun <T> resolveCategory(
    category: SearchCategory<T>,
    expandedCategory: SearchCategory<T>?,
    isExpanded: Boolean,
): SearchCategory<T> {
    return if (isExpanded && expandedCategory != null) {
        expandedCategory
    } else {
        category
    }
}
