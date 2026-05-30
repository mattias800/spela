package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.util.FileStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for #1207: BIOS sync must not attempt to download files
 * the server reports as absent (status == "missing"). Before the fix, the
 * client tried to GET every registry entry and 404-spammed the log for
 * optional BIOS the server doesn't hold (PS2, X68000, …).
 */
class BiosRepositorySyncTest {

    private fun biosFileJson(name: String, status: String): String =
        """{"bundle":false,"consoleId":"nes","consoleName":"NES","description":"d",""" +
            """"expectedMd5":"","md5":"","name":"$name","required":false,"size":0,""" +
            """"status":"$status","subDir":""}"""

    @Test
    fun syncSkipsServerMissingFilesInsteadOfRequesting404() = runTest {
        val requested = mutableListOf<String>()
        val statusJson =
            """{"consoles":[],"files":[""" +
                biosFileJson("present.bin", "present") + "," +
                biosFileJson("absent.bin", "missing") +
                "]}"

        val factory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler { req ->
                        val path = req.url.encodedPath
                        requested.add(path)
                        when {
                            path.endsWith("/api/bios") -> respond(
                                statusJson, HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                            path.contains("present.bin") -> respond(
                                byteArrayOf(1, 2, 3), HttpStatusCode.OK,
                            )
                            else -> respond(
                                """{"error":"bios file not found"}""", HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                })
        }
        val api = SpelaApiClient(factory, TokenManager()).apply { setBaseUrl("http://test") }
        val written = mutableListOf<String>()

        BiosRepository(api, RecordingFileStorage(written)).syncBiosFiles()

        assertTrue(
            requested.any { it.contains("present.bin") },
            "present BIOS should be downloaded; requests=$requested",
        )
        assertFalse(
            requested.any { it.contains("absent.bin") },
            "server-missing BIOS must not be requested (#1207); requests=$requested",
        )
        assertTrue(
            written.any { it.endsWith("present.bin") },
            "present BIOS should be written to disk; written=$written",
        )
    }
}

/** FileStorage that records writes and reports nothing exists yet. */
private class RecordingFileStorage(private val written: MutableList<String>) : FileStorage {
    override fun getGamesDir(): String = "/tmp/games"
    override fun getCoresDir(): String = "/tmp/cores"
    override fun getSavesDir(): String = "/tmp/saves"
    override fun getBiosDir(): String = "/tmp/bios"
    override suspend fun createDirectory(path: String) {}
    override suspend fun writeFile(path: String, data: ByteArray) { written.add(path) }
    override suspend fun readFile(path: String): ByteArray = byteArrayOf()
    override suspend fun fileExists(path: String): Boolean = false
    override suspend fun deleteFile(path: String) {}
    override suspend fun deleteDirectory(path: String) {}
    override suspend fun getDirectorySize(path: String): Long = 0
    override suspend fun writeFileStreaming(path: String, writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit) {}
    override suspend fun getFileSize(path: String): Long = 0
    override suspend fun listFiles(path: String): List<String> = emptyList()
    override suspend fun isDirectory(path: String): Boolean = false
    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0L
    override suspend fun extractTarFile(tarPath: String, destDir: String) {}
    override suspend fun sha256File(path: String): String? = null
}
