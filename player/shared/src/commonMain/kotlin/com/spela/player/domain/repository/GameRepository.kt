package com.spela.player.domain.repository

import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.TopRatedGame

interface GameRepository {
    suspend fun getConsoles(): Result<List<Console>>
    suspend fun getGamesForConsole(consoleId: String): Result<List<Game>>
    suspend fun getAllGames(): Result<List<Game>>
    suspend fun searchGames(
        query: String,
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
    ): Result<List<Game>>
    suspend fun getGameDetail(gameId: String): Result<GameDetail>
    suspend fun getRecentGames(): Result<List<Game>>
    suspend fun getFavoriteGames(): Result<List<Game>>
    suspend fun addFavorite(gameId: String): Result<Unit>
    suspend fun removeFavorite(gameId: String): Result<Unit>
    suspend fun getPlayLaterGames(): Result<List<Game>>
    suspend fun addToPlayLater(gameId: String): Result<Unit>
    suspend fun removeFromPlayLater(gameId: String): Result<Unit>
    suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>>
}
