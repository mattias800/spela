package com.spela.player.data.remote.dto

import com.spela.player.domain.model.*
import kotlinx.datetime.Instant

fun AuthResponse.toDomain(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresAt = Instant.parse(expiresAt),
)

fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    role = role,
)

fun ConsoleDto.toDomain(): Console = Console(
    id = id,
    name = name,
    abbreviation = abbreviation,
    gameCount = gameCount,
    coverUrl = coverUrl,
    colorTheme = colorTheme,
)

fun GameDto.toDomain(): Game = Game(
    id = id,
    title = title,
    consoleId = consoleId,
    consoleName = consoleName,
    coverUrl = coverUrl,
    description = description,
    developer = developer,
    publisher = publisher,
    releaseYear = releaseYear,
    genre = genre,
    fileSize = fileSize,
    fileName = fileName,
    coreName = coreName,
)

fun GameDetailDto.toDomain(): GameDetail = GameDetail(
    game = game.toDomain(),
    screenshots = screenshots,
    lastPlayed = lastPlayed?.let { Instant.parse(it) },
    playTime = playTime,
    isFavorite = isFavorite,
)

fun SaveStateDto.toDomain(): SaveState = SaveState(
    id = id,
    gameId = gameId,
    name = name,
    createdAt = Instant.parse(createdAt),
    size = size,
    isAutoSave = isAutoSave,
    screenshotUrl = screenshotUrl,
)

fun LibretroCoreDto.toDomain(): LibretroCore = LibretroCore(
    id = id,
    name = name,
    systemName = systemName,
    version = version,
    fileSize = fileSize,
)
