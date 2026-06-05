package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.domain.model.DownloadFailureReason
import com.spela.player.domain.model.DownloadState
import com.spela.player.util.FileStorage
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Resume-path tests for [DownloadRepositoryImpl] (#1296). Drives the real
 * SpelaApiClient against a [FakeDownloadServer] (a MockEngine honouring
 * Range/If-Range like the Go server) plus an in-memory [FileStorage] and
 * SQLite, so the full client resume round-trip — Range request, 206 append,
 * stale-validator restart, partial persistence/restore — is exercised
 * without a device or backend. Server-side 206 correctness is covered
 * separately by the Go TestDownloadGame_RangeRequests.
 */
class DownloadRepositoryResumeTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SpelaDatabase
    private lateinit var fileStorage: ResumeInMemoryFileStorage
    private lateinit var server: FakeDownloadServer
    private lateinit var repo: DownloadRepositoryImpl

    private val gameId = "4242"
    private val fileName = "game.iso"
    private fun pathFor(id: String) = "${fileStorage.getGamesDir()}/$id/$fileName"

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        fileStorage = ResumeInMemoryFileStorage()
        server = FakeDownloadServer(content = bytes(0, 60), etag = "\"v1\"")
        val apiClient = SpelaApiClient(server.factory(), TokenManager())
        apiClient.setBaseUrl("http://localhost")
        repo = DownloadRepositoryImpl(apiClient, fileStorage, database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    /** Seed a partial: the first [prefixLen] bytes on disk + a tracking row. */
    private suspend fun seedPartial(id: String, prefixLen: Int, validator: String?, expectedSize: Long) {
        val path = pathFor(id)
        // Real getGamesDir() mkdirs(); the fake must register it too so the
        // launch-time orphan sweep doesn't early-return on a "missing" dir.
        fileStorage.createDirectory(fileStorage.getGamesDir())
        fileStorage.createDirectory("${fileStorage.getGamesDir()}/$id")
        fileStorage.writeFile(path, server.content.copyOfRange(0, prefixLen))
        database.spelaDatabaseQueries.insertPartialDownload(
            id, "Game $id", fileName, path, expectedSize, validator, null, 1_000L,
        )
    }

    @Test
    fun resumeAppendsFromOffsetAndProducesByteIdenticalFile() = runTest {
        seedPartial(gameId, prefixLen = 20, validator = "\"v1\"", expectedSize = 60)

        val result = repo.resumeDownload(gameId)

        assertTrue(result.isSuccess, "resume should succeed: ${result.exceptionOrNull()}")
        // Only the tail was requested — fewer bytes than a fresh download.
        assertEquals("bytes=20-", server.lastRange, "resume must Range-request from the on-disk offset")
        assertEquals("\"v1\"", server.lastIfRange, "resume must guard with the stored validator")
        // The on-disk file is byte-identical to a full download.
        assertEquals(server.content.toList(), fileStorage.readFile(pathFor(gameId)).toList())
        // Completion bookkeeping: partial row cleared, completed row written.
        assertNull(database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull())
        assertTrue(database.spelaDatabaseQueries.getDownload(gameId).executeAsOneOrNull() != null)
        assertEquals(DownloadState.COMPLETED, repo.observeDownload(gameId).first().state)
    }

    @Test
    fun resumeWithStaleValidatorRestartsCleanlyInsteadOfSplicing() = runTest {
        // Partial holds 20 bytes of the OLD file; the server now has different
        // content under a new validator.
        seedPartial(gameId, prefixLen = 20, validator = "\"old\"", expectedSize = 60)
        server.content = bytes(100, 50) // different bytes AND a different length
        server.etag = "\"v2\""

        val result = repo.resumeDownload(gameId)

        assertTrue(result.isSuccess, "stale-validator resume should still complete via a full restart")
        // The file is the NEW content in full — never the stale prefix spliced
        // onto fresh bytes.
        assertEquals(server.content.toList(), fileStorage.readFile(pathFor(gameId)).toList())
        assertEquals(50, fileStorage.getFileSize(pathFor(gameId)))
        assertEquals(DownloadState.COMPLETED, repo.observeDownload(gameId).first().state)
    }

    @Test
    fun pausedPartialIsRestoredOnLaunchAndProtectedFromOrphanSweep() = runTest {
        seedPartial(gameId, prefixLen = 25, validator = "\"v1\"", expectedSize = 60)
        // An unrelated orphan (no rows) should still be swept.
        val orphan = "${fileStorage.getGamesDir()}/9999"
        fileStorage.createDirectory(orphan)
        fileStorage.writeFile("$orphan/junk.bin", byteArrayOf(1))

        // Fresh repo instance = an app restart; nothing in memory yet.
        val freshRepo = DownloadRepositoryImpl(
            SpelaApiClient(server.factory(), TokenManager()).also { it.setBaseUrl("http://localhost") },
            fileStorage,
            database,
        )
        freshRepo.scanForOrphanedDownloads()

        val restored = freshRepo.observeDownload(gameId).first()
        assertEquals(DownloadState.PAUSED, restored.state, "partial must come back as resumable after restart")
        assertEquals(25L, restored.bytesDownloaded, "restored progress reflects bytes on disk")
        assertEquals(60L, restored.totalBytes)
        // Partial dir survived; the true orphan was removed.
        assertTrue(fileStorage.fileExists(pathFor(gameId)), "partial file must survive the orphan sweep")
        assertTrue("9999" !in fileStorage.listFiles(fileStorage.getGamesDir()), "real orphan still swept")
    }

    @Test
    fun partialDownloadIsNotResolvedAsPlayableUntilComplete() = runTest {
        seedPartial(gameId, prefixLen = 20, validator = "\"v1\"", expectedSize = 60)
        // Incomplete: never report it as a cached/playable game.
        assertNull(repo.getLocalGamePath(gameId))
        assertTrue(!repo.isGameCached(gameId))

        repo.resumeDownload(gameId)

        // After completion the partial row is gone, so it resolves normally.
        assertEquals(pathFor(gameId), repo.getLocalGamePath(gameId))
        assertTrue(repo.isGameCached(gameId))
    }

    @Test
    fun multiplePartialsResumeIndependently() = runTest {
        val other = "777"
        seedPartial(gameId, prefixLen = 10, validator = "\"v1\"", expectedSize = 60)
        seedPartial(other, prefixLen = 40, validator = "\"v1\"", expectedSize = 60)

        val result = repo.resumeDownload(gameId)

        assertTrue(result.isSuccess)
        assertEquals(server.content.toList(), fileStorage.readFile(pathFor(gameId)).toList())
        // The untouched partial is still pending and still on disk.
        assertTrue(database.spelaDatabaseQueries.getPartialDownload(other).executeAsOneOrNull() != null)
        assertEquals(40, fileStorage.getFileSize(pathFor(other)))
    }

    @Test
    fun resumeWithoutAPartialRecordFailsCleanly() = runTest {
        val result = repo.resumeDownload("does-not-exist")
        assertTrue(result.isFailure, "resume with no partial record must fail, not crash")
    }

    @Test
    fun resumableNetworkFailureKeepsPartialAndMarksPaused() = runTest {
        seedPartial(gameId, prefixLen = 20, validator = "\"v1\"", expectedSize = 60)
        fileStorage.failAppendWith = RuntimeException("connection reset by peer")

        val result = repo.resumeDownload(gameId)

        assertTrue(result.isFailure)
        val progress = repo.observeDownload(gameId).first()
        assertEquals(DownloadState.PAUSED, progress.state, "a transient network failure stays resumable")
        assertEquals(DownloadFailureReason.NETWORK, progress.failureReason)
        // Partial KEPT so the user can resume again.
        assertTrue(
            database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull() != null,
            "a resumable failure must keep the partial record",
        )
    }

    @Test
    fun terminalDiskFullFailureDiscardsPartialAndSurfacesReason() = runTest {
        seedPartial(gameId, prefixLen = 20, validator = "\"v1\"", expectedSize = 60)
        fileStorage.failAppendWith = RuntimeException("write failed: ENOSPC no space left on device")

        val result = repo.resumeDownload(gameId)

        assertTrue(result.isFailure)
        val progress = repo.observeDownload(gameId).first()
        assertEquals(DownloadState.FAILED, progress.state)
        assertEquals(DownloadFailureReason.DISK_FULL, progress.failureReason)
        // Terminal failure discards the partial so a restart is clean.
        assertNull(database.spelaDatabaseQueries.getPartialDownload(gameId).executeAsOneOrNull())
    }

    private fun bytes(start: Int, count: Int): ByteArray = ByteArray(count) { (start + it).toByte() }
}

/**
 * A MockEngine standing in for the Go download endpoint: serves the full file
 * (200) or a 206 tail for a `Range: bytes=N-` request, and falls back to a
 * full 200 when an `If-Range` validator is stale — matching the server.
 */
private class FakeDownloadServer(var content: ByteArray, var etag: String) {
    var lastRange: String? = null
    var lastIfRange: String? = null

    fun factory(): HttpClientEngineFactory<MockEngineConfig> =
        object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(MockEngineConfig().apply {
                    addHandler { request ->
                        val range = request.headers[HttpHeaders.Range]
                        val ifRange = request.headers[HttpHeaders.IfRange]
                        lastRange = range
                        lastIfRange = ifRange
                        val size = content.size
                        val start = range
                            ?.removePrefix("bytes=")?.substringBefore('-')?.toIntOrNull()
                        val stale = ifRange != null && ifRange != etag
                        when {
                            start == null || stale -> respond(
                                content,
                                HttpStatusCode.OK,
                                headersOf(
                                    HttpHeaders.ETag to listOf(etag),
                                    HttpHeaders.AcceptRanges to listOf("bytes"),
                                    // The real server always sets Content-Length;
                                    // MockEngine won't for a ByteArray, so be explicit.
                                    HttpHeaders.ContentLength to listOf(size.toString()),
                                ),
                            )
                            start in 0..size -> respond(
                                content.copyOfRange(start, size),
                                HttpStatusCode.PartialContent,
                                headersOf(
                                    HttpHeaders.ETag to listOf(etag),
                                    HttpHeaders.AcceptRanges to listOf("bytes"),
                                    HttpHeaders.ContentRange to listOf("bytes $start-${size - 1}/$size"),
                                    HttpHeaders.ContentLength to listOf((size - start).toString()),
                                ),
                            )
                            else -> respond(ByteArray(0), HttpStatusCode.RequestedRangeNotSatisfiable)
                        }
                    }
                    block()
                })
        }
}

/** In-memory [FileStorage] with real append semantics for the resume tests. */
private class ResumeInMemoryFileStorage : FileStorage {
    private val files = mutableMapOf<String, ByteArray>()
    private val dirs = mutableSetOf<String>()

    /** When set, the next append throws this — simulates a disk/IO failure. */
    var failAppendWith: Throwable? = null

    override fun getGamesDir(): String = "/test/games"
    override fun getCoresDir(): String = "/test/cores"
    override fun getSavesDir(): String = "/test/saves"
    override fun getBiosDir(): String = "/test/bios"

    override suspend fun createDirectory(path: String) { dirs.add(path) }

    override suspend fun writeFile(path: String, data: ByteArray) {
        files[path] = data
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) dirs.add(parent)
    }

    override suspend fun readFile(path: String): ByteArray = files[path] ?: byteArrayOf()
    override suspend fun fileExists(path: String): Boolean = path in dirs || path in files

    override suspend fun deleteFile(path: String) { files.remove(path) }

    override suspend fun deleteDirectory(path: String) {
        dirs.remove(path)
        val prefix = "$path/"
        files.keys.filter { it.startsWith(prefix) }.forEach { files.remove(it) }
        dirs.removeAll { it.startsWith(prefix) }
    }

    override suspend fun getDirectorySize(path: String): Long =
        files.entries.filter { it.key.startsWith("$path/") }.sumOf { it.value.size.toLong() }

    override suspend fun writeFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) {
        val buf = ArrayList<Byte>()
        writer { data, offset, length -> for (i in 0 until length) buf.add(data[offset + i]) }
        files[path] = buf.toByteArray()
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) dirs.add(parent)
    }

    override suspend fun appendFileStreaming(
        path: String,
        writer: suspend (append: suspend (ByteArray, Int, Int) -> Unit) -> Unit,
    ) {
        val buf = ArrayList<Byte>().apply { files[path]?.forEach { add(it) } }
        writer { data, offset, length ->
            failAppendWith?.let { throw it }
            for (i in 0 until length) buf.add(data[offset + i])
        }
        files[path] = buf.toByteArray()
    }

    override suspend fun getFileSize(path: String): Long = files[path]?.size?.toLong() ?: 0L

    override suspend fun listFiles(path: String): List<String> {
        val prefix = "$path/"
        val direct = mutableSetOf<String>()
        for (f in files.keys) if (f.startsWith(prefix)) direct.add(f.removePrefix(prefix).substringBefore('/'))
        for (d in dirs) if (d.startsWith(prefix)) direct.add(d.removePrefix(prefix).substringBefore('/'))
        return direct.toList()
    }

    override suspend fun isDirectory(path: String): Boolean = path in dirs

    override suspend fun zipDirectoryToBytes(dirPath: String): ByteArray? = null
    override suspend fun unzipBytesToDirectory(data: ByteArray, targetDir: String) {}
    override suspend fun extractFirstZipEntryFromFile(zipPath: String, destPath: String) {}
    override suspend fun tarDirectoryToFile(dirPath: String, destPath: String): Long = 0L
    override suspend fun extractTarFile(tarPath: String, destDir: String) {}
    override suspend fun sha256File(path: String): String? = null
}
