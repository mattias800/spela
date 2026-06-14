package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.client.models.ConsoleKeyMappingDTO
import com.spela.client.models.UserPreferencesResponse
import com.spela.player.data.device.DeviceManager
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the player side of the synced positional gamepad mapping layer
 * (#1334, Slice B): push assembles `positionMappings` from GamepadMappingEntity
 * (merged with keycode mappings), and pull imports the server's positional
 * mappings into GamepadMappingEntity.
 */
class GamepadMappingSyncTest {

    private fun database(): SpelaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        return SpelaDatabase(driver)
    }

    private suspend fun repo(db: SpelaDatabase, engineFactory: HttpClientEngineFactory<MockEngineConfig>): PreferencesRepositoryImpl {
        val apiClient = SpelaApiClient(engineFactory, TokenManager().also { it.setTokens("a", "r") })
            .also { it.setBaseUrl("http://localhost:8080") }
        return PreferencesRepositoryImpl(
            apiClient = apiClient,
            database = db,
            deviceManager = DeviceManager(db, apiClient),
            keyMappingRepository = KeyMappingRepositoryImpl(db, emptyMap()),
        )
    }

    @Test
    fun pushIncludesPositionMappings() = runTest {
        val db = database()
        // A console with only a positional override (no keyboard preset).
        db.spelaDatabaseQueries.insertGamepadMapping("nes:0:SOUTH", "nes", 0, "SOUTH", 8)
        db.spelaDatabaseQueries.insertGamepadMapping("nes:0:WEST", "nes", 0, "WEST", 0)

        var capturedBody: String? = null
        val factory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler { request ->
                        capturedBody = request.body.toByteArray().decodeToString()
                        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    block()
                })
        }

        repo(db, factory).pushKeyMappingsToServer()

        val body = capturedBody ?: error("no request captured")
        assertTrue(body.contains("positionMappings"), "push body carries positionMappings: $body")
        assertTrue(body.contains("\"SOUTH\":8"), "push body carries SOUTH->8: $body")
        assertTrue(body.contains("\"WEST\":0"), "push body carries WEST->0: $body")
    }

    @Test
    fun syncImportsPositionMappings() = runTest {
        val db = database()
        val prefsJson = Json.encodeToString(
            UserPreferencesResponse.serializer(),
            UserPreferencesResponse(
                autoLoadSaveEnabled = false,
                autoSaveEnabled = false,
                autoUpdateCoresEnabled = false,
                consoleKeyMappings = mapOf(
                    "nes" to ConsoleKeyMappingDTO(
                        selectedMapping = "",
                        customMapping = emptyMap(),
                        positionMappings = mapOf("SOUTH" to 8L, "WEST" to 0L),
                    ),
                ),
                consoleSaveStatePolicies = emptyMap(),
                consoleShaders = emptyMap(),
                customKeyMapping = emptyMap(),
                defaultSecondScreenPage = "",
                gameSaveStatePolicies = emptyMap(),
                preferredRegions = emptyList(),
                raHardcoreEnabled = false,
                raLinked = false,
                raUsername = "",
                selectedKeyMapping = "",
                selectedShader = "none",
                selectedTheme = "",
                showPerformanceOverlay = false,
            ),
        )
        val factory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler {
                        respond(prefsJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    }
                    block()
                })
        }

        repo(db, factory).syncKeyMappingsFromServer()

        val rows = db.spelaDatabaseQueries.getAllGamepadMappings().executeAsList()
            .associate { it.gamepad_position to it.libretro_button_id }
        assertEquals(8L, rows["SOUTH"])
        assertEquals(0L, rows["WEST"])
    }
}
