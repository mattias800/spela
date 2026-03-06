package com.spela.player.presentation.viewmodel

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
            TopListsIntent.DismissError -> _state.update { it.copy(error = null) }
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
}
