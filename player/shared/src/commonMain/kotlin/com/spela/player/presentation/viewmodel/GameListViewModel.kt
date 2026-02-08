package com.spela.player.presentation.viewmodel

import com.spela.player.domain.usecase.*
import com.spela.player.presentation.intent.GameListIntent
import com.spela.player.presentation.state.GameListState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameListViewModel(
    private val getConsolesUseCase: GetConsolesUseCase,
    private val getGamesForConsoleUseCase: GetGamesForConsoleUseCase,
    private val searchGamesUseCase: SearchGamesUseCase,
    private val getRecentGamesUseCase: GetRecentGamesUseCase,
    private val getFavoriteGamesUseCase: GetFavoriteGamesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GameListState())
    val state: StateFlow<GameListState> = _state.asStateFlow()

    fun onIntent(intent: GameListIntent) {
        when (intent) {
            GameListIntent.LoadDashboard -> loadDashboard()
            GameListIntent.LoadConsoles -> loadConsoles()
            is GameListIntent.SelectConsole -> loadGamesForConsole(intent.consoleId)
            is GameListIntent.Search -> searchGames(intent.query)
            is GameListIntent.ToggleFavorite -> toggleFavorite(intent.gameId, intent.isFavorite)
            GameListIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadDashboard() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val consoles = getConsolesUseCase().getOrDefault(emptyList())
            val recent = getRecentGamesUseCase().getOrDefault(emptyList())
            val favorites = getFavoriteGamesUseCase().getOrDefault(emptyList())
            _state.update {
                it.copy(
                    consoles = consoles,
                    recentGames = recent,
                    favoriteGames = favorites,
                    isLoading = false,
                )
            }
        }
    }

    private fun loadConsoles() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            getConsolesUseCase().fold(
                onSuccess = { consoles ->
                    _state.update { it.copy(consoles = consoles, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun loadGamesForConsole(consoleId: String) {
        _state.update { it.copy(isLoading = true, selectedConsoleId = consoleId) }
        scope.launch(dispatchers.io) {
            getGamesForConsoleUseCase(consoleId).fold(
                onSuccess = { games ->
                    _state.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun searchGames(query: String) {
        _state.update { it.copy(searchQuery = query, isLoading = true) }
        scope.launch(dispatchers.io) {
            searchGamesUseCase(query).fold(
                onSuccess = { games ->
                    _state.update { it.copy(games = games, isLoading = false) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }
    }

    private fun toggleFavorite(gameId: String, isFavorite: Boolean) {
        scope.launch(dispatchers.io) {
            toggleFavoriteUseCase(gameId, isFavorite).fold(
                onSuccess = {
                    val updatedFavorites = getFavoriteGamesUseCase().getOrDefault(emptyList())
                    _state.update { it.copy(favoriteGames = updatedFavorites) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
