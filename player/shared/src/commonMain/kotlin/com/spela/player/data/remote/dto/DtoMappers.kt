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
    saveStateSupport = saveStateSupport,
    browserPlayable = browserPlayable,
)

fun GameDiscDto.toDomain(): GameDisc = GameDisc(
    discNumber = discNumber,
    fileName = fileName,
    fileSize = fileSize,
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
    averageRating = averageRating,
    ratingCount = ratingCount,
    userRating = userRating,
    isFavorite = isFavorite,
    isInPlayLater = isInPlayLater,
    lastPlayedAt = lastPlayedAt,
    totalPlayTime = totalPlayTime,
    discCount = discCount,
    discs = discs.map { it.toDomain() },
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
    selectedTheme = selectedTheme,
    consoleShaders = consoleShaders.mapValues { ShaderPreset.fromApiId(it.value) },
)

fun SharedSaveStateDto.toDomain(): SharedSaveState = SharedSaveState(
    id = id,
    userId = userId,
    username = username,
    userAvatarUrl = avatarUrl,
    gameId = gameId,
    name = name,
    description = description,
    fileSize = fileSize,
    downloadCount = downloadCount,
    createdAt = createdAt,
)

fun GameRatingDto.toDomain(): GameRating = GameRating(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    gameId = gameId,
    rating = rating,
    review = review,
    createdAt = createdAt,
)

fun RatingSummaryDto.toDomain(): RatingSummary = RatingSummary(
    averageRating = averageRating,
    totalRatings = totalRatings,
    distribution = distribution.mapKeys { it.key.toIntOrNull() ?: 0 },
)

fun OnlineUserGameDto.toDomain(): OnlineUserGame = OnlineUserGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
)

fun OnlineUserDto.toDomain(): OnlineUser = OnlineUser(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    currentGame = currentGame?.toDomain(),
)

fun ActivityEventDto.toDomain(): ActivityEvent = ActivityEvent(
    id = id,
    userId = userId,
    username = username,
    userAvatarUrl = userAvatarUrl,
    eventType = eventType,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    createdAt = createdAt,
)

fun LibretroCoreDto.toDomain(): LibretroCore = LibretroCore(
    id = id,
    name = name,
    displayName = displayName,
    version = version,
    platforms = platforms,
)

fun PublicProfileGameDto.toDomain(): PublicProfileGame = PublicProfileGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
    playTime = playTime,
)

fun PublicProfileDto.toDomain(): PublicProfile = PublicProfile(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    memberSince = memberSince,
    isOnline = isOnline,
    currentGame = currentGame?.toDomain(),
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    favoriteGames = favoriteGames.map { it.toDomain() },
    recentGames = recentGames.map { it.toDomain() },
    topGames = topGames.map { it.toDomain() },
)

// Relay mappers

fun RelayDto.toDomain(): Relay = Relay(
    id = id,
    name = name,
    ownerId = ownerId,
    ownerUsername = ownerUsername,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    status = status,
    memberCount = memberCount,
    activeUserId = activeUserId,
    lastActivityAt = lastActivityAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RelayDetailDto.toDomain(): RelayDetail = RelayDetail(
    id = id,
    name = name,
    ownerId = ownerId,
    ownerUsername = ownerUsername,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    status = status,
    memberCount = memberCount,
    activeUserId = activeUserId,
    members = members.map { it.toDomain() },
    lastActivityAt = lastActivityAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RelayMemberDto.toDomain(): RelayMember = RelayMember(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    role = role,
    joinedAt = joinedAt,
    lastPlayedAt = lastPlayedAt,
    isOnline = isOnline,
)

fun RelayInvitationDto.toDomain(): RelayInvitation = RelayInvitation(
    id = id,
    relayId = relayId,
    relayName = relayName,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    inviterUsername = inviterUsername,
    inviterAvatarUrl = inviterAvatarUrl,
    createdAt = createdAt,
)

fun RelaySaveDto.toDomain(): RelaySave = RelaySave(
    id = id,
    relayId = relayId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    fileSize = fileSize,
    isAuto = isAuto,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// Netplay mappers

fun NetplaySessionDto.toDomain(): NetplaySession = NetplaySession(
    id = id,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName,
    coverAspectRatio = coverAspectRatio,
    hostUserId = hostId,
    hostUsername = hostUsername,
    hostAvatarUrl = hostAvatarUrl,
    clientUserId = clientId,
    clientUsername = clientUsername,
    clientAvatarUrl = clientAvatarUrl,
    status = NetplaySessionStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: NetplaySessionStatus.WAITING,
    inputDelay = inputDelay,
    inviteCode = inviteCode,
    endReason = endReason,
    createdAt = createdAt,
    startedAt = startedAt,
    endedAt = endedAt,
)
