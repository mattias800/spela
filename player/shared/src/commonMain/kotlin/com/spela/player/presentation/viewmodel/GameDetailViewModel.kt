package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.presentation.intent.GameDetailIntent
import com.spela.player.presentation.state.GameDetailState
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameDetailViewModel(
    private val getGameDetailUseCase: GetGameDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val downloadRepository: DownloadRepository,
    private val saveRepository: SaveRepository,
    private val apiClient: SpelaApiClient,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(GameDetailState())
    val state: StateFlow<GameDetailState> = _state.asStateFlow()

    private var currentGameId: String? = null

    fun onIntent(intent: GameDetailIntent) {
        when (intent) {
            is GameDetailIntent.LoadGame -> loadGame(intent.gameId)
            GameDetailIntent.DownloadGame -> downloadGame()
            GameDetailIntent.PlayGame -> { /* Handled by UI navigation to emulation screen */ }
            GameDetailIntent.DeleteLocalGame -> deleteLocalGame()
            GameDetailIntent.ToggleFavorite -> toggleFavorite()
            GameDetailIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadGame(gameId: String) {
        currentGameId = gameId
        _state.update { it.copy(isLoading = true) }

        scope.launch(dispatchers.io) {
            getGameDetailUseCase(gameId).fold(
                onSuccess = { detail ->
                    val isCached = downloadRepository.isGameCached(gameId)
                    val saves = saveRepository.getSaveStates(gameId).getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            gameDetail = detail,
                            saveStates = saves,
                            isGameCached = isCached,
                            isLoading = false,
                        )
                    }
                    if (detail.game.scrapeAttempts == 0) {
                        scrapeAndRefresh(gameId)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isLoading = false) }
                },
            )
        }

        scope.launch(dispatchers.io) {
            downloadRepository.observeDownload(gameId).collect { progress ->
                _state.update { it.copy(downloadProgress = progress) }
            }
        }
    }

    private fun scrapeAndRefresh(gameId: String) {
        scope.launch(dispatchers.io) {
            try {
                apiClient.scrapeIfNeeded(gameId)
                delay(3000)
                getGameDetailUseCase(gameId).fold(
                    onSuccess = { refreshed ->
                        _state.update { it.copy(gameDetail = refreshed) }
                    },
                    onFailure = { /* ignore refresh failure */ },
                )
            } catch (_: Exception) { /* ignore scrape failure */ }
        }
    }

    private fun downloadGame() {
        val gameId = currentGameId ?: return
        val gameTitle = _state.value.gameDetail?.game?.title ?: ""
        scope.launch(dispatchers.io) {
            downloadRepository.downloadGame(gameId, gameTitle).fold(
                onSuccess = {
                    _state.update { it.copy(isGameCached = true) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun deleteLocalGame() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            downloadRepository.deleteLocalGame(gameId)
            _state.update { it.copy(isGameCached = false) }
        }
    }

    private fun toggleFavorite() {
        val detail = _state.value.gameDetail ?: return
        val currentlyFavorite = detail.game.isFavorite
        scope.launch(dispatchers.io) {
            toggleFavoriteUseCase(detail.game.id, currentlyFavorite).fold(
                onSuccess = {
                    _state.update {
                        val updatedGame = it.gameDetail?.game?.copy(isFavorite = !currentlyFavorite)
                        it.copy(
                            gameDetail = updatedGame?.let { g -> it.gameDetail?.copy(game = g) }
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
