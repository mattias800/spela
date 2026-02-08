package com.spela.player.data.repository

import com.spela.player.data.remote.dto.*
import kotlin.test.*

class GameRepositoryImplTest {

    private val sampleConsoles = listOf(
        ConsoleDto(1, "NES", "NES", gameCount = 10),
        ConsoleDto(2, "SNES", "SNES", gameCount = 5),
    )

    private val sampleGames = listOf(
        GameDto(1, "Super Mario Bros.", 1, fileName = "smb.nes", fileSize = 40960, releaseDate = "1985-09-13"),
        GameDto(2, "Zelda", 1, fileName = "zelda.nes", fileSize = 131072, releaseDate = "1986-02-21"),
    )

    @Test
    fun consoleDtoMapsCorrectly() {
        val mapped = sampleConsoles.map { it.toDomain() }

        assertEquals(2, mapped.size)
        assertEquals("NES", mapped[0].name)
        assertEquals("NES", mapped[0].abbreviation)
        assertEquals(10, mapped[0].gameCount)
        assertEquals(1L, mapped[0].id)
    }

    @Test
    fun gameDtoMapsCorrectly() {
        val dto = sampleGames[0]
        val domain = dto.toDomain()

        assertEquals(1L, domain.id)
        assertEquals("Super Mario Bros.", domain.title)
        assertEquals(1L, domain.consoleId)
        assertEquals("1985-09-13", domain.releaseDate)
        assertEquals(40960, domain.fileSize)
        assertEquals("smb.nes", domain.fileName)
    }

    @Test
    fun gameDtoWithConsoleMapsConsoleName() {
        val consoleDto = ConsoleDto(1, "NES", "NES", gameCount = 10)
        val dto = GameDto(1, "Super Mario Bros.", 1, console = consoleDto, fileName = "smb.nes")
        val domain = dto.toDomain()

        assertEquals("NES", domain.consoleName)
    }

    @Test
    fun gameDtoToGameDetailIncludesScreenshots() {
        val dto = GameDto(1, "Test", 1, screenshotUrl = "https://example.com/ss1.jpg")
        val detail = dto.toGameDetail()

        assertEquals("Test", detail.game.title)
        assertEquals(1, detail.screenshots.size)
        assertEquals("https://example.com/ss1.jpg", detail.screenshots[0])
    }

    @Test
    fun gameDtoToGameDetailWithoutScreenshots() {
        val dto = GameDto(1, "Test", 1)
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
            user = UserDto(1, "testuser", "test@example.com", "user"),
        )
        val tokens = dto.toDomain()

        assertEquals("access123", tokens.accessToken)
        assertEquals("refresh456", tokens.refreshToken)

        val user = dto.extractUser()
        assertEquals("testuser", user.username)
        assertEquals("test@example.com", user.email)
    }

    @Test
    fun playHistoryExtractsGame() {
        val gameDto = GameDto(1, "Super Mario Bros.", 1, fileName = "smb.nes")
        val dto = PlayHistoryDto(id = 1, game = gameDto, lastPlayed = "2024-01-15T10:30:00Z")
        val game = dto.extractGame()

        assertNotNull(game)
        assertEquals("Super Mario Bros.", game.title)
    }

    @Test
    fun favoriteExtractsGame() {
        val gameDto = GameDto(1, "Zelda", 1, fileName = "zelda.nes")
        val dto = FavoriteDto(id = 1, game = gameDto)
        val game = dto.extractGame()

        assertNotNull(game)
        assertEquals("Zelda", game.title)
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
}
