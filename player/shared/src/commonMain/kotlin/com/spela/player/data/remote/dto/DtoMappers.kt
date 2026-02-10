package com.spela.player.data.remote.dto

import com.spela.player.domain.model.*
import kotlinx.datetime.Instant

fun AuthResponse.toDomain(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
)

fun AuthResponse.extractUser(): User = user.toDomain()

fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    email = email,
    role = role,
    avatarUrl = avatarUrl,
)

fun ConsoleDto.toDomain(): Console = Console(
    id = id,
    name = name,
    abbreviation = abbreviation,
    gameCount = gameCount,
    colorTheme = colorTheme,
    coverAspectRatio = coverAspectRatio,
    defaultCore = defaultCore,
    iconUrl = iconUrl,
)

/** Maps the enriched GameResponse DTO to domain Game. */
fun GameDto.toDomain(): Game = Game(
    id = id,
    title = title,
    consoleId = consoleId,
    consoleName = consoleName,
    coverUrl = coverUrl,
    description = description,
    developer = developer,
    publisher = publisher,
    releaseDate = releaseDate,
    genre = genre,
    fileSize = fileSize,
    fileName = fileName,
    coreOverride = coreOverride,
    scrapeAttempts = scrapeAttempts,
    players = players,
    rating = rating,
    isFavorite = isFavorite,
    lastPlayedAt = lastPlayedAt,
    totalPlayTime = totalPlayTime,
)

/**
 * Constructs GameDetail from the enriched GameResponse.
 * screenshotUrls comes directly from the response.
 */
fun GameDto.toGameDetail(): GameDetail = GameDetail(
    game = toDomain(),
    screenshots = screenshotUrls,
)

fun SaveStateDto.toDomain(): SaveState = SaveState(
    id = id,
    gameId = gameId,
    name = name,
    createdAt = createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
    fileSize = fileSize,
    isAuto = isAuto,
)

fun UserPreferencesDto.toDomain(): UserPreferences = UserPreferences(
    showPerformanceOverlay = showPerformanceOverlay,
    autoSaveEnabled = autoSaveEnabled,
    autoLoadSaveEnabled = autoLoadSaveEnabled,
    selectedShader = ShaderPreset.fromApiId(selectedShader),
    consoleShaders = consoleShaders.mapValues { ShaderPreset.fromApiId(it.value) },
)

fun LibretroCoreDto.toDomain(): LibretroCore = LibretroCore(
    id = id,
    name = name,
    displayName = displayName,
    version = version,
    platforms = platforms,
)
