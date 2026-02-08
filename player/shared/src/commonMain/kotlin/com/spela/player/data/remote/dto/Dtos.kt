package com.spela.player.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ConsoleDto(
    val id: Long,
    val name: String,
    val abbreviation: String,
    val extensions: String = "",
    val defaultCore: String = "",
    val coverAspect: String = "3:4",
    val colorTheme: String = "#6366f1",
    val gameCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class GameDto(
    val id: Long,
    val title: String,
    val consoleId: Long,
    val console: ConsoleDto? = null,
    val fileName: String = "",
    val fileSize: Long = 0,
    val coverUrl: String? = null,
    val screenshotUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val players: Int = 0,
    val rating: Double = 0.0,
    val coreOverride: String? = null,
    val scraperId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Wrapper for GET /api/games which returns paginated results */
@Serializable
data class GameListResponse(
    val games: List<GameDto>,
    val total: Long,
    val page: Int,
    val perPage: Int,
)

/** Wrapper for GET /api/consoles/:id/games */
@Serializable
data class ConsoleGamesResponse(
    val console: ConsoleDto,
    val games: List<GameDto>,
)

@Serializable
data class SaveStateDto(
    val id: Long,
    val userId: Long = 0,
    val gameId: Long,
    val name: String,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class LibretroCoreDto(
    val id: Long,
    val name: String,
    val displayName: String = "",
    val description: String? = null,
    val version: String? = null,
    val platforms: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Wrapper for GET /api/games/:id/core when core is not in DB */
@Serializable
data class CoreNameResponse(
    val coreName: String,
)

/** Play history entry returned by GET /api/user/recent */
@Serializable
data class PlayHistoryDto(
    val id: Long,
    val userId: Long = 0,
    val gameId: Long = 0,
    val game: GameDto? = null,
    val lastPlayed: String? = null,
    val playTime: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Favorite entry returned by GET /api/user/favorites */
@Serializable
data class FavoriteDto(
    val id: Long,
    val userId: Long = 0,
    val gameId: Long = 0,
    val game: GameDto? = null,
    val createdAt: String? = null,
)
