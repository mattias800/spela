package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import com.spela.player.domain.model.PendingUploadKind
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit-tests the SQLDelight-backed pending-upload queue (#804 phase 6).
 * Pins the contract the future drain worker hangs off:
 *
 *   - FIFO ordering across enqueue / getAll
 *   - per-session scoping
 *   - retry counter + last_error increment
 *   - delete on success
 *   - persistence across "process restart" (reopening the same DB)
 *
 * The queue is deliberately data-only; the upload side lives in a
 * later slice, so these tests don't talk to the network.
 */
class PendingSaveUploadRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SpelaDatabase
    private lateinit var repo: PendingSaveUploadRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        repo = PendingSaveUploadRepositoryImpl(database)
    }

    @Test
    fun enqueueAssignsAutoIncrementedIdAndPersistsAllFields() = runTest {
        val id = repo.enqueue(
            sessionId = "session-1",
            kind = PendingUploadKind.Manual,
            slot = null,
            name = "Boss attempt 17",
            coreName = "dolphin",
            compression = "gzip",
            filePath = "/tmp/.tmp-save-1",
            fileSize = 75_000_000L,
            screenshotPath = "/tmp/screenshot-1.png",
            createdAt = 1_000L,
        )
        assertTrue(id > 0L)

        val row = repo.getById(id)
        assertNotNull(row)
        assertEquals("session-1", row.sessionId)
        assertEquals(PendingUploadKind.Manual, row.kind)
        assertNull(row.slot)
        assertEquals("Boss attempt 17", row.name)
        assertEquals("dolphin", row.coreName)
        assertEquals("gzip", row.compression)
        assertEquals("/tmp/.tmp-save-1", row.filePath)
        assertEquals(75_000_000L, row.fileSize)
        assertEquals("/tmp/screenshot-1.png", row.screenshotPath)
        assertEquals(1_000L, row.createdAt)
        assertEquals(0, row.retryCount)
        assertNull(row.lastError)
    }

    @Test
    fun getAllReturnsRowsInFifoOrder() = runTest {
        // FIFO matters because the drain worker uploads in order — a
        // user who saves twice in a row expects their second save to
        // overwrite the first one server-side, which only happens if
        // the queue serialises properly.
        val first = repo.enqueueDefault(sessionId = "s", createdAt = 1_000L)
        val second = repo.enqueueDefault(sessionId = "s", createdAt = 2_000L)
        val third = repo.enqueueDefault(sessionId = "s", createdAt = 3_000L)

        val all = repo.getAll().map { it.id }
        assertEquals(listOf(first, second, third), all)
    }

    @Test
    fun getForSessionScopesByConsole() = runTest {
        val a1 = repo.enqueueDefault(sessionId = "a", createdAt = 1L)
        val b1 = repo.enqueueDefault(sessionId = "b", createdAt = 2L)
        val a2 = repo.enqueueDefault(sessionId = "a", createdAt = 3L)

        val a = repo.getForSession("a").map { it.id }
        assertEquals(listOf(a1, a2), a)
        val b = repo.getForSession("b").map { it.id }
        assertEquals(listOf(b1), b)
    }

    @Test
    fun deleteRemovesRowAndDecrementsCount() = runTest {
        val id = repo.enqueueDefault(sessionId = "s", createdAt = 1L)
        assertEquals(1L, repo.count())

        repo.delete(id)

        assertEquals(0L, repo.count())
        assertNull(repo.getById(id))
    }

    @Test
    fun markRetryIncrementsCounterAndStoresLastError() = runTest {
        val id = repo.enqueueDefault(sessionId = "s", createdAt = 1L)
        assertEquals(0, repo.getById(id)!!.retryCount)

        repo.markRetry(id, "HTTP 503")
        repo.markRetry(id, "timeout")

        val row = repo.getById(id)!!
        assertEquals(2, row.retryCount)
        assertEquals("timeout", row.lastError, "last_error stores the latest")
    }

    @Test
    fun queueSurvivesAcrossDatabaseReopen() = runTest {
        // Simulates an app kill: we enqueue, drop the DB handle,
        // re-create it from the same on-disk file (here: the same
        // in-memory connection), and verify the row still exists.
        // SQLDelight migrations + the queue table being durable is
        // what fixes the silent-drop case the spec calls out.
        val id = repo.enqueueDefault(sessionId = "s", createdAt = 1L)

        // Rebuild repo against the same database — equivalent to a
        // fresh app start with the same persistent DB file in
        // production. JDBC IN_MEMORY survives only as long as the
        // driver is open, so we keep the driver but reopen the
        // SpelaDatabase wrapper.
        val rebuilt = PendingSaveUploadRepositoryImpl(SpelaDatabase(driver))
        val row = rebuilt.getById(id)
        assertNotNull(row)
        assertEquals(id, row.id)
    }

    private suspend fun PendingSaveUploadRepositoryImpl.enqueueDefault(
        sessionId: String,
        createdAt: Long,
    ): Long = enqueue(
        sessionId = sessionId,
        kind = PendingUploadKind.Manual,
        slot = null,
        name = "x",
        coreName = "core",
        compression = "",
        filePath = "/tmp/$sessionId-$createdAt",
        fileSize = 1024L,
        screenshotPath = null,
        createdAt = createdAt,
    )
}
