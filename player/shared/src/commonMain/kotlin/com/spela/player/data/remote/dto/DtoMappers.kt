package com.spela.player.data.remote.dto

import com.spela.player.domain.model.*

fun com.spela.client.models.AuthLoginResponse.toDomain(): AuthTokens = AuthTokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
)

fun com.spela.client.models.AuthLoginResponse.extractUser(): User = user.toDomain()

/**
 * Maps a register response to tokens. When the server returns the pending
 * branch (account awaits admin approval) the access + refresh tokens are
 * omitted — this throws an [IllegalStateException] so the repository can
 * surface the condition to the caller; the domain-layer [AuthRepository]
 * signature only returns [AuthTokens]. Callers should catch and translate
 * to a "pending approval" UX state if/when the domain model grows a variant.
 */
fun com.spela.client.models.AuthRegisterResponse.toDomain(): AuthTokens {
    if (pending) {
        error(message.ifEmpty { "Registration pending admin approval" })
    }
    return AuthTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
    )
}

fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    email = email,
    role = role,
    avatarUrl = avatarUrl,
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
    variantCount = variantCount.toInt(),
    groupKey = groupKey,
    timeToBeatHastily = timeToBeatHastily.toInt(),
    timeToBeatNormally = timeToBeatNormally.toInt(),
    timeToBeatCompletely = timeToBeatCompletely.toInt(),
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
    screenshots = screenshotUrls,
    variants = variants.map { it.toDomain() },
    parentGame = parentGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
    romHacks = romHacks.map { it.toDomain() },
)

fun com.spela.client.models.SessionSaveResponse.toDomain(): SaveState = SaveState(
    id = id,
    name = name,
    createdAt = createdAt,
    fileSize = fileSize,
    isAuto = isAuto,
    coreName = coreName,
    notes = notes,
    screenshotUrl = screenshotUrl,
    slot = slot?.toInt(),
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
    currentGame = currentGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
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
    playTime = playTime,
)

fun com.spela.client.models.PublicProfileResponse.toDomain(): PublicProfile = PublicProfile(
    id = id,
    username = username,
    avatarUrl = avatarUrl,
    memberSince = memberSince.toString(),
    isOnline = isOnline,
    currentGame = currentGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
    totalPlayTime = totalPlayTime,
    gamesPlayed = gamesPlayed,
    favoriteGames = favoriteGames.map { it.toDomain() },
    recentGames = recentGames.map { it.toDomain() },
    topGames = topGames.map { it.toDomain() },
)

// Shared Session mappers
//
// The generated types use kotlinx.datetime.Instant for timestamps and
// kotlin.Long for counts; the domain models keep String timestamps and Int
// counts to stay consistent with the rest of the player. The mappers handle
// the conversion.
//
// Fields not present on the server (description, lastActivityAt,
// gameConsoleName on invites, lastPlayedAt, isOnline, inviterAvatarUrl) get
// their Kotlin defaults from the domain model constructors.

fun com.spela.client.models.SharedSessionResponse.toDomain(): SharedSession = SharedSession(
    id = id,
    name = name,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName.orEmpty(),
    ownerId = ownerId,
    ownerUsername = ownerUsername,
    status = status,
    memberCount = memberCount.toInt(),
    activeUserId = activeUserId,
    lastActivityAt = updatedAt.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun com.spela.client.models.SharedSessionDetailResponse.toDomain(): SharedSessionDetail = SharedSessionDetail(
    id = id,
    name = name,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    gameConsoleName = consoleName.orEmpty(),
    ownerId = ownerId,
    ownerUsername = ownerUsername,
    status = status,
    memberCount = memberCount.toInt(),
    activeUserId = activeUserId,
    members = members.map { it.toDomain() },
    lastActivityAt = updatedAt.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun com.spela.client.models.SharedSessionMemberResponse.toDomain(): SharedSessionMember = SharedSessionMember(
    userId = userId,
    username = username,
    avatarUrl = avatarUrl,
    role = role,
    joinedAt = joinedAt.toString(),
    // lastPlayedAt / isOnline are not sent by the server — domain defaults apply.
)

fun com.spela.client.models.SharedSessionInviteResponse.toDomain(): SharedSessionInvitation = SharedSessionInvitation(
    id = id,
    sharedSessionId = sharedSessionId,
    sharedSessionName = sharedSessionName,
    gameTitle = gameTitle,
    // gameId / gameCoverUrl / gameConsoleName / inviterAvatarUrl are not sent —
    // domain defaults apply.
    inviterUsername = inviterUsername,
    createdAt = createdAt.toString(),
)

fun com.spela.client.models.SharedSessionSaveResponse.toDomain(): SharedSessionSave = SharedSessionSave(
    // Server sends id as String; domain keeps Long. The backing column is a
    // 64-bit integer so toLong() is safe.
    id = id.toLong(),
    sharedSessionId = sharedSessionId,
    username = username,
    name = name,
    fileSize = fileSize,
    isAuto = isAuto,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
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
    games = games.map { it.toDomain() },
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
    topPlayers = topPlayers.map { it.toDomain() },
)

fun com.spela.client.models.Achievement.toDomain(): GameAchievement = GameAchievement(
    id = id,
    title = title,
    description = description,
    points = points.toInt(),
    badgeUrl = badgeUrl.ifEmpty { null },
    type = type.ifEmpty { null },
    displayOrder = null,
    rarityPercent = rarityPercent,
)

fun com.spela.client.models.RAProgressEntry.toDomain(): AchievementProgress = AchievementProgress(
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
    timeline = timeline.map { it.toDomain() },
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
    title = title,
    description = description,
    points = points.toInt(),
    badgeUrl = badgeUrl,
    rarityPercent = rarityPercent,
    gameTitle = gameTitle,
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
    status = NetplaySessionStatus.fromApiId(status),
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

// Server emits `igdbCriticsRating`; the hand-written SimilarGameDto
// used `rating` which was always 0.0 — the new mapper pulls the real
// field. coverUrl is @Required non-nullable (empty string = no cover);
// convert back to null via takeIf.
fun com.spela.client.models.SimilarGameResponse.toDomain(): SimilarGame = SimilarGame(
    igdbGameId = igdbGameId.toInt(),
    name = name,
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
    rating = igdbCriticsRating,
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
    memberUsernames = memberUsernames,
    memberAvatars = memberAvatars,
)

fun com.spela.client.models.SessionCheatsResponse.toDomain() = SessionCheatConfig(
    cheatsEnabled = cheatsEnabled,
    enabledIndices = enabledIndices.map { it.toInt() },
)

// Explore mappers

// Explore Featured / Rows — server ships heroUrl/logoUrl as empty strings
// when absent, and the rating field is named `igdbCriticsRating` (the
// hand-written DTO used `rating` which silently stayed 0.0). The mapper
// picks the real field and converts empty strings back to null.
fun com.spela.client.models.FeaturedGameResponse.toDomain(): FeaturedGame = FeaturedGame(
    gameId = gameId,
    title = title,
    heroUrl = heroUrl.takeIf { it.isNotEmpty() },
    logoUrl = logoUrl.takeIf { it.isNotEmpty() },
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
    rating = igdbCriticsRating,
    genre = genre,
    isFavorite = isFavorite,
    isPlayLater = isPlayLater,
)

fun com.spela.client.models.ExploreRowResponse.toDomain(): ExploreRow = ExploreRow(
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

fun com.spela.client.models.FeaturedSeriesResponse.toDomain(): FeaturedSeries = FeaturedSeries(
    id = id,
    name = name,
    libraryGames = libraryGames.toInt(),
    totalGames = totalGames.toInt(),
    consoleCount = consoleCount.toInt(),
    heroUrl = heroUrl,
)

fun com.spela.client.models.SeriesDetailResponse.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoles = consoles.map { it.toDomain() },
    libraryGames = libraryGames.toInt(),
    totalGames = totalGames.toInt(),
    games = games.map { it.toDomain() },
)

fun com.spela.client.models.FranchiseDetailResponse.toDomain(): SeriesDetail = SeriesDetail(
    id = id,
    name = name,
    heroUrl = heroUrl,
    logoUrl = logoUrl,
    consoles = consoles.map { it.toDomain() },
    libraryGames = libraryGames.toInt(),
    totalGames = totalGames.toInt(),
    games = games.map { it.toDomain() },
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


fun com.spela.client.models.MoodResponse.toDomain(): MoodDefinition = MoodDefinition(
    id = id,
    name = name,
    description = description,
    icon = icon,
    gradient = gradient,
)

fun com.spela.client.models.ForYouRowResponse.toDomain(): ForYouRow = ForYouRow(
    type = type,
    title = title,
    sourceGame = sourceGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
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
    genres = genres.map { it.toDomain() },
    themes = themes.map { it.toDomain() },
    topConsoles = topConsoles.map { it.toDomain() },
)

fun com.spela.client.models.PlayersLikeYouResponse.toDomain(): PlayersLikeYouResult = PlayersLikeYouResult(
    games = games.map { it.toDomain() },
    similarUsersCount = similarUsersCount.toInt(),
)

// Developer / Publisher mappers
//
// Generated notes:
// - DeveloperDetailResponse + DeveloperSpotlightResponse use Long for
//   numeric counts — mappers call .toInt().
// - EntityUserStats replaces the hand-written DeveloperDetailUserStatsDto.
//   Its mostPlayedGame is @Required non-nullable: the server emits an
//   empty Game placeholder (empty id) when the user has never played
//   any of the dev's games; the mapper converts back to null via
//   `takeIf { it.id.isNotEmpty() }`.
// - NameCount replaces the hand-written DeveloperDetailPublisherDto.
// - PlatformCount replaces the hand-written platform breakdown DTO.
// - TimelineGame uses `igdbCriticsRating` (hand-written used `rating` —
//   another silent zero). coverUrl is @Required non-nullable (empty
//   string means no cover); mapper restores null via takeIf.

fun com.spela.client.models.DeveloperSummary.toDomain(): DeveloperSummary = DeveloperSummary(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
    consoles = consoles,
)

fun com.spela.client.models.GenreCount.toDeveloperGenreBreakdown(): DeveloperDetailGenreBreakdown =
    DeveloperDetailGenreBreakdown(
        name = name,
        gameCount = gameCount.toInt(),
    )

fun com.spela.client.models.PlatformCount.toDeveloperPlatformBreakdown(): DeveloperDetailPlatformBreakdown =
    DeveloperDetailPlatformBreakdown(
        consoleName = consoleName,
        consoleId = consoleId,
        count = count.toInt(),
    )

fun com.spela.client.models.EntityUserStats.toDeveloperUserStats(): DeveloperDetailUserStats =
    DeveloperDetailUserStats(
        totalPlayTime = totalPlayTime,
        gamesPlayed = gamesPlayed.toInt(),
        favoriteCount = favoriteCount.toInt(),
        // Server emits an empty Game placeholder when the user has never
        // played anything — detect via empty id and pass null through.
        mostPlayedGame = mostPlayedGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
    )

fun com.spela.client.models.NameCount.toDeveloperPublisher(): DeveloperDetailPublisher =
    DeveloperDetailPublisher(
        name = name,
        count = count.toInt(),
    )

fun com.spela.client.models.CompanyInfo.toDomain(): CompanyInfo = CompanyInfo(
    logoUrl = logoUrl.ifEmpty { null },
    description = description.ifEmpty { null },
    foundedYear = foundedYear.takeIf { it > 0 }?.toInt(),
    country = country.ifEmpty { null },
    websiteUrl = websiteUrl.ifEmpty { null },
    wikipediaUrl = wikipediaUrl.ifEmpty { null },
)

fun com.spela.client.models.ActiveYears.toDomain(): ActiveYears = ActiveYears(
    first = first.toInt(),
    last = last.toInt(),
)

fun com.spela.client.models.RatingDistribution.toDomain(): RatingDistribution = RatingDistribution(
    excellent = excellent.toInt(),
    good = good.toInt(),
    average = average.toInt(),
    poor = poor.toInt(),
    unrated = unrated.toInt(),
)

fun com.spela.client.models.TimelineGame.toDomain(): TimelineGame = TimelineGame(
    id = id,
    title = title,
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
    // Server sends `igdbCriticsRating`; hand-written DTO used `rating`
    // which was always 0.0.
    rating = igdbCriticsRating,
)

fun com.spela.client.models.TimelineEntry.toDomain(): TimelineEntry = TimelineEntry(
    year = year.toInt(),
    games = games.map { it.toDomain() },
)

fun com.spela.client.models.RelatedDeveloper.toDomain(): RelatedDeveloper = RelatedDeveloper(
    name = name,
    gameCount = gameCount.toInt(),
    sharedPublishers = sharedPublishers,
)

fun com.spela.client.models.DeveloperDetailResponse.toDomain(): DeveloperDetail = DeveloperDetail(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
    consoles = consoles,
    heroUrl = heroUrl,
    companyInfo = companyInfo.toDomain().takeIf { it.hasAnyData() },
    topGames = topGames.map { it.toDomain() },
    genreBreakdown = genreBreakdown.map { it.toDeveloperGenreBreakdown() },
    platformBreakdown = platformBreakdown.map { it.toDeveloperPlatformBreakdown() },
    userStats = userStats.takeIf { it.gamesPlayed > 0 }?.toDeveloperUserStats(),
    publishers = publishers.map { it.toDeveloperPublisher() },
    games = games.map { it.toDomain() },
    activeYears = activeYears.takeIf { it.first > 0 }?.toDomain(),
    // Generated RatingDistribution is @Required non-null.
    ratingDistribution = ratingDistribution.toDomain(),
    primaryGenre = primaryGenre,
    timeline = timeline.map { it.toDomain() },
    relatedDevelopers = relatedDevelopers.map { it.toDomain() },
)

fun com.spela.client.models.GenreCount.toPublisherGenreBreakdown(): PublisherDetailGenreBreakdown =
    PublisherDetailGenreBreakdown(
        name = name,
        gameCount = gameCount.toInt(),
    )

fun com.spela.client.models.PlatformCount.toPublisherPlatformBreakdown(): PublisherDetailPlatformBreakdown =
    PublisherDetailPlatformBreakdown(
        consoleName = consoleName,
        consoleId = consoleId,
        count = count.toInt(),
    )

fun com.spela.client.models.EntityUserStats.toPublisherUserStats(): PublisherDetailUserStats =
    PublisherDetailUserStats(
        totalPlayTime = totalPlayTime,
        gamesPlayed = gamesPlayed.toInt(),
        favoriteCount = favoriteCount.toInt(),
        mostPlayedGame = mostPlayedGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
    )

fun com.spela.client.models.NameCount.toPublisherDeveloper(): PublisherDetailDeveloper =
    PublisherDetailDeveloper(
        name = name,
        count = count.toInt(),
    )

fun com.spela.client.models.RelatedPublisher.toDomain(): RelatedPublisher = RelatedPublisher(
    name = name,
    gameCount = gameCount.toInt(),
    sharedDevelopers = sharedDevelopers,
)

fun com.spela.client.models.PublisherDetailResponse.toDomain(): PublisherDetail = PublisherDetail(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
    consoles = consoles,
    heroUrl = heroUrl,
    companyInfo = companyInfo.toDomain().takeIf { it.hasAnyData() },
    topGames = topGames.map { it.toDomain() },
    genreBreakdown = genreBreakdown.map { it.toPublisherGenreBreakdown() },
    platformBreakdown = platformBreakdown.map { it.toPublisherPlatformBreakdown() },
    userStats = userStats.takeIf { it.gamesPlayed > 0 }?.toPublisherUserStats(),
    developers = developers.map { it.toPublisherDeveloper() },
    games = games.map { it.toDomain() },
    activeYears = activeYears.takeIf { it.first > 0 }?.toDomain(),
    ratingDistribution = ratingDistribution.toDomain(),
    primaryGenre = primaryGenre,
    timeline = timeline.map { it.toDomain() },
    relatedPublishers = relatedPublishers.map { it.toDomain() },
)

fun com.spela.client.models.DeveloperSpotlightResponse.toDomain(): DeveloperSpotlight = DeveloperSpotlight(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
    consoles = consoles,
    topGames = topGames.map { it.toDomain() },
    heroUrl = heroUrl.ifBlank { null },
)

// Console Showcase mappers

fun com.spela.client.models.GenreCount.toDomain(): GenreCount = GenreCount(
    name = name,
    gameCount = gameCount.toInt(),
)

fun com.spela.client.models.ConsoleShowcaseResponse.toDomain(): ConsoleShowcase = ConsoleShowcase(
    console = console.toDomain(),
    essentials = essentials.map { it.toDomain() },
    hiddenGems = hiddenGems.map { it.toDomain() },
    genreBreakdown = genreBreakdown.map { it.toDomain() },
    topDevelopers = topDevelopers.map { it.toDomain() },
    recentlyPlayed = recentlyPlayed.map { it.toDomain() },
)

// Generated ConsoleHighlight has @Required non-nullable topGame, but the
// underlying JSON is `*GameResponse` (nullable) on the server. The mapper
// passes the whole value to toDomain() — if the spec is ever corrected
// to nullable, the server field can be treated as optional without
// further changes. gameCount is Long — convert to Int.
fun com.spela.client.models.ConsoleHighlight.toDomain(): ConsoleHighlight = ConsoleHighlight(
    id = id,
    name = name,
    colorTheme = colorTheme,
    iconUrl = iconUrl,
    logoUrl = logoUrl,
    gameCount = gameCount.toInt(),
    topGame = topGame.takeIf { it.id.isNotEmpty() }?.toDomain(),
)

fun com.spela.client.models.ArtworkItem.toDomain(): ArtworkItem = ArtworkItem(
    url = url,
    width = width.toInt(),
    height = height.toInt(),
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
)

fun com.spela.client.models.ScreenshotItem.toDomain(): ScreenshotItem = ScreenshotItem(
    url = url,
    gameId = gameId,
    gameTitle = gameTitle,
    consoleName = consoleName,
    consoleAbbreviation = consoleAbbreviation,
    consoleColor = consoleColor,
)

// --- Phase 10: Social & Community Discovery ---

fun com.spela.client.models.TrendingGameResponse.toDomain(): TrendingGame = TrendingGame(
    game = game.toDomain(),
    playersThisWeek = playersThisWeek.toInt(),
)

fun com.spela.client.models.CommunityTopGame.toDomain(): CommunityTopGame = CommunityTopGame(
    game = game.toDomain(),
    avgRating = avgRating,
    ratingCount = ratingCount.toInt(),
)

fun com.spela.client.models.CultClassicGame.toDomain(): CultClassicGame = CultClassicGame(
    game = game.toDomain(),
    communityRating = communityRating,
    igdbCriticsRating = igdbCriticsRating,
    ratingCount = ratingCount.toInt(),
)

fun com.spela.client.models.RecentReviewItem.toDomain(): RecentReviewItem = RecentReviewItem(
    game = game.toDomain(),
    rating = rating.toInt(),
    review = review,
    reviewerName = reviewerName,
    reviewedAt = reviewedAt.toString(),
)

fun com.spela.client.models.ActiveNowItem.toDomain(): ActiveNowItem = ActiveNowItem(
    game = game.toDomain(),
    activeSessions = activeSessions.toInt(),
    activeChallenges = activeChallenges.toInt(),
)

// --- Phase 11: Temporal Discovery ---

fun com.spela.client.models.AnniversaryItem.toDomain(): AnniversaryItem = AnniversaryItem(
    game = game.toDomain(),
    yearsAgo = yearsAgo.toInt(),
    playedAt = playedAt.toString(),
)

// --- Phase 12: Achievement & Challenge-Driven Discovery ---

fun com.spela.client.models.AchievementGameResponse.toDomain() = AchievementGameItem(
    game = game.toDomain(),
    totalAchievements = totalAchievements.toInt(),
    avgCompletion = avgCompletion.toFloat(),
    playersAttempted = playersAttempted.toInt(),
    playersCompleted = playersCompleted.toInt(),
)

fun com.spela.client.models.AlmostDoneGame.toDomain() = AlmostDoneGame(
    game = game.toDomain(),
    unlockedCount = unlockedCount.toInt(),
    totalCount = totalCount.toInt(),
    completionPercent = completionPercent.toFloat(),
)

fun com.spela.client.models.FreshChallengeGame.toDomain() = FreshChallengeGame(
    game = game.toDomain(),
    totalAchievements = totalAchievements.toInt(),
    totalPoints = totalPoints.toInt(),
)

fun com.spela.client.models.ExploreChallengeResponse.toDomain() = ExploreChallenge(
    id = id,
    creatorUsername = creatorUsername,
    gameId = gameId,
    gameTitle = gameTitle,
    gameCoverUrl = gameCoverUrl,
    consoleName = consoleName,
    name = name,
    description = description,
    type = ChallengeType.fromApiId(type),
    difficulty = ChallengeDifficulty.fromApiId(difficulty),
    attemptCount = attemptCount.toInt(),
    completionCount = completionCount.toInt(),
    expiresAt = expiresAt?.toString(),
    createdAt = createdAt.toString(),
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

fun com.spela.client.models.WizardOption.toDomain() = WizardOption(
    id = id,
    label = label,
    description = description.orEmpty(),
)

fun com.spela.client.models.WizardStep.toDomain() = WizardStep(
    step = step.toInt(),
    title = title,
    type = type,
    options = options.map { it.toDomain() },
)

fun com.spela.client.models.WizardResultsResponse.toDomain() = WizardResults(
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
    consoles = consoles.map { it.toDomain() },
    totalGames = totalGames.toInt(),
    totalPlayed = totalPlayed.toInt(),
    overallPct = overallPct.toInt(),
)

// --- Global Search mappers ---
//
// Fully qualified on the generated-type side because every one of these
// clashes name-for-name with a domain model (SearchGameResult etc.).

fun com.spela.client.models.SearchGameResult.toDomain() = SearchGameResult(
    id = id,
    title = title,
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
    consoleName = consoleName,
    consoleId = consoleId,
    developer = developer.takeIf { it.isNotEmpty() },
    genre = genre.takeIf { it.isNotEmpty() },
    coverAspectRatio = coverAspectRatio.toFloat(),
)

fun com.spela.client.models.SearchConsoleResult.toDomain() = SearchConsoleResult(
    id = id,
    name = name,
    iconUrl = iconUrl,
    gameCount = gameCount.toInt(),
    colorTheme = colorTheme,
)

/** The spec uses SearchCompanyResult for both developers and publishers. */
private fun com.spela.client.models.SearchCompanyResult.toDeveloper() = SearchDeveloperResult(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
)

private fun com.spela.client.models.SearchCompanyResult.toPublisher() = SearchPublisherResult(
    name = name,
    gameCount = gameCount.toInt(),
    avgRating = avgRating,
)

fun com.spela.client.models.SearchCollectionResult.toDomain() = SearchCollectionResult(
    id = id,
    name = name,
    gameCount = gameCount.toInt(),
    coverUrl = coverUrl.takeIf { it.isNotEmpty() },
    username = username,
)

fun com.spela.client.models.SearchSeriesResult.toDomain() = SearchSeriesResult(
    id = id,
    name = name,
    totalGames = totalGames.toInt(),
    libraryGames = libraryGames.toInt(),
)

fun com.spela.client.models.SearchFranchiseResult.toDomain() = SearchFranchiseResult(
    id = id,
    name = name,
    totalGames = totalGames.toInt(),
    libraryGames = libraryGames.toInt(),
)

fun com.spela.client.models.SearchResponse.toDomain() = GlobalSearchResult(
    games = SearchCategory(
        results = games.results.map { it.toDomain() },
        total = games.total.toInt(),
    ),
    consoles = SearchCategory(
        results = consoles.results.map { it.toDomain() },
        total = consoles.total.toInt(),
    ),
    developers = SearchCategory(
        results = developers.results.map { it.toDeveloper() },
        total = developers.total.toInt(),
    ),
    publishers = SearchCategory(
        results = publishers.results.map { it.toPublisher() },
        total = publishers.total.toInt(),
    ),
    collections = SearchCategory(
        results = collections.results.map { it.toDomain() },
        total = collections.total.toInt(),
    ),
    series = SearchCategory(
        results = series.results.map { it.toDomain() },
        total = series.total.toInt(),
    ),
    franchises = SearchCategory(
        results = franchises.results.map { it.toDomain() },
        total = franchises.total.toInt(),
    ),
)
