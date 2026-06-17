package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ImportJob
import com.spela.player.domain.model.RemoteGame
import com.spela.player.domain.repository.AuthRepository
import com.spela.player.domain.repository.FederationRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RemoteGameDetailState(
    val game: RemoteGame? = null,
    val isLoading: Boolean = false,
    val notFound: Boolean = false,
    val canImport: Boolean = false,
    val currentJob: ImportJob? = null, // this game's import job, if any
    val starting: Boolean = false, // import request in flight
    val error: String? = null,
)

sealed class RemoteGameDetailIntent {
    /** [preloaded] is the catalog entry from the browse list, so the screen
     *  renders instantly; null when deep-linked (then we fetch by key). */
    data class Load(val key: String, val preloaded: RemoteGame? = null) : RemoteGameDetailIntent()
    data object StartImport : RemoteGameDetailIntent()
}

/**
 * Detail + import for a single connected-server game. Checks the import
 * capability, starts an import, and polls the import queue for this game's
 * job while it's in flight. See #1391.
 */
class RemoteGameDetailViewModel(
    private val federationRepository: FederationRepository,
    private val authRepository: AuthRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(RemoteGameDetailState())
    val state: StateFlow<RemoteGameDetailState> = _state.asStateFlow()

    private var key: String = ""
    private var pollJob: Job? = null

    fun onIntent(intent: RemoteGameDetailIntent) {
        when (intent) {
            is RemoteGameDetailIntent.Load -> load(intent.key, intent.preloaded)
            RemoteGameDetailIntent.StartImport -> startImport()
        }
    }

    private fun load(key: String, preloaded: RemoteGame?) {
        // Cancel any poll from a previous game and clear its job before switching
        // keys, so a late poll can't write the old game's status onto the new one.
        pollJob?.cancel()
        this.key = key
        _state.update { it.copy(currentJob = null) }
        scope.launch(dispatchers.io) {
            authRepository.getCurrentUser().onSuccess { user ->
                val canImport = user.role == "admin" || user.role == "owner" || user.canImportGames
                _state.update { it.copy(canImport = canImport) }
            }
            if (preloaded != null) {
                _state.update { it.copy(game = preloaded) }
            } else {
                _state.update { it.copy(isLoading = true) }
                federationRepository.getRemoteGame(key).fold(
                    onSuccess = { game ->
                        _state.update { it.copy(game = game, notFound = game == null, isLoading = false) }
                    },
                    onFailure = { e -> _state.update { it.copy(error = e.message, isLoading = false) } },
                )
            }
            startPolling()
        }
    }

    private fun startImport() {
        val game = _state.value.game ?: return
        scope.launch(dispatchers.io) {
            _state.update { it.copy(starting = true, error = null) }
            federationRepository.startImport(game.key, game.title, game.console).fold(
                onSuccess = { job ->
                    _state.update { it.copy(currentJob = job, starting = false) }
                    startPolling()
                },
                onFailure = { e -> _state.update { it.copy(starting = false, error = e.message) } },
            )
        }
    }

    private suspend fun refreshJobStatus() {
        federationRepository.listImports().onSuccess { jobs ->
            _state.update { state -> state.copy(currentJob = jobs.firstOrNull { it.key == key }) }
        }
    }

    /** Poll the import queue while this game's job is active; stop once terminal/absent. */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch(dispatchers.io) {
            while (isActive) {
                refreshJobStatus()
                val job = _state.value.currentJob
                if (job == null || !job.status.isActive) break
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1500L
    }
}
