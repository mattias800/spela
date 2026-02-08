package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.data.remote.dto.toGameDetail
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.repository.GameRepository

class GameRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : GameRepository {

    override suspend fun getConsoles(): Result<List<Console>> = runCatching {
        apiClient.getConsoles().map { it.toDomain() }
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = runCatching {
        apiClient.getGamesForConsole(consoleId).map { it.toDomain() }
    }

    override suspend fun getAllGames(): Result<List<Game>> = runCatching {
        apiClient.getAllGames().data.map { it.toDomain() }
    }

    override suspend fun searchGames(query: String): Result<List<Game>> = runCatching {
        apiClient.searchGames(query).data.map { it.toDomain() }
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> = runCatching {
        apiClient.getGameDetail(gameId).toGameDetail()
    }

    override suspend fun getRecentGames(): Result<List<Game>> = runCatching {
        apiClient.getRecentGames().map { it.toDomain() }
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> = runCatching {
        apiClient.getFavoriteGames().map { it.toDomain() }
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.addFavorite(gameId)
    }

    override suspend fun removeFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.removeFavorite(gameId)
    }
}
