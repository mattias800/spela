package com.spela.player.data.remote.dto

import com.spela.player.domain.model.*
import kotlin.time.Instant

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
    code = code,
    colorTheme = colorTheme,
    coverAspectRatio = coverAspectRatio,
    defaultCore = defaultCore,
    iconUrl = iconUrl,
    logoUrl = logoPngUrl.ifEmpty { logoUrl },
    saveStateSupport = saveStateSupport,
    browserPlayable = browserPlayable,
    playable = playable,
    generation = generation,
    makerName = maker?.name,
    makerCode = maker?.code,
    mediaTypeName = mediaType?.name,
    releaseYear = releaseYear,
    unitsSold = unitsSold,
    summary = summary,
)

fun com.spela.client.models.ConsoleResponse.toDomain(): Console = Console(
    id = id,
    name = name,
    abbreviation = abbreviation,
    gameCount = gameCount.toInt(),
    code = code,
    colorTheme = colorTheme,
    coverAspectRatio = coverAspectRatio,
    defaultCore = defaultCore,
    iconUrl = iconUrl,
    logoUrl = logoPngUrl.ifEmpty { logoUrl },
    saveStateSupport = saveStateSupport,
    browserPlayable = browserPlayable,
    playable = playable,
    generation = generation.toInt(),
    makerName = maker.name,
    makerCode = maker.code,
    mediaTypeName = mediaType.name,
    releaseYear = releaseYear?.toInt(),
    unitsSold = unitsSold,
    summary = summary,
)

fun GameDiscDto.toDomain(): GameDisc = GameDisc(
    discNumber = discNumber.toInt(),
    fileName = fileName,
    fileSize = fileSize,
)

/** Maps the enriched GameResponse DTO to domain Game.
 *
 * The generated GameResponse has several fields server-marked @Required
 * with `String` type where the hand-written DTO had `String?` — the server
 * emits empty strings when a field is absent. We convert `""` back to
 * `null` so the domain layer keeps its existing "missing data" semantics.
 */
fun GameDto.toDomain(): Game = Game(
    id = id,
    title = title,
    consoleId = consoleId,
    consoleName = consoleName,
    coverAspectRatio = coverAspectRatio.toFloat(),
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
    description = description.takeIf { it.isNotEmpty() },
    developer = developer.takeIf { it.isNotEmpty() },
    publisher = publisher.takeIf { it.isNotEmpty() },
    releaseDate = releaseDate.takeIf { it.isNotEmpty() },
    genre = genre.takeIf { it.isNotEmpty() },
    fileSize = fileSize,
    fileName = fileName,
    coreOverride = coreOverride,
    scrapeAttempts = scrapeAttempts.toInt(),
    players = players.toInt(),
    igdbCriticsRating = igdbCriticsRating,
    communityRating = averageRating,
    communityRatingCount = ratingCount,
    userRating = userRating?.toInt(),
    isFavorite = isFavorite,
    isInPlayLater = isInPlayLater,
    lastPlayedAt = lastPlayedAt?.toString(),
    totalPlayTime = totalPlayTime,
    discCount = discCount.toInt(),
    discs = discs.orEmpty().map { it.toDomain() },
    achievementsWarning = achievementsWarning,
    verificationStatus = verificationStatus,
    verificationTag = verificationTag,
    region = region,
    playable = playable,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    revision = revision,
    tags = tags,
    isPreRelease = isPreRelease,
    variantCount = variantCount?.toInt() ?: 0,
    groupKey = groupKey,
    timeToBeatHastily = timeToBeatHastily?.toInt() ?: 0,
    timeToBeatNormally = timeToBeatNormally?.toInt() ?: 0,
    timeToBeatCompletely = timeToBeatCompletely?.toInt() ?: 0,
    partyInfo = partyInfo.orEmpty(),
)

fun GameVariantDto.toDomain(): GameVariant = GameVariant(
    id = id,
    title = title,
    fileName = fileName,
    region = region,
    revision = revision,
    tags = tags,
    isPreRelease = isPreRelease,
    fileSize = fileSize,
    verificationStatus = verificationStatus,
)

fun ParentGameDto.toDomain(): ParentGame = ParentGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
)

fun RomHackGameDto.toDomain(): RomHackGame = RomHackGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
)

/** Constructs GameDetail from the enriched GameResponse. */
fun GameDto.toGameDetail(): GameDetail = GameDetail(
    game = toDomain(),
    screenshots = screenshotUrls.orEmpty(),
    variants = variants.orEmpty().map { it.toDomain() },
    parentGame = parentGame?.toDomain(),
    romHacks = romHacks.orEmpty().map { it.toDomain() },
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

fun com.spela.client.models.UserPreferencesResponse.toDomain(): UserPreferences = UserPreferences(
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
            customMapping = it.value.customMapping.orEmpty(),
        )
    },
    defaultSecondScreenPage = defaultSecondScreenPage,
)

fun com.spela.client.models.SharedSaveResponse.toDomain(): SharedSaveState = SharedSaveState(
    id = id,
    userId = userId,
    username = username,
    userAvatarUrl = avatarUrl,
    gameId = gameId,
    name = name,
    description = description.orEmpty(),
    fileSize = fileSize,
    downloadCount = downloadCount.toInt(),
    createdAt = createdAt.toString(),
)

fun com.spela.client.models.GameRatingResponse.toDomain(): GameRating = GameRating(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    gameId = gameId,
    rating = rating.toInt(),
    review = review.orEmpty(),
    createdAt = createdAt.toString(),
)

fun com.spela.client.models.RatingSummaryResponse.toDomain(): RatingSummary = RatingSummary(
    averageRating = averageRating,
    totalRatings = totalRatings,
    distribution = distribution.entries.associate {
        (it.key.toIntOrNull() ?: 0) to it.value.toInt()
    },
)

fun com.spela.client.models.OnlineUserGameResponse.toDomain(): OnlineUserGame = OnlineUserGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
)

fun com.spela.client.models.OnlineUserResponse.toDomain(): OnlineUser = OnlineUser(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    currentGame = currentGame?.toDomain(),
)

fun com.spela.client.models.ActivityEventResponse.toDomain(): ActivityEvent = ActivityEvent(
    id = id,
    userId = userId,
    username = username,
    userAvatarUrl = avatarUrl, // server sends `avatarUrl`
    eventType = eventType,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName, // server sends `consoleName`
    createdAt = createdAt.toString(),
)

fun com.spela.client.models.Core.toDomain(): LibretroCore = LibretroCore(
    id = id,
    name = name,
    displayName = displayName,
    version = version,
    platforms = platforms,
    downloadUrl = downloadUrl,
)

fun com.spela.client.models.HeatmapEntry.toDomain(): HeatmapEntry = HeatmapEntry(
    date = date,
    playTime = playTime,
)

fun com.spela.client.models.PublicProfileGame.toDomain(): PublicProfileGame = PublicProfileGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
    playTime = playTime ?: 0L,
)

fun com.spela.client.models.PublicProfileResponse.toDomain(): PublicProfile = PublicProfile(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    memberSince = memberSince.toString(),
    isOnline = isOnline,
    currentGame = currentGame?.toDomain(),
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    favoriteGames = favoriteGames.orEmpty().map { it.toDomain() },
    recentGames = recentGames.orEmpty().map { it.toDomain() },
    topGames = topGames.orEmpty().map { it.toDomain() },
)

// Shared Session mappers

fun SharedSessionDto.toDomain(): SharedSession = SharedSession(
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

fun SharedSessionDetailDto.toDomain(): SharedSessionDetail = SharedSessionDetail(
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

fun SharedSessionMemberDto.toDomain(): SharedSessionMember = SharedSessionMember(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    role = role,
    joinedAt = joinedAt,
    lastPlayedAt = lastPlayedAt,
    isOnline = isOnline,
)

fun SharedSessionInvitationDto.toDomain(): SharedSessionInvitation = SharedSessionInvitation(
    id = id,
    sharedSessionId = sharedSessionId,
    sharedSessionName = sharedSessionName,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = gameConsoleName,
    inviterUsername = inviterUsername,
    inviterAvatarUrl = inviterAvatarUrl,
    createdAt = createdAt,
)

fun SharedSessionSaveDto.toDomain(): SharedSessionSave = SharedSessionSave(
    id = id,
    sharedSessionId = sharedSessionId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    fileSize = fileSize,
    isAuto = isAuto,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

// Collection mappers

fun com.spela.client.models.CollectionResponse.toDomain(): GameCollection = GameCollection(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    description = description,
    isPublic = isPublic,
    coverUrl = coverUrl,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.CollectionDetailResponse.toDomain(): GameCollectionDetail = GameCollectionDetail(
    id = id,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    name = name,
    description = description,
    isPublic = isPublic,
    coverUrl = coverUrl,
    gameCount = gameCount.toInt(),
    games = games.orEmpty().map { it.toDomain() },
)

// Game Stats mappers

fun com.spela.client.models.GameStatsTopPlayer.toDomain(): TopPlayer = TopPlayer(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl.takeIf { it.isNotEmpty() },
    playTime = playTime,
)

fun com.spela.client.models.GameStatsResponse.toDomain(): GameStats = GameStats(
    totalPlayers = totalPlayers.toInt(),
    totalPlayTime = totalPlayTime,
    averagePlayTime = averagePlayTime,
    topPlayers = topPlayers.orEmpty().map { it.toDomain() },
)

fun GameAchievementDto.toDomain(): GameAchievement = GameAchievement(
    id = id,
    title = title,
    description = description,
    points = points,
    badgeUrl = badgeUrl,
    type = type,
    displayOrder = displayOrder,
    rarityPercent = rarityPercent,
)

fun AchievementProgressEntryDto.toDomain(): AchievementProgress = AchievementProgress(
    achievementId = achievementId,
    unlockedAt = unlockedAt,
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
)

fun com.spela.client.models.RATimelineEntryResponse.toDomain(): AchievementTimelineEntry = AchievementTimelineEntry(
    achievementRaId = achievementRaId,
    title = title,
    description = description,
    points = points.toInt(),
    badgeUrl = badgeUrl,
    unlockedAt = unlockedAt.toString(),
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
)

fun com.spela.client.models.AchievementTimelineResponse.toDomain(): AchievementTimelineData = AchievementTimelineData(
    raGameId = raGameId,
    gameTitle = gameTitle,
    totalPlayTime = totalPlayTime,
    timeline = timeline.orEmpty().map { it.toDomain() },
    totalAchievements = totalAchievements.toInt(),
    unlockedCount = unlockedCount.toInt(),
    totalPoints = totalPoints.toInt(),
    earnedPoints = earnedPoints.toInt(),
)

fun com.spela.client.models.RALeaderboardEntryResponse.toDomain(): AchievementPlayerRanking = AchievementPlayerRanking(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl.takeIf { it.isNotEmpty() },
    unlockedCount = unlockedCount.toInt(),
    earnedPoints = earnedPoints.toInt(),
    firstUnlockedAt = firstUnlockedAt.toString(),
    lastUnlockedAt = lastUnlockedAt.toString(),
)

fun com.spela.client.models.UserStatsResponse.toDomain(): UserStats = UserStats(
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    currentStreak = currentStreak.toInt(),
    longestStreak = longestStreak.toInt(),
    // Server emits an empty Game placeholder when the user has never
    // played anything — detect via empty id and pass null through.
    mostPlayedGame = mostPlayedGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
    mostPlayedGameTime = mostPlayedGameTime,
    lastPlayedAt = lastPlayedAt?.toString(),
)

fun com.spela.client.models.RARecentAchievementResponse.toDomain(): RecentAchievement = RecentAchievement(
    achievementRaId = achievementRaId,
    title = title,
    description = description,
    points = points.toInt(),
    badgeUrl = badgeUrl,
    unlockedAt = unlockedAt.toString(),
    isHardcore = isHardcore,
    playTimeAtUnlock = playTimeAtUnlock,
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    coverUrl = coverUrl,
)

fun com.spela.client.models.ShowcaseEntryResponse.toDomain(): ShowcaseAchievement = ShowcaseAchievement(
    achievementRaId = achievementRaId,
    raGameId = raGameId,
    showcaseOrder = showcaseOrder.toInt(),
    title = title.orEmpty(),
    description = description.orEmpty(),
    points = points?.toInt() ?: 0,
    badgeUrl = badgeUrl,
    rarityPercent = rarityPercent ?: 0.0,
    gameTitle = gameTitle.orEmpty(),
)

fun com.spela.client.models.RAUnlockedAchievementResponse.toDomain(): UnlockedAchievement = UnlockedAchievement(
    achievementRaId = achievementRaId,
    raGameId = raGameId,
    title = title,
    description = description,
    points = points.toInt(),
    badgeUrl = badgeUrl,
    rarityPercent = rarityPercent,
    gameTitle = gameTitle,
    consoleName = consoleName,
)

// Stats mappers

fun com.spela.client.models.MostPlayedEntry.toDomain(): MostPlayedGame = MostPlayedGame(
    game = game.toDomain(),
    totalPlayers = totalPlayers.toInt(),
    totalPlayTime = totalPlayTime,
)

fun com.spela.client.models.ActivePlayerEntry.toDomain(): ActivePlayer = ActivePlayer(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl.takeIf { it.isNotEmpty() },
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed.toInt(),
    lastPlayed = lastPlayed.toString(),
)

// Challenge mappers

fun com.spela.client.models.ChallengeResponse.toDomain(): Challenge = Challenge(
    id = id,
    creatorId = creatorId,
    creatorUsername = creatorUsername,
    creatorAvatarUrl = creatorAvatar,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName.orEmpty(),
    name = name,
    description = description.orEmpty(),
    type = ChallengeType.fromApiId(type),
    difficulty = ChallengeDifficulty.fromApiId(difficulty),
    status = status,
    screenshotUrl = screenshotUrl,
    coreName = coreName.orEmpty(),
    saveFileSize = saveFileSize,
    attemptCount = attemptCount.toInt(),
    completionCount = completionCount.toInt(),
    // bestTimeMs not exposed by the server spec — domain keeps the field
    // so UI can compute it from the user's best attempt if needed.
    bestTimeMs = null,
    expiresAt = expiresAt?.toString(),
    createdAt = createdAt.toString(),
)

fun com.spela.client.models.ChallengeAttemptResponse.toDomain(): ChallengeAttempt = ChallengeAttempt(
    id = id,
    challengeId = challengeId,
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    status = status,
    startedAt = startedAt.toString(),
    completedAt = completedAt?.toString(),
    durationMs = durationMs,
    isBest = isBest,
)

fun com.spela.client.models.ChallengeLeaderboardEntry.toDomain(): ChallengeLeaderboardEntry = ChallengeLeaderboardEntry(
    rank = rank.toInt(),
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    durationMs = durationMs,
    completedAt = completedAt.toString(),
    // isCurrentUser is derived from the auth'd user at the presentation
    // layer — server spec doesn't ship it.
    isCurrentUser = false,
)

// User Search mappers

fun com.spela.client.models.UserSearchResult.toDomain(): UserSearchResult = UserSearchResult(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
)

// Netplay mappers

fun com.spela.client.models.NetplaySessionResponse.toDomain(): NetplaySession = NetplaySession(
    id = id,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName.orEmpty(),
    coverAspectRatio = coverAspectRatio.toFloat(),
    hostUserId = hostId,
    hostUsername = hostUsername,
    hostAvatarUrl = hostAvatarUrl,
    clientUserId = clientId,
    clientUsername = clientUsername,
    clientAvatarUrl = clientAvatarUrl,
    status = NetplaySessionStatus.entries.find { it.name.equals(status, ignoreCase = true) }
        ?: NetplaySessionStatus.WAITING,
    inputDelay = inputDelay.toInt(),
    inviteCode = inviteCode,
    endReason = endReason,
    createdAt = createdAt.toString(),
    startedAt = startedAt?.toString(),
    endedAt = endedAt?.toString(),
)

fun com.spela.client.models.TopListGameResponse.toDomain(): TopListGame = TopListGame(
    rank = rank.toInt(),
    gameId = gameId,
    name = name,
    coverUrl = coverUrl,
    consoleName = consoleName,
    consoleId = consoleId,
    rating = igdbCriticsRating, // server sends igdbCriticsRating, not rating
)

fun com.spela.client.models.TopRatedGameResponse.toDomain(): TopRatedGame = TopRatedGame(
    rank = rank.toInt(),
    name = name,
    coverUrl = coverUrl,
    rating = igdbCriticsRating, // server sends igdbCriticsRating, not rating
    localGameId = localGameId,
    consoleName = consoleName,
)

fun com.spela.client.models.LongestGameResponse.toDomain(): LongestGame = LongestGame(
    rank = rank.toInt(),
    gameId = gameId,
    name = name,
    coverUrl = coverUrl,
    consoleName = consoleName,
    consoleId = consoleId,
    timeToBeatNormally = timeToBeatNormally.toInt(),
    timeToBeatHastily = timeToBeatHastily.toInt(),
    timeToBeatCompletely = timeToBeatCompletely.toInt(),
)

fun SimilarGameDto.toDomain(): SimilarGame = SimilarGame(
    igdbGameId = igdbGameId,
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    localGameId = localGameId,
)

fun com.spela.client.models.DeveloperGameResponse.toDomain(): DeveloperGame = DeveloperGame(
    // Server sends `localGameId` / `name` / `coverUrl`; the hand-written
    // DTO used `id` / `title` / `consoleName` which never matched the
    // server payload. The server never ships `consoleName` for this
    // endpoint — domain defaults it to "".
    id = localGameId,
    title = name,
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
)

fun com.spela.client.models.GameSessionResponse.toDomain(): GameSession = GameSession(
    id = id,
    gameId = gameId,
    name = name,
    lastPlayedAt = lastPlayedAt?.toString(),
    lastPlayedByUsername = lastPlayedByUsername,
    totalPlayTime = totalPlayTime,
    screenshotUrl = screenshotUrl,
    coreName = coreName,
    cheatsEnabled = cheatsEnabled,
    isSharedSession = isSharedSession,
    sharedSessionId = sharedSessionId,
    memberCount = memberCount.toInt(),
    memberUsernames = memberUsernames.orEmpty(),
    memberAvatars = memberAvatars.orEmpty(),
)

fun com.spela.client.models.SessionCheatsResponse.toDomain() = SessionCheatConfig(
    cheatsEnabled = cheatsEnabled,
    enabledIndices = enabledIndices.orEmpty().map { it.toInt() },
)

// Explore mappers

fun FeaturedGameDto.toDomain(): FeaturedGame = FeaturedGame(
    gameId = gameId,
    title = title,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
    rating = rating,
    genre = genre,
    isFavorite = isFavorite,
    isPlayLater = isPlayLater,
)

fun ExploreRowDto.toDomain(): ExploreRow = ExploreRow(
    id = id,
    title = title,
    games = games.map { it.toDomain() },
)

fun com.spela.client.models.ThemeResponse.toDomain(): Theme = Theme(
    id = id,
    name = name,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.KeywordResponse.toDomain(): Keyword = Keyword(
    id = id,
    name = name,
    gameCount = gameCount.toInt(),
)

fun FeaturedSeriesDto.toDomain(): FeaturedSeries = FeaturedSeries(
    id = id,
    name = name,
    libraryGames = libraryGames,
    totalGames = totalGames,
    consoleCount = consoleCount,
    heroUrl = heroUrl,
)

fun com.spela.client.models.SeriesDetailResponse.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoles = consoles.orEmpty().map { it.toDomain() },
    libraryGames = libraryGames.toInt(),
    totalGames = totalGames.toInt(),
    games = games.orEmpty().map { it.toDomain() },
)

fun com.spela.client.models.FranchiseDetailResponse.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoles = consoles.orEmpty().map { it.toDomain() },
    libraryGames = libraryGames.toInt(),
    totalGames = totalGames.toInt(),
    games = games.orEmpty().map { it.toDomain() },
)

fun com.spela.client.models.SeriesConsoleInfo.toDomain(): SeriesConsole = SeriesConsole(
    abbreviation = abbreviation,
    name = name,
    color = color,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.SeriesGameResponse.toDomain(): SeriesGame = SeriesGame(
    igdbGameId = igdbGameId.toInt(),
    name = name,
    inLibrary = inLibrary,
    localGameId = localGameId,
    coverUrl = coverUrl,
    releaseDate = releaseDate,
    // Server emits `igdbCriticsRating`; the hand-written DTO used `rating`
    // which was always 0.0 — the new mapper pulls the real field.
    rating = igdbCriticsRating,
    consoleAbbreviation = consoleAbbreviation,
    consoleName = consoleName,
    consoleColor = consoleColor,
)

fun com.spela.client.models.GameSeriesResponse.toDomain(): GameSeriesLink = GameSeriesLink(
    id = id,
    name = name,
    totalGames = totalGames.toInt(),
    libraryGames = libraryGames.toInt(),
)

fun com.spela.client.models.GameFranchiseResponse.toDomain(): GameFranchiseLink = GameFranchiseLink(
    id = id,
    name = name,
    // Server sends both totalGames and libraryGames; the hand-written DTO
    // only had gameCount — we pick totalGames to preserve the UX
    // ("Part of Foo (N games)"). Domain does not yet split the two.
    gameCount = totalGames.toInt(),
)


fun MoodDefinitionDto.toDomain(): MoodDefinition = MoodDefinition(
    id = id,
    name = name,
    description = description,
    icon = icon,
    gradient = gradient,
)

fun ForYouRowDto.toDomain(): ForYouRow = ForYouRow(
    type = type,
    title = title,
    sourceGame = sourceGame?.toDomain(),
    genre = genre,
    games = games.map { it.toDomain() },
)

fun com.spela.client.models.TasteProfileGenre.toDomain(): TasteBreakdown = TasteBreakdown(
    name = name,
    percentage = percentage,
    playTime = playTime,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.TasteProfileTheme.toDomain(): TasteBreakdown = TasteBreakdown(
    name = name,
    percentage = percentage,
    playTime = playTime,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.TasteProfileConsole.toDomain(): ConsoleBreakdown = ConsoleBreakdown(
    name = name,
    abbreviation = abbreviation,
    playTime = playTime,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.TasteProfileResponse.toDomain(): TasteProfile = TasteProfile(
    totalPlayTime = totalPlayTime,
    genres = genres.orEmpty().map { it.toDomain() },
    themes = themes.orEmpty().map { it.toDomain() },
    topConsoles = topConsoles.orEmpty().map { it.toDomain() },
)

fun PlayersLikeYouResponseDto.toDomain(): PlayersLikeYouResult = PlayersLikeYouResult(
    games = games.map { it.toDomain() },
    similarUsersCount = similarUsersCount,
)

// Developer / Publisher mappers

fun DeveloperSummaryDto.toDomain(): DeveloperSummary = DeveloperSummary(
    name = name,
    gameCount = gameCount,
    avgRating = avgRating,
    consoles = consoles,
)

fun GenreCountDto.toDeveloperGenreBreakdown(): DeveloperDetailGenreBreakdown = DeveloperDetailGenreBreakdown(
    name = name,
    gameCount = gameCount,
)

fun DeveloperDetailPlatformBreakdownDto.toDomain(): DeveloperDetailPlatformBreakdown = DeveloperDetailPlatformBreakdown(
    consoleName = consoleName,
    consoleId = consoleId,
    count = count,
)

fun DeveloperDetailUserStatsDto.toDomain(): DeveloperDetailUserStats = DeveloperDetailUserStats(
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    favoriteCount = favoriteCount,
    mostPlayedGame = mostPlayedGame?.toDomain(),
)

fun DeveloperDetailPublisherDto.toDomain(): DeveloperDetailPublisher = DeveloperDetailPublisher(
    name = name,
    count = count,
)

fun CompanyInfoDto.toDomain(): CompanyInfo = CompanyInfo(
    logoUrl = logoUrl,
    description = description,
    foundedYear = foundedYear,
    country = country,
    websiteUrl = websiteUrl,
    wikipediaUrl = wikipediaUrl,
)

fun ActiveYearsDto.toDomain(): ActiveYears = ActiveYears(
    first = first,
    last = last,
)

fun RatingDistributionDto.toDomain(): RatingDistribution = RatingDistribution(
    excellent = excellent,
    good = good,
    average = average,
    poor = poor,
    unrated = unrated,
)

fun TimelineGameDto.toDomain(): TimelineGame = TimelineGame(
    id = id,
    title = title,
    coverUrl = coverUrl,
    rating = rating,
)

fun TimelineEntryDto.toDomain(): TimelineEntry = TimelineEntry(
    year = year,
    games = games.map { it.toDomain() },
)

fun RelatedDeveloperDto.toDomain(): RelatedDeveloper = RelatedDeveloper(
    name = name,
    gameCount = gameCount,
    sharedPublishers = sharedPublishers,
)

fun DeveloperDetailResponseDto.toDomain(): DeveloperDetail = DeveloperDetail(
    name = name,
    gameCount = gameCount,
    avgRating = avgRating,
    consoles = consoles,
    heroUrl = heroUrl,
    companyInfo = companyInfo?.toDomain(),
    topGames = topGames.map { it.toDomain() },
    genreBreakdown = genreBreakdown.map { it.toDeveloperGenreBreakdown() },
    platformBreakdown = platformBreakdown.map { it.toDomain() },
    userStats = userStats?.toDomain(),
    publishers = publishers.map { it.toDomain() },
    games = games.map { it.toDomain() },
    activeYears = activeYears?.toDomain(),
    ratingDistribution = ratingDistribution?.toDomain(),
    primaryGenre = primaryGenre,
    timeline = timeline.map { it.toDomain() },
    relatedDevelopers = relatedDevelopers.map { it.toDomain() },
)

fun DeveloperSpotlightResponseDto.toDomain(): DeveloperSpotlight = DeveloperSpotlight(
    name = name,
    gameCount = gameCount,
    avgRating = avgRating,
    consoles = consoles,
    topGames = topGames.map { it.toDomain() },
    heroUrl = heroUrl.ifBlank { null },
)

// Console Showcase mappers

fun GenreCountDto.toDomain(): GenreCount = GenreCount(
    name = name,
    gameCount = gameCount,
)

fun ConsoleShowcaseDto.toDomain(): ConsoleShowcase = ConsoleShowcase(
    console = console.toDomain(),
    essentials = essentials.map { it.toDomain() },
    hiddenGems = hiddenGems.map { it.toDomain() },
    genreBreakdown = genreBreakdown.map { it.toDomain() },
    topDevelopers = topDevelopers.map { it.toDomain() },
    recentlyPlayed = recentlyPlayed.map { it.toDomain() },
)

fun ConsoleHighlightDto.toDomain(): ConsoleHighlight = ConsoleHighlight(
    id = id,
    name = name,
    colorTheme = colorTheme,
    iconUrl = iconUrl,
    logoUrl = logoUrl,
    gameCount = gameCount,
    topGame = topGame?.toDomain(),
)

fun ArtworkItemDto.toDomain(): ArtworkItem = ArtworkItem(
    url = url,
    width = width,
    height = height,
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
)

fun ScreenshotItemDto.toDomain(): ScreenshotItem = ScreenshotItem(
    url = url,
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
)

// --- Phase 10: Social & Community Discovery ---

fun TrendingGameDto.toDomain(): TrendingGame = TrendingGame(
    game = game.toDomain(),
    playersThisWeek = playersThisWeek,
)

fun CommunityTopGameDto.toDomain(): CommunityTopGame = CommunityTopGame(
    game = game.toDomain(),
    avgRating = avgRating,
    ratingCount = ratingCount,
)

fun CultClassicGameDto.toDomain(): CultClassicGame = CultClassicGame(
    game = game.toDomain(),
    communityRating = communityRating,
    igdbCriticsRating = igdbCriticsRating,
    ratingCount = ratingCount,
)

fun RecentReviewItemDto.toDomain(): RecentReviewItem = RecentReviewItem(
    game = game.toDomain(),
    rating = rating,
    review = review,
    reviewerName = reviewerName,
    reviewedAt = reviewedAt,
)

fun ActiveNowItemDto.toDomain(): ActiveNowItem = ActiveNowItem(
    game = game.toDomain(),
    activeSessions = activeSessions,
    activeChallenges = activeChallenges,
)

// --- Phase 11: Temporal Discovery ---

fun AnniversaryItemDto.toDomain(): AnniversaryItem = AnniversaryItem(
    game = game.toDomain(),
    yearsAgo = yearsAgo,
    playedAt = playedAt,
)

// --- Phase 12: Achievement & Challenge-Driven Discovery ---

fun AchievementGameItemDto.toDomain() = AchievementGameItem(
    game = game.toDomain(),
    totalAchievements = totalAchievements,
    avgCompletion = avgCompletion,
    playersAttempted = playersAttempted,
    playersCompleted = playersCompleted,
)

fun AlmostDoneGameDto.toDomain() = AlmostDoneGame(
    game = game.toDomain(),
    unlockedCount = unlockedCount,
    totalCount = totalCount,
    completionPercent = completionPercent,
)

fun FreshChallengeGameDto.toDomain() = FreshChallengeGame(
    game = game.toDomain(),
    totalAchievements = totalAchievements,
    totalPoints = totalPoints,
)

fun ExploreChallengeDto.toDomain() = ExploreChallenge(
    id = id,
    creatorUsername = creatorUsername,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    consoleName = consoleName,
    name = name,
    description = description,
    type = type,
    difficulty = difficulty,
    attemptCount = attemptCount,
    completionCount = completionCount,
    expiresAt = expiresAt,
    createdAt = createdAt,
)

// --- Phase 13: Advanced Search & Saved Searches ---

fun com.spela.client.models.SavedSearchResponse.toDomain() = SavedSearch(
    id = id,
    name = name,
    // The generated filters field is a JsonElement? (JsonObject at runtime).
    // Flatten to the domain Map<String, String>. Non-object / null gives
    // an empty map.
    filters = (filters as? kotlinx.serialization.json.JsonObject)
        ?.mapValues { (_, v) -> (v as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty() }
        ?: emptyMap(),
    createdAt = createdAt.toString(),
)

// --- Phase 14: Wild Features ---

fun WizardOptionDto.toDomain() = WizardOption(
    id = id,
    label = label,
    description = description,
)

fun WizardStepDto.toDomain() = WizardStep(
    step = step,
    title = title,
    type = type,
    options = options.map { it.toDomain() },
)

fun WizardResultsResponseDto.toDomain() = WizardResults(
    games = games.map { it.toDomain() },
    title = title,
)

fun com.spela.client.models.ExplorerBadge.toDomain() = ExplorerBadge(
    id = id,
    name = name,
    description = description,
    icon = icon,
    earned = earned,
    progress = progress.toInt(),
    target = target.toInt(),
)

fun com.spela.client.models.CompletionistConsole.toDomain() = CompletionistConsole(
    id = id,
    name = name,
    totalGames = totalGames.toInt(),
    playedGames = playedGames.toInt(),
    percentage = percentage.toInt(),
)

fun com.spela.client.models.CompletionistMapResponse.toDomain() = CompletionistMap(
    consoles = consoles.orEmpty().map { it.toDomain() },
    totalGames = totalGames.toInt(),
    totalPlayed = totalPlayed.toInt(),
    overallPct = overallPct.toInt(),
)

// --- Global Search mappers ---

fun GlobalSearchGameResultDto.toDomain() = SearchGameResult(
    id = id,
    title = title,
    coverUrl = coverUrl,
    consoleName = consoleName,
    consoleId = consoleId,
    developer = developer,
    genre = genre,
    coverAspectRatio = coverAspectRatio.toFloat(),
)

fun GlobalSearchConsoleResultDto.toDomain() = SearchConsoleResult(
    id = id,
    name = name,
    iconUrl = iconUrl,
    gameCount = gameCount,
    colorTheme = colorTheme,
)

fun GlobalSearchDeveloperResultDto.toDomain() = SearchDeveloperResult(
    name = name,
    gameCount = gameCount,
    avgRating = avgRating,
)

fun GlobalSearchPublisherResultDto.toDomain() = SearchPublisherResult(
    name = name,
    gameCount = gameCount,
    avgRating = avgRating,
)

fun GlobalSearchCollectionResultDto.toDomain() = SearchCollectionResult(
    id = id,
    name = name,
    gameCount = gameCount,
    coverUrl = coverUrl,
    username = username,
)

fun GlobalSearchSeriesResultDto.toDomain() = SearchSeriesResult(
    id = id,
    name = name,
    totalGames = totalGames,
    libraryGames = libraryGames,
)

fun GlobalSearchFranchiseResultDto.toDomain() = SearchFranchiseResult(
    id = id,
    name = name,
    totalGames = totalGames,
    libraryGames = libraryGames,
)

fun GlobalSearchResponseDto.toDomain() = GlobalSearchResult(
    games = SearchCategory(
        results = games.results.map { it.toDomain() },
        total = games.total,
    ),
    consoles = SearchCategory(
        results = consoles.results.map { it.toDomain() },
        total = consoles.total,
    ),
    developers = SearchCategory(
        results = developers.results.map { it.toDomain() },
        total = developers.total,
    ),
    publishers = SearchCategory(
        results = publishers.results.map { it.toDomain() },
        total = publishers.total,
    ),
    collections = SearchCategory(
        results = collections.results.map { it.toDomain() },
        total = collections.total,
    ),
    series = SearchCategory(
        results = series.results.map { it.toDomain() },
        total = series.total,
    ),
    franchises = SearchCategory(
        results = franchises.results.map { it.toDomain() },
        total = franchises.total,
    ),
)
