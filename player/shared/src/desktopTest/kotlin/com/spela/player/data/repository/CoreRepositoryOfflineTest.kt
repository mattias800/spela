package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.FileStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreRepositoryOfflineTest {

    @Test
    fun recommendedCoreFallsBackToCachedConsoleDefaultWhenServerOffline() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        val database = SpelaDatabase(driver)
        database.spelaDatabaseQueries.insertCachedConsole(
            id = "nes",
            name = "Nintendo Entertainment System",
            abbreviation = "nes",
            game_count = 1,
            color_theme = "#e74c3c",
            cover_aspect_ratio = 0.72,
            default_core = "fceumm",
            icon_url = "",
            logo_url = "",
            save_state_support = 1,
        )
        database.spelaDatabaseQueries.insertCachedGame(
            id = "game-1",
            console_id = "nes",
            title = "Super Mario Bros",
            console_name = "Nintendo Entertainment System",
            cover_url = null,
            description = null,
            developer = null,
            publisher = null,
            release_date = null,
            genre = null,
            file_size = 40960,
            file_name = "smb.nes",
            cover_aspect_ratio = 0.75,
            disc_count = 0,
            is_favorite = 0,
            is_in_play_later = 0,
            last_played_at = null,
            total_play_time = 0,
            cached_at = 1,
        )
        val failingEngineFactory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler {
                        throw java.net.ConnectException("Offline")
                    }
                    block()
                })
        }
        val repo = CoreRepositoryImpl(
            apiClient = SpelaApiClient(failingEngineFactory, TokenManager()),
            database = database,
            fileStorage = NoopFileStorage,
            httpClient = HttpClient(failingEngineFactory),
        )

        val result = repo.getRecommendedCore("game-1")

        assertTrue(result.isSuccess)
        assertEquals("fceumm", result.getOrThrow().name)
    }
}

private object NoopFileStorage : FileStorage {
    override fun getGamesDir(): String = "/games"
    override fun getCoresDir(): String = "/cores"
    override fun getSavesDir(): String = "/saves"
    override fun getBiosDir(): String = "/bios"
    override suspend fun createDirectory(path: String) = Unit
    override suspend fun writeFile(path: String, data: ByteArray) = Unit
    override suspend fun readFile(path: String): ByteArray = ByteArray(0)
    override suspend fun fileExists(path: String): Boolean = false
    override suspend fun deleteFile(path: String) = Unit
    override suspend fun deleteDirectory(path: String) = Unit
    override suspend fun getDirectorySize(path: String): Long = 0
    override suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) = Unit
    override suspend fun getFileSize(path: String): Long = 0
    override suspend fun listFiles(path: String): List<String> = emptyList()
    override suspend fun isDirectory(path: String): Boolean = false
    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) = Unit
    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) = Unit
    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0
    override suspend fun extractTarFile(tarPath: String, destDir: String) = Unit
    override suspend fun sha256File(path: String): String? = null
}
