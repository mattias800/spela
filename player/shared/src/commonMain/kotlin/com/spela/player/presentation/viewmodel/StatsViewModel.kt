package com.spela.player.presentation.viewmodel

import com.spela.player.domain.usecase.GetMostActivePlayersUseCase
import com.spela.player.domain.usecase.GetMostPlayedGamesUseCase
import com.spela.player.presentation.intent.StatsIntent
import com.spela.player.presentation.state.StatsState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class StatsViewModel(
    private val getMostPlayedGamesUseCase: GetMostPlayedGamesUseCase,
    private val getMostActivePlayersUseCase: GetMostActivePlayersUseCase,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun onIntent(intent: StatsIntent) {
        when (intent) {
            StatsIntent.LoadStats -> loadStats()
            StatsIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadStats() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val games = getMostPlayedGamesUseCase().getOrDefault(emptyList())
            val players = getMostActivePlayersUseCase().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    mostPlayedGames = games,
                    activePlayers = players,
                    isLoading = false,
                    error = if (games.isEmpty() && players.isEmpty()) "Failed to load stats" else null,
                )
            }
        }
    }
}
