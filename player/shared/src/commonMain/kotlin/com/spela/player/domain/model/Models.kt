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
    val expiresAt: Instant,
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val role: String,
)

@Serializable
data class Console(
    val id: String,
    val name: String,
    val abbreviation: String,
    val gameCount: Int,
    val coverUrl: String? = null,
    val colorTheme: String? = null,
)

@Serializable
data class Game(
    val id: String,
    val title: String,
    val consoleId: String,
    val consoleName: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val fileSize: Long = 0,
    val fileName: String = "",
    val coreName: String? = null,
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
    val id: String,
    val gameId: String,
    val name: String,
    val createdAt: Instant,
    val size: Long,
    val isAutoSave: Boolean = false,
    val screenshotUrl: String? = null,
)

@Serializable
data class LibretroCore(
    val id: String,
    val name: String,
    val systemName: String,
    val version: String,
    val fileSize: Long,
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
