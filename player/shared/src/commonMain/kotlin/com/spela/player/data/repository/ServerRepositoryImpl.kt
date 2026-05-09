package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.domain.model.ServerConnection
import com.spela.player.domain.repository.ServerRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ServerRepositoryImpl(
    private val database: SpelaDatabase,
    private val engineFactory: HttpClientEngineFactory<*>,
    private val apiClient: SpelaApiClient,
) : ServerRepository {

    private val queries = database.spelaDatabaseQueries
    private val servers = MutableStateFlow<List<ServerConnection>>(emptyList())

    init {
        refreshFromDb()
        // If a previous run left an active server in the DB, bind the
        // shared SpelaApiClient to it immediately. Without this, the
        // very first API call from any repository runs against an
        // empty baseUrl (Ktor falls back to localhost:80) until the
        // user explicitly logs in or RestoreSessionUseCase fires —
        // both of which can race the test harness's direct Koin
        // access to a repository (issue #1146).
        queries.getActiveServer().executeAsOneOrNull()?.let { entity ->
            apiClient.setBaseUrl(entity.url)
        }
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
        if (isFirst) {
            // First server is auto-activated by the DB schema; bind
            // the API client to its URL so any code that grabs a
            // repository via Koin (UI flow OR test harness) doesn't
            // run against an empty baseUrl (#1146).
            apiClient.setBaseUrl(normalizedUrl)
        }
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
                apiClient.setBaseUrl(remaining.first().url)
            }
        }
        refreshFromDb()
    }

    override suspend fun setActiveServer(id: String) {
        queries.setActiveServer(id)
        refreshFromDb()
        // Whenever the active server changes, the SpelaApiClient must
        // follow. Otherwise the next non-auth API call (e.g. a Koin-
        // resolved SessionRepository in a test, or the user's first
        // browse on a fresh login) targets an empty baseUrl (#1146).
        queries.getActiveServer().executeAsOneOrNull()?.let { entity ->
            apiClient.setBaseUrl(entity.url)
        }
    }

    override suspend fun validateServer(url: String): Boolean {
        return try {
            val normalizedUrl = url.trimEnd('/')
            val client = HttpClient(engineFactory)
            val response = client.use { it.get("$normalizedUrl/api/health") }
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
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
