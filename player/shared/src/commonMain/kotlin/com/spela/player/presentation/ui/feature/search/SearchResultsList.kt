package com.spela.player.presentation.ui.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.presentation.ui.theme.SpColor
import com.spela.player.presentation.ui.theme.SpSpacing

@Composable
fun SearchResultsList(
    results: GlobalSearchResult,
    isLoading: Boolean,
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
        // Loading indicator at top when re-searching
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = SpSpacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = SpColor.Primary,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }

        // Games section
        if (results.games.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Games",
                    total = results.games.total,
                    displayedCount = results.games.results.size,
                )
            }
            items(
                count = results.games.results.size,
                key = { "game_${results.games.results[it].id}" },
            ) { index ->
                val game = results.games.results[index]
                GameSearchResultItem(
                    game = game,
                    onClick = { onGameSelected(game.id) },
                )
            }
        }

        // Consoles section
        if (results.consoles.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Consoles",
                    total = results.consoles.total,
                    displayedCount = results.consoles.results.size,
                )
            }
            items(
                count = results.consoles.results.size,
                key = { "console_${results.consoles.results[it].id}" },
            ) { index ->
                val console = results.consoles.results[index]
                ConsoleSearchResultItem(
                    console = console,
                    onClick = { onConsoleSelected(console.id) },
                )
            }
        }

        // Developers section
        if (results.developers.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Developers",
                    total = results.developers.total,
                    displayedCount = results.developers.results.size,
                )
            }
            items(
                count = results.developers.results.size,
                key = { "developer_${results.developers.results[it].name}" },
            ) { index ->
                val developer = results.developers.results[index]
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
        if (results.publishers.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Publishers",
                    total = results.publishers.total,
                    displayedCount = results.publishers.results.size,
                )
            }
            items(
                count = results.publishers.results.size,
                key = { "publisher_${results.publishers.results[it].name}" },
            ) { index ->
                val publisher = results.publishers.results[index]
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
        if (results.collections.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Collections",
                    total = results.collections.total,
                    displayedCount = results.collections.results.size,
                )
            }
            items(
                count = results.collections.results.size,
                key = { "collection_${results.collections.results[it].id}" },
            ) { index ->
                val collection = results.collections.results[index]
                CollectionSearchResultItem(
                    collection = collection,
                    onClick = { onCollectionSelected(collection.id) },
                )
            }
        }

        // Series section
        if (results.series.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Series",
                    total = results.series.total,
                    displayedCount = results.series.results.size,
                )
            }
            items(
                count = results.series.results.size,
                key = { "series_${results.series.results[it].id}" },
            ) { index ->
                val series = results.series.results[index]
                SeriesSearchResultItem(
                    series = series,
                    onClick = { onSeriesSelected(series.id, series.name) },
                )
            }
        }

        // Franchises section
        if (results.franchises.results.isNotEmpty()) {
            item {
                SearchSectionHeader(
                    title = "Franchises",
                    total = results.franchises.total,
                    displayedCount = results.franchises.results.size,
                )
            }
            items(
                count = results.franchises.results.size,
                key = { "franchise_${results.franchises.results[it].id}" },
            ) { index ->
                val franchise = results.franchises.results[index]
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
