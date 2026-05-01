package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.interceptor.TokenManager
import com.spela.player.test.NoOpMockEngineFactory
import com.spela.player.util.FileStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cleanup-path tests for [DownloadRepositoryImpl] (#845).
 *
 * Two scenarios are exercised against in-memory primitives so they
 * stay fast and deterministic on the desktop suite:
 *   - the launch-time orphan scan removes per-game directories that
 *     have no row in the local downloads table, while preserving
 *     directories that DO have a tracking row.
 *   - cleanup that fails (deleteDirectory throws) does not crash the
 *     scan; the orphan stays on disk and the scan returns normally.
 *
 * The cancel-cleanup integration is verified on-device (logcat) since
 * exercising the suspendable HTTP path would need a full Ktor mock —
 * out of scope for these focused unit tests.
 */
class DownloadRepositoryCleanupTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SpelaDatabase
    private lateinit var fileStorage: InMemoryFileStorage
    private lateinit var repo: DownloadRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        fileStorage = InMemoryFileStorage()
        val apiClient = SpelaApiClient(NoOpMockEngineFactory, TokenManager())
        repo = DownloadRepositoryImpl(apiClient, fileStorage, database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun scanForOrphanedDownloadsRemovesUntrackedDirectoriesButKeepsTrackedOnes() = runTest {
        val gamesDir = fileStorage.getGamesDir()
        fileStorage.createDirectory(gamesDir)
        fileStorage.createDirectory("$gamesDir/100851") // orphan (process-died mid-download)
        fileStorage.writeFile("$gamesDir/100851/monkey.scummvm", byteArrayOf(0))
        fileStorage.createDirectory("$gamesDir/200000") // tracked, completed
        fileStorage.writeFile("$gamesDir/200000/game.rom", byteArrayOf(0))
        database.spelaDatabaseQueries.insertDownload(
            game_id = "200000",
            local_path = "$gamesDir/200000/game.rom",
            file_size = 1L,
            downloaded_at = 1_000L,
        )

        repo.scanForOrphanedDownloads()

        val remaining = fileStorage.listFiles(gamesDir)
        assertFalse("100851" in remaining, "untracked orphan should be removed")
        assertContains(remaining, "200000", "tracked download must survive the scan")
    }

    @Test
    fun scanForOrphanedDownloadsIsIdempotent() = runTest {
        val gamesDir = fileStorage.getGamesDir()
        fileStorage.createDirectory(gamesDir)
        fileStorage.createDirectory("$gamesDir/300000")

        repo.scanForOrphanedDownloads()
        // Second invocation: no surviving orphan, no exception, no
        // change. Lets us run this safely on every app launch even if
        // the previous launch already cleaned up.
        repo.scanForOrphanedDownloads()

        val remaining = fileStorage.listFiles(gamesDir)
        assertEquals(emptyList(), remaining)
    }

    @Test
    fun scanForOrphanedDownloadsHandlesMissingGamesDir() = runTest {
        // Fresh install: gamesDir doesn't exist. Scan must early-return
        // without throwing — otherwise the LaunchedEffect wrapper would
        // log a noisy stack trace on every first launch.
        repo.scanForOrphanedDownloads()
        // No assertion needed — passing the call without throwing is the test.
    }

    @Test
    fun scanForOrphanedDownloadsContinuesWhenIndividualDeleteFails() = runTest {
        // Simulates a stuck file handle, FUSE failure, or read-only
        // mount. cleanupPartialDownload's catch must absorb the error
        // so a single bad orphan doesn't prevent removing the rest.
        val gamesDir = fileStorage.getGamesDir()
        fileStorage.createDirectory(gamesDir)
        fileStorage.createDirectory("$gamesDir/badGame")
        fileStorage.failDeleteFor.add("$gamesDir/badGame")
        fileStorage.createDirectory("$gamesDir/goodGame")

        repo.scanForOrphanedDownloads()

        val remaining = fileStorage.listFiles(gamesDir)
        assertContains(remaining, "badGame", "delete failure leaves the dir on disk")
        assertFalse("goodGame" in remaining, "subsequent orphan still gets cleaned")
    }
}

/**
 * Lightweight in-memory [FileStorage] for the cleanup tests. Tracks
 * directory shape via paths separated by `/`. Real Android / desktop
 * implementations are file-system backed; we just need enough surface
 * to drive `scanForOrphanedDownloads`.
 */
private class InMemoryFileStorage : FileStorage {
    private val files = mutableMapOf<String, ByteArray>()
    private val dirs = mutableSetOf<String>()
    val failDeleteFor: MutableSet<String> = mutableSetOf()

    override fun getGamesDir(): String = "/test/games"
    override fun getCoresDir(): String = "/test/cores"
    override fun getSavesDir(): String = "/test/saves"
    override fun getBiosDir(): String = "/test/bios"

    override suspend fun createDirectory(path: String) {
        dirs.add(path)
    }

    override suspend fun writeFile(path: String, data: ByteArray) {
        files[path] = data
        // Implicitly mark parent as a directory so listFiles works.
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotEmpty()) dirs.add(parent)
    }

    override suspend fun readFile(path: String): ByteArray = files[path] ?: byteArrayOf()

    override suspend fun fileExists(path: String): Boolean =
        path in dirs || path in files

    override suspend fun deleteFile(path: String) {
        if (path in failDeleteFor) throw RuntimeException("simulated delete failure for $path")
        files.remove(path)
    }

    override suspend fun deleteDirectory(path: String) {
        if (path in failDeleteFor) throw RuntimeException("simulated delete failure for $path")
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
        writer { data, offset, length ->
            for (i in 0 until length) buf.add(data[offset + i])
        }
        files[path] = buf.toByteArray()
    }

    override suspend fun getFileSize(path: String): Long =
        files[path]?.size?.toLong() ?: 0L

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
