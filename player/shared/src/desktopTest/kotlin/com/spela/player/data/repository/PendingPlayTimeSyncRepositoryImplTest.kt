package com.spela.player.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.spela.player.data.local.SpelaDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PendingPlayTimeSyncRepositoryImplTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: SpelaDatabase
    private lateinit var repo: PendingPlayTimeSyncRepositoryImpl

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SpelaDatabase.Schema.create(driver)
        database = SpelaDatabase(driver)
        repo = PendingPlayTimeSyncRepositoryImpl(database)
    }

    @Test
    fun enqueueAssignsIdAndPersistsAllFields() = runTest {
        val id = repo.enqueue(
            clientReportId = "report-1",
            serverUrl = "http://server",
            userId = "user-1",
            gameId = "42",
            gameTitle = "Metroid Prime",
            durationSeconds = 95L,
            playedAt = 1_700_000_000_000L,
            createdAt = 1_700_000_030_000L,
        )
        assertTrue(id > 0L)

        val row = repo.getById(id)
        assertNotNull(row)
        assertEquals("report-1", row.clientReportId)
        assertEquals("http://server", row.serverUrl)
        assertEquals("user-1", row.userId)
        assertEquals("42", row.gameId)
        assertEquals("Metroid Prime", row.gameTitle)
        assertEquals(95L, row.durationSeconds)
        assertEquals(1_700_000_000_000L, row.playedAt)
        assertEquals(1_700_000_030_000L, row.createdAt)
        assertEquals(0, row.retryCount)
        assertNull(row.lastError)
    }

    @Test
    fun getForContextScopesByServerAndUser() = runTest {
        val matching = repo.enqueueDefault("report-a", serverUrl = "http://a", userId = "u1", createdAt = 1L)
        repo.enqueueDefault("report-b", serverUrl = "http://b", userId = "u1", createdAt = 2L)
        repo.enqueueDefault("report-c", serverUrl = "http://a", userId = "u2", createdAt = 3L)

        val scoped = repo.getForContext("http://a", "u1")

        assertEquals(listOf(matching), scoped.map { it.id })
    }

    @Test
    fun getAllReturnsRowsInFifoOrder() = runTest {
        val first = repo.enqueueDefault("report-1", createdAt = 1L)
        val second = repo.enqueueDefault("report-2", createdAt = 2L)
        val third = repo.enqueueDefault("report-3", createdAt = 3L)

        assertEquals(listOf(first, second, third), repo.getAll().map { it.id })
    }

    @Test
    fun markRetryAndDeleteUpdateQueue() = runTest {
        val id = repo.enqueueDefault("report-1", createdAt = 1L)

        repo.markRetry(id, "offline")
        val retried = repo.getById(id)
        assertNotNull(retried)
        assertEquals(1, retried.retryCount)
        assertEquals("offline", retried.lastError)

        repo.delete(id)

        assertEquals(0L, repo.count())
        assertNull(repo.getById(id))
    }

    @Test
    fun observeSnapshotExposesAggregateStateAndJobs() = runTest {
        val id = repo.enqueueDefault("report-1", durationSeconds = 125L, createdAt = 1L)
        repo.markRetry(id, "timeout")
        repo.setDraining(true)

        val snapshot = repo.observeSnapshot().first()

        assertEquals(1, snapshot.pendingCount)
        assertEquals(1, snapshot.retryingCount)
        assertEquals(0, snapshot.stuckCount)
        assertEquals(125L, snapshot.totalSeconds)
        assertTrue(snapshot.isDraining)
        val job = snapshot.jobs.single()
        assertEquals(id, job.id)
        assertEquals("report-1", job.clientReportId)
        assertEquals("timeout", job.lastError)
    }

    @Test
    fun queueSurvivesAcrossDatabaseReopen() = runTest {
        val id = repo.enqueueDefault("report-1", createdAt = 1L)

        val rebuilt = PendingPlayTimeSyncRepositoryImpl(SpelaDatabase(driver))

        assertNotNull(rebuilt.getById(id))
    }

    private suspend fun PendingPlayTimeSyncRepositoryImpl.enqueueDefault(
        clientReportId: String,
        serverUrl: String = "http://server",
        userId: String = "user-1",
        durationSeconds: Long = 30L,
        createdAt: Long,
    ): Long = enqueue(
        clientReportId = clientReportId,
        serverUrl = serverUrl,
        userId = userId,
        gameId = "42",
        gameTitle = "Metroid Prime",
        durationSeconds = durationSeconds,
        playedAt = createdAt,
        createdAt = createdAt,
    )
}
