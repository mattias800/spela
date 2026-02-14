package com.spela.player.presentation.viewmodel

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.usecase.GetGameDetailUseCase
import com.spela.player.domain.usecase.ToggleFavoriteUseCase
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.domain.repository.RatingRepository
import com.spela.player.domain.repository.SaveRepository
import com.spela.player.domain.repository.SharedSaveRepository
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
    private val ratingRepository: RatingRepository,
    private val sharedSaveRepository: SharedSaveRepository,
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
            is GameDetailIntent.RateGame -> rateGame(intent.rating, intent.review)
            GameDetailIntent.DeleteRating -> deleteRating()
            GameDetailIntent.LoadSharedSaves -> loadSharedSaves()
            is GameDetailIntent.ShareSave -> shareSave(intent.saveId, intent.name, intent.description)
            is GameDetailIntent.DownloadSharedSave -> downloadSharedSave(intent.saveId)
            is GameDetailIntent.DeleteSharedSave -> deleteSharedSave(intent.saveId)
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
                    val myRating = detail.game.userRating
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    val sharedSaves = sharedSaveRepository.getSharedSaves(gameId).getOrDefault(emptyList())
                    _state.update {
                        it.copy(
                            gameDetail = detail,
                            saveStates = saves,
                            sharedSaves = sharedSaves,
                            isGameCached = isCached,
                            myRating = myRating,
                            ratingSummary = summary,
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
                _state.update { it.copy(isScraping = true) }
                apiClient.scrapeIfNeeded(gameId)

                var attempts = 0
                val maxAttempts = 30
                while (attempts < maxAttempts) {
                    delay(1000)
                    getGameDetailUseCase(gameId).fold(
                        onSuccess = { refreshed ->
                            if (refreshed.game.scrapeAttempts > 0) {
                                _state.update { it.copy(gameDetail = refreshed, isScraping = false) }
                                return@launch
                            }
                        },
                        onFailure = { /* continue polling */ },
                    )
                    attempts++
                }

                _state.update { it.copy(isScraping = false) }
            } catch (e: Exception) {
                println("GameDetailViewModel: scrape failed for game $gameId: ${e.message}")
                _state.update { it.copy(isScraping = false) }
            }
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

    private fun rateGame(rating: Int, review: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isRating = true, myRating = rating) }
        scope.launch(dispatchers.io) {
            ratingRepository.rateGame(gameId, rating, review).fold(
                onSuccess = {
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    _state.update {
                        it.copy(
                            myRating = rating,
                            ratingSummary = summary,
                            isRating = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isRating = false) }
                },
            )
        }
    }

    private fun deleteRating() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            ratingRepository.deleteRating(gameId).fold(
                onSuccess = {
                    val summary = ratingRepository.getRatingSummary(gameId).getOrNull()
                    _state.update {
                        it.copy(
                            myRating = null,
                            ratingSummary = summary,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun loadSharedSaves() {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.getSharedSaves(gameId).fold(
                onSuccess = { saves ->
                    _state.update { it.copy(sharedSaves = saves) }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun shareSave(saveId: String, name: String, description: String) {
        val gameId = currentGameId ?: return
        _state.update { it.copy(isSharing = true) }
        scope.launch(dispatchers.io) {
            val saveData = saveRepository.downloadSaveState(gameId, saveId).getOrElse {
                _state.update { it.copy(error = "Failed to read save data", isSharing = false) }
                return@launch
            }
            sharedSaveRepository.shareSave(gameId, name, description, saveData).fold(
                onSuccess = { shared ->
                    _state.update {
                        it.copy(
                            sharedSaves = listOf(shared) + it.sharedSaves,
                            isSharing = false,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message, isSharing = false) }
                },
            )
        }
    }

    private fun downloadSharedSave(saveId: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.downloadSharedSave(gameId, saveId).fold(
                onSuccess = { data ->
                    saveRepository.uploadSaveState(gameId, "Shared Save", data).fold(
                        onSuccess = { newSave ->
                            _state.update { it.copy(saveStates = it.saveStates + newSave) }
                        },
                        onFailure = { error ->
                            _state.update { it.copy(error = error.message) }
                        },
                    )
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }

    private fun deleteSharedSave(saveId: String) {
        val gameId = currentGameId ?: return
        scope.launch(dispatchers.io) {
            sharedSaveRepository.deleteSharedSave(gameId, saveId).fold(
                onSuccess = {
                    _state.update {
                        it.copy(sharedSaves = it.sharedSaves.filter { s -> s.id != saveId })
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(error = error.message) }
                },
            )
        }
    }
}
