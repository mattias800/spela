package com.spela.player.data.repository

import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ServerRepositoryImpl : ServerRepository {

    private val servers = MutableStateFlow<List<ServerConnection>>(emptyList())
    private var nextId = 1

    override fun observeServers(): Flow<List<ServerConnection>> = servers

    override suspend fun getServers(): List<ServerConnection> = servers.value

    override suspend fun getActiveServer(): ServerConnection? =
        servers.value.firstOrNull { it.isActive }

    override suspend fun addServer(name: String, url: String): ServerConnection {
        val server = ServerConnection(
            id = (nextId++).toString(),
            name = name,
            url = url.trimEnd('/'),
            isActive = servers.value.isEmpty(),
        )
        servers.update { it + server }
        return server
    }

    override suspend fun removeServer(id: String) {
        servers.update { list ->
            val removed = list.filter { it.id != id }
            if (removed.none { it.isActive } && removed.isNotEmpty()) {
                removed.mapIndexed { index, server ->
                    if (index == 0) server.copy(isActive = true) else server
                }
            } else {
                removed
            }
        }
    }

    override suspend fun setActiveServer(id: String) {
        servers.update { list ->
            list.map { it.copy(isActive = it.id == id) }
        }
    }
}
