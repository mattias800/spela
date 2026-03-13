package com.spela.player.domain.repository

import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.PaginatedResult
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.LongestGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopRatedGame

interface GameRepository {
    suspend fun getConsoles(): Result<List<Console>>
    suspend fun getGamesForConsole(consoleId: String): Result<List<Game>>
    suspend fun getGamesForConsolePaginated(
        consoleId: String,
        page: Int = 1,
        pageSize: Int = 50,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
    ): Result<PaginatedResult<Game>>
    suspend fun getAllGames(): Result<List<Game>>
    suspend fun getAllGamesPaginated(
        page: Int = 1,
        pageSize: Int = 50,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
    ): Result<PaginatedResult<Game>>
    suspend fun searchGames(
        query: String,
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
    ): Result<List<Game>>
    suspend fun searchGamesPaginated(
        query: String,
        consoleId: String? = null,
        sortBy: String? = null,
        sortOrder: String? = null,
        page: Int = 1,
        pageSize: Int = 50,
        hidePreRelease: Boolean = true,
        grouped: Boolean = true,
    ): Result<PaginatedResult<Game>>
    suspend fun getGameDetail(gameId: String): Result<GameDetail>
    suspend fun getRecentGames(): Result<List<Game>>
    suspend fun getFavoriteGames(): Result<List<Game>>
    suspend fun addFavorite(gameId: String): Result<Unit>
    suspend fun removeFavorite(gameId: String): Result<Unit>
    suspend fun getPlayLaterGames(): Result<List<Game>>
    suspend fun addToPlayLater(gameId: String): Result<Unit>
    suspend fun removeFromPlayLater(gameId: String): Result<Unit>
    suspend fun getRecentlyAddedGames(): Result<List<Game>>
    suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>>
    suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>>
    suspend fun getTopRatedAvailable(): Result<List<TopListGame>>
    suspend fun getLongestGames(): Result<List<LongestGame>>
    suspend fun getSimilarGames(gameId: String): Result<List<SimilarGame>>
    suspend fun getDeveloperGames(gameId: String): Result<List<DeveloperGame>>
}
