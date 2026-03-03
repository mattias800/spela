package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.DownloadProgress
import com.spela.player.domain.model.DownloadedGame
import com.spela.player.domain.repository.DownloadRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadsState(
    val activeDownloads: List<DownloadProgress> = emptyList(),
    val downloadedGames: List<DownloadedGame> = emptyList(),
    val cacheSize: Long = 0,
    val isLoading: Boolean = false,
    val isClearingCache: Boolean = false,
    val cancellingGameIds: Set<String> = emptySet(),
)

sealed interface DownloadsIntent {
    data object LoadDownloads : DownloadsIntent
    data class CancelDownload(val gameId: String) : DownloadsIntent
    data class DeleteLocalGame(val gameId: String) : DownloadsIntent
    data object ClearCache : DownloadsIntent
}

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DownloadsState())
    val state: StateFlow<DownloadsState> = _state.asStateFlow()

    init {
        scope.launch(dispatchers.io) {
            downloadRepository.observeDownloads().collect { downloads ->
                _state.update { it.copy(activeDownloads = downloads) }
            }
        }
        scope.launch(dispatchers.io) {
            downloadRepository.observeDownloadedGames().collect { games ->
                _state.update { it.copy(downloadedGames = games) }
            }
        }
    }

    fun onIntent(intent: DownloadsIntent) {
        when (intent) {
            DownloadsIntent.LoadDownloads -> loadDownloads()
            is DownloadsIntent.CancelDownload -> cancelDownload(intent.gameId)
            is DownloadsIntent.DeleteLocalGame -> deleteLocalGame(intent.gameId)
            DownloadsIntent.ClearCache -> clearCache()
        }
    }

    private fun loadDownloads() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            val cacheSize = downloadRepository.getCacheSize()
            _state.update { it.copy(cacheSize = cacheSize, isLoading = false) }
        }
    }

    private fun cancelDownload(gameId: String) {
        _state.update { it.copy(cancellingGameIds = it.cancellingGameIds + gameId) }
        scope.launch(dispatchers.io) {
            downloadRepository.cancelDownload(gameId)
            _state.update { it.copy(cancellingGameIds = it.cancellingGameIds - gameId) }
        }
    }

    private fun deleteLocalGame(gameId: String) {
        scope.launch(dispatchers.io) {
            downloadRepository.deleteLocalGame(gameId)
            val cacheSize = downloadRepository.getCacheSize()
            _state.update { it.copy(cacheSize = cacheSize) }
        }
    }

    private fun clearCache() {
        _state.update { it.copy(isClearingCache = true) }
        scope.launch(dispatchers.io) {
            downloadRepository.clearCache()
            _state.update { it.copy(cacheSize = 0, isClearingCache = false) }
        }
    }
}
