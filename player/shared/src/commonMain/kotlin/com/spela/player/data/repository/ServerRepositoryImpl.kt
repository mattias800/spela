package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ServerRepositoryImpl(
    private val database: SpelaDatabase,
) : ServerRepository {

    private val queries = database.spelaDatabaseQueries
    private val servers = MutableStateFlow<List<ServerConnection>>(emptyList())

    init {
        refreshFromDb()
    }

    override fun observeServers(): Flow<List<ServerConnection>> = servers

    override suspend fun getServers(): List<ServerConnection> = servers.value

    override suspend fun getActiveServer(): ServerConnection? {
        val entity = queries.getActiveServer().executeAsOneOrNull() ?: return null
        return ServerConnection(
            id = entity.id,
            name = entity.name,
            url = entity.url,
            isActive = entity.is_active == 1L,
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun addServer(name: String, url: String): ServerConnection {
        val id = Uuid.random().toString()
        val normalizedUrl = url.trimEnd('/')
        val isFirst = queries.getAllServers().executeAsList().isEmpty()
        queries.insertServer(
            id = id,
            name = name,
            url = normalizedUrl,
            is_active = if (isFirst) 1L else 0L,
        )
        refreshFromDb()
        return ServerConnection(
            id = id,
            name = name,
            url = normalizedUrl,
            isActive = isFirst,
        )
    }

    override suspend fun removeServer(id: String) {
        val wasActive = queries.getActiveServer().executeAsOneOrNull()?.id == id
        queries.deleteServer(id)
        if (wasActive) {
            val remaining = queries.getAllServers().executeAsList()
            if (remaining.isNotEmpty()) {
                queries.setActiveServer(remaining.first().id)
            }
        }
        refreshFromDb()
    }

    override suspend fun setActiveServer(id: String) {
        queries.setActiveServer(id)
        refreshFromDb()
    }

    private fun refreshFromDb() {
        val entities = queries.getAllServers().executeAsList()
        servers.value = entities.map { entity ->
            ServerConnection(
                id = entity.id,
                name = entity.name,
                url = entity.url,
                isActive = entity.is_active == 1L,
            )
        }
    }
}
