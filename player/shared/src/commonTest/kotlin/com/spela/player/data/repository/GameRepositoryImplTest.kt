package com.spela.player.data.repository

import com.spela.player.data.remote.dto.*
import kotlin.test.*

private fun testGameResponse(
    id: String,
    title: String,
    consoleId: String,
    consoleName: String = "",
    fileName: String = "",
    fileSize: Long = 0,
    coverAspectRatio: Double = 0.75,
    coverUrl: String = "",
    screenshotUrls: List<String>? = null,
    description: String = "",
    developer: String = "",
    publisher: String = "",
    releaseDate: String = "",
    genre: String = "",
    isFavorite: Boolean = false,
    isInPlayLater: Boolean = false,
    lastPlayedAt: kotlin.time.Instant? = null,
    totalPlayTime: Long = 0,
): com.spela.client.models.GameResponse {
    val now = kotlin.time.Instant.fromEpochSeconds(0)
    return com.spela.client.models.GameResponse(
        averageRating = 0.0,
        consoleId = consoleId,
        consoleName = consoleName,
        coverAspectRatio = coverAspectRatio,
        coverUrl = coverUrl,
        createdAt = now,
        description = description,
        developer = developer,
        discCount = 0L,
        fileName = fileName,
        fileSize = fileSize,
        genre = genre,
        id = id,
        igdbCriticsRating = 0.0,
        isFavorite = isFavorite,
        isInPlayLater = isInPlayLater,
        isPreRelease = false,
        lastPlayedAt = lastPlayedAt,
        playable = true,
        players = 0L,
        publisher = publisher,
        ratingCount = 0L,
        releaseDate = releaseDate,
        scrapeAttempts = 0L,
        screenshotUrls = screenshotUrls ?: emptyList(),
        title = title,
        totalPlayTime = totalPlayTime,
        updatedAt = now,
        achievementsWarning = "",
        ageRatings = emptyList(),
        biosStatus = "",
        coreOverride = "",
        discs = emptyList(),
        gameModes = "",
        groupKey = "",
        heroUrl = "",
        igdbUserRating = 0.0,
        igdbUserRatingCount = 0L,
        languageSupports = emptyList(),
        logoUrl = "",
        parentGame = com.spela.client.models.ParentGameResponse(id = "", title = "", coverUrl = ""),
        partyInfo = "",
        region = "",
        releaseDates = emptyList(),
        revision = "",
        romHacks = emptyList(),
        scraperId = "",
        storyline = "",
        tags = "",
        timeToBeatCompletely = 0L,
        timeToBeatHastily = 0L,
        timeToBeatNormally = 0L,
        totalRating = 0.0,
        totalRatingCount = 0L,
        userRating = 0L,
        variantCount = 0L,
        variants = emptyList(),
        verificationStatus = "",
        verificationTag = "",
        videos = emptyList(),
    )
}

private fun testConsoleResponse(
    id: String,
    name: String,
    abbreviation: String,
    gameCount: Long = 0L,
    coverAspectRatio: Double = 0.75,
): com.spela.client.models.ConsoleResponse {
    val now = kotlin.time.Instant.fromEpochSeconds(0)
    return com.spela.client.models.ConsoleResponse(
        abbreviation = abbreviation,
        browserPlayable = false,
        code = "",
        colorTheme = "#6366f1",
        coverAspectRatio = coverAspectRatio,
        createdAt = now,
        defaultCore = "",
        emulatorJsCore = "",
        extensions = emptyList(),
        gameCount = gameCount,
        generation = 0L,
        iconUrl = "",
        id = id,
        logoPngUrl = "",
        logoUrl = "",
        maker = com.spela.client.models.HardwareMakerResponse(code = "", name = ""),
        mediaType = com.spela.client.models.MediaTypeResponse(
            code = "",
            name = "",
            category = com.spela.client.models.MediaTypeCategoryResponse(code = "", name = ""),
        ),
        name = name,
        playable = true,
        releaseYear = null,
        saveStateSupport = true,
        summary = null,
        unitsSold = null,
        updatedAt = now,
    )
}

class GameRepositoryImplTest {

    private val sampleGames = listOf(
        testGameResponse("1", "Super Mario Bros.", "1", consoleName = "NES", fileName = "smb.nes", fileSize = 40960, releaseDate = "1985-09-13"),
        testGameResponse("2", "Zelda", "1", consoleName = "NES", fileName = "zelda.nes", fileSize = 131072, releaseDate = "1986-02-21"),
    )

    @Test
    fun consoleResponseMapsCorrectly() {
        val mapped = listOf(
            testConsoleResponse("1", "NES", "NES", gameCount = 10L),
            testConsoleResponse("2", "SNES", "SNES", gameCount = 5L),
        ).map { it.toDomain() }

        assertEquals(2, mapped.size)
        assertEquals("NES", mapped[0].name)
        assertEquals("NES", mapped[0].abbreviation)
        assertEquals(10, mapped[0].gameCount)
        assertEquals("1", mapped[0].id)
    }

    @Test
    fun consoleResponseMapsCoverAspectRatio() {
        val dto = testConsoleResponse("1", "NES", "NES", coverAspectRatio = 0.75)
        val domain = dto.toDomain()
        assertEquals(0.75, domain.coverAspectRatio)
    }

    @Test
    fun completionistMapResponseMapsToDomain() {
        val response = com.spela.client.models.CompletionistMapResponse(
            consoles = listOf(
                com.spela.client.models.CompletionistConsole(
                    id = "nes",
                    name = "NES",
                    percentage = 75L,
                    playedGames = 15L,
                    totalGames = 20L,
                ),
                com.spela.client.models.CompletionistConsole(
                    id = "snes",
                    name = "SNES",
                    percentage = 50L,
                    playedGames = 10L,
                    totalGames = 20L,
                ),
            ),
            overallPct = 62L,
            totalGames = 40L,
            totalPlayed = 25L,
        )

        val domain = response.toDomain()

        assertEquals(2, domain.consoles.size)
        assertEquals("nes", domain.consoles[0].id)
        assertEquals(75, domain.consoles[0].percentage) // Long -> Int
        assertEquals(15, domain.consoles[0].playedGames)
        assertEquals(40, domain.totalGames)
        assertEquals(25, domain.totalPlayed)
        assertEquals(62, domain.overallPct)
    }

    @Test
    fun completionistMapResponseHandlesEmptyConsolesList() {
        // Server guarantees non-null lists after DefaultArrayNullable=false;
        // this exercises the empty-but-present path.
        val response = com.spela.client.models.CompletionistMapResponse(
            consoles = emptyList(),
            overallPct = 0L,
            totalGames = 0L,
            totalPlayed = 0L,
        )
        val domain = response.toDomain()
        assertTrue(domain.consoles.isEmpty())
    }

    @Test
    fun challengeResponseMapsToDomain() {
        val now = kotlin.time.Instant.fromEpochSeconds(1_700_000_000)
        val response = com.spela.client.models.ChallengeResponse(
            attemptCount = 25L,
            completionCount = 4L,
            createdAt = now,
            creatorId = "u1",
            creatorUsername = "mattias",
            difficulty = "hard",
            gameId = "g1",
            gameTitle = "Super Metroid",
            id = "c1",
            name = "100% Run",
            saveFileSize = 65_536L,
            status = "active",
            type = "completion",
            updatedAt = now,
            consoleName = "SNES", // server sends consoleName, not gameConsoleName
            coreName = "snes9x",
            creatorAvatar = "/avatars/mattias.png", // server sends creatorAvatar, not creatorAvatarUrl
            description = "Collect everything",
            expiresAt = null,
            gameCoverUrl = "/covers/smetroid.png",
            screenshotUrl = "/screenshots/smetroid.png",
        )

        val domain = response.toDomain()

        assertEquals("c1", domain.id)
        assertEquals("100% Run", domain.name)
        assertEquals("SNES", domain.gameConsoleName) // mapped from consoleName
        assertEquals("/avatars/mattias.png", domain.creatorAvatarUrl) // mapped from creatorAvatar
        assertEquals(25, domain.attemptCount) // Long -> Int
        assertEquals(4, domain.completionCount)
        assertEquals(com.spela.player.domain.model.ChallengeDifficulty.HARD, domain.difficulty)
        assertEquals(com.spela.player.domain.model.ChallengeType.COMPLETION, domain.type)
        assertEquals(now.toString(), domain.createdAt)
        assertEquals(null, domain.bestTimeMs) // not in spec
    }

    @Test
    fun sharedSaveResponseMapsToDomain() {
        val now = kotlin.time.Instant.fromEpochSeconds(1_700_000_000)
        val response = com.spela.client.models.SharedSaveResponse(
            createdAt = now,
            downloadCount = 12L,
            fileSize = 65_536L,
            gameId = "42",
            id = "ss1",
            name = "Boss Fight",
            userId = "u1",
            username = "Alice",
            avatarUrl = "/avatars/alice.png",
            description = "Before final boss",
            screenshotUrl = "",
        )
        val domain = response.toDomain()
        assertEquals("ss1", domain.id)
        assertEquals("Alice", domain.username)
        assertEquals("/avatars/alice.png", domain.userAvatarUrl)
        assertEquals("Before final boss", domain.description)
        assertEquals(12, domain.downloadCount) // Long -> Int
        assertEquals(65_536L, domain.fileSize)
        assertEquals(now.toString(), domain.createdAt)
    }

    @Test
    fun sharedSaveResponseHandlesNullDescription() {
        val now = kotlin.time.Instant.fromEpochSeconds(0)
        val response = com.spela.client.models.SharedSaveResponse(
            createdAt = now,
            downloadCount = 0L,
            fileSize = 0L,
            gameId = "g",
            id = "s",
            name = "Untitled",
            userId = "u",
            username = "bob",
            avatarUrl = "",
            description = "",
            screenshotUrl = "",
        )
        assertEquals("", response.toDomain().description)
    }

    @Test
    fun themeResponseMapsToDomain() {
        val response = com.spela.client.models.ThemeResponse(
            gameCount = 42L,
            id = "action",
            name = "Action",
        )
        val domain = response.toDomain()
        assertEquals("action", domain.id)
        assertEquals("Action", domain.name)
        assertEquals(42, domain.gameCount) // Long -> Int
    }

    @Test
    fun keywordResponseMapsToDomain() {
        val response = com.spela.client.models.KeywordResponse(
            gameCount = 7L,
            id = "time-travel",
            name = "Time Travel",
        )
        val domain = response.toDomain()
        assertEquals("time-travel", domain.id)
        assertEquals("Time Travel", domain.name)
        assertEquals(7, domain.gameCount)
    }

    @Test
    fun explorerBadgeMapsToDomain() {
        val generated = com.spela.client.models.ExplorerBadge(
            description = "Played 100 games",
            earned = true,
            icon = "trophy",
            id = "centurion",
            name = "Centurion",
            progress = 100L,
            target = 100L,
        )

        val domain = generated.toDomain()

        assertEquals("centurion", domain.id)
        assertEquals("Centurion", domain.name)
        assertEquals("Played 100 games", domain.description)
        assertTrue(domain.earned)
        assertEquals(100, domain.progress) // Long -> Int
        assertEquals(100, domain.target)
    }

    @Test
    fun gameRatingResponseMapsToDomain() {
        val createdAt = kotlin.time.Instant.fromEpochSeconds(1_700_000_000)
        val response = com.spela.client.models.GameRatingResponse(
            createdAt = createdAt,
            gameId = "42",
            id = "r1",
            rating = 4L,
            updatedAt = createdAt,
            userId = "u1",
            username = "mattias",
            avatarUrl = "/avatars/mattias.png",
            review = "Great game",
        )

        val domain = response.toDomain()

        assertEquals("r1", domain.id)
        assertEquals("u1", domain.userId)
        assertEquals("42", domain.gameId)
        assertEquals(4, domain.rating) // Long -> Int
        assertEquals("Great game", domain.review)
        assertEquals("/avatars/mattias.png", domain.avatarUrl)
        assertEquals(createdAt.toString(), domain.createdAt)
    }

    @Test
    fun gameRatingResponseHandlesNullReview() {
        val now = kotlin.time.Instant.fromEpochSeconds(0)
        val response = com.spela.client.models.GameRatingResponse(
            createdAt = now,
            gameId = "42",
            id = "r1",
            rating = 3L,
            updatedAt = now,
            userId = "u1",
            username = "mattias",
            avatarUrl = "",
            review = "",
        )
        assertEquals("", response.toDomain().review)
    }

    @Test
    fun ratingSummaryResponseMapsToDomain() {
        val response = com.spela.client.models.RatingSummaryResponse(
            averageRating = 4.25,
            distribution = mapOf("1" to 1L, "2" to 2L, "3" to 5L, "4" to 10L, "5" to 20L),
            totalRatings = 38L,
        )

        val domain = response.toDomain()

        assertEquals(4.25, domain.averageRating)
        assertEquals(38L, domain.totalRatings)
        assertEquals(5, domain.distribution.size)
        assertEquals(20, domain.distribution[5]) // String "5" -> Int 5 key, Long 20 -> Int 20 value
        assertEquals(1, domain.distribution[1])
    }

    @Test
    fun consoleResponseMapsToDomain() {
        val now = kotlin.time.Instant.fromEpochSeconds(0)
        val response = com.spela.client.models.ConsoleResponse(
            abbreviation = "NES",
            browserPlayable = false,
            code = "nintendo-nes",
            colorTheme = "#e60012",
            coverAspectRatio = 0.75,
            createdAt = now,
            defaultCore = "nestopia",
            emulatorJsCore = "nes",
            extensions = listOf("nes"),
            gameCount = 42L,
            generation = 3L,
            iconUrl = "icon",
            id = "1",
            logoPngUrl = "logo.png",
            logoUrl = "logo.svg",
            maker = com.spela.client.models.HardwareMakerResponse(code = "nintendo", name = "Nintendo"),
            mediaType = com.spela.client.models.MediaTypeResponse(
                category = com.spela.client.models.MediaTypeCategoryResponse(code = "cart", name = "Cartridge"),
                code = "nes-cart",
                name = "NES Cartridge",
            ),
            name = "Nintendo Entertainment System",
            playable = true,
            releaseYear = 1983L,
            saveStateSupport = true,
            summary = "8-bit classic",
            unitsSold = 61_900_000L,
            updatedAt = now,
        )

        val domain = response.toDomain()

        assertEquals("1", domain.id)
        assertEquals("Nintendo Entertainment System", domain.name)
        assertEquals("NES", domain.abbreviation)
        assertEquals(42, domain.gameCount) // Long -> Int conversion
        assertEquals(3, domain.generation)
        assertEquals(1983, domain.releaseYear)
        assertEquals("Nintendo", domain.makerName)
        assertEquals("nintendo", domain.makerCode)
        assertEquals("NES Cartridge", domain.mediaTypeName)
        assertEquals("logo.png", domain.logoUrl) // logoPngUrl preferred when non-empty
        assertEquals(61_900_000L, domain.unitsSold)
    }

    @Test
    fun gameDtoMapsCorrectly() {
        val dto = sampleGames[0]
        val domain = dto.toDomain()

        assertEquals("1", domain.id)
        assertEquals("Super Mario Bros.", domain.title)
        assertEquals("1", domain.consoleId)
        assertEquals("NES", domain.consoleName)
        assertEquals("1985-09-13", domain.releaseDate)
        assertEquals(40960, domain.fileSize)
        assertEquals("smb.nes", domain.fileName)
    }

    @Test
    fun gameDtoMapsEnrichedFields() {
        val lastPlayed = kotlin.time.Instant.parse("2024-01-15T10:30:00Z")
        val dto = testGameResponse(
            "1", "Test", "1",
            isFavorite = true,
            lastPlayedAt = lastPlayed,
            totalPlayTime = 3600,
        )
        val domain = dto.toDomain()

        assertTrue(domain.isFavorite)
        assertEquals(lastPlayed.toString(), domain.lastPlayedAt)
        assertEquals(3600, domain.totalPlayTime)
    }

    @Test
    fun gameDtoToGameDetailIncludesScreenshots() {
        val dto = testGameResponse("1", "Test", "1", screenshotUrls = listOf("https://example.com/ss1.jpg", "https://example.com/ss2.jpg"))
        val detail = dto.toGameDetail()

        assertEquals("Test", detail.game.title)
        assertEquals(2, detail.screenshots.size)
        assertEquals("https://example.com/ss1.jpg", detail.screenshots[0])
    }

    @Test
    fun gameDtoToGameDetailWithoutScreenshots() {
        val dto = testGameResponse("1", "Test", "1")
        val detail = dto.toGameDetail()

        assertTrue(detail.screenshots.isEmpty())
    }

    @Test
    fun saveStateDtoMapsCorrectly() {
        val now = kotlin.time.Instant.parse("2024-01-15T10:30:00Z")
        val dto = com.spela.client.models.SessionSaveResponse(
            id = "save-1",
            sessionId = "session-1",
            userId = "user-1",
            username = "alice",
            name = "Slot 1",
            fileSize = 65536,
            isAuto = false,
            isCurrent = false,
            createdAt = now,
            updatedAt = now,
            coreMatch = false,
            coreName = "",
            coreSha256 = "",
            currentCore = "",
            notes = "",
            screenshotUrl = "",
            slot = 0L,
        )
        val domain = dto.toDomain()

        assertEquals("save-1", domain.id)
        assertEquals("Slot 1", domain.name)
        assertEquals(65536, domain.fileSize)
        assertFalse(domain.isAuto)
        assertNotNull(domain.createdAt)
    }

    @Test
    fun authResponseMapsCorrectly() {
        val now = kotlin.time.Instant.fromEpochSeconds(0)
        val dto = com.spela.client.models.AuthLoginResponse(
            accessToken = "access123",
            refreshToken = "refresh456",
            user = UserDto(
                avatarUrl = "",
                createdAt = now,
                disabled = false,
                email = "test@example.com",
                id = "1",
                pendingApproval = false,
                role = "user",
                updatedAt = now,
                username = "testuser",
            ),
        )
        val tokens = dto.toDomain()

        assertEquals("access123", tokens.accessToken)
        assertEquals("refresh456", tokens.refreshToken)

        val user = dto.extractUser()
        assertEquals("testuser", user.username)
        assertEquals("test@example.com", user.email)
        assertEquals("1", user.id)
    }

    @Test
    fun recentGamesAreEnrichedGameResponses() {
        val lastPlayed = kotlin.time.Instant.parse("2024-01-15T10:30:00Z")
        val dto = testGameResponse(
            "1", "Super Mario Bros.", "1",
            consoleName = "NES",
            fileName = "smb.nes",
            lastPlayedAt = lastPlayed,
            totalPlayTime = 7200,
        )
        val game = dto.toDomain()

        assertEquals("Super Mario Bros.", game.title)
        assertEquals(lastPlayed.toString(), game.lastPlayedAt)
        assertEquals(7200, game.totalPlayTime)
    }

    @Test
    fun favoriteGamesHaveIsFavoriteTrue() {
        val dto = testGameResponse("1", "Zelda", "1", consoleName = "NES", fileName = "zelda.nes", isFavorite = true)
        val game = dto.toDomain()

        assertEquals("Zelda", game.title)
        assertTrue(game.isFavorite)
    }

    @Test
    fun coreResponseMapsCorrectly() {
        val now = kotlin.time.Instant.fromEpochSeconds(0)
        val dto = com.spela.client.models.Core(
            createdAt = now,
            description = "",
            displayName = "Nestopia UE",
            downloadUrl = "",
            fetchedAt = null,
            id = 1,
            name = "nestopia",
            platforms = "windows,linux,macos,android",
            sha256 = "",
            sizeBytes = 0L,
            sourceUrl = "",
            updatedAt = now,
            version = "1.52.0",
        )
        val domain = dto.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("nestopia", domain.name)
        assertEquals("Nestopia UE", domain.displayName)
        assertEquals("1.52.0", domain.version)
        assertEquals("windows,linux,macos,android", domain.platforms)
    }

    @Test
    fun paginatedResponseUsesDataField() {
        val response = GameListResponse(
            data = listOf(testGameResponse("1", "Test", "1")),
            total = 1L,
            page = 1L,
            pageSize = 50L,
        )

        assertEquals(1, response.data.orEmpty().size)
        assertEquals("Test", response.data.orEmpty()[0].title)
        assertEquals(50, response.pageSize)
    }
}
