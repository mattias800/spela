package com.spela.player.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ServerConnection(
    val id: String,
    val name: String,
    val url: String,
    val isActive: Boolean = false,
)

@Serializable
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class User(
    val id: Long,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
)

@Serializable
data class Console(
    val id: Long,
    val name: String,
    val abbreviation: String,
    val gameCount: Int,
    val colorTheme: String = "#6366f1",
    val coverAspect: String = "3:4",
    val defaultCore: String = "",
)

@Serializable
data class Game(
    val id: Long,
    val title: String,
    val consoleId: Long,
    val consoleName: String = "",
    val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val fileSize: Long = 0,
    val fileName: String = "",
    val coreOverride: String? = null,
    val players: Int = 0,
    val rating: Double = 0.0,
)

@Serializable
data class GameDetail(
    val game: Game,
    val screenshots: List<String> = emptyList(),
    val lastPlayed: Instant? = null,
    val playTime: Long = 0,
    val isFavorite: Boolean = false,
)

@Serializable
data class SaveState(
    val id: Long,
    val gameId: Long,
    val name: String,
    val createdAt: Instant? = null,
    val fileSize: Long = 0,
    val isAuto: Boolean = false,
)

@Serializable
data class LibretroCore(
    val id: Long,
    val name: String,
    val displayName: String = "",
    val version: String? = null,
    val platforms: String = "",
)

enum class DownloadState {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

data class DownloadProgress(
    val gameId: String,
    val state: DownloadState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
