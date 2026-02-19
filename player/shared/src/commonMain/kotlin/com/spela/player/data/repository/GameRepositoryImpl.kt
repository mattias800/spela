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
        apiClient.getConsoles().map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> = runCatching {
        apiClient.getGamesForConsole(consoleId).map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun getAllGames(): Result<List<Game>> = runCatching {
        apiClient.getAllGames().data.map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun searchGames(
        query: String,
        consoleId: String?,
        sortBy: String?,
        sortOrder: String?,
    ): Result<List<Game>> = runCatching {
        apiClient.searchGames(query, consoleId, sortBy, sortOrder).data.map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> = runCatching {
        val detail = apiClient.getGameDetail(gameId).toGameDetail()
        detail.copy(
            game = detail.game.resolveImageUrls(),
            screenshots = detail.screenshots.mapNotNull { apiClient.resolveUrl(it) },
        )
    }

    override suspend fun getRecentGames(): Result<List<Game>> = runCatching {
        apiClient.getRecentGames().map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> = runCatching {
        apiClient.getFavoriteGames().map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.addFavorite(gameId)
    }

    override suspend fun removeFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.removeFavorite(gameId)
    }

    override suspend fun getPlayLaterGames(): Result<List<Game>> = runCatching {
        apiClient.getPlayLaterGames().map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun addToPlayLater(gameId: String): Result<Unit> = runCatching {
        apiClient.addToPlayLater(gameId)
    }

    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = runCatching {
        apiClient.removeFromPlayLater(gameId)
    }

    /** Resolve relative image URLs to absolute URLs using the server base URL. */
    private fun Game.resolveImageUrls(): Game = copy(
        coverUrl = apiClient.resolveUrl(coverUrl),
    )

    private fun Console.resolveImageUrls(): Console = copy(
        iconUrl = apiClient.resolveUrl(iconUrl) ?: "",
    )
}
