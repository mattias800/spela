package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.TopListTab
import com.spela.player.domain.repository.GameRepository
import com.spela.player.presentation.intent.TopListsIntent
import com.spela.player.presentation.state.TopListsState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TopListsViewModel(
    private val gameRepository: GameRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TopListsState())
    val state: StateFlow<TopListsState> = _state.asStateFlow()

    fun onIntent(intent: TopListsIntent) {
        when (intent) {
            TopListsIntent.LoadTopLists -> loadTopLists()
            is TopListsIntent.SelectTab -> selectTab(intent.tab)
            TopListsIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun selectTab(tab: TopListTab) {
        _state.update { it.copy(selectedTab = tab) }
        when (tab) {
            TopListTab.TOP_RATED -> {
                if (_state.value.games.isEmpty()) loadTopLists()
            }
            TopListTab.LONGEST -> {
                if (_state.value.longestGames.isEmpty()) loadLongestGames()
            }
        }
    }

    private fun loadTopLists() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val result = gameRepository.getTopRatedAvailable()
            result.fold(
                onSuccess = { games ->
                    _state.update {
                        it.copy(
                            games = games,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load top lists",
                        )
                    }
                },
            )
        }
    }

    private fun loadLongestGames() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val result = gameRepository.getLongestGames()
            result.fold(
                onSuccess = { games ->
                    _state.update {
                        it.copy(
                            longestGames = games,
                            isLoading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load longest games",
                        )
                    }
                },
            )
        }
    }
}
