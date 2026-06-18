package com.spela.player.data.repository

import com.spela.client.models.StartImportInputBody
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.ConnectedConsole
import com.spela.player.domain.model.FriendPresence
import com.spela.player.domain.model.ImportJob
import com.spela.player.domain.model.RemoteGame
import com.spela.player.domain.repository.FederationRepository

class FederationRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : FederationRepository {
    override suspend fun getConnectedConsoles(): Result<List<ConnectedConsole>> = runCatching {
        apiClient.getFederationConsoles().consoles.map { it.toDomain() }
    }

    override suspend fun getGamesForConsole(console: String): Result<List<RemoteGame>> = runCatching {
        apiClient.getFederationAvailableGames(console = console).games.map { it.toDomain() }
    }

    override suspend fun getRemoteGame(key: String): Result<RemoteGame?> = runCatching {
        apiClient.getFederationAvailableGames(key = key).games.firstOrNull()?.toDomain()
    }

    override suspend fun startImport(key: String, title: String, console: String): Result<ImportJob> = runCatching {
        apiClient
            .startFederationImport(StartImportInputBody(console = console, key = key, title = title))
            .job
            .toDomain()
    }

    override suspend fun listImports(): Result<List<ImportJob>> = runCatching {
        apiClient.getFederationImports().imports.map { it.toDomain() }
    }

    override suspend fun getAggregatedPresence(): Result<List<FriendPresence>> = runCatching {
        apiClient.getFederationPresence().presence.map { it.toDomain() }
    }
}
