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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.domain.model.SearchCategory
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

        // Games section
        val gamesExpanded = SearchCategoryNames.GAMES in expandedCategories
        val gamesCategory = resolveCategory(
            category = results.games,
            expandedCategory = expandedResults?.games,
            isExpanded = gamesExpanded,
        )
        if (gamesCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.GAMES,
                    total = gamesCategory.total,
                    displayedCount = gamesCategory.results.size,
                    isExpanded = gamesExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.GAMES) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.GAMES) },
                )
            }
            items(
                count = gamesCategory.results.size,
                key = { "game_${gamesCategory.results[it].id}" },
            ) { index ->
                val game = gamesCategory.results[index]
                GameSearchResultItem(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }

        // Consoles section
        val consolesExpanded = SearchCategoryNames.CONSOLES in expandedCategories
        val consolesCategory = resolveCategory(
            category = results.consoles,
            expandedCategory = expandedResults?.consoles,
            isExpanded = consolesExpanded,
        )
        if (consolesCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.CONSOLES,
                    total = consolesCategory.total,
                    displayedCount = consolesCategory.results.size,
                    isExpanded = consolesExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.CONSOLES) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.CONSOLES) },
                )
            }
            items(
                count = consolesCategory.results.size,
                key = { "console_${consolesCategory.results[it].id}" },
            ) { index ->
                val console = consolesCategory.results[index]
                ConsoleSearchResultItem(
                    console = console,
                    onClick = { onConsoleSelected(console.id) },
                )
            }
        }

        // Developers section
        val developersExpanded = SearchCategoryNames.DEVELOPERS in expandedCategories
        val developersCategory = resolveCategory(
            category = results.developers,
            expandedCategory = expandedResults?.developers,
            isExpanded = developersExpanded,
        )
        if (developersCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.DEVELOPERS,
                    total = developersCategory.total,
                    displayedCount = developersCategory.results.size,
                    isExpanded = developersExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.DEVELOPERS) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.DEVELOPERS) },
                )
            }
            items(
                count = developersCategory.results.size,
                key = { "developer_${developersCategory.results[it].name}" },
            ) { index ->
                val developer = developersCategory.results[index]
                CompanySearchResultItem(
                    name = developer.name,
                    gameCount = developer.gameCount,
                    avgRating = developer.avgRating,
                    label = "Developer",
                    onClick = { onDeveloperSelected(developer.name) },
                )
            }
        }

        // Publishers section
        val publishersExpanded = SearchCategoryNames.PUBLISHERS in expandedCategories
        val publishersCategory = resolveCategory(
            category = results.publishers,
            expandedCategory = expandedResults?.publishers,
            isExpanded = publishersExpanded,
        )
        if (publishersCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.PUBLISHERS,
                    total = publishersCategory.total,
                    displayedCount = publishersCategory.results.size,
                    isExpanded = publishersExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.PUBLISHERS) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.PUBLISHERS) },
                )
            }
            items(
                count = publishersCategory.results.size,
                key = { "publisher_${publishersCategory.results[it].name}" },
            ) { index ->
                val publisher = publishersCategory.results[index]
                CompanySearchResultItem(
                    name = publisher.name,
                    gameCount = publisher.gameCount,
                    avgRating = publisher.avgRating,
                    label = "Publisher",
                    onClick = { onPublisherSelected(publisher.name) },
                )
            }
        }

        // Collections section
        val collectionsExpanded = SearchCategoryNames.COLLECTIONS in expandedCategories
        val collectionsCategory = resolveCategory(
            category = results.collections,
            expandedCategory = expandedResults?.collections,
            isExpanded = collectionsExpanded,
        )
        if (collectionsCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.COLLECTIONS,
                    total = collectionsCategory.total,
                    displayedCount = collectionsCategory.results.size,
                    isExpanded = collectionsExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.COLLECTIONS) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.COLLECTIONS) },
                )
            }
            items(
                count = collectionsCategory.results.size,
                key = { "collection_${collectionsCategory.results[it].id}" },
            ) { index ->
                val collection = collectionsCategory.results[index]
                CollectionSearchResultItem(
                    collection = collection,
                    onClick = { onCollectionSelected(collection.id) },
                )
            }
        }

        // Series section
        val seriesExpanded = SearchCategoryNames.SERIES in expandedCategories
        val seriesCategory = resolveCategory(
            category = results.series,
            expandedCategory = expandedResults?.series,
            isExpanded = seriesExpanded,
        )
        if (seriesCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.SERIES,
                    total = seriesCategory.total,
                    displayedCount = seriesCategory.results.size,
                    isExpanded = seriesExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.SERIES) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.SERIES) },
                )
            }
            items(
                count = seriesCategory.results.size,
                key = { "series_${seriesCategory.results[it].id}" },
            ) { index ->
                val series = seriesCategory.results[index]
                SeriesSearchResultItem(
                    series = series,
                    onClick = { onSeriesSelected(series.id, series.name) },
                )
            }
        }

        // Franchises section
        val franchisesExpanded = SearchCategoryNames.FRANCHISES in expandedCategories
        val franchisesCategory = resolveCategory(
            category = results.franchises,
            expandedCategory = expandedResults?.franchises,
            isExpanded = franchisesExpanded,
        )
        if (franchisesCategory.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = SearchCategoryNames.FRANCHISES,
                    total = franchisesCategory.total,
                    displayedCount = franchisesCategory.results.size,
                    isExpanded = franchisesExpanded,
                    onSeeAll = { onExpandCategory(SearchCategoryNames.FRANCHISES) },
                    onShowLess = { onCollapseCategory(SearchCategoryNames.FRANCHISES) },
                )
            }
            items(
                count = franchisesCategory.results.size,
                key = { "franchise_${franchisesCategory.results[it].id}" },
            ) { index ->
                val franchise = franchisesCategory.results[index]
                FranchiseSearchResultItem(
                    franchise = franchise,
                    onClick = { onFranchiseSelected(franchise.id, franchise.name) },
                )
            }
        }

        // Bottom spacer
        item { Spacer(Modifier.height(SpSpacing.XLarge)) }
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
