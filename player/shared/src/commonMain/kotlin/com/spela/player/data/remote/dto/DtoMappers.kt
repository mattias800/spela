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
    logoUrl = logoPngUrl.ifEmpty { logoUrl },
    saveStateSupport = saveStateSupport,
    browserPlayable = browserPlayable,
    playable = playable,
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
    coverAspectRatio = coverAspectRatio.toFloat(),
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
    achievementsWarning = achievementsWarning,
    verificationStatus = verificationStatus,
    verificationTag = verificationTag,
    region = region,
    playable = playable,
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
    coreName = coreName,
    notes = notes,
    screenshotUrl = screenshotUrl,
    slot = slot,
    isSynced = true,
)

fun StorageUsageDto.toDomain(): com.spela.player.domain.model.StorageUsage =
    com.spela.player.domain.model.StorageUsage(
        totalBytes = totalBytes,
        games = games.map { it.toDomain() },
    )

fun GameStorageUsageDto.toDomain(): com.spela.player.domain.model.GameStorageUsage =
    com.spela.player.domain.model.GameStorageUsage(
        gameId = gameId,
        gameTitle = gameTitle,
        totalBytes = totalBytes,
        saveStateBytes = saveStateBytes,
        sramBytes = sramBytes,
    )

fun UserPreferencesDto.toDomain(): UserPreferences = UserPreferences(
    showPerformanceOverlay = showPerformanceOverlay,
    autoSaveEnabled = autoSaveEnabled,
    autoLoadSaveEnabled = autoLoadSaveEnabled,
    selectedShader = ShaderPreset.fromApiId(selectedShader),
    selectedTheme = selectedTheme,
    consoleShaders = consoleShaders.mapValues { ShaderPreset.fromApiId(it.value) },
    selectedKeyMapping = selectedKeyMapping,
    consoleKeyMappings = consoleKeyMappings.mapValues {
        ConsoleKeyMappingPref(
            selectedMapping = it.value.selectedMapping,
            customMapping = it.value.customMapping,
        )
    },
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
    downloadUrl = downloadUrl,
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

// Collection mappers

fun CollectionDto.toDomain(): GameCollection = GameCollection(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    description = description,
    isPublic = isPublic,
    coverUrl = coverUrl,
    gameCount = gameCount,
)

fun CollectionDetailDto.toDomain(): GameCollectionDetail = GameCollectionDetail(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    description = description,
    isPublic = isPublic,
    coverUrl = coverUrl,
    gameCount = gameCount,
    games = games.map { it.toDomain() },
)

// Game Stats mappers

fun TopPlayerDto.toDomain(): TopPlayer = TopPlayer(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    playTime = playTime,
)

fun GameStatsDto.toDomain(): GameStats = GameStats(
    totalPlayers = totalPlayers,
    totalPlayTime = totalPlayTime,
    averagePlayTime = averagePlayTime,
    topPlayers = topPlayers.map { it.toDomain() },
)

fun GameAchievementDto.toDomain(): GameAchievement = GameAchievement(
    id = id,
    title = title,
    description = description,
    points = points,
    badgeUrl = badgeUrl,
    type = type,
    displayOrder = displayOrder,
)

fun AchievementProgressEntryDto.toDomain(): AchievementProgress = AchievementProgress(
    achievementId = achievementId,
    unlockedAt = unlockedAt,
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
)

fun AchievementTimelineEntryDto.toDomain(): AchievementTimelineEntry = AchievementTimelineEntry(
    achievementRaId = achievementRaId,
    title = title,
    description = description,
    points = points,
    badgeUrl = badgeUrl,
    unlockedAt = unlockedAt,
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
)

fun AchievementTimelineResponse.toDomain(): AchievementTimelineData = AchievementTimelineData(
    raGameId = raGameId,
    gameTitle = gameTitle,
    totalPlayTime = totalPlayTime,
    timeline = timeline.map { it.toDomain() },
    totalAchievements = totalAchievements,
    unlockedCount = unlockedCount,
    totalPoints = totalPoints,
    earnedPoints = earnedPoints,
)

fun AchievementLeaderboardEntryDto.toDomain(): AchievementPlayerRanking = AchievementPlayerRanking(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    unlockedCount = unlockedCount,
    earnedPoints = earnedPoints,
    firstUnlockedAt = firstUnlockedAt,
    lastUnlockedAt = lastUnlockedAt,
)

fun UserStatsDto.toDomain(): UserStats = UserStats(
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    mostPlayedGame = mostPlayedGame?.toDomain(),
    mostPlayedGameTime = mostPlayedGameTime,
    lastPlayedAt = lastPlayedAt,
)

fun RecentAchievementDto.toDomain(): RecentAchievement = RecentAchievement(
    achievementRaId = achievementRaId,
    title = title,
    description = description,
    points = points,
    badgeUrl = badgeUrl,
    unlockedAt = unlockedAt,
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    coverUrl = coverUrl,
)

// Stats mappers

fun MostPlayedGameDto.toDomain(): MostPlayedGame = MostPlayedGame(
    game = game.toDomain(),
    totalPlayers = totalPlayers,
    totalPlayTime = totalPlayTime,
)

fun ActivePlayerDto.toDomain(): ActivePlayer = ActivePlayer(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    lastPlayed = lastPlayed,
)

// Challenge mappers

fun ChallengeDto.toDomain(): Challenge = Challenge(
    id = id,
    creatorId = creatorId,
    creatorUsername = creatorUsername,
    creatorAvatarUrl = creatorAvatarUrl,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    name = name,
    description = description,
    type = ChallengeType.fromApiId(type),
    difficulty = ChallengeDifficulty.fromApiId(difficulty),
    status = status,
    screenshotUrl = screenshotUrl,
    coreName = coreName,
    saveFileSize = saveFileSize,
    attemptCount = attemptCount,
    completionCount = completionCount,
    bestTimeMs = bestTimeMs,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

fun ChallengeAttemptDto.toDomain(): ChallengeAttempt = ChallengeAttempt(
    id = id,
    challengeId = challengeId,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    status = status,
    startedAt = startedAt,
    completedAt = completedAt,
    durationMs = durationMs,
    isBest = isBest,
)

fun ChallengeLeaderboardEntryDto.toDomain(): ChallengeLeaderboardEntry = ChallengeLeaderboardEntry(
    rank = rank,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    durationMs = durationMs,
    completedAt = completedAt,
    isCurrentUser = isCurrentUser,
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

fun TopRatedGameDto.toDomain(): TopRatedGame = TopRatedGame(
    rank = rank,
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    localGameId = localGameId,
)

fun SimilarGameDto.toDomain(): SimilarGame = SimilarGame(
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    localGameId = localGameId,
)

fun DeveloperGameDto.toDomain(): DeveloperGame = DeveloperGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
)

fun GameSessionDto.toDomain(): GameSession = GameSession(
    id = id,
    gameId = gameId,
    name = name,
    lastPlayedAt = lastPlayedAt,
    lastPlayedByUsername = lastPlayedByUsername,
    totalPlayTime = totalPlayTime,
    screenshotUrl = screenshotUrl,
    cheatsEnabled = cheatsEnabled,
    memberCount = memberCount,
    memberAvatars = memberAvatars,
)

fun SaveDataDto.toDomain() = SaveData(
    id = id,
    gameId = gameId,
    name = name,
    fileSize = fileSize,
    isActive = isActive,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrNull(),
    updatedAt = runCatching { Instant.parse(updatedAt) }.getOrNull(),
)
