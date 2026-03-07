package com.spela.player.data.repository

import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.ConnectivityMonitor
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.data.remote.dto.toGameDetail
import com.spela.player.domain.model.Console
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameDetail
import com.spela.player.domain.model.DeveloperGame
import com.spela.player.domain.model.SimilarGame
import com.spela.player.domain.model.TopListGame
import com.spela.player.domain.model.TopRatedGame
import com.spela.player.domain.repository.GameRepository
import kotlin.time.Clock

class GameRepositoryImpl(
    private val apiClient: SpelaApiClient,
    private val database: SpelaDatabase,
    private val connectivityMonitor: ConnectivityMonitor,
) : GameRepository {

    override suspend fun getConsoles(): Result<List<Console>> {
        return runCatching {
            val consoles = apiClient.getConsoles().map { it.toDomain().resolveImageUrls() }
            cacheConsoles(consoles)
            consoles
        }.recoverCatching {
            val cached = getCachedConsoles()
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun getGamesForConsole(consoleId: String): Result<List<Game>> {
        return runCatching {
            val games = apiClient.getGamesForConsole(consoleId, pageSize = 200).data
                .map { it.toDomain().resolveImageUrls() }
            cacheGames(games)
            games
        }.recoverCatching {
            val cached = getCachedGamesForConsole(consoleId)
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun getAllGames(): Result<List<Game>> {
        return runCatching {
            val games = apiClient.getAllGames().data.map { it.toDomain().resolveImageUrls() }
            cacheGames(games)
            games
        }.recoverCatching {
            val cached = getAllCachedGames()
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun searchGames(
        query: String,
        consoleId: String?,
        sortBy: String?,
        sortOrder: String?,
    ): Result<List<Game>> {
        return runCatching {
            apiClient.searchGames(query, consoleId, sortBy, sortOrder).data.map { it.toDomain().resolveImageUrls() }
        }.recoverCatching {
            val cached = searchCachedGames(query)
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun getGameDetail(gameId: String): Result<GameDetail> {
        return runCatching {
            val detail = apiClient.getGameDetail(gameId).toGameDetail()
            detail.copy(
                game = detail.game.resolveImageUrls(),
                screenshots = detail.screenshots.mapNotNull { apiClient.resolveUrl(it) },
            )
        }.recoverCatching {
            val cachedGame = getCachedGame(gameId)
            if (cachedGame != null) GameDetail(game = cachedGame) else throw it
        }
    }

    override suspend fun getRecentGames(): Result<List<Game>> {
        return runCatching {
            val games = apiClient.getRecentGames().map { it.toDomain().resolveImageUrls() }
            cacheGames(games)
            games
        }.recoverCatching {
            val cached = getCachedRecentGames()
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun getFavoriteGames(): Result<List<Game>> {
        return runCatching {
            val games = apiClient.getFavoriteGames().map { it.toDomain().resolveImageUrls() }
            cacheGames(games)
            games
        }.recoverCatching {
            val cached = getCachedFavorites()
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun addFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.addFavorite(gameId)
    }

    override suspend fun removeFavorite(gameId: String): Result<Unit> = runCatching {
        apiClient.removeFavorite(gameId)
    }

    override suspend fun getPlayLaterGames(): Result<List<Game>> {
        return runCatching {
            val games = apiClient.getPlayLaterGames().map { it.toDomain().resolveImageUrls() }
            cacheGames(games)
            games
        }.recoverCatching {
            val cached = getCachedPlayLater()
            if (cached.isNotEmpty()) cached else throw it
        }
    }

    override suspend fun addToPlayLater(gameId: String): Result<Unit> = runCatching {
        apiClient.addToPlayLater(gameId)
    }

    override suspend fun removeFromPlayLater(gameId: String): Result<Unit> = runCatching {
        apiClient.removeFromPlayLater(gameId)
    }

    override suspend fun getRecentlyAddedGames(): Result<List<Game>> = runCatching {
        apiClient.getRecentlyAddedGames().data.map { it.toDomain().resolveImageUrls() }
    }

    override suspend fun getTopRatedGames(consoleId: String): Result<List<TopRatedGame>> {
        return runCatching {
            apiClient.getTopRatedGames(consoleId).map { it.toDomain() }
        }
    }

    override suspend fun getTopRatedGamesGlobal(): Result<List<TopRatedGame>> {
        return runCatching {
            apiClient.getTopRatedGamesGlobal().map { it.toDomain() }
        }
    }

    override suspend fun getTopRatedAvailable(): Result<List<TopListGame>> {
        return runCatching {
            apiClient.getTopRatedAvailable().map { it.toDomain() }
        }
    }

    override suspend fun getSimilarGames(gameId: String): Result<List<SimilarGame>> {
        return runCatching {
            apiClient.getSimilarGames(gameId).map { it.toDomain() }
        }
    }

    override suspend fun getDeveloperGames(gameId: String): Result<List<DeveloperGame>> {
        return runCatching {
            apiClient.getDeveloperGames(gameId).map { dto ->
                dto.toDomain().copy(coverUrl = apiClient.resolveUrl(dto.coverUrl))
            }
        }
    }

    // --- Cache helpers ---

    private fun cacheConsoles(consoles: List<Console>) {
        database.spelaDatabaseQueries.deleteAllCachedConsoles()
        consoles.forEach { c ->
            database.spelaDatabaseQueries.insertCachedConsole(
                id = c.id,
                name = c.name,
                abbreviation = c.abbreviation,
                game_count = c.gameCount.toLong(),
                color_theme = c.colorTheme,
                cover_aspect_ratio = c.coverAspectRatio,
                default_core = c.defaultCore,
                icon_url = c.iconUrl,
                logo_url = c.logoUrl,
                save_state_support = if (c.saveStateSupport) 1L else 0L,
            )
        }
    }

    private fun getCachedConsoles(): List<Console> {
        return database.spelaDatabaseQueries.getAllCachedConsoles().executeAsList().map {
            Console(
                id = it.id,
                name = it.name,
                abbreviation = it.abbreviation,
                gameCount = it.game_count.toInt(),
                colorTheme = it.color_theme,
                coverAspectRatio = it.cover_aspect_ratio,
                defaultCore = it.default_core,
                iconUrl = it.icon_url,
                logoUrl = it.logo_url,
                saveStateSupport = it.save_state_support != 0L,
            )
        }
    }

    private fun cacheGames(games: List<Game>) {
        val now = Clock.System.now().toEpochMilliseconds()
        games.forEach { g ->
            database.spelaDatabaseQueries.insertCachedGame(
                id = g.id,
                console_id = g.consoleId,
                title = g.title,
                console_name = g.consoleName,
                cover_url = g.coverUrl,
                description = g.description,
                developer = g.developer,
                publisher = g.publisher,
                release_date = g.releaseDate,
                genre = g.genre,
                file_size = g.fileSize,
                file_name = g.fileName,
                cover_aspect_ratio = g.coverAspectRatio.toDouble(),
                disc_count = g.discCount.toLong(),
                is_favorite = if (g.isFavorite) 1L else 0L,
                is_in_play_later = if (g.isInPlayLater) 1L else 0L,
                last_played_at = g.lastPlayedAt,
                total_play_time = g.totalPlayTime,
                cached_at = now,
            )
        }
    }

    private fun getCachedGamesForConsole(consoleId: String): List<Game> {
        return database.spelaDatabaseQueries.getCachedGamesForConsole(consoleId).executeAsList().map { it.toGame() }
    }

    private fun getAllCachedGames(): List<Game> {
        return database.spelaDatabaseQueries.getAllCachedGames().executeAsList().map { it.toGame() }
    }

    private fun getCachedGame(gameId: String): Game? {
        return database.spelaDatabaseQueries.getCachedGame(gameId).executeAsOneOrNull()?.toGame()
    }

    private fun searchCachedGames(query: String): List<Game> {
        return database.spelaDatabaseQueries.searchCachedGames(query).executeAsList().map { it.toGame() }
    }

    private fun getCachedFavorites(): List<Game> {
        return database.spelaDatabaseQueries.getCachedFavorites().executeAsList().map { it.toGame() }
    }

    private fun getCachedPlayLater(): List<Game> {
        return database.spelaDatabaseQueries.getCachedPlayLater().executeAsList().map { it.toGame() }
    }

    private fun getCachedRecentGames(): List<Game> {
        return database.spelaDatabaseQueries.getCachedRecentGames().executeAsList().map { it.toGame() }
    }

    private fun com.spela.player.GetCachedRecentGames.toGame(): Game = Game(
        id = id,
        title = title,
        consoleId = console_id,
        consoleName = console_name,
        coverAspectRatio = cover_aspect_ratio.toFloat(),
        coverUrl = cover_url,
        description = description,
        developer = developer,
        publisher = publisher,
        releaseDate = release_date,
        genre = genre,
        fileSize = file_size,
        fileName = file_name,
        discCount = disc_count.toInt(),
        isFavorite = is_favorite != 0L,
        isInPlayLater = is_in_play_later != 0L,
        lastPlayedAt = last_played_at,
        totalPlayTime = total_play_time,
    )

    private fun com.spela.player.CachedGameEntity.toGame(): Game = Game(
        id = id,
        title = title,
        consoleId = console_id,
        consoleName = console_name,
        coverAspectRatio = cover_aspect_ratio.toFloat(),
        coverUrl = cover_url,
        description = description,
        developer = developer,
        publisher = publisher,
        releaseDate = release_date,
        genre = genre,
        fileSize = file_size,
        fileName = file_name,
        discCount = disc_count.toInt(),
        isFavorite = is_favorite != 0L,
        isInPlayLater = is_in_play_later != 0L,
        lastPlayedAt = last_played_at,
        totalPlayTime = total_play_time,
    )

    /** Resolve relative image URLs to absolute URLs using the server base URL. */
    private fun Game.resolveImageUrls(): Game = copy(
        coverUrl = apiClient.resolveUrl(coverUrl),
    )

    private fun Console.resolveImageUrls(): Console = copy(
        iconUrl = apiClient.resolveUrl(iconUrl) ?: "",
        logoUrl = apiClient.resolveUrl(logoUrl) ?: "",
    )
}
