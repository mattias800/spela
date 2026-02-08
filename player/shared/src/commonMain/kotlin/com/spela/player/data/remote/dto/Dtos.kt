package com.spela.player.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val role: String,
)

@Serializable
data class ConsoleDto(
    val id: String,
    val name: String,
    val abbreviation: String,
    @SerialName("game_count") val gameCount: Int,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("color_theme") val colorTheme: String? = null,
)

@Serializable
data class GameDto(
    val id: String,
    val title: String,
    @SerialName("console_id") val consoleId: String,
    @SerialName("console_name") val consoleName: String,
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    @SerialName("release_year") val releaseYear: Int? = null,
    val genre: String? = null,
    @SerialName("file_size") val fileSize: Long = 0,
    @SerialName("file_name") val fileName: String = "",
    @SerialName("core_name") val coreName: String? = null,
)

@Serializable
data class GameDetailDto(
    val game: GameDto,
    val screenshots: List<String> = emptyList(),
    @SerialName("last_played") val lastPlayed: String? = null,
    @SerialName("play_time") val playTime: Long = 0,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

@Serializable
data class SaveStateDto(
    val id: String,
    @SerialName("game_id") val gameId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String,
    val size: Long,
    @SerialName("is_auto_save") val isAutoSave: Boolean = false,
    @SerialName("screenshot_url") val screenshotUrl: String? = null,
)

@Serializable
data class LibretroCoreDto(
    val id: String,
    val name: String,
    @SerialName("system_name") val systemName: String,
    val version: String,
    @SerialName("file_size") val fileSize: Long,
)
