package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.repository.ExploreRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExploreState(
    val featuredGames: List<FeaturedGame> = emptyList(),
    val rows: List<ExploreRow> = emptyList(),
    val isLoadingFeatured: Boolean = false,
    val isLoadingRows: Boolean = false,
    val error: String? = null,
) {
    val isLoading: Boolean get() = isLoadingFeatured || isLoadingRows
    val isEmpty: Boolean get() = featuredGames.isEmpty() && rows.isEmpty() && !isLoading
}

class ExploreViewModel(
    private val exploreRepository: ExploreRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private var featuredJob: Job? = null
    private var rowsJob: Job? = null

    fun load() {
        loadFeatured()
        loadRows()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun loadFeatured() {
        if (featuredJob?.isActive == true) return
        _state.update { it.copy(isLoadingFeatured = true) }
        featuredJob = scope.launch(dispatchers.io) {
            exploreRepository.getFeaturedGames().fold(
                onSuccess = { featured ->
                    _state.update { it.copy(featuredGames = featured, isLoadingFeatured = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingFeatured = false, error = error.message) }
                },
            )
        }
    }

    private fun loadRows() {
        if (rowsJob?.isActive == true) return
        _state.update { it.copy(isLoadingRows = true) }
        rowsJob = scope.launch(dispatchers.io) {
            exploreRepository.getExploreRows().fold(
                onSuccess = { rows ->
                    _state.update { it.copy(rows = rows, isLoadingRows = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(isLoadingRows = false, error = error.message) }
                },
            )
        }
    }
}
