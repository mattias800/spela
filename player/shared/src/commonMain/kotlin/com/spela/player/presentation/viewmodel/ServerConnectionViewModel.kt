package com.spela.player.presentation.viewmodel

import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.repository.ServerRepository
import com.spela.player.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerConnectionState(
    val servers: List<ServerConnection> = emptyList(),
    val newServerName: String = "",
    val newServerUrl: String = "",
    val isAddingServer: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedServerId: String? = null,
)

sealed interface ServerConnectionIntent {
    data object LoadServers : ServerConnectionIntent
    data class SetNewServerName(val name: String) : ServerConnectionIntent
    data class SetNewServerUrl(val url: String) : ServerConnectionIntent
    data object ToggleAddServer : ServerConnectionIntent
    data object AddServer : ServerConnectionIntent
    data class SelectServer(val serverId: String) : ServerConnectionIntent
    data class RemoveServer(val serverId: String) : ServerConnectionIntent
    data object DismissError : ServerConnectionIntent
}

class ServerConnectionViewModel(
    private val serverRepository: ServerRepository,
    private val dispatchers: DispatcherProvider,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ServerConnectionState())
    val state: StateFlow<ServerConnectionState> = _state.asStateFlow()

    fun onIntent(intent: ServerConnectionIntent) {
        when (intent) {
            ServerConnectionIntent.LoadServers -> loadServers()
            is ServerConnectionIntent.SetNewServerName -> _state.update { it.copy(newServerName = intent.name) }
            is ServerConnectionIntent.SetNewServerUrl -> _state.update { it.copy(newServerUrl = intent.url) }
            ServerConnectionIntent.ToggleAddServer -> _state.update { it.copy(isAddingServer = !it.isAddingServer) }
            ServerConnectionIntent.AddServer -> addServer()
            is ServerConnectionIntent.SelectServer -> selectServer(intent.serverId)
            is ServerConnectionIntent.RemoveServer -> removeServer(intent.serverId)
            ServerConnectionIntent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun loadServers() {
        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            try {
                val servers = serverRepository.getServers()
                val active = serverRepository.getActiveServer()
                _state.update {
                    it.copy(
                        servers = servers,
                        selectedServerId = active?.id,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun addServer() {
        val current = _state.value
        if (current.newServerName.isBlank() || current.newServerUrl.isBlank()) {
            _state.update { it.copy(error = "Name and URL are required") }
            return
        }

        _state.update { it.copy(isLoading = true) }
        scope.launch(dispatchers.io) {
            try {
                serverRepository.addServer(current.newServerName, current.newServerUrl)
                val servers = serverRepository.getServers()
                _state.update {
                    it.copy(
                        servers = servers,
                        newServerName = "",
                        newServerUrl = "",
                        isAddingServer = false,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    private fun selectServer(serverId: String) {
        scope.launch(dispatchers.io) {
            try {
                serverRepository.setActiveServer(serverId)
                _state.update { it.copy(selectedServerId = serverId) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun removeServer(serverId: String) {
        scope.launch(dispatchers.io) {
            try {
                serverRepository.removeServer(serverId)
                val servers = serverRepository.getServers()
                _state.update { it.copy(servers = servers) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}
