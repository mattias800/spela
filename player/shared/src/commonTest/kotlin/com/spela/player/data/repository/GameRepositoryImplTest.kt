package com.spela.player.data.repository

import com.spela.player.data.remote.dto.*
import kotlin.test.*

class GameRepositoryImplTest {

    private val sampleConsoles = listOf(
        ConsoleDto("1", "NES", "NES", gameCount = 10),
        ConsoleDto("2", "SNES", "SNES", gameCount = 5),
    )

    private val sampleGames = listOf(
        GameDto("1", "Super Mario Bros.", "1", consoleName = "NES", fileName = "smb.nes", fileSize = 40960, releaseDate = "1985-09-13"),
        GameDto("2", "Zelda", "1", consoleName = "NES", fileName = "zelda.nes", fileSize = 131072, releaseDate = "1986-02-21"),
    )

    @Test
    fun consoleDtoMapsCorrectly() {
        val mapped = sampleConsoles.map { it.toDomain() }

        assertEquals(2, mapped.size)
        assertEquals("NES", mapped[0].name)
        assertEquals("NES", mapped[0].abbreviation)
        assertEquals(10, mapped[0].gameCount)
        assertEquals("1", mapped[0].id)
    }

    @Test
    fun consoleDtoMapsCoverAspectRatio() {
        val dto = ConsoleDto("1", "NES", "NES", coverAspectRatio = 0.75)
        val domain = dto.toDomain()
        assertEquals(0.75, domain.coverAspectRatio)
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
        val dto = GameDto(
            "1", "Test", "1",
            isFavorite = true,
            lastPlayedAt = "2024-01-15T10:30:00Z",
            totalPlayTime = 3600,
        )
        val domain = dto.toDomain()

        assertTrue(domain.isFavorite)
        assertEquals("2024-01-15T10:30:00Z", domain.lastPlayedAt)
        assertEquals(3600, domain.totalPlayTime)
    }

    @Test
    fun gameDtoToGameDetailIncludesScreenshots() {
        val dto = GameDto("1", "Test", "1", screenshotUrls = listOf("https://example.com/ss1.jpg", "https://example.com/ss2.jpg"))
        val detail = dto.toGameDetail()

        assertEquals("Test", detail.game.title)
        assertEquals(2, detail.screenshots.size)
        assertEquals("https://example.com/ss1.jpg", detail.screenshots[0])
    }

    @Test
    fun gameDtoToGameDetailWithoutScreenshots() {
        val dto = GameDto("1", "Test", "1")
        val detail = dto.toGameDetail()

        assertTrue(detail.screenshots.isEmpty())
    }

    @Test
    fun saveStateDtoMapsCorrectly() {
        val dto = SaveStateDto(
            id = 1,
            gameId = 1,
            name = "Slot 1",
            fileSize = 65536,
            isAuto = false,
            createdAt = "2024-01-15T10:30:00Z",
        )
        val domain = dto.toDomain()

        assertEquals(1L, domain.id)
        assertEquals(1L, domain.gameId)
        assertEquals("Slot 1", domain.name)
        assertEquals(65536, domain.fileSize)
        assertFalse(domain.isAuto)
        assertNotNull(domain.createdAt)
    }

    @Test
    fun authResponseMapsCorrectly() {
        val dto = AuthResponse(
            accessToken = "access123",
            refreshToken = "refresh456",
            user = UserDto("1", "testuser", "test@example.com", "user"),
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
        val dto = GameDto(
            "1", "Super Mario Bros.", "1",
            consoleName = "NES",
            fileName = "smb.nes",
            lastPlayedAt = "2024-01-15T10:30:00Z",
            totalPlayTime = 7200,
        )
        val game = dto.toDomain()

        assertEquals("Super Mario Bros.", game.title)
        assertEquals("2024-01-15T10:30:00Z", game.lastPlayedAt)
        assertEquals(7200, game.totalPlayTime)
    }

    @Test
    fun favoriteGamesHaveIsFavoriteTrue() {
        val dto = GameDto("1", "Zelda", "1", consoleName = "NES", fileName = "zelda.nes", isFavorite = true)
        val game = dto.toDomain()

        assertEquals("Zelda", game.title)
        assertTrue(game.isFavorite)
    }

    @Test
    fun coreResponseMapsCorrectly() {
        val dto = LibretroCoreDto(
            id = 1,
            name = "nestopia",
            displayName = "Nestopia UE",
            version = "1.52.0",
            platforms = "windows,linux,macos,android",
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
            data = listOf(GameDto("1", "Test", "1")),
            total = 1,
            page = 1,
            pageSize = 50,
        )

        assertEquals(1, response.data.size)
        assertEquals("Test", response.data[0].title)
        assertEquals(50, response.pageSize)
    }
}
