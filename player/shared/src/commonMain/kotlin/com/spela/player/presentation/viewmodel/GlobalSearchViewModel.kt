package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.GlobalSearchResult
import com.spela.player.domain.model.SearchSuggestion
import com.spela.player.domain.model.SuggestionNavigationType
import com.spela.player.domain.repository.SearchRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlobalSearchState(
    val query: String = "",
    val results: GlobalSearchResult? = null,
    val suggestions: List<SearchSuggestion> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val recentSearches: List<String> = emptyList(),
    val expandedCategories: Set<String> = emptySet(),
    val expandedResults: GlobalSearchResult? = null,
) {
    val hasResults: Boolean get() = results != null && !results.isEmpty
    val showHint: Boolean get() = query.trim().length < 2 && query.isNotEmpty() && !isLoading && results == null
    val showNoResults: Boolean get() = query.trim().length >= 2 && !isLoading && results != null && results.isEmpty
    val showPlaceholder: Boolean get() = query.isEmpty() && !isLoading && results == null && recentSearches.isEmpty()
    val showRecentSearches: Boolean get() = query.isEmpty() && recentSearches.isNotEmpty()
}

class GlobalSearchViewModel(
    private val searchRepository: SearchRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GlobalSearchState())
    val state: StateFlow<GlobalSearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadRecentSearches()
    }

    fun updateQuery(query: String) {
        _state.update {
            it.copy(
                query = query,
                error = null,
                expandedCategories = emptySet(),
                expandedResults = null,
            )
        }

        searchJob?.cancel()

        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _state.update { it.copy(results = null, suggestions = emptyList(), isLoading = false) }
            return
        }

        _state.update { it.copy(isLoading = true) }

        searchJob = scope.launch(dispatchers.io) {
            delay(250) // debounce
            searchRepository.globalSearch(trimmed).fold(
                onSuccess = { result ->
                    searchRepository.addRecentSearch(trimmed)
                    _state.update {
                        it.copy(
                            results = result,
                            suggestions = computeSuggestions(result),
                            isLoading = false,
                            error = null,
                            recentSearches = searchRepository.getRecentSearches(),
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                },
            )
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            GlobalSearchState(recentSearches = searchRepository.getRecentSearches())
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun selectRecentSearch(query: String) {
        _state.update { it.copy(query = query) }
        updateQuery(query)
    }

    fun removeRecentSearch(query: String) {
        scope.launch(dispatchers.io) {
            searchRepository.removeRecentSearch(query)
            val recents = searchRepository.getRecentSearches()
            _state.update { it.copy(recentSearches = recents) }
        }
    }

    fun clearRecentSearches() {
        scope.launch(dispatchers.io) {
            searchRepository.clearRecentSearches()
            _state.update { it.copy(recentSearches = emptyList()) }
        }
    }

    fun expandCategory(category: String) {
        val current = _state.value
        val newExpanded = current.expandedCategories + category
        _state.update { it.copy(expandedCategories = newExpanded) }

        // Re-fetch with higher limit if not already done
        if (current.expandedResults == null) {
            scope.launch(dispatchers.io) {
                val trimmed = current.query.trim()
                searchRepository.globalSearch(trimmed, limit = 50).fold(
                    onSuccess = { result ->
                        _state.update { it.copy(expandedResults = result) }
                    },
                    onFailure = { /* Keep showing original results */ },
                )
            }
        }
    }

    fun collapseCategory(category: String) {
        _state.update { it.copy(expandedCategories = it.expandedCategories - category) }
    }

    private fun loadRecentSearches() {
        scope.launch(dispatchers.io) {
            val recents = searchRepository.getRecentSearches()
            _state.update { it.copy(recentSearches = recents) }
        }
    }

    private companion object {
        const val SUGGESTION_CAP = 5

        fun computeSuggestions(result: GlobalSearchResult): List<SearchSuggestion> {
            val items = mutableListOf<SearchSuggestion>()

            // Priority 1: Up to 2 games
            result.games.results.take(2).forEach { game ->
                items.add(
                    SearchSuggestion(
                        id = game.id,
                        name = game.title,
                        subtitle = game.consoleName,
                        imageUrl = game.coverUrl,
                        navigationType = SuggestionNavigationType.Game(game.id),
                    )
                )
            }

            // Priority 2: Up to 1 console
            result.consoles.results.take(1).forEach { console ->
                items.add(
                    SearchSuggestion(
                        id = console.id,
                        name = console.name,
                        subtitle = "${console.gameCount} games",
                        imageUrl = console.iconUrl.ifBlank { null },
                        navigationType = SuggestionNavigationType.Console(console.id),
                    )
                )
            }

            // Priority 3: Up to 1 developer
            result.developers.results.take(1).forEach { dev ->
                items.add(
                    SearchSuggestion(
                        id = dev.name,
                        name = dev.name,
                        subtitle = "${dev.gameCount} games",
                        navigationType = SuggestionNavigationType.Developer(dev.name),
                    )
                )
            }

            // Priority 4: Up to 1 publisher
            result.publishers.results.take(1).forEach { pub ->
                items.add(
                    SearchSuggestion(
                        id = pub.name,
                        name = pub.name,
                        subtitle = "${pub.gameCount} games",
                        navigationType = SuggestionNavigationType.Publisher(pub.name),
                    )
                )
            }

            // Priority 5: Up to 1 collection
            result.collections.results.take(1).forEach { coll ->
                items.add(
                    SearchSuggestion(
                        id = coll.id,
                        name = coll.name,
                        subtitle = "${coll.gameCount} games",
                        imageUrl = coll.coverUrl,
                        navigationType = SuggestionNavigationType.Collection(coll.id),
                    )
                )
            }

            // Priority 6: Up to 1 series
            result.series.results.take(1).forEach { s ->
                items.add(
                    SearchSuggestion(
                        id = s.id,
                        name = s.name,
                        subtitle = "${s.libraryGames}/${s.totalGames} games in library",
                        navigationType = SuggestionNavigationType.Series(s.id, s.name),
                    )
                )
            }

            // Priority 7: Up to 1 franchise
            result.franchises.results.take(1).forEach { f ->
                items.add(
                    SearchSuggestion(
                        id = f.id,
                        name = f.name,
                        subtitle = "${f.libraryGames}/${f.totalGames} games in library",
                        navigationType = SuggestionNavigationType.Franchise(f.id, f.name),
                    )
                )
            }

            return items.take(SUGGESTION_CAP)
        }
    }
}
