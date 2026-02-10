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
    val id: String,
    val username: String,
    val email: String = "",
    val role: String,
    val avatarUrl: String? = null,
)

@Serializable
data class Console(
    val id: String,
    val name: String,
    val abbreviation: String,
    val gameCount: Int,
    val colorTheme: String = "#6366f1",
    val coverAspectRatio: Double = 0.75,
    val defaultCore: String = "",
    val iconUrl: String = "",
)

@Serializable
data class Game(
    val id: String,
    val title: String,
    val consoleId: String,
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
    val scrapeAttempts: Int = 0,
    val players: Int = 0,
    val rating: Double = 0.0,
    val isFavorite: Boolean = false,
    val lastPlayedAt: String? = null,
    val totalPlayTime: Long = 0,
)

@Serializable
data class GameDetail(
    val game: Game,
    val screenshots: List<String> = emptyList(),
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

enum class ShaderPreset(val apiId: String, val displayName: String, val description: String) {
    NONE("none", "None (Pixel Perfect)", "Raw pixels, nearest-neighbor scaling"),
    BILINEAR("bilinear", "Smooth (Bilinear)", "Softens hard pixel edges"),
    SHARP_BILINEAR("sharp-bilinear", "Sharp Bilinear", "Smooth edges, preserves pixel grid"),
    CRT_SIMPLE("crt-simple", "CRT Classic", "Scanlines, curvature, and phosphor glow"),
    LCD_GRID("lcd-grid", "LCD Grid", "Simulates handheld LCD pixel grid"),
    SCANLINES("scanlines", "Scanlines Only", "Horizontal scanline darkening");

    companion object {
        fun fromApiId(id: String): ShaderPreset =
            entries.find { it.apiId == id } ?: NONE
    }
}

data class UserPreferences(
    val showPerformanceOverlay: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val autoLoadSaveEnabled: Boolean = true,
    val selectedShader: ShaderPreset = ShaderPreset.NONE,
    val consoleShaders: Map<String, ShaderPreset> = emptyMap(),
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
    val gameTitle: String = "",
    val state: DownloadState,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
) {
    val progress: Float
        get() = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else 0f
}
