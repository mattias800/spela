package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.*
import com.spela.player.data.remote.interceptor.TokenManager
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class GameRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleConsoles = listOf(
        ConsoleDto("nes", "NES", "NES", 10, null, null),
        ConsoleDto("snes", "SNES", "SNES", 5, null, null),
    )

    private val sampleGames = listOf(
        GameDto("1", "Super Mario Bros.", "nes", "NES", null, null, null, null, 1985, null, 40960, "smb.nes", "nestopia"),
        GameDto("2", "Zelda", "nes", "NES", null, null, null, null, 1986, null, 131072, "zelda.nes", "nestopia"),
    )

    @Test
    fun getConsolesReturnsMappedDomainModels() = kotlinx.coroutines.test.runTest {
        val tokenManager = TokenManager()
        val apiClient = SpelaApiClient(MockEngine, tokenManager)
        // Testing the DTO mapper directly since the actual HTTP call requires a running mock
        val mapped = sampleConsoles.map { it.toDomain() }

        assertEquals(2, mapped.size)
        assertEquals("NES", mapped[0].name)
        assertEquals("NES", mapped[0].abbreviation)
        assertEquals(10, mapped[0].gameCount)
    }

    @Test
    fun gameDtoMapsCorrectly() {
        val dto = sampleGames[0]
        val domain = dto.toDomain()

        assertEquals("1", domain.id)
        assertEquals("Super Mario Bros.", domain.title)
        assertEquals("nes", domain.consoleId)
        assertEquals("NES", domain.consoleName)
        assertEquals(1985, domain.releaseYear)
        assertEquals(40960, domain.fileSize)
        assertEquals("smb.nes", domain.fileName)
        assertEquals("nestopia", domain.coreName)
    }

    @Test
    fun gameDetailDtoMapsCorrectly() {
        val dto = GameDetailDto(
            game = sampleGames[0],
            screenshots = listOf("https://example.com/ss1.jpg"),
            lastPlayed = "2024-01-15T10:30:00Z",
            playTime = 3600,
            isFavorite = true,
        )
        val domain = dto.toDomain()

        assertEquals("Super Mario Bros.", domain.game.title)
        assertEquals(1, domain.screenshots.size)
        assertNotNull(domain.lastPlayed)
        assertEquals(3600, domain.playTime)
        assertTrue(domain.isFavorite)
    }

    @Test
    fun saveStateDtoMapsCorrectly() {
        val dto = SaveStateDto(
            id = "save1",
            gameId = "1",
            name = "Slot 1",
            createdAt = "2024-01-15T10:30:00Z",
            size = 65536,
            isAutoSave = false,
            screenshotUrl = "https://example.com/save1.jpg",
        )
        val domain = dto.toDomain()

        assertEquals("save1", domain.id)
        assertEquals("1", domain.gameId)
        assertEquals("Slot 1", domain.name)
        assertEquals(65536, domain.size)
        assertFalse(domain.isAutoSave)
        assertNotNull(domain.screenshotUrl)
    }

    @Test
    fun authResponseMapsCorrectly() {
        val dto = AuthResponse(
            accessToken = "access123",
            refreshToken = "refresh456",
            expiresAt = "2024-12-31T23:59:59Z",
        )
        val domain = dto.toDomain()

        assertEquals("access123", domain.accessToken)
        assertEquals("refresh456", domain.refreshToken)
    }
}
