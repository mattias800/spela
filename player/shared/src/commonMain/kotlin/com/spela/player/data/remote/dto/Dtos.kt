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
    val id: String,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Matches ConsoleResponse in responses.go */
@Serializable
data class ConsoleDto(
    val id: String,
    val name: String,
    val abbreviation: String,
    val extensions: List<String> = emptyList(),
    val defaultCore: String = "",
    val coverAspectRatio: Double = 0.75,
    val colorTheme: String = "#6366f1",
    val iconUrl: String = "",
    val gameCount: Int = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Matches GameResponse in responses.go - enriched with consoleName, isFavorite, etc. */
@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val consoleId: String,
    val consoleName: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val coverUrl: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val players: Int = 0,
    val rating: Double = 0.0,
    val averageRating: Double = 0.0,
    val ratingCount: Long = 0,
    val userRating: Int? = null,
    val coreOverride: String? = null,
    val scraperId: String? = null,
    val scrapeAttempts: Int = 0,
    val isFavorite: Boolean = false,
    val isInPlayLater: Boolean = false,
    val lastPlayedAt: String? = null,
    val totalPlayTime: Long = 0,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Wrapper for GET /api/games which returns {data, total, page, pageSize} */
@Serializable
data class GameListResponse(
    val data: List<GameDto>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
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

@Serializable
data class UserPreferencesDto(
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val selectedShader: String = "none",
    val selectedTheme: String = "default-dark",
    val consoleShaders: Map<String, String> = emptyMap(),
    val raLinked: Boolean = false,
    val raUsername: String = "",
    val raHardcoreEnabled: Boolean = false,
)

@Serializable
data class UpdatePreferencesRequest(
    val showPerformanceOverlay: Boolean? = null,
    val autoSaveEnabled: Boolean? = null,
    val autoLoadSaveEnabled: Boolean? = null,
    val selectedShader: String? = null,
    val selectedTheme: String? = null,
    val consoleShaders: Map<String, String>? = null,
)

// Devices

@Serializable
data class RegisterDeviceRequest(
    val deviceUuid: String,
    val name: String,
    val platform: String,
)

@Serializable
data class DeviceDto(
    val id: Long,
    val deviceUuid: String,
    val name: String,
    val platform: String,
    val lastSeenAt: String = "",
    val consoleShaders: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateDevicePreferencesRequest(
    val consoleShaders: Map<String, String>,
)

// Shared Saves

@Serializable
data class SharedSaveStateDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val gameId: String,
    val name: String,
    val description: String = "",
    val fileSize: Long = 0,
    val screenshotUrl: String? = null,
    val downloadCount: Int = 0,
    val createdAt: String = "",
)

@Serializable
data class SharedSavesResponse(
    val data: List<SharedSaveStateDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

// Ratings

@Serializable
data class RateGameRequest(
    val rating: Int,
    val review: String = "",
)

@Serializable
data class GameRatingDto(
    val id: String,
    val userId: String,
    val username: String,
    val avatarUrl: String? = null,
    val gameId: String,
    val rating: Int,
    val review: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class GameRatingsResponse(
    val data: List<GameRatingDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

@Serializable
data class RatingSummaryDto(
    val averageRating: Double = 0.0,
    val totalRatings: Long = 0,
    val distribution: Map<String, Int> = emptyMap(),
)

// Social

@Serializable
data class OnlineUserGameDto(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    val consoleName: String = "",
)

@Serializable
data class OnlineUserDto(
    val id: String,
    val username: String,
    val avatarUrl: String? = null,
    val currentGame: OnlineUserGameDto? = null,
)

@Serializable
data class OnlineUsersResponse(
    val users: List<OnlineUserDto> = emptyList(),
)

@Serializable
data class ActivityEventDto(
    val id: String,
    val userId: String,
    val username: String,
    val userAvatarUrl: String? = null,
    val eventType: String,
    val gameId: String? = null,
    val gameTitle: String? = null,
    val gameCoverUrl: String? = null,
    val gameConsoleName: String? = null,
    val metadata: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ActivityFeedResponse(
    val data: List<ActivityEventDto> = emptyList(),
    val total: Long = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
)

/** Wrapper for GET /api/games/:id/core when core is not in DB */
@Serializable
data class CoreNameResponse(
    val coreName: String,
)

// RetroAchievements

@Serializable
data class RAStatusDto(
    val linked: Boolean = false,
    val username: String = "",
    val hardcoreEnabled: Boolean = false,
)

@Serializable
data class RALinkRequestDto(
    val username: String,
    val password: String,
)

@Serializable
data class RATokenResponseDto(
    val username: String,
    val token: String,
)

@Serializable
data class RASettingsRequestDto(
    val hardcoreEnabled: Boolean,
)
