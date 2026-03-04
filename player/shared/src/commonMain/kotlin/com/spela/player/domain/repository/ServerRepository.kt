package com.spela.player.domain.repository

import com.spela.player.domain.model.ServerConnection
import kotlinx.coroutines.flow.Flow

interface ServerRepository {
    fun observeServers(): Flow<List<ServerConnection>>
    suspend fun getServers(): List<ServerConnection>
    suspend fun getActiveServer(): ServerConnection?
    suspend fun addServer(name: String, url: String): ServerConnection
    suspend fun removeServer(id: String)
    suspend fun setActiveServer(id: String)
    suspend fun validateServer(url: String): Boolean
}
