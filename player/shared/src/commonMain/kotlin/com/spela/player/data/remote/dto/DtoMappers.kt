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
    igdbCriticsRating = igdbCriticsRating,
    communityRating = averageRating,
    communityRatingCount = ratingCount,
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
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    revision = revision,
    tags = tags,
    isPreRelease = isPreRelease,
    variantCount = variantCount,
    groupKey = groupKey,
    timeToBeatHastily = timeToBeatHastily,
    timeToBeatNormally = timeToBeatNormally,
    timeToBeatCompletely = timeToBeatCompletely,
    partyInfo = partyInfo,
)

/**
 * Constructs GameDetail from the enriched GameResponse.
 * screenshotUrls comes directly from the response.
 */
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

fun GameDto.toGameDetail(): GameDetail = GameDetail(
    game = toDomain(),
    screenshots = screenshotUrls,
    variants = variants.map { it.toDomain() },
    parentGame = parentGame?.toDomain(),
    romHacks = romHacks.map { it.toDomain() },
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

fun LibretroCoreDto.toDomain(): LibretroCore = LibretroCore(
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
    rarityPercent = rarityPercent,
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

fun ShowcaseAchievementDto.toDomain(): ShowcaseAchievement = ShowcaseAchievement(
    achievementRaId = achievementRaId,
    raGameId = raGameId,
    showcaseOrder = showcaseOrder,
    title = title ?: "",
    description = description ?: "",
    points = points ?: 0,
    badgeUrl = badgeUrl,
    rarityPercent = rarityPercent ?: 0.0,
    gameTitle = gameTitle ?: "",
)

fun UnlockedAchievementDto.toDomain(): UnlockedAchievement = UnlockedAchievement(
    achievementRaId = achievementRaId,
    raGameId = raGameId,
    title = title,
    description = description,
    points = points,
    badgeUrl = badgeUrl,
    rarityPercent = rarityPercent,
    gameTitle = gameTitle,
    consoleName = consoleName,
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

fun TopListGameDto.toDomain(): TopListGame = TopListGame(
    rank = rank,
    gameId = gameId,
    name = name,
    coverUrl = coverUrl,
    consoleName = consoleName,
    consoleId = consoleId,
    rating = rating,
)

fun TopRatedGameDto.toDomain(): TopRatedGame = TopRatedGame(
    rank = rank,
    name = name,
    coverUrl = coverUrl,
    rating = rating,
    localGameId = localGameId,
    consoleName = consoleName,
)

fun LongestGameDto.toDomain(): LongestGame = LongestGame(
    rank = rank,
    gameId = gameId,
    name = name,
    coverUrl = coverUrl,
    consoleName = consoleName,
    consoleId = consoleId,
    timeToBeatNormally = timeToBeatNormally,
    timeToBeatHastily = timeToBeatHastily,
    timeToBeatCompletely = timeToBeatCompletely,
)

fun SimilarGameDto.toDomain(): SimilarGame = SimilarGame(
    igdbGameId = igdbGameId,
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
    coreName = coreName,
    cheatsEnabled = cheatsEnabled,
    isSharedSession = isSharedSession,
    sharedSessionId = sharedSessionId,
    memberCount = memberCount,
    memberUsernames = memberUsernames,
    memberAvatars = memberAvatars,
)

fun SessionCheatConfigDto.toDomain() = SessionCheatConfig(
    cheatsEnabled = cheatsEnabled,
    enabledIndices = enabledIndices,
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

fun SeriesDetailDto.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoles = consoles.map { it.toDomain() },
    libraryGames = libraryGames,
    totalGames = totalGames,
    games = games.map { it.toDomain() },
)

fun SeriesConsoleDto.toDomain(): SeriesConsole = SeriesConsole(
    abbreviation = abbreviation,
    name = name,
    color = color,
    gameCount = gameCount,
)

fun SeriesGameDto.toDomain(): SeriesGame = SeriesGame(
    igdbGameId = igdbGameId,
    name = name,
    inLibrary = inLibrary,
    localGameId = localGameId,
    coverUrl = coverUrl,
    releaseDate = releaseDate,
    rating = rating,
    consoleAbbreviation = consoleAbbreviation,
    consoleName = consoleName,
    consoleColor = consoleColor,
)

fun GameSeriesLinkDto.toDomain(): GameSeriesLink = GameSeriesLink(
    id = id,
    name = name,
    totalGames = totalGames,
    libraryGames = libraryGames,
)

fun GameFranchiseLinkDto.toDomain(): GameFranchiseLink = GameFranchiseLink(
    id = id,
    name = name,
    gameCount = gameCount,
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

fun TasteBreakdownDto.toDomain(): TasteBreakdown = TasteBreakdown(
    name = name,
    percentage = percentage,
    playTime = playTime,
    gameCount = gameCount,
)

fun ConsoleBreakdownDto.toDomain(): ConsoleBreakdown = ConsoleBreakdown(
    name = name,
    abbreviation = abbreviation,
    playTime = playTime,
    gameCount = gameCount,
)

fun TasteProfileDto.toDomain(): TasteProfile = TasteProfile(
    totalPlayTime = totalPlayTime,
    genres = genres.map { it.toDomain() },
    themes = themes.map { it.toDomain() },
    topConsoles = topConsoles.map { it.toDomain() },
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

fun SavedSearchDto.toDomain() = SavedSearch(
    id = id,
    name = name,
    filters = filters.mapValues { (_, v) -> v.content },
    createdAt = createdAt,
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
