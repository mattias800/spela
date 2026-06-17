package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ConnectedConsole
import com.spela.player.domain.model.RemoteGame
import com.spela.player.domain.repository.FederationRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectedServersState(
    val consoles: List<ConnectedConsole> = emptyList(),
    val selectedConsole: String? = null,
    val games: List<RemoteGame> = emptyList(),
    val isLoadingConsoles: Boolean = false,
    val isLoadingGames: Boolean = false,
    val error: String? = null,
)

sealed class ConnectedServersIntent {
    data object Load : ConnectedServersIntent()
    data class SelectConsole(val console: String) : ConnectedServersIntent()
}

/**
 * Browse games available on connected federation servers (parallel-worlds:
 * separate from the local library). Loads the per-console counts, then the
 * games for a selected console. See #1391.
 */
class ConnectedServersViewModel(
    private val federationRepository: FederationRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ConnectedServersState())
    val state: StateFlow<ConnectedServersState> = _state.asStateFlow()

    private var gamesJob: Job? = null

    fun onIntent(intent: ConnectedServersIntent) {
        when (intent) {
            ConnectedServersIntent.Load -> load()
            is ConnectedServersIntent.SelectConsole -> selectConsole(intent.console)
        }
    }

    private fun load() {
        scope.launch(dispatchers.io) {
            _state.update { it.copy(isLoadingConsoles = true, error = null) }
            federationRepository.getConnectedConsoles().fold(
                onSuccess = { consoles ->
                    _state.update { it.copy(consoles = consoles, isLoadingConsoles = false) }
                    // Auto-select the first console so the grid isn't empty on entry.
                    consoles.firstOrNull()?.let { selectConsole(it.console) }
                },
                onFailure = { e ->
                    _state.update { it.copy(isLoadingConsoles = false, error = e.message) }
                },
            )
        }
    }

    private fun selectConsole(console: String) {
        if (_state.value.selectedConsole == console && _state.value.games.isNotEmpty()) return
        gamesJob?.cancel()
        _state.update { it.copy(selectedConsole = console, games = emptyList()) }
        gamesJob = scope.launch(dispatchers.io) {
            _state.update { it.copy(isLoadingGames = true, error = null) }
            federationRepository.getGamesForConsole(console).fold(
                onSuccess = { games -> _state.update { it.copy(games = games, isLoadingGames = false) } },
                onFailure = { e -> _state.update { it.copy(isLoadingGames = false, error = e.message) } },
            )
        }
    }
}
